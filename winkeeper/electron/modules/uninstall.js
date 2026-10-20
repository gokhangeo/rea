const fs = require('fs');
const fsp = fs.promises;
const path = require('path');
const { spawn } = require('child_process');
const { app: electronApp, shell } = require('electron');
const os = require('os');
const { runPS, runElevated } = require('../ps');
const { dirSize } = require('../fsutil');

let apps = [];
let allowedLeftovers = new Set();

const LIST_CMD = `
$p = 'HKLM:\\Software\\Microsoft\\Windows\\CurrentVersion\\Uninstall\\*',
     'HKLM:\\Software\\WOW6432Node\\Microsoft\\Windows\\CurrentVersion\\Uninstall\\*',
     'HKCU:\\Software\\Microsoft\\Windows\\CurrentVersion\\Uninstall\\*'
@(Get-ItemProperty $p -ErrorAction SilentlyContinue |
  Where-Object { $_.DisplayName -and -not $_.SystemComponent -and -not $_.ParentKeyName -and $_.UninstallString } |
  Select-Object DisplayName, DisplayVersion, Publisher, InstallLocation, UninstallString, EstimatedSize, InstallDate, DisplayIcon, PSPath) |
  ConvertTo-Json -Compress`;

const STORE_CMD = `
@(Get-AppxPackage -ErrorAction SilentlyContinue |
  Where-Object { -not $_.IsFramework -and -not $_.NonRemovable -and $_.SignatureKind -eq 'Store' } |
  Select-Object Name, PackageFullName, Version, Publisher, InstallLocation) | ConvertTo-Json -Compress`;

function parseJson(s) {
  let d = [];
  try { d = JSON.parse(s.trim() || '[]'); } catch {}
  return Array.isArray(d) ? d : [d];
}

function iconPath(a) {
  const cands = [];
  if (a.DisplayIcon) cands.push(a.DisplayIcon.replace(/"/g, '').replace(/,\s*-?\d+$/, '').trim());
  const u = (a.UninstallString || '').match(/"([^"]+\.exe)"|^(\S+\.exe)/i);
  if (u && !/msiexec/i.test(u[1] || u[2])) cands.push(u[1] || u[2]);
  return cands.find((c) => c && /\.(exe|ico|dll)$/i.test(c) && fs.existsSync(c));
}

async function iconFor(p) {
  if (!p) return null;
  try { return (await electronApp.getFileIcon(p, { size: 'large' })).toDataURL(); } catch { return null; }
}

function prettyStore(name) {
  const last = name.split('.').pop();
  return last.replace(/([a-z])([A-Z])/g, '$1 $2');
}

async function list() {
  const [desk, store] = await Promise.all([runPS(LIST_CMD), runPS(STORE_CMD)]);
  const seen = new Set();
  apps = [];
  for (const a of parseJson(desk.stdout)) {
    const key = `${a.DisplayName}|${a.DisplayVersion}`;
    if (seen.has(key)) continue;
    seen.add(key);
    const d = /^\d{8}$/.test(a.InstallDate || '') ? `${a.InstallDate.slice(0, 4)}-${a.InstallDate.slice(4, 6)}-${a.InstallDate.slice(6)}` : '';
    apps.push({
      id: apps.length, kind: 'desktop', name: a.DisplayName, version: a.DisplayVersion || '', publisher: a.Publisher || '',
      location: a.InstallLocation || '', size: (Number(a.EstimatedSize) || 0) * 1024, installDate: d,
      uninstallString: a.UninstallString, icon: iconPath(a),
      regKey: String(a.PSPath || '').replace(/^.*::/, '').replace(/^HKEY_LOCAL_MACHINE/, 'HKLM').replace(/^HKEY_CURRENT_USER/, 'HKCU'),
    });
  }
  for (const s of parseJson(store.stdout)) {
    apps.push({
      id: apps.length, kind: 'store', name: prettyStore(s.Name), version: s.Version || '',
      publisher: (s.Publisher || '').replace(/^CN=([^,]+).*/, '$1'), location: s.InstallLocation || '', size: 0, installDate: '',
      packageFullName: s.PackageFullName, packageName: s.Name,
    });
  }
  const icons = await Promise.all(apps.map((a) => iconFor(a.icon)));
  apps.forEach((a, i) => { a.iconData = icons[i]; });
  return apps.map(({ uninstallString, icon, packageFullName, ...rest }) => rest);
}

function runUninstaller(id) {
  const app = apps[id];
  if (!app) return Promise.resolve({ ok: false, error: 'Uygulama bulunamadı' });
  if (app.kind === 'store') {
    return runPS(`Remove-AppxPackage -Package '${app.packageFullName.replace(/'/g, "''")}'`).then((r) => ({ ok: r.ok, error: r.stderr.trim() }));
  }
  return new Promise((resolve) => {
    const child = spawn(app.uninstallString, [], { shell: true, windowsHide: false });
    child.on('error', (e) => resolve({ ok: false, error: e.message }));
    child.on('exit', (code) => resolve({ ok: code === 0, code }));
  });
}

const PROTECTED = new Set(['microsoft', 'windows', 'programs', 'packages', 'temp', 'google', 'mozilla', 'common files', 'nvidia corporation', 'intel', 'amd', 'windowsapps', 'package cache']);

function cleanName(n) {
  return n.replace(/\(.*?\)/g, '').replace(/\bv?\d+(\.\d+)+\b/g, '').replace(/\b(x64|x86|64-bit|32-bit)\b/gi, '').replace(/\s+/g, ' ').trim().toLowerCase();
}

async function leftovers(id) {
  const app = apps[id];
  if (!app) return [];
  const found = new Map();
  const add = (p, personal, why) => { if (!found.has(p)) found.set(p, { personal, why }); };

  if (app.kind === 'store') {
    const p = path.join(process.env.LOCALAPPDATA || '', 'Packages');
    let names = [];
    try { names = await fsp.readdir(p); } catch {}
    for (const n of names) if (n.toLowerCase().startsWith(app.packageName.toLowerCase() + '_')) add(path.join(p, n), true, 'Uygulama verisi');
  } else {
    const name = cleanName(app.name);
    const pub = (app.publisher || '').toLowerCase().replace(/[,.]?\s*(inc|ltd|llc|gmbh|corp|corporation)\.?$/i, '').trim();
    const roots = [
      { dir: process.env.APPDATA, personal: true, why: 'Ayarlar ve kullanıcı verisi' },
      { dir: process.env.LOCALAPPDATA, personal: false, why: 'Yerel önbellek' },
      { dir: path.join(process.env.LOCALAPPDATA || '', 'Programs'), personal: false, why: 'Kullanıcı kurulumu' },
      { dir: process.env.ProgramData, personal: false, why: 'Ortak veri' },
      { dir: process.env.ProgramFiles, personal: false, why: 'Program klasörü' },
      { dir: process.env['ProgramFiles(x86)'], personal: false, why: 'Program klasörü' },
    ].filter((r) => r.dir && fs.existsSync(r.dir));

    if (app.location && fs.existsSync(app.location) && path.parse(app.location).root !== path.resolve(app.location)) add(path.resolve(app.location), false, 'Kurulum klasörü');
    for (const r of roots) {
      let names = [];
      try { names = await fsp.readdir(r.dir); } catch {}
      for (const n of names) {
        const low = n.toLowerCase();
        if (PROTECTED.has(low)) continue;
        if (name && low === name) add(path.join(r.dir, n), r.personal, r.why);
        else if (pub && low === pub) {
          let sub = [];
          try { sub = await fsp.readdir(path.join(r.dir, n)); } catch {}
          for (const s of sub) if (cleanName(s) === name) add(path.join(r.dir, n, s), r.personal, r.why);
        }
      }
    }
  }

  const out = [];
  for (const [p, meta] of found) {
    const { size } = await dirSize(p);
    out.push({ path: p, size, kind: 'dir', ...meta });
  }
  if (app.kind === 'desktop') out.push(...(await registryLeftovers(app)));
  out.forEach((o) => allowedLeftovers.add(o.path));
  return out;
}

// Kayıt defterinde uygulamaya ait anahtarları bulur (Software altı + kaldırma kaydı).
const REG_ROOTS = ['HKCU\\Software', 'HKLM\\Software', 'HKLM\\Software\\WOW6432Node'];
async function regChildren(keys) {
  const list = keys.map((k) => `'${k.replace(/'/g, "''")}'`).join(',');
  const r = await runPS(`$o=@{}; foreach($k in @(${list})){ $o[$k] = @(Get-ChildItem -Path ('Registry::' + $k) -ErrorAction SilentlyContinue | ForEach-Object { $_.PSChildName }) }; $o | ConvertTo-Json -Compress -Depth 3`);
  try { return JSON.parse(r.stdout.trim() || '{}'); } catch { return {}; }
}

async function registryLeftovers(app) {
  const name = cleanName(app.name);
  const pub = (app.publisher || '').toLowerCase().replace(/[,.]?\s*(inc|ltd|llc|gmbh|corp|corporation)\.?$/i, '').trim();
  const found = [];
  const roots = await regChildren(REG_ROOTS);
  const pubKeys = [];
  for (const root of REG_ROOTS) {
    for (const n of roots[root] || []) {
      const low = n.toLowerCase();
      if (PROTECTED.has(low) || low === 'classes' || low === 'policies' || low === 'wow6432node') continue;
      if (name && cleanName(n) === name) found.push(`${root}\\${n}`);
      else if (pub && low === pub) pubKeys.push(`${root}\\${n}`);
    }
  }
  if (pubKeys.length) {
    const subs = await regChildren(pubKeys);
    for (const pk of pubKeys) for (const n of subs[pk] || []) if (cleanName(n) === name) found.push(`${pk}\\${n}`);
  }
  if (app.regKey) {
    const ex = await runPS(`Test-Path -Path 'Registry::${app.regKey.replace(/'/g, "''")}'`);
    if (ex.stdout.trim() === 'True') found.push(app.regKey);
  }
  return [...new Set(found)].map((k) => ({
    path: k, size: 0, kind: 'reg', personal: k.startsWith('HKCU'),
    why: k === app.regKey ? 'Kaldırma kaydı (Programlar listesi)' : 'Kayıt defteri ayarları',
  }));
}

// Anahtarları siler. HKLM anahtarları için tek bir UAC onayı istenir.
async function removeRegistry(keys) {
  if (!keys.length) return [];
  const q = (x) => x.replace(/'/g, "''");
  const script = (list) => list.map((k) => `Remove-Item -Path 'Registry::${q(k)}' -Recurse -Force -ErrorAction SilentlyContinue`).join('\n');
  const user = keys.filter((k) => k.startsWith('HKCU'));
  const machine = keys.filter((k) => !k.startsWith('HKCU'));
  if (user.length) await runPS(script(user));
  if (machine.length) await runElevated(script(machine));
  const check = await runPS(`@(${keys.map((k) => `[bool](Test-Path -Path 'Registry::${q(k)}')`).join(',')}) | ConvertTo-Json -Compress`);
  let still = [];
  try { still = [].concat(JSON.parse(check.stdout.trim())); } catch {}
  return keys.map((k, i) => ({ path: k, ok: still[i] === false, error: still[i] ? 'Silinemedi' : undefined }));
}

async function trash(paths) {
  const results = [];
  const allowed = (paths || []).filter((p) => allowedLeftovers.has(p));
  const reg = allowed.filter((p) => /^HK(CU|LM)\\/.test(p));
  for (const r of await removeRegistry(reg)) { if (r.ok) allowedLeftovers.delete(r.path); results.push(r); }
  for (const p of paths || []) {
    if (reg.includes(p)) continue;
    if (!allowedLeftovers.has(p)) { results.push({ path: p, ok: false, error: 'İzin verilmeyen yol' }); continue; }
    try { await shell.trashItem(p); allowedLeftovers.delete(p); results.push({ path: p, ok: true }); }
    catch (e) { results.push({ path: p, ok: false, error: e.message }); }
  }
  return results;
}

function reveal(id) {
  const a = apps[id];
  if (a && a.location && fs.existsSync(a.location)) shell.openPath(a.location);
}

module.exports = { list, runUninstaller, leftovers, trash, reveal };

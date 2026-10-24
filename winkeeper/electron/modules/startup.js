const fs = require('fs');
const path = require('path');
const { app: electronApp, shell, dialog } = require('electron');
const { runPS, runElevated } = require('../ps');

// Windows açılırken başlayan her şey tek listede: kayıt defteri Run anahtarları, Başlangıç klasörleri,
// oturum açılınca/açılışta çalışan zamanlanmış görevler ve Mağaza uygulamalarının başlangıç görevleri.
// Açma/kapama Görev Yöneticisi'nin kullandığı aynı yöntemle yapılır (StartupApproved), yani Windows
// ayarlarında da aynı durum görünür.

const CV = 'Software\\Microsoft\\Windows\\CurrentVersion';
const LIST_CMD = `
$ErrorActionPreference = 'SilentlyContinue'
function St($a, $n) { $v = (Get-ItemProperty -Path $a -Name $n).$n; if ($v -and ($v[0] % 2) -eq 1) { 'off' } else { 'on' } }
function ExeOf($c) {
  if (-not $c) { return '' }
  $c = [Environment]::ExpandEnvironmentVariables($c.Trim())
  if ($c.StartsWith('"')) { return $c.Substring(1, [Math]::Max(0, $c.IndexOf('"', 1) - 1)) }
  $m = [regex]::Match($c, '^(.+?\\.(exe|bat|cmd|com|vbs|ps1|lnk))', 'IgnoreCase')
  if ($m.Success) { return $m.Groups[1].Value }
  return ($c -split ' ')[0]
}
function Info($exe) {
  $pub = ''; $desc = ''; $ok = $false
  if ($exe -and (Test-Path -LiteralPath $exe)) {
    $ok = $true
    $vi = (Get-Item -LiteralPath $exe).VersionInfo
    if ($vi) { $pub = [string]$vi.CompanyName; $desc = [string]$vi.FileDescription }
  }
  @{ pub = $pub.Trim(); desc = $desc.Trim(); exists = $ok }
}
$out = @()
$sets = @(
  @{ src = 'Kayıt (kullanıcı)'; kind = 'reg'; key = 'HKCU:\\${CV}\\Run'; ap = 'HKCU:\\${CV}\\Explorer\\StartupApproved\\Run'; admin = $false },
  @{ src = 'Kayıt (tüm kullanıcılar)'; kind = 'reg'; key = 'HKLM:\\${CV}\\Run'; ap = 'HKLM:\\${CV}\\Explorer\\StartupApproved\\Run'; admin = $true },
  @{ src = 'Kayıt (32 bit)'; kind = 'reg'; key = 'HKLM:\\Software\\WOW6432Node\\Microsoft\\Windows\\CurrentVersion\\Run'; ap = 'HKLM:\\${CV}\\Explorer\\StartupApproved\\Run32'; admin = $true }
)
foreach ($s in $sets) {
  $p = Get-ItemProperty $s.key
  if ($p) { $p.PSObject.Properties | Where-Object { $_.Name -notlike 'PS*' } | ForEach-Object {
    $exe = ExeOf ([string]$_.Value); $i = Info $exe
    $out += [pscustomobject]@{ kind = $s.kind; source = $s.src; name = $_.Name; command = [string]$_.Value; exe = $exe; key = $s.key;
      publisher = $i.pub; desc = $i.desc; exists = $i.exists; ap = $s.ap; admin = $s.admin; state = (St $s.ap $_.Name) } } }
}
$sh = New-Object -ComObject WScript.Shell
$folders = @(
  @{ src = 'Başlangıç klasörü'; dir = [Environment]::GetFolderPath('Startup'); ap = 'HKCU:\\${CV}\\Explorer\\StartupApproved\\StartupFolder'; admin = $false },
  @{ src = 'Ortak başlangıç klasörü'; dir = [Environment]::GetFolderPath('CommonStartup'); ap = 'HKLM:\\${CV}\\Explorer\\StartupApproved\\StartupFolder'; admin = $true }
)
foreach ($f in $folders) {
  Get-ChildItem $f.dir -File | Where-Object { $_.Name -ne 'desktop.ini' } | ForEach-Object {
    $exe = $_.FullName
    if ($_.Extension -eq '.lnk') { $t = $sh.CreateShortcut($_.FullName).TargetPath; if ($t) { $exe = $t } }
    $i = Info $exe
    $out += [pscustomobject]@{ kind = 'folder'; source = $f.src; name = $_.BaseName; file = $_.Name; command = $_.FullName; exe = $exe; key = $f.dir;
      publisher = $i.pub; desc = $i.desc; exists = $i.exists; ap = $f.ap; admin = $f.admin; state = (St $f.ap $_.Name) } }
}
Get-ScheduledTask | Where-Object { $_.TaskPath -notlike '\\Microsoft\\*' -and ($_.Triggers | Where-Object { $_.CimClass.CimClassName -in 'MSFT_TaskLogonTrigger','MSFT_TaskBootTrigger' }) } | ForEach-Object {
  $a = $_.Actions | Select-Object -First 1
  $exe = [Environment]::ExpandEnvironmentVariables([string]$a.Execute).Trim('"'); $i = Info $exe
  $boot = [bool]($_.Triggers | Where-Object { $_.CimClass.CimClassName -eq 'MSFT_TaskBootTrigger' })
  $out += [pscustomobject]@{ kind = 'task'; source = $(if ($boot) { 'Görev (açılışta)' } else { 'Görev (oturum açınca)' }); name = $_.TaskName; taskPath = $_.TaskPath;
    command = ([string]$a.Execute + ' ' + [string]$a.Arguments).Trim(); exe = $exe; publisher = $i.pub; desc = $(if ($i.desc) { $i.desc } else { [string]$_.Description });
    exists = $i.exists; admin = $true; state = $(if ($_.State -eq 'Disabled') { 'off' } else { 'on' }) }
}
$ad = 'HKCU:\\Software\\Classes\\Local Settings\\Software\\Microsoft\\Windows\\CurrentVersion\\AppModel\\SystemAppData'
Get-ChildItem $ad | ForEach-Object { $pkg = $_.PSChildName; Get-ChildItem $_.PSPath | ForEach-Object {
  $s = (Get-ItemProperty $_.PSPath -Name State).State
  if ($s -ne $null) {
    $nm = ($pkg -split '_')[0]; $nm = ($nm -split '\\.')[-1]
    $out += [pscustomobject]@{ kind = 'store'; source = 'Mağaza uygulaması'; name = $nm; command = $pkg; key = $_.PSPath; exe = ''; publisher = ''; desc = '';
      exists = $true; admin = $false; state = $(if ($s -eq 2 -or $s -eq 4) { 'on' } else { 'off' }) } }
} }
@($out) | ConvertTo-Json -Compress -Depth 3`;

let items = [];

function psq(s) { return String(s).replace(/'/g, "''"); }

function prettyName(n) {
  return n.replace(/\.(lnk|exe)$/i, '').replace(/([a-z])([A-Z])/g, '$1 $2').trim();
}

async function iconFor(p) {
  if (!p || !/\.(exe|lnk|ico)$/i.test(p) || !fs.existsSync(p)) return null;
  try { return (await electronApp.getFileIcon(p, { size: 'normal' })).toDataURL(); } catch { return null; }
}

async function list() {
  const r = await runPS(LIST_CMD, 2 * 60 * 1000);
  let d = [];
  try { d = JSON.parse(r.stdout.trim() || '[]'); } catch {}
  if (!Array.isArray(d)) d = [d];
  items = d.filter(Boolean).map((x, i) => ({ id: i, ...x, title: x.desc && x.desc.length < 48 ? x.desc : prettyName(x.name) }));
  const icons = await Promise.all(items.map((x) => iconFor(x.exe)));
  return items.map(({ ap, key, ...x }, i) => ({ ...x, icon: icons[i] }));
}

function setCmd(it, on) {
  if (it.kind === 'task') {
    return `${on ? 'Enable' : 'Disable'}-ScheduledTask -TaskName '${psq(it.name)}' -TaskPath '${psq(it.taskPath)}' -ErrorAction Stop | Out-Null`;
  }
  if (it.kind === 'store') {
    return `Set-ItemProperty -LiteralPath '${psq(it.key)}' -Name State -Value ${on ? 2 : 1} -Type DWord -ErrorAction Stop`;
  }
  const valueName = it.kind === 'folder' ? it.file : it.name;
  const bytes = on ? '2,0,0,0,0,0,0,0,0,0,0,0' : '3,0,0,0,0,0,0,0,0,0,0,0';
  return `if (-not (Test-Path '${psq(it.ap)}')) { New-Item -Path '${psq(it.ap)}' -Force | Out-Null }
Set-ItemProperty -Path '${psq(it.ap)}' -Name '${psq(valueName)}' -Value ([byte[]](${bytes})) -Type Binary -ErrorAction Stop`;
}

// Önce normal izinle dener; yetmezse (HKLM, başkasının görevi) tek UAC onayıyla yeniden dener.
async function exec(cmd, admin) {
  if (!admin) return runPS(cmd);
  const r = await runPS(cmd);
  return r.ok ? r : runElevated(cmd);
}

async function set(id, on) {
  const it = items[id];
  if (!it) return { ok: false, error: 'Öğe bulunamadı, listeyi yenileyin.' };
  const r = await exec(setCmd(it, on), it.admin);
  if (r.ok) it.state = on ? 'on' : 'off';
  return { ok: r.ok, error: r.ok ? null : (r.stderr || 'Yönetici izni verilmedi').trim().slice(-300) };
}

// Birden çok öğe: yönetici gerekenler tek UAC onayında toplanır.
async function setMany(ids, on) {
  const chosen = ids.map((i) => items[i]).filter(Boolean);
  const results = [];
  for (const it of chosen.filter((x) => !x.admin)) {
    const r = await runPS(setCmd(it, on));
    if (r.ok) it.state = on ? 'on' : 'off';
    results.push({ id: it.id, ok: r.ok });
  }
  const admin = chosen.filter((x) => x.admin);
  if (admin.length) {
    const tries = await Promise.all(admin.map((it) => runPS(setCmd(it, on))));
    const rest = [];
    admin.forEach((it, i) => {
      if (tries[i].ok) { it.state = on ? 'on' : 'off'; results.push({ id: it.id, ok: true }); } else rest.push(it);
    });
    if (rest.length) {
      const script = rest.map((it) => `try { ${setCmd(it, on)} } catch {}`).join('\n');
      await runElevated(script);
      // Sonucu listeyi yeniden okuyarak doğrula.
      const fresh = await list();
      for (const it of rest) {
        const f = fresh.find((x) => x.kind === it.kind && x.name === it.name && x.source === it.source);
        results.push({ id: f ? f.id : it.id, ok: !!f && f.state === (on ? 'on' : 'off') });
      }
      return { results, reloaded: fresh };
    }
  }
  return { results };
}

// Girdiyi tamamen siler (programın kendisine dokunmaz). Klasör kısayolu geri dönüşüm kutusuna gider.
async function remove(id) {
  const it = items[id];
  if (!it) return { ok: false, error: 'Öğe bulunamadı.' };
  if (it.kind === 'folder') {
    try { await shell.trashItem(it.command); return { ok: true }; } catch (e) {
      const r = await runElevated(`Remove-Item -LiteralPath '${psq(it.command)}' -Force -ErrorAction Stop`);
      return { ok: r.ok, error: r.ok ? null : 'Silinemedi' };
    }
  }
  if (it.kind === 'reg') {
    const cmd = `Remove-ItemProperty -Path '${psq(it.key)}' -Name '${psq(it.name)}' -ErrorAction Stop
Remove-ItemProperty -Path '${psq(it.ap)}' -Name '${psq(it.name)}' -ErrorAction SilentlyContinue`;
    const r = await exec(cmd, it.admin);
    return { ok: r.ok, error: r.ok ? null : 'Silinemedi' };
  }
  if (it.kind === 'task') {
    const cmd = `Unregister-ScheduledTask -TaskName '${psq(it.name)}' -TaskPath '${psq(it.taskPath)}' -Confirm:$false -ErrorAction Stop`;
    const r = await exec(cmd, true);
    return { ok: r.ok, error: r.ok ? null : 'Silinemedi' };
  }
  return { ok: false, error: 'Mağaza uygulamalarının başlangıç görevi silinemez, yalnızca kapatılabilir.' };
}

// Kullanıcının seçtiği programı kendi hesabının Run anahtarına ekler.
async function add(win) {
  const r = await dialog.showOpenDialog(win, {
    title: 'Başlangıca eklenecek programı seçin',
    properties: ['openFile'],
    filters: [{ name: 'Programlar', extensions: ['exe', 'bat', 'cmd', 'lnk'] }],
  });
  if (r.canceled || !r.filePaths[0]) return { ok: false, canceled: true };
  const file = r.filePaths[0];
  const name = path.basename(file).replace(/\.[^.]+$/, '');
  const cmd = `Set-ItemProperty -Path 'HKCU:\\${CV}\\Run' -Name '${psq(name)}' -Value '"${psq(file)}"' -ErrorAction Stop`;
  const res = await runPS(cmd);
  return { ok: res.ok, name };
}

function reveal(id) {
  const it = items[id];
  if (!it) return false;
  if (it.exe && fs.existsSync(it.exe)) { shell.showItemInFolder(it.exe); return true; }
  if (it.kind === 'folder') { shell.showItemInFolder(it.command); return true; }
  return false;
}

function openFolder() {
  return shell.openPath(path.join(process.env.APPDATA || '', 'Microsoft', 'Windows', 'Start Menu', 'Programs', 'Startup'));
}

module.exports = { list, set, setMany, remove, add, reveal, openFolder };

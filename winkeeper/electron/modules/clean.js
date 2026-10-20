const fsp = require('fs').promises;
const path = require('path');
const { entrySize, expand, rmBest } = require('../fsutil');
const { runPS } = require('../ps');

const env = process.env;
const LA = env.LOCALAPPDATA || '';
const RA = env.APPDATA || '';
const UP = env.USERPROFILE || '';
const WIN = env.SystemRoot || 'C:\\Windows';
const PD = env.ProgramData || 'C:\\ProgramData';
const PF86 = env['ProgramFiles(x86)'] || 'C:\\Program Files (x86)';

const chromium = (base) => [`${base}\\*\\Cache\\Cache_Data`, `${base}\\*\\Code Cache`, `${base}\\*\\GPUCache`, `${base}\\*\\Service Worker\\CacheStorage`];
const electronApp = (base) => [`${base}\\Cache\\Cache_Data`, `${base}\\Code Cache`, `${base}\\GPUCache`];

// group: arayüzde gruplama. safe=false olanlar varsayılan olarak seçilmez.
// mode 'contents': klasörün içi silinir. 'files': desene uyan dosyalar. 'old': N günden eski dosyalar.
const CATEGORIES = [
  { id: 'user-temp', group: 'Sistem', name: 'Kullanıcı geçici dosyaları', desc: '%TEMP% klasöründeki artıklar', mode: 'contents', safe: true, targets: [env.TEMP] },
  { id: 'recycle', group: 'Sistem', name: 'Geri dönüşüm kutusu', desc: 'Silinmeyi bekleyen dosyalar', special: 'recycle', safe: false },
  { id: 'thumbs', group: 'Sistem', name: 'Küçük resim önbelleği', desc: 'Gezgin yeniden oluşturur', mode: 'files', safe: true, targets: [`${LA}\\Microsoft\\Windows\\Explorer\\thumbcache_*.db`] },
  { id: 'crash', group: 'Sistem', name: 'Çökme dökümleri', desc: 'Uygulama çökme dökümleri', mode: 'contents', safe: true, targets: [`${LA}\\CrashDumps`] },
  { id: 'wer', group: 'Sistem', name: 'Hata raporları', desc: 'Windows Hata Bildirimi arşivi', mode: 'contents', safe: true, targets: [
    `${PD}\\Microsoft\\Windows\\WER\\ReportArchive`, `${PD}\\Microsoft\\Windows\\WER\\ReportQueue`, `${LA}\\Microsoft\\Windows\\WER\\ReportArchive`, `${LA}\\Microsoft\\Windows\\WER\\ReportQueue`] },
  { id: 'win-temp', group: 'Sistem', name: 'Windows geçici dosyaları', desc: 'C:\\Windows\\Temp', mode: 'contents', safe: false, targets: [`${WIN}\\Temp`] },
  { id: 'wu', group: 'Sistem', name: 'Windows Update indirmeleri', desc: 'Kurulmuş güncellemelerin paketleri', mode: 'contents', safe: false, targets: [`${WIN}\\SoftwareDistribution\\Download`] },
  { id: 'logs', group: 'Sistem', name: 'Eski sistem günlükleri', desc: '14 günden eski .log dosyaları', mode: 'old', days: 14, ext: ['.log'], safe: true, targets: [`${WIN}\\Logs\\CBS`, `${WIN}\\Logs\\DISM`] },

  { id: 'chrome', group: 'Tarayıcılar', name: 'Chrome', desc: 'Sayfa, kod ve GPU önbelleği; oturumlar korunur', mode: 'contents', safe: true, targets: chromium(`${LA}\\Google\\Chrome\\User Data`) },
  { id: 'edge', group: 'Tarayıcılar', name: 'Edge', desc: 'Sayfa, kod ve GPU önbelleği; oturumlar korunur', mode: 'contents', safe: true, targets: chromium(`${LA}\\Microsoft\\Edge\\User Data`) },
  { id: 'brave', group: 'Tarayıcılar', name: 'Brave', desc: 'Sayfa, kod ve GPU önbelleği', mode: 'contents', safe: true, targets: chromium(`${LA}\\BraveSoftware\\Brave-Browser\\User Data`) },
  { id: 'opera', group: 'Tarayıcılar', name: 'Opera', desc: 'Sayfa ve kod önbelleği', mode: 'contents', safe: true, targets: [...electronApp(`${LA}\\Opera Software\\Opera Stable`), ...electronApp(`${LA}\\Opera Software\\Opera GX Stable`)] },
  { id: 'firefox', group: 'Tarayıcılar', name: 'Firefox', desc: 'cache2 klasörleri', mode: 'contents', safe: true, targets: [`${LA}\\Mozilla\\Firefox\\Profiles\\*\\cache2`] },

  { id: 'discord', group: 'Uygulamalar', name: 'Discord', desc: 'Medya ve kod önbelleği', mode: 'contents', safe: true, targets: electronApp(`${RA}\\discord`) },
  { id: 'spotify', group: 'Uygulamalar', name: 'Spotify', desc: 'Çevrimdışı olmayan şarkı önbelleği', mode: 'contents', safe: true, targets: [`${LA}\\Spotify\\Data`, `${LA}\\Spotify\\Browser\\Cache`] },
  { id: 'teams', group: 'Uygulamalar', name: 'Microsoft Teams', desc: 'Web görünümü önbelleği', mode: 'contents', safe: true, targets: [
    ...electronApp(`${RA}\\Microsoft\\Teams`), `${LA}\\Packages\\MSTeams_*\\LocalCache\\Microsoft\\MSTeams\\EBWebView\\*\\Cache\\Cache_Data`] },
  { id: 'vscode', group: 'Uygulamalar', name: 'VS Code', desc: 'Önbellek ve eski sürüm verileri', mode: 'contents', safe: true, targets: [...electronApp(`${RA}\\Code`), `${RA}\\Code\\CachedData`, `${RA}\\Code\\CachedExtensionVSIXs`] },
  { id: 'steam', group: 'Uygulamalar', name: 'Steam', desc: 'Web ve uygulama önbelleği', mode: 'contents', safe: true, targets: [`${PF86}\\Steam\\appcache\\httpcache`, `${LA}\\Steam\\htmlcache`] },
  { id: 'dx', group: 'Uygulamalar', name: 'Gölgelendirici önbellekleri', desc: 'DirectX, NVIDIA ve AMD; oyunlar yeniden oluşturur', mode: 'contents', safe: true, targets: [
    `${LA}\\D3DSCache`, `${LA}\\NVIDIA\\DXCache`, `${LA}\\NVIDIA\\GLCache`, `${LA}\\AMD\\DxCache`, `${LA}\\AMD\\DxcCache`] },

  { id: 'npm', group: 'Geliştirici', name: 'npm / Yarn / pnpm', desc: 'Paket önbellekleri; gerekirse yeniden indirilir', mode: 'contents', safe: true, targets: [`${LA}\\npm-cache\\_cacache`, `${LA}\\Yarn\\Cache`, `${LA}\\pnpm-cache`] },
  { id: 'pip', group: 'Geliştirici', name: 'pip', desc: 'Python paket önbelleği', mode: 'contents', safe: true, targets: [`${LA}\\pip\\cache`] },
  { id: 'nuget', group: 'Geliştirici', name: 'NuGet', desc: 'HTTP ve v3 önbelleği', mode: 'contents', safe: true, targets: [`${LA}\\NuGet\\v3-cache`, `${LA}\\NuGet\\Cache`] },
  { id: 'gradle', group: 'Geliştirici', name: 'Gradle', desc: 'Derleme önbelleği', mode: 'contents', safe: false, targets: [`${UP}\\.gradle\\caches`] },

  { id: 'installers', group: 'Kişisel', name: 'Eski kurulum dosyaları', desc: 'İndirilenler\'de 30 günden eski .exe ve .msi', mode: 'old', days: 30, ext: ['.exe', '.msi'], flat: true, safe: false, targets: [`${UP}\\Downloads`] },
];

let lastScan = new Map(); // id -> [{ path, size }]

async function oldFiles(root, days, exts, flat) {
  const limit = Date.now() - days * 86400000;
  const out = [];
  const stack = [root];
  while (stack.length) {
    const dir = stack.pop();
    let ents;
    try { ents = await fsp.readdir(dir, { withFileTypes: true }); } catch { continue; }
    for (const e of ents) {
      const p = path.join(dir, e.name);
      if (e.isDirectory() && !e.isSymbolicLink()) { if (!flat) stack.push(p); continue; }
      if (!e.isFile() || !exts.includes(path.extname(e.name).toLowerCase())) continue;
      try {
        const s = await fsp.stat(p);
        if (s.mtimeMs < limit) out.push({ path: p, size: s.size, isDir: false });
      } catch {}
    }
  }
  return out;
}

async function recycleSize() {
  const r = await runPS(`$s=(New-Object -ComObject Shell.Application).NameSpace(10); $t=[int64]0; $c=0;
    foreach($i in $s.Items()){ $c++; try { $t += [int64]$i.ExtendedProperty('Size') } catch {} }; "$t|$c"`, 60000);
  const [size, count] = r.stdout.trim().split('|').map(Number);
  return { size: size || 0, count: count || 0 };
}

async function scanCategory(cat) {
  if (cat.special === 'recycle') {
    const r = await recycleSize();
    return r.size ? [{ path: 'Geri dönüşüm kutusu', size: r.size, isDir: true, count: r.count }] : [];
  }
  const items = [];
  for (const t of cat.targets) {
    if (!t) continue;
    for (const r of await expand(t)) {
      if (cat.mode === 'old') { items.push(...(await oldFiles(r, cat.days, cat.ext, cat.flat))); continue; }
      let paths = [r];
      if (cat.mode === 'contents') {
        try { paths = (await fsp.readdir(r)).map((n) => path.join(r, n)); } catch { paths = []; }
      }
      for (const p of paths) {
        const s = await entrySize(p);
        if (s && s.size > 0) items.push({ path: p, size: s.size, isDir: s.isDir });
      }
    }
  }
  items.sort((a, b) => b.size - a.size);
  return items;
}

async function scan(onProgress = () => {}) {
  lastScan = new Map();
  const out = [];
  for (let i = 0; i < CATEGORIES.length; i++) {
    const cat = CATEGORIES[i];
    onProgress({ index: i, total: CATEGORIES.length, name: cat.name });
    const items = await scanCategory(cat);
    lastScan.set(cat.id, items);
    const res = {
      id: cat.id, group: cat.group, name: cat.name, desc: cat.desc, safe: cat.safe,
      size: items.reduce((s, x) => s + x.size, 0),
      count: items.length,
      items: items.slice(0, 200).map(({ path: p, size, isDir }) => ({ path: p, size, isDir })),
    };
    out.push(res);
    onProgress({ index: i + 1, total: CATEGORIES.length, done: res });
  }
  return out;
}

// Yalnızca son taramada bulunan öğeleri siler; renderer'dan gelen yollar doğrudan silinmez.
async function run({ ids, exclude }, onProgress = () => {}) {
  const skip = new Set(exclude || []);
  let freed = 0;
  let failed = 0;
  const all = (ids || []).flatMap((id) => (lastScan.get(id) || []).map((item) => ({ id, item })));
  let i = 0;
  for (const { id, item } of all) {
    i++;
    if (skip.has(item.path)) continue;
    if (id === 'recycle') {
      const r = await runPS('Clear-RecycleBin -Force -ErrorAction SilentlyContinue');
      if (r.ok) freed += item.size; else failed++;
    } else {
      const f = await rmBest(item.path);
      freed += f;
      if (f < item.size) failed++;
    }
    onProgress({ done: i, total: all.length, freed });
  }
  lastScan = new Map();
  return { freed, failed };
}

module.exports = { scan, run };

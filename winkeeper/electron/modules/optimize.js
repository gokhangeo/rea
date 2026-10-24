const fs = require('fs');
const os = require('os');
const path = require('path');
const { runPS, runElevated } = require('../ps');

// recommended: "Önerilen" seçimine dahil. reboot: etkisi için yeniden başlatma gerekir.
const TASKS = [
  { id: 'dns', group: 'Ağ', name: 'DNS önbelleğini temizle', desc: 'Eski alan adı kayıtlarını siler; açılmayan siteler için.', admin: false, recommended: true,
    cmd: 'ipconfig /flushdns' },
  { id: 'arp', group: 'Ağ', name: 'ARP önbelleğini temizle', desc: 'Yerel ağ adres tablosunu sıfırlar.', admin: true, recommended: true,
    cmd: 'netsh interface ip delete arpcache' },
  { id: 'nbt', group: 'Ağ', name: 'NetBIOS önbelleğini yenile', desc: 'Ağdaki bilgisayar adlarını yeniden öğrenir.', admin: true,
    cmd: 'nbtstat -R' },
  { id: 'winsock', group: 'Ağ', name: 'Winsock\'u sıfırla', desc: 'Bozuk ağ yığınını onarır. Yeniden başlatma gerekir.', admin: true, reboot: true,
    cmd: 'netsh winsock reset' },
  { id: 'time', group: 'Ağ', name: 'Saati eşitle', desc: 'Sistem saatini internet saatiyle eşitler.', admin: true, recommended: true,
    cmd: 'Start-Service w32time -ErrorAction SilentlyContinue; w32tm /resync /force' },

  { id: 'explorer', group: 'Gezgin', name: 'Gezgin\'i yeniden başlat', desc: 'Donan görev çubuğu ve masaüstü için. Ekran bir an kararır.', admin: false,
    cmd: 'Stop-Process -Name explorer -Force; Start-Sleep 2; if (-not (Get-Process explorer -ErrorAction SilentlyContinue)) { Start-Process explorer.exe }' },
  { id: 'iconcache', group: 'Gezgin', name: 'Simge önbelleğini yenile', desc: 'Bozuk ya da boş görünen simgeleri düzeltir. Gezgin yeniden başlar.', admin: false,
    cmd: 'Stop-Process -Name explorer -Force; Start-Sleep 1; Remove-Item "$env:LOCALAPPDATA\\IconCache.db","$env:LOCALAPPDATA\\Microsoft\\Windows\\Explorer\\iconcache_*.db" -Force -ErrorAction SilentlyContinue; Start-Sleep 1; if (-not (Get-Process explorer -ErrorAction SilentlyContinue)) { Start-Process explorer.exe }' },
  { id: 'recent', group: 'Gezgin', name: 'Son kullanılanlar listesini temizle', desc: 'Gezgin\'deki son dosya kısayollarını siler (dosyaların kendisine dokunmaz).', admin: false,
    cmd: 'Get-ChildItem "$env:APPDATA\\Microsoft\\Windows\\Recent" -File -Force -ErrorAction SilentlyContinue | Remove-Item -Force -ErrorAction SilentlyContinue' },
  { id: 'clipboard', group: 'Gezgin', name: 'Pano geçmişini temizle', desc: 'Win+V geçmişindeki sabitlenmemiş öğeleri siler.', admin: false,
    cmd: '$null = [Windows.ApplicationModel.DataTransfer.Clipboard,Windows.ApplicationModel.DataTransfer,ContentType=WindowsRuntime]::ClearHistory()' },

  { id: 'recycle', group: 'Disk', name: 'Geri dönüşüm kutusunu boşalt', desc: 'Kutudaki dosyaları kalıcı olarak siler.', admin: false,
    cmd: 'Clear-RecycleBin -Force -ErrorAction SilentlyContinue' },
  { id: 'd3d', group: 'Disk', name: 'DirectX gölgelendirici önbelleği', desc: 'D3DSCache klasörünü temizler.', admin: false, recommended: true,
    cmd: 'Remove-Item "$env:LOCALAPPDATA\\D3DSCache\\*" -Recurse -Force -ErrorAction SilentlyContinue' },
  { id: 'trim', group: 'Disk', name: 'SSD TRIM çalıştır', desc: 'Sistem sürücüsüne TRIM gönderir; SSD yazma hızını korur.', admin: true, recommended: true,
    cmd: 'Optimize-Volume -DriveLetter $env:SystemDrive[0] -ReTrim' },
  { id: 'delivery', group: 'Disk', name: 'Teslim iyileştirme önbelleği', desc: 'Windows Update eşler arası önbelleğini siler.', admin: true, recommended: true,
    cmd: 'Delete-DeliveryOptimizationCache -Force' },
  { id: 'oldlogs', group: 'Disk', name: 'Eski Windows günlükleri', desc: 'C:\\Windows\\Logs içindeki 14 günden eski günlükleri siler.', admin: true, recommended: true,
    cmd: 'Get-ChildItem "$env:SystemRoot\\Logs" -Recurse -File -Include *.log,*.etl -ErrorAction SilentlyContinue | Where-Object { $_.LastWriteTime -lt (Get-Date).AddDays(-14) } | Remove-Item -Force -ErrorAction SilentlyContinue' },
  { id: 'dism', group: 'Disk', name: 'Bileşen deposunu temizle', desc: 'DISM ile eski güncelleme bileşenlerini kaldırır. Birkaç dakika sürer.', admin: true,
    cmd: 'Dism.exe /Online /Cleanup-Image /StartComponentCleanup' },

  { id: 'spooler', group: 'Sistem', name: 'Yazdırma kuyruğunu temizle', desc: 'Takılı kalmış yazdırma işlerini siler.', admin: true,
    cmd: 'Stop-Service Spooler -Force; Remove-Item "$env:SystemRoot\\System32\\spool\\PRINTERS\\*" -Force -ErrorAction SilentlyContinue; Start-Service Spooler' },
  { id: 'fontcache', group: 'Sistem', name: 'Yazı tipi önbelleğini yenile', desc: 'Bozuk görünen yazı tipleri için.', admin: true,
    cmd: 'Stop-Service FontCache -Force -ErrorAction SilentlyContinue; Remove-Item "$env:SystemRoot\\ServiceProfiles\\LocalService\\AppData\\Local\\FontCache\\*" -Recurse -Force -ErrorAction SilentlyContinue; Start-Service FontCache -ErrorAction SilentlyContinue' },
  { id: 'search', group: 'Sistem', name: 'Arama hizmetini yeniden başlat', desc: 'Başlat menüsü araması yavaşsa ya da sonuç vermiyorsa.', admin: true,
    cmd: 'Restart-Service WSearch -Force' },
  { id: 'defender', group: 'Sistem', name: 'Defender tanımlarını güncelle', desc: 'Microsoft Defender virüs tanımlarını indirir.', admin: true, recommended: true,
    cmd: 'Update-MpSignature' },
  { id: 'sfc', group: 'Sistem', name: 'Sistem dosyalarını onar', desc: 'SFC ile bozuk Windows dosyalarını tarar ve onarır. 10-20 dakika sürebilir.', admin: true,
    cmd: 'sfc /scannow' },
];

function list() {
  return TASKS.map(({ cmd, ...t }) => t);
}

function psq(s) { return s.replace(/'/g, "''"); }

// Yönetici görevleri tek bir UAC onayında topluca çalıştırılır.
async function runAdminBatch(tasks) {
  const out = path.join(os.tmpdir(), `winkeeper-${process.pid}-${Date.now()}.json`);
  const body = tasks.map((t) => `
$global:LASTEXITCODE = 0
try { $o = (& { ${t.cmd} } 2>&1 | Out-String); $res['${t.id}'] = @{ ok = ($LASTEXITCODE -eq 0); out = $o } }
catch { $res['${t.id}'] = @{ ok = $false; out = $_.Exception.Message } }`).join('\n');
  const script = `$ErrorActionPreference = 'Continue'\n$res = @{}\n${body}\n$res | ConvertTo-Json -Depth 3 | Set-Content -Encoding UTF8 -Path '${psq(out)}'`;
  const r = await runElevated(script);
  let data = {};
  try { data = JSON.parse(fs.readFileSync(out, 'utf8').replace(/^\uFEFF/, '')); fs.unlinkSync(out); } catch {}
  return tasks.map((t) => data[t.id]
    ? { id: t.id, ok: !!data[t.id].ok, output: String(data[t.id].out || '').trim().slice(-2000) }
    : { id: t.id, ok: false, output: r.ok ? 'Sonuç okunamadı' : 'Yönetici izni verilmedi' });
}

async function runMany(ids, onUpdate) {
  const chosen = TASKS.filter((t) => ids.includes(t.id));
  const results = [];
  for (const t of chosen.filter((x) => !x.admin)) {
    onUpdate({ id: t.id, state: 'running' });
    const r = await runPS(t.cmd);
    const res = { id: t.id, ok: r.ok, output: (r.stdout + r.stderr).trim().slice(-2000) };
    results.push(res);
    onUpdate({ ...res, state: res.ok ? 'ok' : 'fail' });
  }
  const admin = chosen.filter((x) => x.admin);
  if (admin.length) {
    admin.forEach((t) => onUpdate({ id: t.id, state: 'running' }));
    for (const res of await runAdminBatch(admin)) {
      results.push(res);
      onUpdate({ ...res, state: res.ok ? 'ok' : 'fail' });
    }
  }
  return results;
}

module.exports = { list, runMany };

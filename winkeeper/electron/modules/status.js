const si = require('systeminformation');

let tick = 0;
let procs = [];
let disks = [];
let gpu = null;
let info = null;

async function staticInfo() {
  if (info) return info;
  const [cpu, os, iface] = await Promise.all([
    si.cpu().catch(() => null), si.osInfo().catch(() => null), si.networkInterfaces('default').catch(() => null),
  ]);
  info = {
    cpu: cpu ? `${cpu.manufacturer} ${cpu.brand}`.trim() : '',
    cores: cpu ? `${cpu.physicalCores} çekirdek / ${cpu.cores} iş parçacığı` : '',
    os: os ? `${os.distro} ${os.release}` : '',
    host: os ? os.hostname : '',
    ip: iface && !Array.isArray(iface) ? iface.ip4 : '',
    iface: iface && !Array.isArray(iface) ? iface.ifaceName || iface.iface : '',
  };
  return info;
}

function health(d) {
  let score = 100;
  const notes = [];
  if (d.cpu && d.cpu.load > 85) { score -= 20; notes.push('İşlemci yoğun'); }
  else if (d.cpu && d.cpu.load > 60) score -= 8;
  if (d.mem) {
    const m = d.mem.used / d.mem.total;
    if (m > 0.9) { score -= 25; notes.push('Bellek neredeyse dolu'); } else if (m > 0.75) score -= 10;
  }
  for (const k of d.disks) {
    const u = k.used / k.size;
    if (u > 0.95) { score -= 25; notes.push(`${k.mount} dolmak üzere`); } else if (u > 0.85) { score -= 10; notes.push(`${k.mount} %${Math.round(u * 100)} dolu`); }
  }
  if (d.temp && d.temp > 90) { score -= 15; notes.push('İşlemci çok sıcak'); }
  if (d.uptime > 14 * 86400) { score -= 5; notes.push('Uzun süredir yeniden başlatılmadı'); }
  score = Math.max(0, Math.min(100, score));
  return { score, notes, label: score >= 85 ? 'Harika' : score >= 65 ? 'İyi' : score >= 40 ? 'Yorgun' : 'Zorlanıyor' };
}

async function get() {
  tick++;
  const [load, mem, net, battery, temp, sinfo] = await Promise.all([
    si.currentLoad().catch(() => null),
    si.mem().catch(() => null),
    si.networkStats('*').catch(() => []),
    si.battery().catch(() => null),
    si.cpuTemperature().catch(() => null),
    staticInfo(),
  ]);

  if (tick % 2 === 1) {
    const p = await si.processes().catch(() => null);
    if (p) {
      procs = p.list
        .filter((x) => x.name && x.pid > 4 && x.name !== 'System Idle Process')
        .map((x) => ({ pid: x.pid, name: x.name, cpu: x.cpu || 0, mem: (x.memRss || 0) * 1024, user: x.user || '' }));
    }
  }
  if (tick % 5 === 1) disks = (await si.fsSize().catch(() => [])).map((d) => ({ mount: d.mount, size: d.size, used: d.used, type: d.type }));
  if (tick % 15 === 1) {
    const g = await si.graphics().catch(() => null);
    const c = g && g.controllers && g.controllers[0];
    gpu = c ? { model: c.model, util: c.utilizationGpu ?? null, vram: c.vram ?? null, temp: c.temperatureGpu ?? null } : null;
  }

  const byCpu = [...procs].sort((a, b) => b.cpu - a.cpu).slice(0, 25);
  const byMem = [...procs].sort((a, b) => b.mem - a.mem).slice(0, 25);
  const top = [...new Map([...byCpu, ...byMem].map((p) => [p.pid, p])).values()];

  const d = {
    info: sinfo,
    cpu: load ? { load: load.currentLoad, cores: load.cpus.map((c) => c.load) } : null,
    mem: mem ? { total: mem.total, used: mem.active, cached: mem.buffcache, swapUsed: mem.swapused, swapTotal: mem.swaptotal } : null,
    net: { rx: net.reduce((s, n) => s + (n.rx_sec || 0), 0), tx: net.reduce((s, n) => s + (n.tx_sec || 0), 0) },
    battery: battery && battery.hasBattery ? { percent: battery.percent, charging: battery.isCharging, remaining: battery.timeRemaining } : null,
    temp: temp && temp.main ? temp.main : null,
    uptime: si.time().uptime,
    gpu,
    disks,
    procs: top,
    procCount: procs.length,
  };
  d.health = health(d);
  return d;
}

function kill(pid) {
  if (!procs.some((p) => p.pid === pid)) return { ok: false, error: 'İşlem listede yok' };
  try { process.kill(pid); return { ok: true }; } catch (e) { return { ok: false, error: e.message }; }
}

module.exports = { get, kill };

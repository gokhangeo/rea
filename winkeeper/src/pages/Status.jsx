import { useEffect, useRef, useState } from 'react';
import { bytes, duration } from '../util.js';
import Planet from '../Planet.jsx';
import { Spark, Ring, CountNum, Toast, useConfirm } from '../ui.jsx';

const N = 30; // 2 sn aralıkla 60 sn

function Card({ title, children, className = '', i = 0, pinned, onPin }) {
  return (
    <div className={`card stagger ${className} ${pinned ? 'pinned' : ''}`} style={{ '--i': i }}>
      <div className="card-title">
        <small>{title}</small>
        {onPin && <button className="pin" onClick={onPin} title={pinned ? 'Sabitlemeyi kaldır' : 'Başa sabitle'}>{pinned ? '★' : '☆'}</button>}
      </div>
      {children}
    </div>
  );
}

function loadPins() { try { return JSON.parse(localStorage.getItem('pins') || '[]'); } catch { return []; } }

export default function Status({ active, theme }) {
  const [s, setS] = useState(null);
  const [sort, setSort] = useState({ key: 'cpu', dir: -1 });
  const [pins, setPins] = useState(loadPins);
  const [toast, setToast] = useState(null);
  const [ask, confirmEl] = useConfirm();
  const hist = useRef({ cpu: [], mem: [], rx: [], tx: [] });

  useEffect(() => {
    if (!active) return;
    let alive = true;
    let timer;
    async function poll() {
      const d = await window.api.status.get();
      if (!alive) return;
      const h = hist.current;
      const push = (k, v) => { h[k] = [...h[k], v].slice(-N); };
      if (d.cpu) push('cpu', d.cpu.load);
      if (d.mem) push('mem', (d.mem.used / d.mem.total) * 100);
      push('rx', d.net.rx); push('tx', d.net.tx);
      setS(d);
      timer = setTimeout(poll, 2000);
    }
    poll();
    return () => { alive = false; clearTimeout(timer); };
  }, [active]);

  const togglePin = (id) => setPins((p) => {
    const n = p.includes(id) ? p.filter((x) => x !== id) : [...p, id];
    try { localStorage.setItem('pins', JSON.stringify(n)); } catch {}
    return n;
  });

  async function kill(p) {
    const ok = await ask({ title: 'İşlemi sonlandır', ok: 'Sonlandır', danger: true,
      body: <><b>{p.name}</b> (PID {p.pid}) kapatılacak. Kaydedilmemiş veriler kaybolabilir.</> });
    if (!ok) return;
    const r = await window.api.status.kill(p.pid);
    setToast(r.ok ? { text: `${p.name} sonlandırıldı.`, type: 'ok' } : { text: r.error, type: 'err' });
  }

  if (!s) {
    return (
      <div className="hero busy">
        <div className="hero-planet"><div className="orbit"><i /><i /><i /></div><Planet kind={theme.planet} size={200} speed={6} active={active} /></div>
        <p className="tagline live">Her kalp atışı, kendi ışığında…</p>
      </div>
    );
  }

  const h = hist.current;
  const memPct = s.mem ? s.mem.used / s.mem.total : 0;
  const procs = [...s.procs].sort((a, b) => {
    const va = a[sort.key], vb = b[sort.key];
    return (typeof va === 'string' ? va.localeCompare(vb) : va - vb) * sort.dir;
  }).slice(0, 15);
  const maxCpu = Math.max(1, ...procs.map((p) => p.cpu));
  const maxMem = Math.max(1, ...procs.map((p) => p.mem));
  const th = (key, label) => (
    <th className={sort.key === key ? 'on' : ''} onClick={() => setSort((o) => ({ key, dir: o.key === key ? -o.dir : key === 'name' ? 1 : -1 }))}>
      {label}{sort.key === key ? (sort.dir < 0 ? ' ↓' : ' ↑') : ''}
    </th>
  );

  const cards = {
    cpu: (i) => (
      <Card key="cpu" title="İşlemci" i={i} pinned={pins.includes('cpu')} onPin={() => togglePin('cpu')}>
        <div className="big"><CountNum value={s.cpu ? s.cpu.load : 0} ms={800} suffix="%" /></div>
        <small className="ellipsis">{s.info.cpu}</small>
        <Spark data={h.cpu} max={100} color="#7cc4ff" />
        <div className="cores">{s.cpu && s.cpu.cores.map((c, k) => <span key={k} style={{ '--v': `${c}%` }} title={`Çekirdek ${k + 1}: %${c.toFixed(0)}`} />)}</div>
      </Card>
    ),
    mem: (i) => (
      <Card key="mem" title="Bellek" i={i} pinned={pins.includes('mem')} onPin={() => togglePin('mem')}>
        <div className="row-flex">
          <Ring value={memPct} size={74} stroke={7} color="#6ee0c2"><b><CountNum value={memPct * 100} /></b><small>%</small></Ring>
          <div>
            <div className="mid">{bytes(s.mem && s.mem.used)}</div>
            <small>{bytes(s.mem && s.mem.total)} toplam</small>
            <small>Takas {bytes(s.mem && s.mem.swapUsed)}</small>
          </div>
        </div>
        <Spark data={h.mem} max={100} color="#6ee0c2" height={30} />
      </Card>
    ),
    net: (i) => (
      <Card key="net" title="Ağ" i={i} pinned={pins.includes('net')} onPin={() => togglePin('net')}>
        <div className="net-row"><span className="arrow down">↓</span><b>{bytes(s.net.rx)}/sn</b></div>
        <div className="net-row"><span className="arrow up">↑</span><b>{bytes(s.net.tx)}/sn</b></div>
        <div className="spark-stack">
          <Spark data={h.rx} max={Math.max(...h.rx, ...h.tx, 1)} color="#ffcf6e" height={40} />
          <Spark data={h.tx} max={Math.max(...h.rx, ...h.tx, 1)} color="#ff8a6b" height={40} />
        </div>
        <small>{s.info.iface} · {s.info.ip}</small>
      </Card>
    ),
    gpu: (i) => (
      <Card key="gpu" title="Ekran kartı" i={i} pinned={pins.includes('gpu')} onPin={() => togglePin('gpu')}>
        <div className="big">{s.gpu && s.gpu.util != null ? `${s.gpu.util}%` : '—'}</div>
        <small className="ellipsis">{s.gpu ? s.gpu.model : 'Bilgi yok'}</small>
        {s.gpu && s.gpu.vram ? <small>{bytes(s.gpu.vram * 1048576)} bellek</small> : null}
      </Card>
    ),
    temp: (i) => (
      <Card key="temp" title="Sıcaklık" i={i} pinned={pins.includes('temp')} onPin={() => togglePin('temp')}>
        <div className="big">{s.temp ? `${s.temp.toFixed(0)}°` : '—'}</div>
        <small>{s.temp ? 'İşlemci' : 'Bu donanımda okunamıyor'}</small>
      </Card>
    ),
    battery: (i) => (
      <Card key="battery" title="Pil" i={i} pinned={pins.includes('battery')} onPin={() => togglePin('battery')}>
        {s.battery ? (
          <>
            <div className="battery"><div style={{ width: `${s.battery.percent}%` }} className={s.battery.charging ? 'charging' : ''} /></div>
            <div className="mid">%{s.battery.percent}</div>
            <small>{s.battery.charging ? 'Şarj oluyor' : s.battery.remaining > 0 ? `${s.battery.remaining} dk kaldı` : 'Pilde'}</small>
          </>
        ) : <><div className="big">—</div><small>Masaüstü bilgisayar</small></>}
      </Card>
    ),
    uptime: (i) => (
      <Card key="uptime" title="Çalışma süresi" i={i} pinned={pins.includes('uptime')} onPin={() => togglePin('uptime')}>
        <div className="big">{duration(s.uptime)}</div>
        <small>{s.info.os}</small>
        <small>{s.procCount} işlem çalışıyor</small>
      </Card>
    ),
    disks: (i) => (
      <Card key="disks" title="Diskler" className="wide" i={i} pinned={pins.includes('disks')} onPin={() => togglePin('disks')}>
        {s.disks.map((d) => {
          const u = d.used / d.size;
          return (
            <div key={d.mount} className="disk">
              <b>{d.mount}</b>
              <div className="bar"><div style={{ width: `${u * 100}%`, background: u > 0.9 ? '#ff6b6b' : u > 0.8 ? '#ffcf6e' : 'var(--accent)' }} /></div>
              <small>{bytes(d.size - d.used)} boş</small>
            </div>
          );
        })}
      </Card>
    ),
  };
  const order = ['cpu', 'mem', 'net', 'gpu', 'temp', 'battery', 'uptime', 'disks'];
  const sorted = [...order.filter((o) => pins.includes(o)), ...order.filter((o) => !pins.includes(o))];

  return (
    <div className="results status">
      <div className="scroll-area">
        <div className="bento">
          <div className="card health stagger" style={{ '--i': 0 }}>
            <Planet kind={theme.planet} size={120} active={active} speed={1 + (s.cpu ? s.cpu.load / 25 : 0)} />
            <div>
              <small>Sistem sağlığı</small>
              <div className="score"><CountNum value={s.health.score} ms={1200} /><span>/100</span></div>
              <b className="health-label">{s.health.label}</b>
              {s.health.notes.slice(0, 3).map((n) => <small key={n}>• {n}</small>)}
              {s.health.notes.length === 0 && <small>Her şey yolunda görünüyor.</small>}
              <small className="muted">{s.info.host}</small>
            </div>
          </div>
          {sorted.map((k, i) => cards[k](i + 1))}

          <div className="card procs stagger" style={{ '--i': 10 }}>
            <div className="card-title"><small>İşlemler</small></div>
            <table>
              <thead><tr>{th('name', 'Ad')}{th('pid', 'PID')}{th('cpu', 'İşlemci')}{th('mem', 'Bellek')}<th /></tr></thead>
              <tbody>
                {procs.map((p) => (
                  <tr key={p.pid}>
                    <td className="ellipsis">{p.name}</td>
                    <td className="muted">{p.pid}</td>
                    <td><div className="cell-bar"><i style={{ width: `${(p.cpu / maxCpu) * 100}%` }} /><span>{p.cpu.toFixed(1)}%</span></div></td>
                    <td><div className="cell-bar mem"><i style={{ width: `${(p.mem / maxMem) * 100}%` }} /><span>{bytes(p.mem)}</span></div></td>
                    <td><button className="kill" onClick={() => kill(p)} title="Sonlandır">×</button></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      </div>
      <Toast msg={toast} onDone={() => setToast(null)} />
      {confirmEl}
    </div>
  );
}

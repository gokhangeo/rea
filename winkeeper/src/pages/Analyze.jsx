import { useEffect, useRef, useState } from 'react';
import { bytes } from '../util.js';
import Planet from '../Planet.jsx';
import { Hero, Ring, CountBytes, CountNum, Toast, useConfirm } from '../ui.jsx';

function worst(row, short) {
  const s = row.reduce((a, r) => a + r.area, 0);
  let mx = 0, mn = Infinity;
  for (const r of row) { if (r.area > mx) mx = r.area; if (r.area < mn) mn = r.area; }
  return Math.max((short * short * mx) / (s * s), (s * s) / (short * short * mn));
}

// Squarified treemap
function squarify(items, X, Y, W, H) {
  const list = items.filter((i) => i.size > 0);
  const total = list.reduce((s, i) => s + i.size, 0);
  if (!total || W < 2 || H < 2) return [];
  let rest = list.map((i) => ({ item: i, area: (i.size * W * H) / total }));
  let x = X, y = Y, w = W, h = H;
  const out = [];
  while (rest.length) {
    const short = Math.min(w, h);
    const row = [rest[0]];
    let i = 1;
    let wr = worst(row, short);
    while (i < rest.length) {
      const nw = worst([...row, rest[i]], short);
      if (nw > wr) break;
      row.push(rest[i]); wr = nw; i++;
    }
    const sum = row.reduce((s, r) => s + r.area, 0);
    const thick = sum / short;
    let off = 0;
    for (const r of row) {
      const len = r.area / thick;
      out.push(w >= h ? { ...r.item, x, y: y + off, w: thick, h: len } : { ...r.item, x: x + off, y, w: len, h: thick });
      off += len;
    }
    if (w >= h) { x += thick; w -= thick; } else { y += thick; h -= thick; }
    rest = rest.slice(i);
  }
  return out;
}

const HUES = [168, 200, 140, 45, 260, 15, 320, 95, 225, 30];
const TYPE_COLORS = { Video: '#ff8a6b', Ses: '#ffcf6e', 'Görsel': '#7cc4ff', Belge: '#c3a6ff', 'Arşiv': '#ffa24c', Program: '#6ee0c2', Kod: '#9ad06c', 'Disk görüntüsü': '#ff6b9a', 'Diğer': '#7d8799' };

export default function Analyze({ active, theme }) {
  const [phase, setPhase] = useState('hero');
  const [drives, setDrives] = useState([]);
  const [progress, setProgress] = useState({ files: 0, bytes: 0, current: '' });
  const [node, setNode] = useState(null);
  const [crumbs, setCrumbs] = useState([]);
  const [summary, setSummary] = useState(null);
  const [side, setSide] = useState('largest');
  const [menu, setMenu] = useState(null);
  const [tip, setTip] = useState(null);
  const [box, setBox] = useState({ w: 0, h: 0 });
  const [anim, setAnim] = useState(0);
  const [toast, setToast] = useState(null);
  const [ask, confirmEl] = useConfirm();
  const ref = useRef(null);

  useEffect(() => { window.api.analyze.drives().then(setDrives); return window.api.analyze.onProgress(setProgress); }, []);
  useEffect(() => {
    if (!ref.current) return;
    const ro = new ResizeObserver(([e]) => setBox({ w: e.contentRect.width, h: e.contentRect.height }));
    ro.observe(ref.current);
    return () => ro.disconnect();
  }, [phase]);
  useEffect(() => {
    const c = () => setMenu(null);
    const k = (e) => { if (active && e.key === 'Backspace' && crumbs.length > 1 && e.target.tagName !== 'INPUT') goto(crumbs.length - 2); };
    window.addEventListener('click', c);
    window.addEventListener('keydown', k);
    return () => { window.removeEventListener('click', c); window.removeEventListener('keydown', k); };
  });

  async function start(root) {
    if (!root) return;
    setPhase('scanning'); setProgress({ files: 0, bytes: 0, current: root });
    await window.api.analyze.scan(root);
    const [n, s] = await Promise.all([window.api.analyze.node(null), window.api.analyze.summary()]);
    setNode(n); setCrumbs([n]); setSummary(s); setPhase('map'); setAnim((a) => a + 1);
  }

  async function open(p) {
    const n = await window.api.analyze.node(p);
    if (n) { setNode(n); setCrumbs((c) => [...c, n]); setAnim((a) => a + 1); setTip(null); }
  }

  async function goto(i) {
    const n = await window.api.analyze.node(crumbs[i].path);
    setNode(n); setCrumbs(crumbs.slice(0, i).concat(n)); setAnim((a) => a + 1);
  }

  async function trash(item) {
    const ok = await ask({ title: 'Geri dönüşüm kutusuna taşı', ok: 'Taşı', danger: true,
      body: <><b>{item.name}</b> ({bytes(item.size)}) geri dönüşüm kutusuna taşınacak.</> });
    if (!ok) return;
    const r = await window.api.analyze.trash(item.path);
    if (!r.ok) return setToast({ text: r.error, type: 'err' });
    setToast({ text: `${bytes(r.removed)} geri dönüşüm kutusuna taşındı.`, type: 'ok' });
    const [n, s] = await Promise.all([window.api.analyze.node(node.path), window.api.analyze.summary()]);
    setNode(n); setSummary(s); setCrumbs((c) => c.slice(0, -1).concat(n));
  }

  if (phase === 'hero' || phase === 'scanning') {
    return (
      <Hero planet={theme.planet} active={active} lines={['En geniş göz, haritadaki en küçük klasörü bile görür.', 'Diskinizin tamamı tek bakışta.']}
        busy={phase === 'scanning'} status={<>Taranıyor · <CountNum value={progress.files} ms={250} /> dosya</>}
        sub={<><CountBytes value={progress.bytes} ms={250} /><small className="path live-path">{progress.current}</small>
          <button className="ghost sm" onClick={() => window.api.analyze.cancel()}>Durdur</button></>}>
        {phase === 'hero' && (
          <div className="drives">
            {drives.map((d, i) => (
              <button key={d.mount} className="drive stagger" style={{ '--i': i }} onClick={() => start(d.mount)}>
                <Ring value={d.used / d.size} size={58} stroke={5}><b>{Math.round((d.used / d.size) * 100)}%</b></Ring>
                <span><b>{d.mount}</b><small>{bytes(d.used)} / {bytes(d.size)}</small></span>
              </button>
            ))}
            <button className="drive stagger" style={{ '--i': drives.length }} onClick={async () => start(await window.api.analyze.pick())}>
              <span className="folder-ico">＋</span>
              <span><b>Klasör seç</b><small>Belirli bir klasörü tara</small></span>
            </button>
          </div>
        )}
      </Hero>
    );
  }

  const tiles = [];
  for (const [i, r] of squarify(node.children, 0, 0, box.w, box.h).entries()) {
    const hue = HUES[i % HUES.length];
    tiles.push({ ...r, depth: 0, hue });
    if (r.type === 'dir' && r.children && r.w > 70 && r.h > 54) {
      for (const c of squarify(r.children, r.x + 3, r.y + 22, r.w - 6, r.h - 25)) tiles.push({ ...c, depth: 1, hue, parent: r });
    }
  }
  const maxType = summary ? Math.max(1, ...summary.types.map((t) => t.size)) : 1;

  return (
    <div className="results analyze">
      <div className="results-head">
        <Planet kind={theme.planet} size={64} active={active} />
        <div>
          <div className="headline"><CountBytes value={node.size} /> <span>{node.count ? `${node.count.toLocaleString('tr')} dosya` : ''}</span></div>
          <div className="crumbs">
            {crumbs.map((c, i) => (
              <span key={c.path}>{i > 0 && <i>›</i>}<a onClick={() => goto(i)}>{i === 0 ? c.name : c.name.split('\\').pop()}</a></span>
            ))}
          </div>
        </div>
        <div className="spacer" />
        {crumbs.length > 1 && <button className="ghost" onClick={() => goto(crumbs.length - 2)}>← Geri</button>}
        <button className="ghost" onClick={() => setPhase('hero')}>Yeni tarama</button>
      </div>

      <div className="map-wrap">
        <div className="treemap" ref={ref} onMouseLeave={() => setTip(null)}>
          {tiles.map((t, i) => {
            const bg = t.type === 'dir'
              ? `hsl(${t.hue} 42% ${t.depth ? 30 : 20}%)`
              : t.type === 'file' ? `hsl(${t.hue} 18% ${t.depth ? 24 : 22}%)` : 'hsl(210 8% 16%)';
            return (
              <div key={anim + ':' + (t.path || t.name) + t.depth}
                className={`tile d${t.depth} ${t.type}`}
                style={{ transform: `translate(${t.x}px, ${t.y}px)`, width: t.w, height: t.h, background: bg, animationDelay: `${Math.min(i, 60) * 8}ms` }}
                onClick={(e) => { e.stopPropagation(); if (t.type === 'dir') open(t.path); }}
                onMouseMove={(e) => setTip({ x: e.clientX, y: e.clientY, t })}
                onContextMenu={(e) => { e.preventDefault(); e.stopPropagation(); if (t.path) setMenu({ x: e.clientX, y: e.clientY, item: t }); }}>
                {t.w > 56 && t.h > 20 && (
                  <div className="tile-label"><b>{t.name}</b>{(t.depth === 0 || t.h > 34) && <small>{bytes(t.size)}</small>}</div>
                )}
              </div>
            );
          })}
        </div>

        <aside className="side">
          <div className="seg small">
            <button className={side === 'largest' ? 'on' : ''} onClick={() => setSide('largest')}>En büyük dosyalar</button>
            <button className={side === 'types' ? 'on' : ''} onClick={() => setSide('types')}>Dosya türleri</button>
          </div>
          <div className="side-list">
            {side === 'largest' && summary && summary.largest.map((f, i) => (
              <div key={f.path} className="side-row stagger" style={{ '--i': Math.min(i, 20) }}
                onContextMenu={(e) => { e.preventDefault(); e.stopPropagation(); setMenu({ x: e.clientX, y: e.clientY, item: { ...f, type: 'file' } }); }}
                onDoubleClick={() => window.api.analyze.reveal(f.path)}>
                <span className="grow"><b title={f.path}>{f.name}</b><small className="path">{f.path}</small></span>
                <span className="size">{bytes(f.size)}</span>
              </div>
            ))}
            {side === 'types' && summary && summary.types.map((t, i) => (
              <div key={t.name} className="type-row stagger" style={{ '--i': i }}>
                <div className="type-top"><b><i style={{ background: TYPE_COLORS[t.name] || '#888' }} />{t.name}</b><span className="size">{bytes(t.size)}</span></div>
                <div className="meter"><div style={{ width: `${(t.size / maxType) * 100}%`, background: TYPE_COLORS[t.name] }} /></div>
                <small>{t.count.toLocaleString('tr')} dosya</small>
              </div>
            ))}
          </div>
        </aside>
      </div>

      {tip && !menu && (
        <div className="tooltip" style={{ left: tip.x + 14, top: tip.y + 14 }}>
          <b>{tip.t.name}</b>
          <span>{bytes(tip.t.size)}{tip.t.count ? ` · ${tip.t.count.toLocaleString('tr')} dosya` : ''}</span>
          <small>{node.size ? `%${((tip.t.size / node.size) * 100).toFixed(1)} bu klasörün` : ''}</small>
        </div>
      )}

      {menu && (
        <div className="menu" style={{ left: menu.x, top: menu.y }} onClick={(e) => e.stopPropagation()}>
          <div className="menu-title">{menu.item.name}</div>
          {menu.item.type === 'dir' && <button onClick={() => { open(menu.item.path); setMenu(null); }}>İçine gir</button>}
          {menu.item.type === 'file' && <button onClick={() => { window.api.analyze.open(menu.item.path); setMenu(null); }}>Aç</button>}
          <button onClick={() => { window.api.analyze.reveal(menu.item.path); setMenu(null); }}>Gezgin'de göster</button>
          <button onClick={() => { navigator.clipboard.writeText(menu.item.path); setMenu(null); setToast({ text: 'Yol kopyalandı.', type: 'ok' }); }}>Yolu kopyala</button>
          <button className="danger" onClick={() => { const it = menu.item; setMenu(null); trash(it); }}>Geri dönüşüm kutusuna taşı</button>
        </div>
      )}
      <Toast msg={toast} onDone={() => setToast(null)} />
      {confirmEl}
    </div>
  );
}

import { useEffect, useMemo, useState } from 'react';
import { bytes } from '../util.js';
import Planet from '../Planet.jsx';
import { Hero, CountBytes, Check, Particles, useConfirm } from '../ui.jsx';

export default function Clean({ active, theme }) {
  const [phase, setPhase] = useState('hero');
  const [cats, setCats] = useState([]);
  const [prog, setProg] = useState({ index: 0, total: 1, name: '' });
  const [selected, setSelected] = useState(new Set());
  const [excluded, setExcluded] = useState(new Set());
  const [open, setOpen] = useState(null);
  const [runProg, setRunProg] = useState(null);
  const [result, setResult] = useState(null);
  const [ask, confirmEl] = useConfirm();

  useEffect(() => {
    const a = window.api.clean.onProgress((p) => {
      setProg(p);
      if (p.done) setCats((c) => [...c, p.done]);
    });
    const b = window.api.clean.onRunProgress(setRunProg);
    return () => { a(); b(); };
  }, []);

  async function scan() {
    setCats([]); setResult(null); setPhase('scanning');
    const r = await window.api.clean.scan();
    setCats(r);
    setSelected(new Set(r.filter((c) => c.size > 0 && c.safe).map((c) => c.id)));
    setExcluded(new Set());
    setPhase('results');
  }

  const found = cats.reduce((s, c) => s + c.size, 0);
  const sizeOf = (c) => c.size - c.items.filter((i) => excluded.has(i.path)).reduce((a, i) => a + i.size, 0);
  const total = cats.filter((c) => selected.has(c.id)).reduce((s, c) => s + sizeOf(c), 0);
  const max = Math.max(1, ...cats.map((c) => c.size));
  const groups = useMemo(() => {
    const g = new Map();
    for (const c of cats.filter((c) => c.size > 0)) { if (!g.has(c.group)) g.set(c.group, []); g.get(c.group).push(c); }
    return [...g.entries()];
  }, [cats]);

  async function run() {
    const ok = await ask({ title: 'Temizlik onayı', ok: `${bytes(total)} temizle`,
      body: <>Seçili {selected.size} kategorideki dosyalar <b>kalıcı olarak</b> silinecek. Kullanımdaki dosyalar atlanır.</> });
    if (!ok) return;
    setRunProg({ done: 0, total: 1, freed: 0 });
    setPhase('cleaning');
    const r = await window.api.clean.run({ ids: [...selected], exclude: [...excluded] });
    setResult(r);
    setPhase('done');
  }

  const toggle = (set, setter, key) => { const n = new Set(set); n.has(key) ? n.delete(key) : n.add(key); setter(n); };

  if (phase === 'hero' || phase === 'scanning' || phase === 'cleaning') {
    const busy = phase !== 'hero';
    return (
      <>
        <Hero planet={theme.planet} active={active}
          lines={['Yağmur toprağı temizler.', 'Biriken her şey sessizce akıp gider.']}
          cta="Bilgisayarı tara" onCta={scan} busy={busy}
          progress={phase === 'scanning' ? prog.index / prog.total : runProg ? runProg.done / Math.max(1, runProg.total) : 0}
          status={phase === 'scanning' ? `${prog.name || 'Hazırlanıyor'} taranıyor…` : 'Temizleniyor…'}
          sub={phase === 'scanning' ? <><CountBytes value={found} ms={400} /> bulundu</> : <><CountBytes value={runProg ? runProg.freed : 0} ms={400} /> boşaltıldı</>}>
          {phase === 'scanning' && (
            <div className="chips">
              {cats.filter((c) => c.size > 0).slice(-6).map((c) => <span key={c.id} className="chip pop">{c.name} · {bytes(c.size)}</span>)}
            </div>
          )}
        </Hero>
        {confirmEl}
      </>
    );
  }

  if (phase === 'done') {
    return (
      <div className="hero done">
        <Particles kind="rain" />
        <div className="hero-planet"><Planet kind={theme.planet} size={200} active={active} /></div>
        <div className="big-number"><CountBytes value={result.freed} ms={1600} /></div>
        <p className="tagline">boşaltıldı. Toprak yeniden nefes alıyor.</p>
        {result.failed > 0 && <small className="muted">{result.failed} öğe kullanımda olduğu için atlandı.</small>}
        <button className="pill" onClick={() => setPhase('hero')}>Tamam</button>
      </div>
    );
  }

  return (
    <div className="results">
      <div className="results-head">
        <Planet kind={theme.planet} size={64} active={active} />
        <div>
          <div className="headline"><CountBytes value={found} /> <span>temizlenebilir</span></div>
          <small>{cats.filter((c) => c.size).length} kategoride. Varsayılan seçim yalnızca güvenli olanları içerir.</small>
        </div>
        <div className="spacer" />
        <button className="ghost" onClick={() => setSelected(new Set(cats.filter((c) => c.size && c.safe).map((c) => c.id)))}>Önerilen</button>
        <button className="ghost" onClick={() => setSelected(new Set(cats.filter((c) => c.size).map((c) => c.id)))}>Tümü</button>
        <button className="ghost" onClick={() => setSelected(new Set())}>Hiçbiri</button>
        <button className="ghost" onClick={scan}>Yeniden tara</button>
      </div>

      <div className="scroll-area">
        {groups.map(([g, list], gi) => (
          <div key={g} className="group">
            <h4>{g}</h4>
            {list.map((c, i) => (
              <div key={c.id} className={`cat stagger ${selected.has(c.id) ? 'sel' : ''} ${open === c.id ? 'open' : ''}`} style={{ '--i': gi * 3 + i }}>
                <div className="cat-row" onClick={() => setOpen(open === c.id ? null : c.id)}>
                  <Check on={selected.has(c.id)} onChange={() => toggle(selected, setSelected, c.id)} />
                  <div className="grow">
                    <b>{c.name} {!c.safe && <span className="tag">Gözden geçirin</span>}</b>
                    <small>{c.desc} · {c.count.toLocaleString('tr')} öğe</small>
                    <div className="meter"><div style={{ width: `${(c.size / max) * 100}%` }} /></div>
                  </div>
                  <span className="size">{bytes(sizeOf(c))}</span>
                  <span className="chev">›</span>
                </div>
                <div className="cat-items">
                  <div>
                    {open === c.id && c.items.map((it) => (
                      <label key={it.path} className={`item ${excluded.has(it.path) ? 'off' : ''}`}>
                        <Check on={!excluded.has(it.path)} onChange={() => toggle(excluded, setExcluded, it.path)} />
                        <span className="path" title={it.path}>{it.path}</span>
                        <span className="size">{bytes(it.size)}</span>
                      </label>
                    ))}
                    {open === c.id && c.count > c.items.length && <small className="more">…ve {(c.count - c.items.length).toLocaleString('tr')} küçük öğe daha</small>}
                  </div>
                </div>
              </div>
            ))}
          </div>
        ))}
        {groups.length === 0 && <div className="empty">Temizlenecek bir şey bulunamadı. Her şey tertemiz.</div>}
      </div>

      <div className="actionbar">
        <span>{selected.size} kategori seçili</span>
        <button className="pill" onClick={run} disabled={!selected.size || !total}><CountBytes value={total} ms={400} /> temizle</button>
      </div>
      {confirmEl}
    </div>
  );
}

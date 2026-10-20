import { useEffect, useMemo, useState } from 'react';
import Planet from '../Planet.jsx';
import { Hero, Toggle, Check, Toast, useConfirm } from '../ui.jsx';

const STATE = { running: 'Çalışıyor', ok: 'Tamam', fail: 'Başarısız' };

export default function Optimize({ active, theme }) {
  const [phase, setPhase] = useState('hero');
  const [tab, setTab] = useState('tasks');
  const [tasks, setTasks] = useState([]);
  const [sel, setSel] = useState(new Set());
  const [state, setState] = useState({});
  const [out, setOut] = useState({});
  const [busy, setBusy] = useState(false);
  const [startup, setStartup] = useState(null);
  const [toast, setToast] = useState(null);
  const [ask, confirmEl] = useConfirm();

  useEffect(() => {
    window.api.optimize.list().then((t) => { setTasks(t); setSel(new Set(t.filter((x) => x.recommended).map((x) => x.id))); });
    return window.api.optimize.onUpdate((u) => {
      setState((s) => ({ ...s, [u.id]: u.state }));
      if (u.output != null) setOut((o) => ({ ...o, [u.id]: u.output }));
    });
  }, []);

  useEffect(() => { if (tab === 'startup' && !startup) window.api.startup.list().then(setStartup); }, [tab]);

  const groups = useMemo(() => {
    const g = new Map();
    for (const t of tasks) { if (!g.has(t.group)) g.set(t.group, []); g.get(t.group).push(t); }
    return [...g.entries()];
  }, [tasks]);

  async function run() {
    const chosen = tasks.filter((t) => sel.has(t.id));
    const admin = chosen.filter((t) => t.admin).length;
    const reboot = chosen.some((t) => t.reboot);
    const ok = await ask({ title: `${chosen.length} görev çalıştırılacak`, ok: 'Başlat',
      body: <>
        {admin > 0 && <p>{admin} görev yönetici izni istiyor; hepsi için <b>tek bir</b> Windows onayı açılacak.</p>}
        {reboot && <p>Bazı görevlerin etkisi yeniden başlatmadan sonra görülür.</p>}
        {!admin && !reboot && <p>Görevler sırayla çalışacak.</p>}
      </> });
    if (!ok) return;
    setBusy(true);
    setState(Object.fromEntries(chosen.map((t) => [t.id, 'queued'])));
    setOut({});
    const res = await window.api.optimize.run(chosen.map((t) => t.id));
    setBusy(false);
    const okN = res.filter((r) => r.ok).length;
    setToast({ text: `${okN}/${res.length} görev başarıyla tamamlandı.`, type: okN === res.length ? 'ok' : 'err' });
  }

  async function setStart(item, on) {
    setStartup((s) => s.map((x) => (x.id === item.id ? { ...x, state: on ? 'on' : 'off', pending: true } : x)));
    const r = await window.api.startup.set(item.id, on);
    setStartup((s) => s.map((x) => (x.id === item.id ? { ...x, state: r.ok ? (on ? 'on' : 'off') : item.state, pending: false } : x)));
    if (!r.ok) setToast({ text: 'Değişiklik uygulanamadı.', type: 'err' });
  }

  if (phase === 'hero') {
    return <Hero planet={theme.planet} active={active} lines={['En yakın yörünge, en hızlı tur.', 'Küçük bakımlar, akıcı bir sistem.']}
      cta="Bakıma başla" onCta={() => setPhase('list')} />;
  }

  const doneCount = Object.values(state).filter((s) => s === 'ok' || s === 'fail').length;
  const runCount = Object.keys(state).length;

  return (
    <div className="results">
      <div className="results-head">
        <Planet kind={theme.planet} size={64} speed={busy ? 10 : 1} active={active} />
        <div>
          <div className="headline">{busy ? <>Çalışıyor <span>{doneCount}/{runCount}</span></> : <>{tasks.length} <span>bakım görevi</span></>}</div>
          <small>Yönetici gerektirenler tek bir onayla birlikte çalışır.</small>
        </div>
        <div className="spacer" />
        <div className="seg">
          <button className={tab === 'tasks' ? 'on' : ''} onClick={() => setTab('tasks')}>Bakım görevleri</button>
          <button className={tab === 'startup' ? 'on' : ''} onClick={() => setTab('startup')}>Başlangıç uygulamaları</button>
        </div>
      </div>

      {tab === 'tasks' && (
        <>
          <div className="scroll-area">
            {groups.map(([g, list], gi) => (
              <div key={g} className="group">
                <h4>{g}</h4>
                <div className="task-grid">
                  {list.map((t, i) => (
                    <div key={t.id} className={`task stagger ${sel.has(t.id) ? 'sel' : ''} ${state[t.id] || ''}`} style={{ '--i': gi * 4 + i }}
                      onClick={() => !busy && setSel((s) => { const n = new Set(s); n.has(t.id) ? n.delete(t.id) : n.add(t.id); return n; })}>
                      <div className="task-top">
                        <Check on={sel.has(t.id)} disabled={busy} onChange={() => setSel((s) => { const n = new Set(s); n.has(t.id) ? n.delete(t.id) : n.add(t.id); return n; })} />
                        <b className="grow">{t.name}</b>
                        {state[t.id] && state[t.id] !== 'queued' ? (
                          <span className={`status-dot ${state[t.id]}`}>{state[t.id] === 'running' ? <i className="spin" /> : state[t.id] === 'ok' ? '✓' : '!'}</span>
                        ) : null}
                      </div>
                      <small>{t.desc}</small>
                      <div className="tags">
                        {t.admin && <span className="tag">Yönetici</span>}
                        {t.recommended && <span className="tag soft">Önerilen</span>}
                        {t.reboot && <span className="tag">Yeniden başlatma</span>}
                        {state[t.id] && STATE[state[t.id]] && <span className={`tag st-${state[t.id]}`}>{STATE[state[t.id]]}</span>}
                      </div>
                      {out[t.id] && state[t.id] === 'fail' && <pre>{out[t.id]}</pre>}
                    </div>
                  ))}
                </div>
              </div>
            ))}
          </div>
          <div className="actionbar">
            <button className="ghost" disabled={busy} onClick={() => setSel(new Set(tasks.filter((x) => x.recommended).map((x) => x.id)))}>Önerilen</button>
            <button className="ghost" disabled={busy} onClick={() => setSel(new Set())}>Hiçbiri</button>
            <button className="pill" onClick={run} disabled={busy || !sel.size}>{busy ? 'Çalışıyor…' : `${sel.size} görevi çalıştır`}</button>
          </div>
        </>
      )}

      {tab === 'startup' && (
        <div className="scroll-area">
          {!startup && <div className="empty">Başlangıç öğeleri okunuyor…</div>}
          {startup && (
            <div className="group">
              <h4>Windows açılırken başlayanlar · {startup.filter((s) => s.state === 'on').length} etkin</h4>
              {startup.map((s, i) => (
                <div key={s.id} className={`startup-row stagger ${s.state}`} style={{ '--i': i }}>
                  <div className="grow">
                    <b>{s.name}</b>
                    <small className="path" title={s.command}>{s.command}</small>
                  </div>
                  <span className="tag soft">{s.source}</span>
                  {s.admin && <span className="tag">Yönetici</span>}
                  <Toggle on={s.state === 'on'} disabled={s.pending} onChange={(on) => setStart(s, on)} />
                </div>
              ))}
              {startup.length === 0 && <div className="empty small">Başlangıç öğesi bulunamadı.</div>}
              <button className="ghost" onClick={() => window.api.startup.openSettings()}>Windows ayarlarında aç</button>
            </div>
          )}
        </div>
      )}
      <Toast msg={toast} onDone={() => setToast(null)} />
      {confirmEl}
    </div>
  );
}

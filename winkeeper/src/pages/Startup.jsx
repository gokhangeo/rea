import { useMemo, useState } from 'react';
import Planet from '../Planet.jsx';
import { Hero, Toggle, Check, Toast, useConfirm } from '../ui.jsx';

const FILTERS = [
  { id: 'all', name: 'Tümü' },
  { id: 'on', name: 'Açık' },
  { id: 'off', name: 'Kapalı' },
  { id: 'reg', name: 'Kayıt' },
  { id: 'folder', name: 'Klasör' },
  { id: 'task', name: 'Görev' },
  { id: 'store', name: 'Mağaza' },
];

function Icon({ item }) {
  if (item.icon) return <img className="app-icon" src={item.icon} width={32} height={32} alt="" />;
  const h = [...item.title].reduce((a, c) => a + c.charCodeAt(0), 0) % 360;
  return <span className="app-icon letter" style={{ width: 32, height: 32, background: `hsl(${h} 40% 34%)` }}>{item.title[0]}</span>;
}

export default function Startup({ active, theme }) {
  const [phase, setPhase] = useState('hero');
  const [items, setItems] = useState([]);
  const [q, setQ] = useState('');
  const [filter, setFilter] = useState('all');
  const [sel, setSel] = useState(new Set());
  const [busy, setBusy] = useState(false);
  const [toast, setToast] = useState(null);
  const [ask, confirmEl] = useConfirm();

  async function load() {
    setPhase((p) => (p === 'hero' ? 'loading' : p));
    setItems(await window.api.startup.list());
    setSel(new Set());
    setPhase('list');
  }

  const shown = useMemo(() => {
    const ql = q.toLowerCase();
    let f = items.filter((x) => `${x.title} ${x.name} ${x.publisher} ${x.command}`.toLowerCase().includes(ql));
    if (filter === 'on' || filter === 'off') f = f.filter((x) => x.state === filter);
    else if (filter !== 'all') f = f.filter((x) => x.kind === filter);
    return [...f].sort((a, b) => (a.state === b.state ? a.title.localeCompare(b.title, 'tr') : a.state === 'on' ? -1 : 1));
  }, [items, q, filter]);

  const onCount = items.filter((x) => x.state === 'on').length;
  const patch = (id, p) => setItems((s) => s.map((x) => (x.id === id ? { ...x, ...p } : x)));

  async function toggle(item, on) {
    patch(item.id, { state: on ? 'on' : 'off', pending: true });
    const r = await window.api.startup.set(item.id, on);
    patch(item.id, { state: r.ok ? (on ? 'on' : 'off') : item.state, pending: false });
    setToast(r.ok
      ? { text: `${item.title} ${on ? 'açılışta başlayacak' : 'artık açılışta başlamayacak'}.`, type: 'ok' }
      : { text: r.error || 'Değişiklik uygulanamadı.', type: 'err' });
  }

  async function bulk(on) {
    const list = items.filter((x) => sel.has(x.id) && x.state !== (on ? 'on' : 'off'));
    if (!list.length) { setSel(new Set()); return; }
    const admin = list.filter((x) => x.admin).length;
    if (admin) {
      const ok = await ask({ title: `${list.length} öğe ${on ? 'açılacak' : 'kapatılacak'}`, ok: on ? 'Aç' : 'Kapat',
        body: <p>{admin} öğe tüm kullanıcılar için kayıtlı; gerekirse hepsi için <b>tek bir</b> Windows onayı açılacak.</p> });
      if (!ok) return;
    }
    setBusy(true);
    list.forEach((x) => patch(x.id, { pending: true }));
    const r = await window.api.startup.setMany(list.map((x) => x.id), on);
    if (r.reloaded) setItems(r.reloaded);
    else setItems((s) => s.map((x) => {
      const res = r.results.find((y) => y.id === x.id);
      return res ? { ...x, pending: false, state: res.ok ? (on ? 'on' : 'off') : x.state } : x;
    }));
    const okN = r.results.filter((x) => x.ok).length;
    setBusy(false);
    setSel(new Set());
    setToast({ text: `${okN}/${r.results.length} öğe ${on ? 'açıldı' : 'kapatıldı'}.`, type: okN === r.results.length ? 'ok' : 'err' });
  }

  async function remove(item) {
    const ok = await ask({ title: `${item.title} başlangıçtan silinsin mi?`, ok: 'Sil', danger: true,
      body: <>Yalnızca başlangıç girdisi silinir, programın kendisi kalır. {item.kind === 'folder'
        ? 'Kısayol geri dönüşüm kutusuna taşınır.'
        : <>Bu işlem <b>geri alınamaz</b>; sadece kapatmak isterseniz anahtarı kullanın.</>}</> });
    if (!ok) return;
    const r = await window.api.startup.remove(item.id);
    setToast(r.ok ? { text: `${item.title} silindi.`, type: 'ok' } : { text: r.error || 'Silinemedi.', type: 'err' });
    if (r.ok) load();
  }

  async function add() {
    const r = await window.api.startup.add();
    if (r.canceled) return;
    setToast(r.ok ? { text: `${r.name} başlangıca eklendi.`, type: 'ok' } : { text: 'Eklenemedi.', type: 'err' });
    if (r.ok) load();
  }

  if (phase === 'hero' || phase === 'loading') {
    return <Hero planet={theme.planet} active={active} busy={phase === 'loading'} status="Sabah yıldızı uyananları sayıyor…"
      lines={['Gün doğmadan uyananlar,', 'yalnızca istediklerin olsun.']} cta="Başlangıcı incele" onCta={load} />;
  }

  const allSel = shown.length > 0 && shown.every((x) => sel.has(x.id));

  return (
    <div className="results startup">
      <div className="results-head">
        <Planet kind={theme.planet} size={64} speed={busy ? 8 : 1} active={active} />
        <div>
          <div className="headline">{onCount} <span>/ {items.length} öğe açılışta başlıyor</span></div>
          <small>Kapattığınız öğeler silinmez; istediğiniz an yeniden açabilirsiniz.</small>
        </div>
        <div className="spacer" />
        <input className="search" placeholder="Ara…" value={q} onChange={(e) => setQ(e.target.value)} />
        <button className="ghost" onClick={load} disabled={busy} title="Listeyi yeniden oku">Yenile</button>
      </div>

      <div className="startup-bar">
        <div className="seg small">
          {FILTERS.map((f) => {
            const n = f.id === 'all' ? items.length : f.id === 'on' || f.id === 'off' ? items.filter((x) => x.state === f.id).length : items.filter((x) => x.kind === f.id).length;
            if (!n && f.id !== 'all' && f.id !== filter) return null;
            return <button key={f.id} className={filter === f.id ? 'on' : ''} onClick={() => setFilter(f.id)}>{f.name} <span className="muted">{n}</span></button>;
          })}
        </div>
        <div className="spacer" />
        <button className="ghost sm" onClick={add}>+ Program ekle</button>
        <button className="ghost sm" onClick={() => window.api.startup.openFolder()}>Başlangıç klasörü</button>
        <button className="ghost sm" onClick={() => window.api.startup.openSettings()}>Windows ayarları</button>
      </div>

      <div className="scroll-area">
        {shown.length > 0 && (
          <div className="startup-row head">
            <Check on={allSel} onChange={() => setSel(allSel ? new Set() : new Set(shown.map((x) => x.id)))} />
            <small className="grow">{sel.size ? `${sel.size} seçili` : 'Tümünü seç'}</small>
          </div>
        )}
        {shown.map((s, i) => (
          <div key={`${s.kind}-${s.source}-${s.name}`} className={`startup-row stagger ${s.state} ${sel.has(s.id) ? 'sel' : ''}`} style={{ '--i': Math.min(i, 20) }}
            onClick={() => setSel((p) => { const n = new Set(p); n.has(s.id) ? n.delete(s.id) : n.add(s.id); return n; })}>
            <Check on={sel.has(s.id)} onChange={() => setSel((p) => { const n = new Set(p); n.has(s.id) ? n.delete(s.id) : n.add(s.id); return n; })} />
            <Icon item={s} />
            <div className="grow">
              <b>{s.title}</b>{s.publisher && <span className="muted"> · {s.publisher}</span>}
              <small className="path" title={s.command}>{s.command}</small>
            </div>
            <div className="tags">
              <span className="tag soft">{s.source}</span>
              {s.admin && <span className="tag">Yönetici</span>}
              {!s.exists && <span className="tag st-fail" title="Komutun gösterdiği dosya bulunamadı; girdi büyük olasılıkla artık işe yaramıyor.">Dosya yok</span>}
            </div>
            <div className="row-actions" onClick={(e) => e.stopPropagation()}>
              {(s.exists && s.kind !== 'store') && <button className="ghost sm" onClick={() => window.api.startup.reveal(s.id)} title="Dosya konumunu aç">Konum</button>}
              {s.kind !== 'store' && <button className="ghost sm danger" onClick={() => remove(s)} title="Başlangıç girdisini sil">Sil</button>}
            </div>
            <Toggle on={s.state === 'on'} disabled={s.pending || busy} onChange={(on) => toggle(s, on)} />
          </div>
        ))}
        {items.length === 0 && <div className="empty">Açılışta başlayan öğe bulunamadı.</div>}
        {items.length > 0 && shown.length === 0 && <div className="empty small">Bu filtreye uyan öğe yok.</div>}
      </div>

      <div className={`actionbar ${sel.size ? '' : 'hidden'}`}>
        <span>{sel.size} öğe seçili</span>
        <button className="ghost" disabled={busy} onClick={() => setSel(new Set())}>Vazgeç</button>
        <button className="ghost" disabled={busy} onClick={() => bulk(true)}>Aç</button>
        <button className="pill" disabled={busy} onClick={() => bulk(false)}>{busy ? 'Uygulanıyor…' : 'Açılışta başlatma'}</button>
      </div>
      <Toast msg={toast} onDone={() => setToast(null)} />
      {confirmEl}
    </div>
  );
}

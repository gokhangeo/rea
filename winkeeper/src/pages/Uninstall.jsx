import { useMemo, useState } from 'react';
import { bytes } from '../util.js';
import Planet from '../Planet.jsx';
import { Hero, Check, CountBytes, Toast, useConfirm } from '../ui.jsx';

const FILTERS = [
  { id: 'all', name: 'Tümü' },
  { id: 'desktop', name: 'Masaüstü' },
  { id: 'store', name: 'Mağaza' },
  { id: 'big', name: 'Büyük (>500 MB)' },
  { id: 'recent', name: 'Son 30 gün' },
];

function Avatar({ app, size = 36 }) {
  if (app.iconData) return <img className="app-icon" src={app.iconData} width={size} height={size} alt="" />;
  const h = [...app.name].reduce((a, c) => a + c.charCodeAt(0), 0) % 360;
  return <span className="app-icon letter" style={{ width: size, height: size, background: `hsl(${h} 45% 35%)` }}>{app.name[0]}</span>;
}

export default function Uninstall({ active, theme }) {
  const [phase, setPhase] = useState('hero');
  const [apps, setApps] = useState([]);
  const [q, setQ] = useState('');
  const [filter, setFilter] = useState('all');
  const [sort, setSort] = useState('name');
  const [sel, setSel] = useState(new Set());
  const [detail, setDetail] = useState(null);
  const [queue, setQueue] = useState(null); // [{ app, state }]
  const [left, setLeft] = useState([]);
  const [chosen, setChosen] = useState(new Set());
  const [toast, setToast] = useState(null);
  const [ask, confirmEl] = useConfirm();

  async function load() {
    setPhase('loading');
    setApps(await window.api.uninstall.list());
    setPhase('list');
  }

  const shown = useMemo(() => {
    const ql = q.toLowerCase();
    const recent = Date.now() - 30 * 86400000;
    let f = apps.filter((a) => (a.name + ' ' + a.publisher).toLowerCase().includes(ql));
    if (filter === 'desktop' || filter === 'store') f = f.filter((a) => a.kind === filter);
    if (filter === 'big') f = f.filter((a) => a.size > 500 * 1048576);
    if (filter === 'recent') f = f.filter((a) => a.installDate && new Date(a.installDate).getTime() > recent);
    if (sort === 'size') f = [...f].sort((a, b) => b.size - a.size);
    if (sort === 'date') f = [...f].sort((a, b) => (b.installDate || '').localeCompare(a.installDate || ''));
    return f;
  }, [apps, q, filter, sort]);

  const selApps = apps.filter((a) => sel.has(a.id));
  const selSize = selApps.reduce((s, a) => s + a.size, 0);

  async function uninstall(list, deep = false) {
    const ok = await ask({
      title: (list.length > 1 ? `${list.length} uygulama` : list[0].name) + (deep ? ' tamamen kaldırılacak' : ' kaldırılacak'), ok: deep ? 'Tamamen kaldır' : 'Kaldır', danger: true,
      body: deep
        ? <>Önce uygulamanın kendi kaldırıcısı açılır. Ardından geride kalan <b>tüm klasörler</b> (ayarlar dahil) geri dönüşüm kutusuna taşınır ve uygulamaya ait <b>kayıt defteri anahtarları silinir</b>. Kayıt defteri silme işlemi geri alınamaz.</>
        : <>Her uygulamanın kendi kaldırıcısı sırayla açılacak. Bittikten sonra geride kalan klasörleri ve kayıt defteri girdilerini birlikte gözden geçireceğiz.</>,
    });
    if (!ok) return;
    setDetail(null);
    const qd = list.map((a) => ({ app: a, state: 'wait' }));
    setQueue(qd); setLeft([]); setPhase('queue');
    const allLeft = [];
    for (let i = 0; i < qd.length; i++) {
      qd[i] = { ...qd[i], state: 'run' }; setQueue([...qd]);
      const r = await window.api.uninstall.run(qd[i].app.id);
      const l = await window.api.uninstall.leftovers(qd[i].app.id);
      allLeft.push(...l.map((x) => ({ ...x, app: qd[i].app.name })));
      qd[i] = { ...qd[i], state: r.ok ? 'ok' : 'fail', error: r.error }; setQueue([...qd]);
    }
    if (deep && allLeft.length) {
      const r = await window.api.uninstall.trash(allLeft.map((x) => x.path));
      const okN = r.filter((x) => x.ok).length;
      setToast({ text: `${okN} kalıntı temizlendi${r.length - okN ? `, ${r.length - okN} silinemedi` : ''}.`, type: okN === r.length ? 'ok' : 'err' });
      const remaining = allLeft.filter((l) => !r.find((x) => x.ok && x.path === l.path));
      setLeft(remaining);
      setChosen(new Set(remaining.map((x) => x.path)));
      return;
    }
    setLeft(allLeft);
    setChosen(new Set(allLeft.filter((x) => !x.personal).map((x) => x.path)));
  }

  async function scanLeftOnly(a) {
    const l = await window.api.uninstall.leftovers(a.id);
    setDetail({ ...a, leftovers: l });
  }

  async function trashLeft(items) {
    if (items.some((i) => i.personal)) {
      const ok = await ask({ title: 'Kişisel veri uyarısı', ok: 'Yine de taşı', danger: true,
        body: <>Seçimde ayar ya da kullanıcı verisi içerebilecek klasörler var (AppData\Roaming). Geri dönüşüm kutusundan geri alabilirsiniz.</> });
      if (!ok) return false;
    }
    const r = await window.api.uninstall.trash(items.map((i) => i.path));
    const ok = r.filter((x) => x.ok);
    setToast({ text: `${ok.length} kalıntı temizlendi${r.length - ok.length ? `, ${r.length - ok.length} taşınamadı` : ''}.`, type: ok.length ? 'ok' : 'err' });
    return r;
  }

  if (phase === 'hero' || phase === 'loading') {
    return (
      <Hero planet={theme.planet} active={active}
        lines={['Kızıl toz, artık kullanmadığın her şeyi örter.', 'Geride iz bırakmadan.']}
        cta="Uygulamaları seç" onCta={load} busy={phase === 'loading'} status="Uygulamalar okunuyor…" />
    );
  }

  if (phase === 'queue') {
    const done = queue.every((x) => x.state === 'ok' || x.state === 'fail');
    const leftSize = left.filter((l) => chosen.has(l.path)).reduce((s, l) => s + l.size, 0);
    return (
      <div className="results">
        <div className="results-head">
          <Planet kind={theme.planet} size={64} speed={done ? 1 : 8} active={active} />
          <div>
            <div className="headline">{done ? 'Kaldırma tamamlandı' : 'Kaldırılıyor…'}</div>
            <small>{done ? 'Geride kalan klasörleri gözden geçirin.' : 'Açılan kaldırıcı pencerelerini takip edin.'}</small>
          </div>
        </div>
        <div className="scroll-area">
          <div className="queue">
            {queue.map((x, i) => (
              <div key={x.app.id} className={`queue-row stagger ${x.state}`} style={{ '--i': i }}>
                <Avatar app={x.app} />
                <b className="grow">{x.app.name}</b>
                <span className="state-badge">{{ wait: 'Bekliyor', run: 'Kaldırıcı açık', ok: 'Kaldırıldı', fail: 'Tamamlanmadı' }[x.state]}</span>
              </div>
            ))}
          </div>
          {done && (
            <div className="group">
              <h4>Kalıntılar</h4>
              {left.length === 0 && <div className="empty small">Geride klasör kalmadı.</div>}
              {left.map((l, i) => (
                <label key={l.path} className="item big stagger" style={{ '--i': i }}>
                  <Check on={chosen.has(l.path)} onChange={() => { const n = new Set(chosen); n.has(l.path) ? n.delete(l.path) : n.add(l.path); setChosen(n); }} />
                  <span className="grow">
                    <span className="path" title={l.path}>{l.path}</span>
                    <small>{l.kind === 'reg' ? 'Kayıt defteri' : 'Klasör'} · {l.app} · {l.why}{l.personal && <em className="warn"> · kişisel veri olabilir</em>}</small>
                  </span>
                  <span className="size">{l.kind === 'reg' ? '' : bytes(l.size)}</span>
                </label>
              ))}
            </div>
          )}
        </div>
        {done && (
          <div className="actionbar">
            <button className="ghost" onClick={() => { setPhase('list'); load(); setSel(new Set()); }}>Listeye dön</button>
            <button className="pill" disabled={!chosen.size} onClick={async () => {
              const r = await trashLeft(left.filter((l) => chosen.has(l.path)));
              if (r) { setLeft(left.filter((l) => !r.find((x) => x.ok && x.path === l.path))); setChosen(new Set()); }
            }}><CountBytes value={leftSize} ms={300} /> kalıntıyı kaldır</button>
          </div>
        )}
        <Toast msg={toast} onDone={() => setToast(null)} />
        {confirmEl}
      </div>
    );
  }

  return (
    <div className="results">
      <div className="results-head">
        <Planet kind={theme.planet} size={64} active={active} />
        <div>
          <div className="headline">{apps.length} <span>uygulama</span></div>
          <small>Toplam <CountBytes value={apps.reduce((s, a) => s + a.size, 0)} /> (bildirilen boyutlar)</small>
        </div>
        <div className="spacer" />
        <input className="search" placeholder="Ara…" value={q} onChange={(e) => setQ(e.target.value)} />
        <select value={sort} onChange={(e) => setSort(e.target.value)}>
          <option value="name">Ada göre</option>
          <option value="size">Boyuta göre</option>
          <option value="date">Kurulum tarihine göre</option>
        </select>
      </div>
      <div className="seg">
        {FILTERS.map((f) => <button key={f.id} className={filter === f.id ? 'on' : ''} onClick={() => setFilter(f.id)}>{f.name}</button>)}
      </div>

      <div className="scroll-area">
        <div className="app-grid">
          {shown.map((a, i) => (
            <div key={a.id} className={`app-card stagger ${sel.has(a.id) ? 'sel' : ''}`} style={{ '--i': Math.min(i, 30) }} onClick={() => setDetail(a)}>
              <Check on={sel.has(a.id)} onChange={() => { const n = new Set(sel); n.has(a.id) ? n.delete(a.id) : n.add(a.id); setSel(n); }} />
              <Avatar app={a} />
              <div className="grow">
                <b title={a.name}>{a.name}</b>
                <small>{a.publisher || '—'}{a.kind === 'store' ? ' · Mağaza' : ''}</small>
              </div>
              <span className="size">{a.size ? bytes(a.size) : ''}</span>
            </div>
          ))}
        </div>
        {shown.length === 0 && <div className="empty">Eşleşen uygulama yok.</div>}
      </div>

      <div className={`actionbar ${sel.size ? '' : 'hidden'}`}>
        <span>{sel.size} uygulama · {bytes(selSize)}</span>
        <button className="ghost" onClick={() => setSel(new Set())}>Seçimi temizle</button>
        <button className="ghost danger" onClick={() => uninstall(selApps, true)}>Tamamen kaldır</button>
        <button className="pill danger" onClick={() => uninstall(selApps)}>Seçilenleri kaldır</button>
      </div>

      <div className={`drawer ${detail ? 'open' : ''}`} onClick={() => setDetail(null)}>
        {detail && (
          <aside onClick={(e) => e.stopPropagation()}>
            <button className="x" onClick={() => setDetail(null)}>×</button>
            <Avatar app={detail} size={56} />
            <h2>{detail.name}</h2>
            <dl>
              <dt>Yayıncı</dt><dd>{detail.publisher || '—'}</dd>
              <dt>Sürüm</dt><dd>{detail.version || '—'}</dd>
              <dt>Tür</dt><dd>{detail.kind === 'store' ? 'Microsoft Store' : 'Masaüstü'}</dd>
              <dt>Kurulum</dt><dd>{detail.installDate || '—'}</dd>
              <dt>Boyut</dt><dd>{detail.size ? bytes(detail.size) : '—'}</dd>
              <dt>Konum</dt><dd className="path" title={detail.location}>{detail.location || '—'}</dd>
            </dl>
            <div className="drawer-actions">
              <button className="pill danger" onClick={() => uninstall([detail])}>Kaldır</button>
              <button className="ghost danger" onClick={() => uninstall([detail], true)}>Tamamen kaldır (kayıt defteri dahil)</button>
              <button className="ghost" onClick={() => scanLeftOnly(detail)}>Kalıntıları tara</button>
              {detail.location && <button className="ghost" onClick={() => window.api.uninstall.reveal(detail.id)}>Klasörü aç</button>}
            </div>
            {detail.leftovers && (
              <div className="group">
                <h4>Bulunan klasörler</h4>
                {detail.leftovers.length === 0 && <small>Eşleşen klasör yok.</small>}
                {detail.leftovers.map((l) => (
                  <div key={l.path} className="item">
                    <span className="grow"><span className="path" title={l.path}>{l.path}</span><small>{l.kind === 'reg' ? 'Kayıt defteri · ' : ''}{l.why}{l.personal && <em className="warn"> · kişisel veri olabilir</em>}</small></span>
                    <span className="size">{l.kind === 'reg' ? '' : bytes(l.size)}</span>
                    <button className="ghost sm" onClick={async () => {
                      const r = await trashLeft([l]);
                      if (r && r[0].ok) setDetail({ ...detail, leftovers: detail.leftovers.filter((x) => x.path !== l.path) });
                    }}>Taşı</button>
                  </div>
                ))}
              </div>
            )}
          </aside>
        )}
      </div>
      <Toast msg={toast} onDone={() => setToast(null)} />
      {confirmEl}
    </div>
  );
}

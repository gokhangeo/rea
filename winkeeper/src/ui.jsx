import { useEffect, useRef, useState } from 'react';
import Planet from './Planet.jsx';
import { bytes } from './util.js';

// Değeri yumuşak biçimde hedefe doğru sayar.
export function useTween(target, ms = 900) {
  const [v, setV] = useState(target || 0);
  const from = useRef(v);
  useEffect(() => {
    const start = performance.now();
    const a = from.current;
    let raf;
    const step = (now) => {
      const t = Math.min(1, (now - start) / ms);
      const e = 1 - Math.pow(1 - t, 3);
      const cur = a + (target - a) * e;
      from.current = cur;
      setV(cur);
      if (t < 1) raf = requestAnimationFrame(step);
    };
    raf = requestAnimationFrame(step);
    return () => cancelAnimationFrame(raf);
  }, [target]);
  return v;
}

export function CountBytes({ value, ms }) {
  return <>{bytes(useTween(value || 0, ms))}</>;
}

export function CountNum({ value, ms, digits = 0, suffix = '' }) {
  const v = useTween(value || 0, ms);
  return <>{v.toFixed(digits)}{suffix}</>;
}

export function Toggle({ on, onChange, disabled }) {
  return (
    <button type="button" className={`toggle ${on ? 'on' : ''}`} disabled={disabled}
      onClick={(e) => { e.stopPropagation(); onChange(!on); }} aria-pressed={on}>
      <span />
    </button>
  );
}

export function Check({ on, onChange, disabled }) {
  return (
    <button type="button" className={`check ${on ? 'on' : ''}`} disabled={disabled}
      onClick={(e) => { e.stopPropagation(); onChange(!on); }} aria-pressed={on}>
      <svg viewBox="0 0 16 16"><path d="M3.5 8.5l3 3 6-7" /></svg>
    </button>
  );
}

export function Ring({ value, size = 64, stroke = 6, color = 'var(--accent)', children }) {
  const r = (size - stroke) / 2;
  const c = 2 * Math.PI * r;
  const v = useTween(Math.max(0, Math.min(1, value || 0)), 700);
  return (
    <div className="ring" style={{ width: size, height: size }}>
      <svg width={size} height={size}>
        <circle cx={size / 2} cy={size / 2} r={r} stroke="var(--track)" strokeWidth={stroke} fill="none" />
        <circle cx={size / 2} cy={size / 2} r={r} stroke={color} strokeWidth={stroke} fill="none" strokeLinecap="round"
          strokeDasharray={c} strokeDashoffset={c * (1 - v)} transform={`rotate(-90 ${size / 2} ${size / 2})`} />
      </svg>
      <div className="ring-label">{children}</div>
    </div>
  );
}

export function Spark({ data, max, color = 'var(--accent)', height = 44, n = 30 }) {
  const id = useRef('g' + Math.random().toString(36).slice(2)).current;
  if (!data || data.length < 2) return <svg className="spark" style={{ height }} />;
  const m = max || Math.max(...data, 1);
  const pts = data.map((v, i) => [((i + n - data.length) / (n - 1)) * 100, 30 - (v / m) * 27]);
  const line = pts.map((p) => p.join(',')).join(' ');
  const area = `${pts[0][0]},30 ${line} ${pts[pts.length - 1][0]},30`;
  return (
    <svg className="spark" viewBox="0 0 100 30" preserveAspectRatio="none" style={{ height }}>
      <defs>
        <linearGradient id={id} x1="0" y1="0" x2="0" y2="1">
          <stop offset="0" stopColor={color} stopOpacity=".35" />
          <stop offset="1" stopColor={color} stopOpacity="0" />
        </linearGradient>
      </defs>
      <polygon points={area} fill={`url(#${id})`} />
      <polyline points={line} fill="none" stroke={color} strokeWidth="1.6" vectorEffect="non-scaling-stroke" strokeLinejoin="round" />
    </svg>
  );
}

// Gezegen + iki satırlık şiirsel slogan + ana düğme. Tarama sırasında yörünge halkası döner.
export function Hero({ planet, active, lines, cta, onCta, busy, progress, status, sub, children }) {
  return (
    <div className={`hero ${busy ? 'busy' : ''}`}>
      <div className="hero-planet">
        {busy && <div className="orbit"><i /><i /><i /></div>}
        {busy && progress != null && (
          <svg className="hero-progress" viewBox="0 0 100 100">
            <circle cx="50" cy="50" r="48" pathLength="100" strokeDasharray={`${progress * 100} 100`} />
          </svg>
        )}
        <Planet kind={planet} size={240} speed={busy ? 9 : 1} active={active} />
      </div>
      {!busy && <p className="tagline">{lines[0]}<br />{lines[1]}</p>}
      {busy && <p className="tagline live">{status}</p>}
      {busy && sub && <div className="hero-sub">{sub}</div>}
      {!busy && cta && <button className="pill" onClick={onCta}>{cta}</button>}
      {children}
    </div>
  );
}

// Temizlik sonrası yağmur / toz parçacıkları
export function Particles({ kind = 'rain', count = 60 }) {
  const items = useRef(Array.from({ length: count }, () => ({
    left: Math.random() * 100, delay: Math.random() * 1.6, dur: 0.8 + Math.random() * 1.2, size: 1 + Math.random() * 2,
  }))).current;
  return (
    <div className={`particles ${kind}`}>
      {items.map((p, i) => (
        <i key={i} style={{ left: `${p.left}%`, animationDelay: `${p.delay}s`, animationDuration: `${p.dur}s`, '--s': `${p.size}px` }} />
      ))}
    </div>
  );
}

export function Empty({ children }) {
  return <div className="empty">{children}</div>;
}

export function Toast({ msg, onDone }) {
  useEffect(() => { if (!msg) return; const t = setTimeout(onDone, 3800); return () => clearTimeout(t); }, [msg]);
  if (!msg) return null;
  return <div className={`toast ${msg.type || ''}`}>{msg.text}</div>;
}

export function Confirm({ open, title, body, ok = 'Onayla', danger, onOk, onCancel }) {
  if (!open) return null;
  return (
    <div className="modal-back" onClick={onCancel}>
      <div className="modal" onClick={(e) => e.stopPropagation()}>
        <h3>{title}</h3>
        <div className="modal-body">{body}</div>
        <div className="modal-actions">
          <button className="ghost" onClick={onCancel}>Vazgeç</button>
          <button className={danger ? 'pill danger' : 'pill'} onClick={onOk}>{ok}</button>
        </div>
      </div>
    </div>
  );
}

export function useConfirm() {
  const [state, setState] = useState(null);
  const ask = (opts) => new Promise((resolve) => setState({ ...opts, resolve }));
  const el = (
    <Confirm open={!!state} {...(state || {})}
      onOk={() => { state.resolve(true); setState(null); }}
      onCancel={() => { state.resolve(false); setState(null); }} />
  );
  return [ask, el];
}

import { useEffect, useLayoutEffect, useRef, useState } from 'react';
import Clean from './pages/Clean.jsx';
import Uninstall from './pages/Uninstall.jsx';
import Optimize from './pages/Optimize.jsx';
import Analyze from './pages/Analyze.jsx';
import Status from './pages/Status.jsx';

export const THEMES = {
  clean: { name: 'Temizle', planet: 'earth', top: '#0b1226', accent: '#7cc4ff', C: Clean },
  uninstall: { name: 'Kaldır', planet: 'mars', top: '#1d0a0a', accent: '#ff8a6b', C: Uninstall },
  optimize: { name: 'Optimize', planet: 'mercury', top: '#1a1407', accent: '#ffcf6e', C: Optimize },
  analyze: { name: 'Analiz', planet: 'jupiter', top: '#061b1a', accent: '#6ee0c2', C: Analyze },
  status: { name: 'Durum', planet: 'sun', top: '#15100c', accent: '#ffa24c', C: Status },
};
const IDS = Object.keys(THEMES);

export default function App() {
  const [active, setActive] = useState('clean');
  const tabs = useRef({});
  const [ind, setInd] = useState({ left: 0, width: 0 });

  useLayoutEffect(() => {
    const el = tabs.current[active];
    if (el) setInd({ left: el.offsetLeft, width: el.offsetWidth });
  }, [active]);

  useEffect(() => {
    window.api.theme(THEMES[active].top);
    const onKey = (e) => {
      if (e.ctrlKey && e.key >= '1' && e.key <= '5') setActive(IDS[Number(e.key) - 1]);
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [active]);

  return (
    <div className="app" style={{ '--accent': THEMES[active].accent }}>
      {IDS.map((id) => <div key={id} className={`bg bg-${id} ${active === id ? 'on' : ''}`} />)}
      <div className="stars" />

      <header className="titlebar">
        <div className="logo"><span className="logo-dot" />WinKeeper</div>
        <nav className="tabs">
          <span className="tab-ind" style={{ transform: `translateX(${ind.left}px)`, width: ind.width }} />
          {IDS.map((id, i) => (
            <button key={id} ref={(el) => (tabs.current[id] = el)} className={`tab ${active === id ? 'on' : ''}`}
              onClick={() => setActive(id)} title={`Ctrl+${i + 1}`}>
              <span className={`tab-planet p-${THEMES[id].planet}`} />
              {THEMES[id].name}
            </button>
          ))}
        </nav>
      </header>

      <main className="stage">
        {IDS.map((id) => {
          const { C } = THEMES[id];
          return (
            <section key={id} className={`page ${active === id ? 'on' : ''}`}>
              <C active={active === id} theme={THEMES[id]} />
            </section>
          );
        })}
      </main>
    </div>
  );
}

import { useEffect, useRef } from 'react';

// Küre üzerinde örneklenen 3B değer gürültüsü: dokuda dikiş izi oluşmaz.
function hash(x, y, z, seed) {
  let h = (x * 374761393 + y * 668265263 + z * 2147483647 + seed * 144665) | 0;
  h = Math.imul(h ^ (h >>> 13), 1274126177);
  return ((h ^ (h >>> 16)) >>> 0) / 4294967295;
}
function noise3(x, y, z, seed) {
  const xi = Math.floor(x), yi = Math.floor(y), zi = Math.floor(z);
  const xf = x - xi, yf = y - yi, zf = z - zi;
  const u = xf * xf * (3 - 2 * xf), v = yf * yf * (3 - 2 * yf), w = zf * zf * (3 - 2 * zf);
  const L = (a, b, t) => a + (b - a) * t;
  const c = (dx, dy, dz) => hash(xi + dx, yi + dy, zi + dz, seed);
  return L(
    L(L(c(0, 0, 0), c(1, 0, 0), u), L(c(0, 1, 0), c(1, 1, 0), u), v),
    L(L(c(0, 0, 1), c(1, 0, 1), u), L(c(0, 1, 1), c(1, 1, 1), u), v), w);
}
function fbm(x, y, z, seed, oct = 5) {
  let a = 0.5, f = 1, s = 0, n = 0;
  for (let i = 0; i < oct; i++) { s += a * noise3(x * f, y * f, z * f, seed + i * 17); n += a; a *= 0.5; f *= 2; }
  return s / n;
}

const hex = (h) => [parseInt(h.slice(1, 3), 16), parseInt(h.slice(3, 5), 16), parseInt(h.slice(5, 7), 16)];
function ramp(stops, t) {
  t = Math.max(0, Math.min(1, t));
  for (let i = 1; i < stops.length; i++) {
    if (t <= stops[i][0]) {
      const [t0, c0] = stops[i - 1], [t1, c1] = stops[i];
      const k = (t - t0) / (t1 - t0 || 1);
      return [c0[0] + (c1[0] - c0[0]) * k, c0[1] + (c1[1] - c0[1]) * k, c0[2] + (c1[2] - c0[2]) * k];
    }
  }
  return stops[stops.length - 1][1];
}
const S = (arr) => arr.map(([t, h]) => [t, hex(h)]);

export const PLANETS = {
  earth: {
    seed: 3, scale: 2.2, clouds: true, glow: '#6fb6ff',
    color: (n, lat) => {
      if (Math.abs(lat) > 1.25 + (n - 0.5) * 0.4) return [236, 242, 248];
      if (n < 0.5) return ramp(S([[0, '#0b2a6b'], [0.42, '#1a56a8'], [0.5, '#2f86c9']]), n);
      if (n < 0.52) return hex('#d9c78f');
      return ramp(S([[0.52, '#3f8f3a'], [0.62, '#2d6b2a'], [0.7, '#7a6a43'], [0.8, '#bfb6a6']]), n);
    },
  },
  mars: {
    seed: 11, scale: 2.6, glow: '#ff7a52',
    color: (n, lat) => {
      if (Math.abs(lat) > 1.35) return [240, 220, 210];
      return ramp(S([[0, '#3d0f08'], [0.35, '#7a2512'], [0.5, '#b4441f'], [0.62, '#d9733c'], [0.8, '#f0a36b']]), n);
    },
  },
  mercury: {
    seed: 23, scale: 3.4, glow: '#ffd27a',
    color: (n) => ramp(S([[0, '#3a2c14'], [0.38, '#7d6232'], [0.52, '#c39a52'], [0.66, '#e9c987'], [0.85, '#fff1c9']]), Math.abs(n - 0.5) * 2.2),
  },
  jupiter: {
    seed: 7, scale: 1.6, bands: true, glow: '#7fe0c8',
    color: (n, lat) => {
      const b = 0.5 + 0.5 * Math.sin(lat * 9 + (n - 0.5) * 7);
      return ramp(S([[0, '#0c3533'], [0.3, '#17584f'], [0.55, '#2f8f7c'], [0.78, '#79c9ad'], [1, '#b9e6d2']]), b * 0.85 + (n - 0.5) * 0.3);
    },
  },
  sun: {
    seed: 41, scale: 4, emissive: true, glow: '#ff9a3c',
    color: (n) => ramp(S([[0, '#b8360b'], [0.4, '#ec6a17'], [0.6, '#ffa531'], [0.8, '#ffd76a'], [1, '#fff4c4']]), n),
  },
};

const TW = 512, TH = 256;
const textures = {};
function texture(kind) {
  if (textures[kind]) return textures[kind];
  const p = PLANETS[kind];
  const data = new Uint8ClampedArray(TW * TH * 3);
  const cloud = p.clouds ? new Uint8ClampedArray(TW * TH) : null;
  for (let y = 0; y < TH; y++) {
    const lat = (0.5 - y / TH) * Math.PI;
    const cl = Math.cos(lat), sl = Math.sin(lat);
    for (let x = 0; x < TW; x++) {
      const lon = (x / TW) * Math.PI * 2;
      const px = cl * Math.cos(lon), py = sl, pz = cl * Math.sin(lon);
      const sx = p.bands ? 0.6 : 1;
      const n = fbm(px * p.scale * sx, py * p.scale, pz * p.scale * sx, p.seed);
      const c = p.color(n, lat);
      const i = (y * TW + x) * 3;
      data[i] = c[0]; data[i + 1] = c[1]; data[i + 2] = c[2];
      if (cloud) {
        const cn = fbm(px * 3 + 9, py * 5, pz * 3, p.seed + 99, 4);
        cloud[y * TW + x] = Math.max(0, (cn - 0.52) * 4) * 255;
      }
    }
  }
  return (textures[kind] = { data, cloud });
}

/**
 * Dönen gezegen. speed: tur/sn çarpanı. active=false iken çizim durur.
 */
export default function Planet({ kind = 'earth', size = 220, speed = 1, active = true, className = '' }) {
  const ref = useRef(null);
  const speedRef = useRef(speed);
  speedRef.current = speed;

  useEffect(() => {
    if (!active) return;
    const cv = ref.current;
    const dpr = Math.min(window.devicePixelRatio || 1, 1.5);
    const R = Math.round(size * dpr);
    cv.width = R; cv.height = R;
    const ctx = cv.getContext('2d');
    const img = ctx.createImageData(R, R);
    const p = PLANETS[kind];
    const tex = texture(kind);

    // Her piksel için enlem/boylam ve ışığı bir kez hesapla
    const idx = [], row = [], lonF = [], light = [], alpha = [];
    const tilt = 0.32, ct = Math.cos(tilt), st = Math.sin(tilt);
    const L = [-0.55, 0.45, 0.7];
    const r = R / 2;
    for (let y = 0; y < R; y++) {
      for (let x = 0; x < R; x++) {
        const nx = (x + 0.5 - r) / r, ny = (r - y - 0.5) / r;
        const d2 = nx * nx + ny * ny;
        if (d2 > 1) continue;
        const nz = Math.sqrt(1 - d2);
        const ty = ny * ct - nz * st, tz = ny * st + nz * ct;
        const lat = Math.asin(Math.max(-1, Math.min(1, ty)));
        const lon = Math.atan2(tz, nx);
        idx.push((y * R + x) * 4);
        row.push(Math.min(TH - 1, Math.floor((0.5 - lat / Math.PI) * TH)));
        lonF.push(lon / (Math.PI * 2) + 1);
        const lam = Math.max(0, nx * L[0] + ny * L[1] + nz * L[2]);
        const edge = Math.min(1, (1 - Math.sqrt(d2)) * R * 0.5); // kenar yumuşatma
        light.push(p.emissive ? 0.55 + 0.45 * nz : 0.08 + 0.92 * Math.pow(lam, 0.85));
        alpha.push(255 * edge);
      }
    }
    const N = idx.length;
    const out = img.data;
    let rot = 0, cloudRot = 0, last = performance.now(), raf;
    const draw = (now) => {
      const dt = Math.min(0.05, (now - last) / 1000);
      last = now;
      rot += dt * 0.025 * speedRef.current;
      cloudRot += dt * 0.035 * speedRef.current;
      const td = tex.data, cl = tex.cloud;
      const flick = p.emissive ? 0.06 * Math.sin(now / 300) : 0;
      for (let i = 0; i < N; i++) {
        const tx = Math.floor((lonF[i] + rot) * TW) % TW;
        const ti = (row[i] * TW + tx) * 3;
        let cr = td[ti], cg = td[ti + 1], cb = td[ti + 2];
        if (cl) {
          const cx = Math.floor((lonF[i] + cloudRot) * TW) % TW;
          const a = cl[row[i] * TW + cx] / 255;
          cr += (245 - cr) * a; cg += (248 - cg) * a; cb += (252 - cb) * a;
        }
        const l = light[i] * (1 + flick);
        const o = idx[i];
        out[o] = cr * l; out[o + 1] = cg * l; out[o + 2] = cb * l;
      }
      ctx.putImageData(img, 0, 0);
      raf = requestAnimationFrame(draw);
    };
    for (let i = 0; i < N; i++) out[idx[i] + 3] = alpha[i];
    raf = requestAnimationFrame(draw);
    return () => cancelAnimationFrame(raf);
  }, [kind, size, active]);

  const glow = PLANETS[kind].glow;
  return (
    <div className={`planet ${className}`} style={{ width: size, height: size, '--glow': glow }}>
      <canvas ref={ref} style={{ width: size, height: size }} />
    </div>
  );
}

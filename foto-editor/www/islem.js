// FotoRea görüntü işleme motoru: tamamen cihaz üzerinde, internetsiz çalışır.
// Tüm hesaplar 0..1 aralığında yapılır, sonuç tuvale yazılır.
(function () {
  "use strict";

  const sinirla = (v) => (v < 0 ? 0 : v > 1 ? 1 : v);
  const parlaklikHesapla = (r, g, b) => 0.2126 * r + 0.7152 * g + 0.0722 * b;

  // ---------- Hazır filtreler ----------
  function doygunlukUygula(r, g, b, s) {
    const l = parlaklikHesapla(r, g, b);
    return [l + (r - l) * s, l + (g - l) * s, l + (b - l) * s];
  }
  function kontrastUygula(v, k) { return (v - 0.5) * k + 0.5; }

  const FILTRELER = [
    { ad: "Orijinal", f: null },
    { ad: "Canlı", f: (r, g, b) => doygunlukUygula(kontrastUygula(r, 1.1), kontrastUygula(g, 1.1), kontrastUygula(b, 1.1), 1.35) },
    { ad: "Sinema", f: (r, g, b) => {
        const l = parlaklikHesapla(r, g, b), gol = (1 - l) * (1 - l), vur = l * l;
        r = r - 0.08 * gol + 0.10 * vur; g = g + 0.02 * gol + 0.03 * vur; b = b + 0.10 * gol - 0.10 * vur;
        return doygunlukUygula(kontrastUygula(r, 1.15), kontrastUygula(g, 1.15), kontrastUygula(b, 1.15), 0.9);
      } },
    { ad: "Altın saat", f: (r, g, b) => doygunlukUygula(r * 1.08 + 0.03, g * 1.02 + 0.01, b * 0.86, 1.12) },
    { ad: "Soğuk", f: (r, g, b) => [r * 0.92, g * 0.99 + 0.01, b * 1.08 + 0.03] },
    { ad: "Pastel", f: (r, g, b) => { [r, g, b] = doygunlukUygula(r, g, b, 0.8); return [0.12 + r * 0.85, 0.1 + g * 0.87, 0.14 + b * 0.84]; } },
    { ad: "Vintage", f: (r, g, b) => { [r, g, b] = doygunlukUygula(r, g, b, 0.75); return [0.08 + r * 0.9 + 0.04, 0.06 + g * 0.88 + 0.02, 0.1 + b * 0.72]; } },
    { ad: "Retro", f: (r, g, b) => [r * r * (3 - 2 * r), Math.pow(g, 0.95), 0.12 + b * 0.76] },
    { ad: "Dramatik", f: (r, g, b) => doygunlukUygula(kontrastUygula(r, 1.35), kontrastUygula(g, 1.35), kontrastUygula(b, 1.35), 0.8) },
    { ad: "Siyah-beyaz", f: (r, g, b) => { const l = kontrastUygula(parlaklikHesapla(r, g, b), 1.12); return [l, l, l]; } },
    { ad: "Noir", f: (r, g, b) => { let l = kontrastUygula(parlaklikHesapla(r, g, b), 1.5); l = l * l * (3 - 2 * sinirla(l)); return [l, l, l]; } },
    { ad: "Sepya", f: (r, g, b) => [r * 0.393 + g * 0.769 + b * 0.189, r * 0.349 + g * 0.686 + b * 0.168, r * 0.272 + g * 0.534 + b * 0.131] },
  ];

  // ---------- Kutu bulanıklığı (RGB, iki geçiş ≈ Gauss) ----------
  function bulaniklastir(kaynak, w, h, r) {
    r = Math.max(1, Math.round(r));
    let a = new Float32Array(w * h * 3), bt = new Float32Array(w * h * 3);
    for (let i = 0, j = 0; i < kaynak.length; i += 4, j += 3) { a[j] = kaynak[i]; a[j + 1] = kaynak[i + 1]; a[j + 2] = kaynak[i + 2]; }
    for (let gecis = 0; gecis < 2; gecis++) {
      yatay(a, bt, w, h, r); dikey(bt, a, w, h, r);
    }
    return a; // 0..255 değerleri, 3 kanal
  }
  function yatay(src, dst, w, h, r) {
    const n = 2 * r + 1;
    for (let y = 0; y < h; y++) {
      const sat = y * w * 3;
      for (let c = 0; c < 3; c++) {
        let top = 0;
        for (let k = -r; k <= r; k++) top += src[sat + Math.min(w - 1, Math.max(0, k)) * 3 + c];
        for (let x = 0; x < w; x++) {
          dst[sat + x * 3 + c] = top / n;
          const cik = Math.max(0, x - r), gir = Math.min(w - 1, x + r + 1);
          top += src[sat + gir * 3 + c] - src[sat + cik * 3 + c];
        }
      }
    }
  }
  function dikey(src, dst, w, h, r) {
    const n = 2 * r + 1, adim = w * 3;
    for (let x = 0; x < w; x++) {
      for (let c = 0; c < 3; c++) {
        const o = x * 3 + c;
        let top = 0;
        for (let k = -r; k <= r; k++) top += src[Math.min(h - 1, Math.max(0, k)) * adim + o];
        for (let y = 0; y < h; y++) {
          dst[y * adim + o] = top / n;
          const cik = Math.max(0, y - r), gir = Math.min(h - 1, y + r + 1);
          top += src[gir * adim + o] - src[cik * adim + o];
        }
      }
    }
  }

  // ---------- Ten maskesi (YCbCr kuralı + yumuşatma) ----------
  function tenMaskesi(d, w, h) {
    const m = new Uint8ClampedArray(w * h * 4);
    for (let i = 0; i < d.length; i += 4) {
      const R = d[i], G = d[i + 1], B = d[i + 2];
      const Y = 0.299 * R + 0.587 * G + 0.114 * B;
      const Cb = 128 - 0.168736 * R - 0.331264 * G + 0.5 * B;
      const Cr = 128 + 0.5 * R - 0.418688 * G - 0.081312 * B;
      // Merkezden uzaklığa göre yumuşak üyelik
      const db = Math.abs(Cb - 104) / 24, dr = Math.abs(Cr - 152) / 22;
      let v = 1 - Math.max(db, dr);
      if (Y < 45 || Y > 250) v = 0;
      v = sinirla(v * 2.2);
      m[i] = m[i + 1] = m[i + 2] = v * 255;
    }
    const yum = bulaniklastir(m, w, h, Math.max(2, Math.max(w, h) / 220));
    const sonuc = new Float32Array(w * h);
    for (let p = 0; p < w * h; p++) sonuc[p] = yum[p * 3] / 255;
    return sonuc;
  }

  // ---------- Geometri: döndür, çevir, kırp, ölçekle ----------
  function geometri(img, d, maxKenar) {
    const W = img.naturalWidth || img.width, H = img.naturalHeight || img.height;
    const tek = d.donus % 2 === 1;
    const RW = tek ? H : W, RH = tek ? W : H;
    let cw = RW, ch = RH;
    if (d.oran) {
      if (RW / RH > d.oran) cw = RH * d.oran; else ch = RW / d.oran;
    }
    const s = Math.min(1, maxKenar / Math.max(cw, ch));
    const gecici = document.createElement("canvas");
    gecici.width = Math.max(1, Math.round(RW * s)); gecici.height = Math.max(1, Math.round(RH * s));
    const gc = gecici.getContext("2d");
    gc.translate(gecici.width / 2, gecici.height / 2);
    gc.rotate(d.donus * Math.PI / 2);
    gc.scale(d.cevir ? -1 : 1, 1);
    gc.drawImage(img, -W * s / 2, -H * s / 2, W * s, H * s);
    const cikti = document.createElement("canvas");
    cikti.width = Math.max(1, Math.round(cw * s)); cikti.height = Math.max(1, Math.round(ch * s));
    cikti.getContext("2d").drawImage(gecici, ((RW - cw) / 2) * s, ((RH - ch) / 2) * s, cw * s, ch * s, 0, 0, cikti.width, cikti.height);
    return cikti;
  }

  // Bulanıklık ve maske gibi pahalı ara sonuçları aynı geometri için saklar.
  function onbellekAl(ob, anahtar, hesapla) {
    if (!(anahtar in ob)) ob[anahtar] = hesapla();
    return ob[anahtar];
  }

  // ---------- Ana işleme ----------
  // img: kaynak görüntü, d: düzenleme durumu, maxKenar: çıktı uzun kenarı, ob: önbellek nesnesi
  function isle(img, d, maxKenar, ob) {
    const geoAnahtar = [d.donus, d.cevir, d.oran, maxKenar].join("|");
    if (ob.geo !== geoAnahtar) { for (const k in ob) delete ob[k]; ob.geo = geoAnahtar; }
    const tuval = onbellekAl(ob, "tuval", () => geometri(img, d, maxKenar));
    const w = tuval.width, h = tuval.height;
    const ham = onbellekAl(ob, "ham", () => tuval.getContext("2d").getImageData(0, 0, w, h).data);
    const olcek = Math.max(w, h) / 1000;

    const p = d.p;
    const yum = p.yumusatma / 100, isilti = p.isilti / 100, aydinlat = p.aydinlat / 100;
    const netlik = p.netlik / 100, parlama = p.parlama / 100;
    const rotusVar = yum || isilti || aydinlat;
    const maske = rotusVar ? onbellekAl(ob, "maske", () => tenMaskesi(ham, w, h)) : null;
    const bulCilt = yum ? onbellekAl(ob, "bulCilt", () => bulaniklastir(ham, w, h, 6 * olcek + 1)) : null;
    const bulKucuk = netlik ? onbellekAl(ob, "bulKucuk", () => bulaniklastir(ham, w, h, 2 * olcek + 1)) : null;
    const bulBuyuk = parlama ? onbellekAl(ob, "bulBuyuk", () => bulaniklastir(ham, w, h, 18 * olcek + 2)) : null;

    // Parlaklık için gama tablosu
    const gama = Math.pow(2, -p.parlaklik / 100 * 0.9);
    const kontrast = p.kontrast >= 0 ? 1 + p.kontrast / 100 : 1 + p.kontrast / 100 * 0.7;
    const doygunluk = 1 + (p.doygunluk >= 0 ? p.doygunluk / 100 : p.doygunluk / 100);
    const sicak = p.sicaklik / 100 * 0.1, ton = p.ton / 100 * 0.08;
    const golge = p.golge / 100 * 0.35, vurgu = p.vurgu / 100 * 0.35;
    const soluk = p.soluk / 100, vinyet = p.vinyet / 100, gren = p.gren / 100;
    const filtre = FILTRELER[d.filtre].f, fy = d.filtreYogunluk / 100;

    const cikti = document.createElement("canvas");
    cikti.width = w; cikti.height = h;
    const cx = cikti.getContext("2d");
    const veri = cx.createImageData(w, h), o = veri.data;
    const ortaX = w / 2, ortaY = h / 2, yaricap = Math.sqrt(ortaX * ortaX + ortaY * ortaY);
    let tohum = 1234567;

    for (let y = 0, i = 0, j = 0, px = 0; y < h; y++) {
      for (let x = 0; x < w; x++, i += 4, j += 3, px++) {
        let r = ham[i], g = ham[i + 1], b = ham[i + 2];

        if (rotusVar) {
          const m = maske[px];
          if (yum && m > 0) {
            const k = m * yum * 0.85;
            r += (bulCilt[j] - r) * k; g += (bulCilt[j + 1] - g) * k; b += (bulCilt[j + 2] - b) * k;
          }
          r /= 255; g /= 255; b /= 255;
          if (isilti && m > 0) { const k = m * isilti; r += 0.07 * k; g += 0.045 * k; b += 0.03 * k; }
          if (aydinlat && m > 0) { const e = 1 - m * aydinlat * 0.3; r = Math.pow(sinirla(r), e); g = Math.pow(sinirla(g), e); b = Math.pow(sinirla(b), e); }
        } else { r /= 255; g /= 255; b /= 255; }

        if (netlik) {
          const k = netlik * 1.6;
          r += (r - bulKucuk[j] / 255) * k; g += (g - bulKucuk[j + 1] / 255) * k; b += (b - bulKucuk[j + 2] / 255) * k;
        }
        if (gama !== 1) { r = Math.pow(sinirla(r), gama); g = Math.pow(sinirla(g), gama); b = Math.pow(sinirla(b), gama); }
        if (kontrast !== 1) { r = kontrastUygula(r, kontrast); g = kontrastUygula(g, kontrast); b = kontrastUygula(b, kontrast); }
        if (golge || vurgu) {
          const l = sinirla(parlaklikHesapla(r, g, b));
          const ek = golge * (1 - l) * (1 - l) + vurgu * l * l;
          r += ek; g += ek; b += ek;
        }
        if (sicak || ton) { r += sicak + ton * 0.5; g -= ton; b += -sicak + ton * 0.5; }
        if (doygunluk !== 1) [r, g, b] = doygunlukUygula(r, g, b, doygunluk);
        if (filtre && fy) {
          const [fr, fg, fb] = filtre(sinirla(r), sinirla(g), sinirla(b));
          r += (fr - r) * fy; g += (fg - g) * fy; b += (fb - b) * fy;
        }
        if (parlama) {
          const k = parlama * 0.6;
          r = 1 - (1 - sinirla(r)) * (1 - bulBuyuk[j] / 255 * k);
          g = 1 - (1 - sinirla(g)) * (1 - bulBuyuk[j + 1] / 255 * k);
          b = 1 - (1 - sinirla(b)) * (1 - bulBuyuk[j + 2] / 255 * k);
        }
        if (soluk) { r = 0.1 * soluk + r * (1 - 0.2 * soluk); g = 0.1 * soluk + g * (1 - 0.2 * soluk); b = 0.11 * soluk + b * (1 - 0.2 * soluk); }
        if (vinyet) {
          const dx = x - ortaX, dy = y - ortaY;
          let t = (Math.sqrt(dx * dx + dy * dy) / yaricap - 0.35) / 0.65;
          t = t < 0 ? 0 : t > 1 ? 1 : t * t * (3 - 2 * t);
          const k = 1 - vinyet * 0.8 * t;
          r *= k; g *= k; b *= k;
        }
        if (gren) {
          tohum = (tohum * 1103515245 + 12345) & 0x7fffffff;
          const n = (tohum / 0x7fffffff - 0.5) * gren * 0.2;
          r += n; g += n; b += n;
        }
        o[i] = r * 255; o[i + 1] = g * 255; o[i + 2] = b * 255; o[i + 3] = 255;
      }
    }
    cx.putImageData(veri, 0, 0);
    return cikti;
  }

  window.FotoIslem = { FILTRELER, isle, geometri };
})();

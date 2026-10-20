// FotoRea arayüzü: fotoğraf seçme, düzenleme durumu, önizleme, geri alma ve kaydetme.
(function () {
  "use strict";
  const { FILTRELER, isle, geometri } = window.FotoIslem;
  const $ = (s) => document.querySelector(s);
  const $$ = (s) => Array.from(document.querySelectorAll(s));

  const ORANLAR = [
    { ad: "Orijinal", oran: null }, { ad: "Kare", oran: 1 }, { ad: "4:5", oran: 4 / 5 }, { ad: "3:4", oran: 3 / 4 },
    { ad: "9:16", oran: 9 / 16 }, { ad: "16:9", oran: 16 / 9 }, { ad: "3:2", oran: 3 / 2 },
  ];
  const ONIZLEME_KENAR = 1280, KAYIT_KENAR = 4000;

  const bosDurum = () => ({
    donus: 0, cevir: false, oran: null, filtre: 0, filtreYogunluk: 100,
    p: { parlaklik: 0, kontrast: 0, doygunluk: 0, sicaklik: 0, ton: 0, golge: 0, vurgu: 0, netlik: 0,
         yumusatma: 0, isilti: 0, aydinlat: 0, vinyet: 0, gren: 0, soluk: 0, parlama: 0 },
  });

  let gorsel = null, durum = bosDurum(), gecmis = [], onbellek = {}, bekleyen = false, karsilastir = false;
  const tuval = $("#tuval");

  // ---------- Bildirim ----------
  let bildirimZamani;
  function bildir(metin) {
    const b = $("#bildirim");
    b.textContent = metin; b.hidden = false;
    clearTimeout(bildirimZamani);
    bildirimZamani = setTimeout(() => (b.hidden = true), 2600);
  }

  // ---------- Çizim ----------
  function ciz() {
    if (!gorsel) return;
    const sonuc = karsilastir ? onbellek.tuval : isle(gorsel, durum, ONIZLEME_KENAR, onbellek);
    if (!sonuc) return;
    tuval.width = sonuc.width; tuval.height = sonuc.height;
    tuval.getContext("2d").drawImage(sonuc, 0, 0);
  }
  function cizimIste() {
    if (bekleyen) return;
    bekleyen = true;
    requestAnimationFrame(() => { bekleyen = false; ciz(); });
  }

  // ---------- Geçmiş ----------
  function kaydetGecmis() {
    const anlik = JSON.stringify(durum);
    if (gecmis[gecmis.length - 1] !== anlik) gecmis.push(anlik);
    if (gecmis.length > 60) gecmis.shift();
    dugmeleriGuncelle();
  }
  function geriAl() {
    if (gecmis.length < 2) return;
    gecmis.pop();
    durum = JSON.parse(gecmis[gecmis.length - 1]);
    arayuzuDurumaGetir(); cizimIste(); dugmeleriGuncelle();
  }
  function dugmeleriGuncelle() {
    $("#btnGeri").disabled = gecmis.length < 2;
    $("#btnSifirla").disabled = !gorsel;
    $("#btnKaydet").disabled = !gorsel;
  }

  // ---------- Arayüzü durumla eşle ----------
  function ciktiYaz(input) { input.parentElement.querySelector("output").textContent = input.value; }
  function arayuzuDurumaGetir() {
    $$("input[data-p]").forEach((el) => { el.value = durum.p[el.dataset.p]; ciktiYaz(el); });
    const fy = $("#filtreYogunluk"); fy.value = durum.filtreYogunluk; ciktiYaz(fy);
    $$("#filtreler .kucuk").forEach((el, i) => el.classList.toggle("secili", i === durum.filtre));
    $$("#oranlar .kucuk").forEach((el, i) => el.classList.toggle("secili", ORANLAR[i].oran === durum.oran));
  }

  // ---------- Ekranlar ----------
  function ekranGoster(id) { $$(".ekran").forEach((e) => (e.hidden = e.id !== id)); }

  // ---------- Düzenleyiciyi aç ----------
  let oncekiEkran = "ekranAna";
  function duzenleyiciyiAc(img, geriEkran) {
    gorsel = img; durum = bosDurum(); gecmis = []; onbellek = {};
    oncekiEkran = geriEkran || "ekranAna";
    ekranGoster("ekranDuzenle");
    const ipucu = $("#ipucu"); ipucu.hidden = false; ipucu.style.opacity = 1;
    setTimeout(() => (ipucu.style.opacity = 0), 3000);
    kucukResimleriYap(); arayuzuDurumaGetir(); kaydetGecmis(); ciz();
  }
  $("#btnDuzenGeri").onclick = () => ekranGoster(oncekiEkran);

  // ---------- Filtre ve oran şeritleri ----------
  function kucukResimleriYap() {
    const kutu = $("#filtreler"); kutu.innerHTML = "";
    const kucukKaynak = geometri(gorsel, { donus: 0, cevir: false, oran: 1 }, 144);
    FILTRELER.forEach((f, i) => {
      const d = bosDurum(); d.filtre = i;
      const c = isle(kucukKaynak, d, 144, {});
      const btn = document.createElement("button");
      btn.className = "kucuk"; btn.appendChild(c);
      btn.appendChild(document.createTextNode(f.ad));
      btn.onclick = () => { durum.filtre = i; arayuzuDurumaGetir(); cizimIste(); kaydetGecmis(); };
      kutu.appendChild(btn);
    });
  }
  (function oranlariYap() {
    const kutu = $("#oranlar");
    ORANLAR.forEach((o) => {
      const btn = document.createElement("button");
      btn.className = "kucuk";
      const k = document.createElement("span"); k.className = "oran-kutu";
      const i = document.createElement("i");
      const a = o.oran || 0.8;
      i.style.width = (a >= 1 ? 40 : 40 * a) + "px"; i.style.height = (a >= 1 ? 40 / a : 40) + "px";
      k.appendChild(i); btn.appendChild(k); btn.appendChild(document.createTextNode(o.ad));
      btn.onclick = () => { durum.oran = o.oran; arayuzuDurumaGetir(); cizimIste(); kaydetGecmis(); };
      kutu.appendChild(btn);
    });
  })();

  // ---------- Kaydırıcılar ----------
  $$("input[data-p]").forEach((el) => {
    el.addEventListener("input", () => { durum.p[el.dataset.p] = +el.value; ciktiYaz(el); cizimIste(); });
    el.addEventListener("change", kaydetGecmis);
    el.addEventListener("dblclick", () => { el.value = 0; el.dispatchEvent(new Event("input")); kaydetGecmis(); });
  });
  const fy = $("#filtreYogunluk");
  fy.addEventListener("input", () => { durum.filtreYogunluk = +fy.value; ciktiYaz(fy); cizimIste(); });
  fy.addEventListener("change", kaydetGecmis);

  $("#btnDondur").onclick = () => { durum.donus = (durum.donus + 1) % 4; cizimIste(); kaydetGecmis(); };
  $("#btnCevir").onclick = () => { durum.cevir = !durum.cevir; cizimIste(); kaydetGecmis(); };
  $("#btnGeri").onclick = geriAl;
  $("#btnSifirla").onclick = () => { durum = bosDurum(); arayuzuDurumaGetir(); cizimIste(); kaydetGecmis(); };

  // ---------- Sekmeler ----------
  $$("#sekmeler button").forEach((btn) => {
    btn.onclick = () => {
      $$("#sekmeler button").forEach((b) => b.classList.toggle("secili", b === btn));
      $$(".sekme-icerik").forEach((s) => (s.hidden = s.dataset.sekme !== btn.dataset.sekme));
    };
  });

  // ---------- Önce / sonra ----------
  const basla = () => { if (gorsel) { karsilastir = true; ciz(); } };
  const bitir = () => { if (karsilastir) { karsilastir = false; ciz(); } };
  tuval.addEventListener("pointerdown", basla);
  ["pointerup", "pointercancel", "pointerleave"].forEach((e) => tuval.addEventListener(e, bitir));

  // ---------- Kaydetme ----------
  $("#btnKaydet").onclick = async () => {
    if (!gorsel) return;
    const btn = $("#btnKaydet"); btn.disabled = true; btn.textContent = "Hazırlanıyor…";
    await new Promise((r) => setTimeout(r, 30));
    try {
      const sonuc = isle(gorsel, durum, KAYIT_KENAR, {});
      const ad = "FotoRea_" + new Date().toISOString().replace(/[-:T.Z]/g, "").slice(0, 14) + ".jpg";
      await paylas(sonuc, ad);
    } catch (e) {
      if (!(e && e.name === "AbortError")) bildir("Kaydedilemedi: " + (e && e.message ? e.message : e));
    } finally {
      btn.disabled = false; btn.textContent = "Kaydet";
    }
  };

  async function paylas(c, ad) {
    const cap = window.Capacitor;
    if (cap && cap.isNativePlatform && cap.isNativePlatform()) {
      // Telefonda: dosyayı önbelleğe yaz, paylaşım ekranından "Görüntüyü Kaydet" / Galeri seçilir.
      const { Filesystem, Share } = cap.Plugins;
      const veri = c.toDataURL("image/jpeg", 0.92).split(",")[1];
      const yazilan = await Filesystem.writeFile({ path: ad, data: veri, directory: "CACHE" });
      await Share.share({ title: "FotoRea", files: [yazilan.uri], dialogTitle: "Fotoğrafı kaydet veya paylaş" });
      return;
    }
    const blob = await new Promise((r) => c.toBlob(r, "image/jpeg", 0.92));
    const dosya = new File([blob], ad, { type: "image/jpeg" });
    if (navigator.canShare && navigator.canShare({ files: [dosya] })) {
      await navigator.share({ files: [dosya], title: "FotoRea" });
      return;
    }
    const a = document.createElement("a");
    a.href = URL.createObjectURL(blob); a.download = ad;
    document.body.appendChild(a); a.click(); a.remove();
    bildir("Fotoğraf indirildi.");
  }

  dugmeleriGuncelle();
  window.FotoUygulama = { ekranGoster, duzenleyiciyiAc, paylas, bildir };
})();

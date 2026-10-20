// Ana ekran ve yapay zekâ akışı: fotoğraf seç, "yeniden çek" / stil / akıllı düzenleme uygula, sonuçları göster.
(function () {
  "use strict";
  const $ = (s) => document.querySelector(s);
  const { ekranGoster, duzenleyiciyiAc, paylas, bildir } = window.FotoUygulama;

  // Modelin kişiyi değiştirmemesi için her isteme eklenen ortak cümle
  const KORU = " Keep the person's identity, facial features, hair, skin tone and body exactly the same. Photorealistic.";

  const KATEGORILER = [
    { ad: "Yeniden çek", istemler: [
      { ad: "Gülümse", aciklama: "Doğal, içten bir gülüş", prompt: "Make the person smile naturally with a genuine, relaxed smile. Keep clothing, pose and background unchanged." + KORU },
      { ad: "Gözleri aç", aciklama: "Kapalı gözleri düzelt", prompt: "Open the person's eyes naturally, looking at the camera. Keep everything else unchanged." + KORU },
      { ad: "Doğal ışık", aciklama: "Yumuşak, güzel ışık", prompt: "Relight the photo with soft, flattering natural golden hour light. Keep composition unchanged." + KORU },
      { ad: "Yeni poz", aciklama: "Aynı yerde farklı poz", prompt: "Show the same person in the same place with a different, natural and confident pose." + KORU },
      { ad: "Daha iyi açı", aciklama: "Hafif yandan, zarif", prompt: "Retake the photo from a slightly different, more flattering camera angle at eye level." + KORU },
      { ad: "Kusursuz cilt", aciklama: "Doğal rötuş", prompt: "Retouch the skin naturally: reduce blemishes and under-eye shadows while keeping real skin texture." + KORU },
    ] },
    { ad: "Stiller", istemler: [
      { ad: "Stüdyo portre", aciklama: "Profesyonel stüdyo", prompt: "Turn this into a professional studio portrait with a soft neutral backdrop and studio lighting." + KORU },
      { ad: "Profesyonel", aciklama: "İş ve CV fotoğrafı", prompt: "Turn this into a professional business headshot wearing smart business attire, clean office background." + KORU },
      { ad: "Sinematik", aciklama: "Film karesi gibi", prompt: "Make this look like a cinematic film still with moody color grading and shallow depth of field." + KORU },
      { ad: "Vintage film", aciklama: "90'lar analog", prompt: "Make this look like a 1990s analog film photo with natural grain and warm faded colors." + KORU },
      { ad: "Anime", aciklama: "Çizgi film karakteri", prompt: "Redraw this photo as a high quality anime illustration while keeping the person recognizable." },
      { ad: "Yağlı boya", aciklama: "Klasik tablo", prompt: "Repaint this photo as a classical oil painting portrait while keeping the person recognizable." },
    ] },
    { ad: "Akıllı düzenle", ozel: true, istemler: [
      { ad: "Arka planı sil", aciklama: "Düz beyaz arka plan", prompt: "Replace the background with a clean plain white background. Keep the person and edges unchanged." + KORU, adet: 1 },
      { ad: "Sahil", aciklama: "Arka planı değiştir", prompt: "Replace the background with a sunny beach at golden hour, matching the lighting naturally." + KORU },
      { ad: "Şehir gece", aciklama: "Arka planı değiştir", prompt: "Replace the background with a city street at night with soft bokeh lights, matching the lighting." + KORU },
      { ad: "Kalite artır", aciklama: "Netleştir, gürültü al", prompt: "Restore and enhance this photo: remove noise and blur, increase sharpness and detail, natural colors." + KORU, adet: 1 },
    ] },
  ];

  let secilenFoto = null, secilenVeri = null, kategori = 0, sonucSayaci = 0, acikSonuc = null;

  // ---------- Fotoğraf seçme ----------
  $("#btnSec").onclick = () => { $("#dosya").value = ""; $("#dosya").click(); };
  $("#dosya").onchange = (e) => {
    const dosya = e.target.files && e.target.files[0];
    if (!dosya) return;
    const img = new Image();
    img.onload = () => {
      secilenFoto = img; secilenVeri = kucult(img, 1024);
      $("#secilenFoto").src = img.src;
      $("#sonuclar").innerHTML = ""; $("#sonucBaslik").hidden = true;
      ekranGoster("ekranAI");
      if (!window.FotoAI.acikMi()) bildir("Yapay zekâ için önce ⚙ Ayarlar'dan servis adresini girin.");
    };
    img.onerror = () => bildir("Bu dosya açılamadı. Lütfen JPG veya PNG seçin.");
    img.src = URL.createObjectURL(dosya);
  };

  // Gönderilecek fotoğrafı küçült (hız ve ücret için uzun kenar en çok 1024 px)
  function kucult(img, maks) {
    const s = Math.min(1, maks / Math.max(img.naturalWidth, img.naturalHeight));
    const c = document.createElement("canvas");
    c.width = Math.round(img.naturalWidth * s); c.height = Math.round(img.naturalHeight * s);
    c.getContext("2d").drawImage(img, 0, 0, c.width, c.height);
    return c.toDataURL("image/jpeg", 0.9);
  }

  // ---------- Kategoriler ve istem kartları ----------
  function kategorileriCiz() {
    const kutu = $("#kategoriler"); kutu.innerHTML = "";
    KATEGORILER.forEach((k, i) => {
      const b = document.createElement("button");
      b.className = "cip" + (i === kategori ? " secili" : ""); b.textContent = k.ad;
      b.onclick = () => { kategori = i; kategorileriCiz(); };
      kutu.appendChild(b);
    });
    const kartlar = $("#istemler"); kartlar.innerHTML = "";
    KATEGORILER[kategori].istemler.forEach((ist) => {
      const b = document.createElement("button");
      b.className = "istem";
      b.innerHTML = "<b></b><small></small>";
      b.querySelector("b").textContent = ist.ad; b.querySelector("small").textContent = ist.aciklama;
      b.onclick = () => uret(ist.ad, ist.prompt, ist.adet || 2);
      kartlar.appendChild(b);
    });
    $("#ozelIstem").hidden = !KATEGORILER[kategori].ozel;
  }
  kategorileriCiz();

  $("#btnOzel").onclick = () => {
    const metin = $("#ozelMetin").value.trim();
    if (!metin) return bildir("Ne değişmesini istediğinizi yazın.");
    uret("Özel", metin + "." + KORU, 1);
  };

  // ---------- Üretim ----------
  function uret(ad, prompt, adet) {
    if (!secilenVeri) return;
    if (!window.FotoAI.acikMi()) { ayarlariAc(); return bildir("Önce yapay zekâ servis adresini girin."); }
    $("#sonucBaslik").hidden = false;
    for (let n = 0; n < adet; n++) {
      const kart = document.createElement("button");
      kart.className = "sonuc bekliyor";
      $("#sonuclar").prepend(kart);
      const tohum = Date.now() % 100000 + (sonucSayaci++) * 7919;
      window.FotoAI.calistir(prompt, secilenVeri, tohum).then((blob) => {
        const img = new Image();
        img.onload = () => {
          kart.classList.remove("bekliyor");
          kart.appendChild(img);
          const etiket = document.createElement("span"); etiket.textContent = ad; kart.appendChild(etiket);
          kart.onclick = () => sonucuAc(img);
        };
        img.src = URL.createObjectURL(blob);
      }).catch((e) => {
        kart.classList.remove("bekliyor"); kart.classList.add("hata");
        kart.textContent = "Olmadı: " + (e && e.message ? e.message : e);
        kart.onclick = () => kart.remove();
      });
    }
    setTimeout(() => $("#sonucBaslik").scrollIntoView({ behavior: "smooth" }), 50);
  }

  // ---------- Sonuç görüntüleyici ----------
  function sonucuAc(img) {
    acikSonuc = img; $("#sonucBuyuk").src = img.src; $("#orijinalKucuk").src = secilenFoto.src;
    ekranGoster("ekranSonuc");
  }
  const orijinalGoster = (e) => { e.preventDefault(); $("#sonucBuyuk").src = secilenFoto.src; };
  const sonucaDon = () => { if (acikSonuc) $("#sonucBuyuk").src = acikSonuc.src; };
  $("#orijinalKutu").addEventListener("pointerdown", orijinalGoster);
  ["pointerup", "pointercancel", "pointerleave"].forEach((e) => $("#orijinalKutu").addEventListener(e, sonucaDon));
  $("#btnSonucGeri").onclick = () => ekranGoster("ekranAI");
  $("#btnSonucDuzenle").onclick = () => duzenleyiciyiAc(acikSonuc, "ekranSonuc");
  $("#btnSonucKaydet").onclick = async () => {
    const c = document.createElement("canvas");
    c.width = acikSonuc.naturalWidth; c.height = acikSonuc.naturalHeight;
    c.getContext("2d").drawImage(acikSonuc, 0, 0);
    try { await paylas(c, "FotoRea_AI_" + Date.now() + ".jpg"); }
    catch (e) { if (!(e && e.name === "AbortError")) bildir("Kaydedilemedi: " + (e && e.message ? e.message : e)); }
  };

  $("#btnAIGeri").onclick = () => ekranGoster("ekranAna");
  $("#btnElle").onclick = () => secilenFoto && duzenleyiciyiAc(secilenFoto, "ekranAI");

  // ---------- Ayarlar ----------
  function ayarlariAc() {
    const a = window.FotoAI.ayarOku();
    $("#ayarAdres").value = a.adres || ""; $("#ayarAnahtar").value = a.anahtar || "";
    $("#ayarPerde").hidden = false;
  }
  $("#btnAyarlar").onclick = ayarlariAc;
  $("#btnAyarKapat").onclick = () => ($("#ayarPerde").hidden = true);
  $("#btnAyarKaydet").onclick = () => {
    const adres = $("#ayarAdres").value.trim();
    if (adres && !/^https:\/\//.test(adres)) return bildir("Adres https:// ile başlamalı.");
    window.FotoAI.ayarYaz({ adres, anahtar: $("#ayarAnahtar").value.trim() });
    $("#ayarPerde").hidden = true;
    bildir(adres ? "Yapay zekâ bağlantısı kaydedildi." : "Yapay zekâ bağlantısı kaldırıldı.");
  };
})();

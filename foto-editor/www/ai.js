// Yapay zekâ bağlantısı. Uygulama, fotoğrafı ara sunucuya (ai-sunucu/worker.js) gönderir;
// ara sunucu Replicate'teki görsel modelini çalıştırıp sonucu döndürür.
// Servis adresi ve uygulama anahtarı, uygulamadaki Ayarlar (⚙) ekranından girilir ve telefonda saklanır.
(function () {
  "use strict";

  const ANAHTAR = "fotorea-ai-ayar";

  function ayarOku() {
    try { return JSON.parse(localStorage.getItem(ANAHTAR)) || {}; } catch (e) { return {}; }
  }
  function ayarYaz(ayar) {
    try { localStorage.setItem(ANAHTAR, JSON.stringify(ayar)); } catch (e) { /* saklanamazsa oturum boyunca geçerli */ }
    gecerli = ayar;
  }
  let gecerli = ayarOku();

  // istem: modelin ne yapacağını anlatan metin, foto: JPEG data URL, tohum: farklı sonuçlar için sayı
  async function calistir(istem, foto, tohum) {
    if (!acikMi()) throw new Error("kapali");
    const adres = gecerli.adres.replace(/\/+$/, "") + "/uret";
    const yanit = await fetch(adres, {
      method: "POST",
      headers: { "Content-Type": "application/json", "X-Uygulama-Anahtari": gecerli.anahtar || "" },
      body: JSON.stringify({ istem, foto, tohum }),
    });
    if (!yanit.ok) {
      let mesaj = "Servis hatası (" + yanit.status + ")";
      try { const j = await yanit.json(); if (j.hata) mesaj = j.hata; } catch (e) { /* metin değil */ }
      throw new Error(mesaj);
    }
    return await yanit.blob();
  }

  function acikMi() { return !!(gecerli && gecerli.adres); }

  window.FotoAI = { calistir, acikMi, ayarOku: () => ({ ...gecerli }), ayarYaz };
})();

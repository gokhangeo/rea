// Yapay zekâ gerektiren araçların bağlantı yeri.
// Bir servis (ör. arka plan silme, kalite artırma API'si) seçildiğinde adresi buraya yazılır;
// FotoAI.calistir fotoğrafı gönderip dönen görüntüyü uygulamaya verir.
(function () {
  "use strict";

  const AYAR = {
    servisAdresi: "", // Örn. "https://ornek-servis.com/api" (boşken AI araçları kapalıdır)
  };

  async function calistir(arac, jpegBlob) {
    if (!AYAR.servisAdresi) throw new Error("kapali");
    const form = new FormData();
    form.append("arac", arac);
    form.append("foto", jpegBlob, "foto.jpg");
    const yanit = await fetch(AYAR.servisAdresi, { method: "POST", body: form });
    if (!yanit.ok) throw new Error("Servis hatası: " + yanit.status);
    return await yanit.blob();
  }

  window.FotoAI = { AYAR, calistir, acikMi: () => !!AYAR.servisAdresi };
})();

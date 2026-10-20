# FotoRea

Telefonda çalışan, internetsiz foto düzenleme uygulaması. Tek kod tabanından (Capacitor) hem Android `.apk` hem iOS `.ipa` üretilir.

## Bu sürümde hazır olanlar (cihaz üzerinde, çevrimdışı)

- **Filtre:** 11 hazır filtre (Canlı, Sinema, Altın saat, Soğuk, Pastel, Vintage, Retro, Dramatik, Siyah-beyaz, Noir, Sepya) ve yoğunluk ayarı
- **Ayarla:** parlaklık, kontrast, doygunluk, sıcaklık, ton, gölgeler, vurgular, netlik
- **Rötuş:** cilt yumuşatma, cilt ışıltısı, yüz aydınlatma (ten rengi otomatik bulunur, arka plan etkilenmez)
- **Kırp:** Orijinal, Kare, 4:5, 3:4, 9:16, 16:9, 3:2; döndürme ve yatay çevirme
- **Efekt:** vinyet, film greni, soluklaştırma, rüya parlaması
- Önce/sonra (fotoğrafa basılı tutunca orijinal görünür), geri al, sıfırla
- **Kaydet:** tam çözünürlükte (uzun kenar en çok 4000 px) JPG; telefonda paylaşım ekranından "Görüntüyü Kaydet" / Galeri seçilir

## Yapay zekâ servisi bağlanınca gelecekler

Arka plan silme/değiştirme, kalite artırma, AI portre stilleri, nesne silme. Arayüzde yerleri hazır; servis adresi `www/ai.js` içindeki `servisAdresi` alanına yazılınca açılır.

## Derleme ve kurulum

- `foto-editor/` altında bir değişiklik depoya gönderildiğinde GitHub Actions **"FotoRea derle (apk + ipa)"** işi çalışır.
- Actions > son çalışma > Artifacts:
  - `FotoRea-apk` → zip içindeki `FotoRea.apk` Android telefona doğrudan kurulur ("bilinmeyen kaynaklardan yükleme" izni istenir).
  - `FotoRea-ipa` → zip içindeki `FotoRea.ipa` iPhone'a SideStore veya iloader "Import IPA" ile kurulur (bkz. `ios-ipa/rehber.md`).
- Arayüz `www/` klasöründedir; tarayıcıda `www/index.html` açılarak da denenebilir.

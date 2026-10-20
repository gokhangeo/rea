# FotoRea

Telefonda çalışan, internetsiz foto düzenleme uygulaması. Tek kod tabanından (Capacitor) hem Android `.apk` hem iOS `.ipa` üretilir.

## Akış

1. **Ana ekran:** "Fotoğraf seç".
2. **Yapay zekâ ekranı:** üç grup kart.
   - *Yeniden çek:* Gülümse, Gözleri aç, Doğal ışık, Yeni poz, Daha iyi açı, Kusursuz cilt
   - *Stiller:* Stüdyo portre, Profesyonel, Sinematik, Vintage film, Anime, Yağlı boya
   - *Akıllı düzenle:* Arka planı sil, Sahil, Şehir gece, Kalite artır ve serbest metinle istek
   Sonuçlar altta ızgara olarak çıkar; dokununca büyür, kaydedilir veya elle düzenlemeye gönderilir.
3. **Elle düzenle (internetsiz):** 11 filtre, ışık/renk ayarları, rötuş (cilt yumuşatma, ışıltı, yüz aydınlatma), kırpma, döndürme, efektler, önce/sonra, geri al.

## Yapay zekâ bağlantısı

Uygulama fotoğrafı `ai-sunucu/worker.js` ara sunucusuna (Cloudflare Worker) gönderir; o da Replicate'teki
`black-forest-labs/flux-kontext-pro` modelini çalıştırır. Replicate anahtarı yalnızca ara sunucuda durur.
Servis adresi ve uygulama anahtarı uygulamadaki ⚙ Ayarlar'dan girilir; yeniden derleme gerekmez.

## Derleme ve kurulum

- `foto-editor/` altında bir değişiklik depoya gönderildiğinde GitHub Actions **"FotoRea derle (apk + ipa)"** işi çalışır.
- Actions > son çalışma > Artifacts:
  - `FotoRea-apk` → zip içindeki `FotoRea.apk` Android telefona doğrudan kurulur ("bilinmeyen kaynaklardan yükleme" izni istenir).
  - `FotoRea-ipa` → zip içindeki `FotoRea.ipa` iPhone'a SideStore veya iloader "Import IPA" ile kurulur (bkz. `ios-ipa/rehber.md`).
- Arayüz `www/` klasöründedir; tarayıcıda `www/index.html` açılarak da denenebilir.

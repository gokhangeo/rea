# Kutu Kurucu

Evdeki iki Android TV kutusuna (Xiaomi Mi Box 4 ve Turkcell TV+ / Skyworth) telefondan ya da bilgisayardan
kolayca APK kurmak ve kumandanın Home tuşunun hangi ana ekranı açacağını seçmek için küçük bir TV uygulaması.
Her kutuya ayrı kurulur; her kutunun kendi adresi ve kodu vardır.

| Kutu | Adres |
| --- | --- |
| Mi Box 4 | http://192.168.1.106:8090 |
| TV+ kutusu | http://192.168.1.174:8090 |

## Ne yapar?

- **APK yükleme:** Sayfada APK'yı seçin ya da sürükleyip bırakın. Dosya kutuya gider, TV'de Android'in kendi
  "Yükle" penceresi açılır, kumandayla onaylarsınız. Sessiz kurulum yoktur.
- **Bağlantıdan kurma:** Bir `.apk` bağlantısı ya da GitHub sürüm sayfası bağlantısı yapıştırın; kutu kendisi indirir.
- **Ana ekran:** Home tuşu kutunun kendi ana ekranını mı, Ev Ekranı'nı mı, başka bir başlatıcıyı mı açsın, sayfadan seçilir.
- **Uygulamalar:** Kutudaki uygulamalar listelenir; açılabilir, APK'sı indirilebilir (yedek ya da öbür kutuya kurmak için),
  kaldırılabilir. Sistem ve operatör (Turkcell/Skyworth) uygulamaları kaldırılamaz.
- **Ekrana mesaj:** Sayfadan yazılan mesaj TV'de ne açık olursa olsun (kanal, film, uygulama) üstte görünür ve seçilen süre
  sonunda kendiliğinden kaybolur. `POST /api/mesaj?metin=...&sure=8000`.
- **Gol bildirimleri:** Takım elle yazılmaz: önce lig (Süper Lig, Premier League, LaLiga, Bundesliga, Serie A, Ligue 1,
  Eredivisie, Primeira Liga, Belçika, İskoçya, Avrupa kupaları, milli takım turnuvaları), sonra o ligin takımı listeden seçilir
  (liste ESPN'den gelir). Eşleşme ESPN takım kimliğiyle yapılır, ad benzerliğiyle değil. Kutu 20 saniyede bir takımın kendi
  ligini, Avrupa kupalarını ve Türkiye Kupası'nı yoklar; gol olunca skoru üstte 12 saniye gösterir ve kısa bir gol sesi (düdük + boru) çalar; ses sayfadan kapatılabilir,
  "Dene" düğmesi örnek bildirim gösterir. Kanal açıkken de görünür.
  Eski sürümde elle yazılmış adlar "elle yazılmış" etiketiyle görünür, silinene kadar eskisi gibi izlenir.
- **Telefondan görüntü/video:** Telefondan seçilen fotoğraf ya da video kutuya gider ve tam ekran açılır; kumandada GERİ kapatır,
  video bitince kendiliğinden kapanır (`POST /api/medya`).
- **Kendini güncelleme:** Sayfadaki "Kutu Kurucu'yu güncelle" düğmesi GitHub'daki son sürümü kurar.
- **Kutu Kurucu'yu kaldır:** Sayfadaki bu düğme TV'de Android'in kaldırma penceresini açar. Yeni sürüm "başka anahtarla
  imzalı" diye kurulmuyorsa eski sürüm böyle kaldırılır; onaydan sonra panel kapanır, APK yeniden kurulur.
- **Eskisini kaldır:** Bir APK "başka anahtarla imzalı ya da daha yeni" diye kurulamazsa, iş listesinde hatanın hemen altında
  bu düğme çıkar. Kutudaki eski sürümün kaldırma penceresini TV'de açar; onaydan sonra APK yeniden gönderilir.
  Kutu Kurucu'nun kendisiyse kendi kaldırma yolu kullanılır, sonra 8091'deki Kurulum'dan yeni sürüm kurulur.
- Sürümler depodaki `kutu-kurucu/kutu-kurucu.jks` anahtarıyla imzalanır; anahtar değişmedikçe yeni sürüm eskisinin üstüne kurulur.

## Kutu Kurucu Kurulum (ayrı küçük uygulama, port 8091)

Kutu Kurucu silindiğinde 8090'daki sayfa da gider; geri kuracak bir şey kalmaz. Bu yüzden ayrı paket
(`com.gokhan.kutukurucu.kurulum`) bir yardımcı var:

- `http://KUTU-IP:8091` adresinde tek sayfa: "Kur / güncelle", "Kaldır", "İzin ekranını aç".
- Kutu Kurucu'yu GitHub'daki en yeni sürümden indirip kurar (TV'de "Yükle" onayı), gerekirse önce kaldırır.
- Kutu Kurucu silinse de kutuda kalır; kutu açılınca kendini yeniden başlatır.
- İlk kurulumda kutuda bu uygulamaya "bilinmeyen uygulama yükleme" izni verilir (sayfadaki "İzin ekranını aç").

- Sayfa TV ekranında yazan 4 haneli kodu ister. Port 8090; Ev Ekranı'nın 8080'deki sayfasıyla çakışmaz.

## İlk kurulum (her kutuya bir kez)

İzinler bir kez ADB ile verilir; sonra ADB kapatılabilir, Kutu Kurucu ADB'siz çalışır.

1. GitHub sürüm sayfasından `KutuKurucu-1.N.apk` ve `ilk-kurulum.bat` dosyalarını aynı klasöre indirin.
2. Kutularda ADB (ağ üzerinden hata ayıklama) açıkken APK'yı `ilk-kurulum.bat` üzerine sürükleyin.
   Betik iki kutuya da kurar ve şu izinleri verir:
   - `WRITE_SECURE_SETTINGS`: ana ekran seçimi için Home tuşu servisini kendi açabilsin;
   - `SYSTEM_ALERT_WINDOW`: Android 10+ kutuda (TV+) arka plandayken de onay penceresini açabilsin;
   - `REQUEST_INSTALL_PACKAGES`: uygulama yükleme izni.
3. TV'de Kutu Kurucu açılır; ekranda adres ve kod yazar. Tüm satırlar yeşil ✔ olmalı.

ADB yoksa Downloader ile de kurulabilir; o zaman eksik izinleri TV'deki düğmelerle verin. Ayar ekranı olmayan izin
varsa (bazı TV'lerde "üstte gösterme" yok) APK kurarken TV'de Kutu Kurucu açık dursun, onay penceresi orada çıkar.

## Bilgisayardan hızlı gönderme

`Kutuya-Gonder.bat` ve `kutuya-gonder.ps1` aynı klasörde dursun. APK'ları `Kutuya-Gonder.bat` üzerine sürükleyin,
Mi Box / TV+ / ikisi seçin. Kod ilk seferde sorulur ve hatırlanır. Tarayıcıyla adrese girip sürüklemek de olur.

## Ana ekran seçimi nasıl çalışır?

Android TV seçilen ana ekran uygulamasını çoğu zaman yok sayar. Bu yüzden seçim Home tuşu üzerinden yapılır:
Kutu Kurucu'nun "Home tuşu" erişilebilirlik servisi tuşu yakalar ve seçilen ekranı açar. Servis başka hiçbir tuşa ve
ekran içeriğine dokunmaz. Kutunun kendi ana ekranı seçilince servis kapanır, kutu eskisi gibi çalışır.
Ev Ekranı seçilirse tuşu Ev Ekranı'nın kendi servisi yönetir; başka bir ekran seçilince Ev Ekranı'nın servisi kapatılır.

## Geliştirme

- Kod `app/src/main/java/com/gokhan/kutukurucu/`, telefon/PC sayfası `app/src/main/assets/sayfa.html`.
- APK GitHub Actions'ta derlenir (`.github/workflows/kutu-kurucu.yml`); main'e her birleştirmede `kutu-kurucu-v1.N`
  sürümü yayınlanır. İmza depodaki `kutu-kurucu.jks` anahtarıdır; 1.5 ve sonrası birbirinin üstüne kurulur. 1.4 ve öncesi farklı anahtarla imzalıdır, bir kez kaldırılması gerekir.

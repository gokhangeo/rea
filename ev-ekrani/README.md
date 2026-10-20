# Ev Ekranı

Xiaomi TV kutusu (Android TV / Google TV) için reklamsız ana ekran:

- **Solda Fenerbahçe köşesi:** sıradaki maç ve geri sayım, son maç sonucu, sonraki iki maç (Süper Lig, Avrupa kupaları, Türkiye Kupası).
- **Ortada 3D aile fotoğrafları:** telefondan gönderilen fotoğrafların derinliği kutunun kendisinde yapay zekâyla çıkarılır, fotoğraf yavaşça kayarken derinlik kazanır. Fotoğraflar internete gönderilmez.
- **Sağ üstte saat, tarih ve Mersin hava durumu.**
- **Altta uygulama çubuğu:** kumandayla seçilir; OK'a basılı tutunca taşıma/kaldırma menüsü, en sondaki **+** ile yeni uygulama eklenir.

## Kurulum

1. **APK'yı indirin.** Depodaki *Releases* (Sürümler) sayfasında en son `EvEkrani-0.1.x.apk` dosyası var.
2. **Kutuya yükleyin.** En kolayı:
   - Telefona ve kutuya Play Store'dan **Send Files to TV** uygulamasını kurun, APK'yı telefondan kutuya gönderin; ya da
   - APK'yı bir USB belleğe koyup kutuya takın ve bir dosya yöneticisiyle (ör. *X-plore*) açın.
   - Kutu "bilinmeyen kaynaklardan yükleme" izni isterse, açtığınız dosya yöneticisi uygulamasına izin verin.
3. **Home tuşunu Ev Ekranı'na bağlayın.** Kutuda: *Ayarlar → Cihaz Tercihleri → Erişilebilirlik → Ev Ekranı Home tuşu → Aç*.
   Bundan sonra kumandanın Home tuşu Ev Ekranı'nı açar. Eski Xiaomi/Google ana ekranı silinmez, yedek olarak kalır.
4. **İlk açılış.** Ev Ekranı'nı bir kez uygulamalar listesinden açın. Ekranda fotoğraf alanında bir adres (ör. `192.168.1.25:8080`) ve 4 haneli kod görünür.
5. **Fotoğraf ekleyin.** Telefon aynı Wi-Fi'deyken tarayıcıda o adresi açın, kodu girin, *Fotoğraf ekle* ile seçin.
   İlk fotoğrafta 3D modeli (yaklaşık 100 MB) bir kez indirilir; sonra her fotoğrafın 3D hazırlığı kutuda bir iki dakika sürer.
   Aynı sayfadan uygulama çubuğunu da düzenleyebilirsiniz.

## Geri almak

- Home tuşunu eski ana ekrana döndürmek: *Ayarlar → Cihaz Tercihleri → Erişilebilirlik → Ev Ekranı Home tuşu → Kapat*.
- Tamamen kaldırmak: *Ayarlar → Uygulamalar → Ev Ekranı → Kaldır*.
- Uygulama üst üste çökerse Home tuşu kendiliğinden eski ana ekrana döner (2 dakika içinde 3 çökme).

## Erişilebilirlik menüsünde görünmezse (bilgisayardan, ADB ile)

Kutuda *Geliştirici seçenekleri → USB hata ayıklama* açıkken bilgisayardan:

```sh
adb connect <kutunun-ip-adresi>:5555
adb install -r EvEkrani-0.1.x.apk
adb shell pm grant com.gokhan.evekrani android.permission.WRITE_SECURE_SETTINGS
adb shell settings get secure enabled_accessibility_services   # önce mevcut değeri not edin
adb shell settings put secure enabled_accessibility_services com.gokhan.evekrani/.HomeButtonService
adb shell settings put secure accessibility_enabled 1
```

`WRITE_SECURE_SETTINGS` izni verilirse uygulama, kutu güncellemelerinde kapanan Home tuşu servisini kendisi yeniden açar.

## Teknik notlar

- Arayüz `app/src/main/assets/ui/` altında: `index.html` TV ekranı, `telefon.html` telefon sayfası. Uygulama bunları kutunun içindeki küçük sunucudan (8080 portu) açar.
- Maç bilgileri ESPN'in herkese açık skor servisinden, hava durumu Open-Meteo'dan gelir; ikisi de anahtar istemez. İnternet yokken son bilgiler gösterilir.
- 3D: Depth Anything V2 (küçük, Apache-2.0) modeli kutuda ONNX Runtime ile çalışır. Model ilk kullanımda indirilir.
- İmza: yeni sürümün eskisinin üstüne kurulabilmesi için her derleme aynı anahtarla imzalanır. Anahtar depoda tutulmaz; GitHub Actions önbelleğindeki hata ayıklama anahtarı kullanılır. Daha kalıcı olması için depo ayarlarına `EV_KEYSTORE_B64` (anahtar dosyasının base64 hali) ve `EV_KEYSTORE_PASS` gizli değerleri eklenebilir. Bir güncelleme "uygulama yüklenmedi" hatası verirse eski sürümü kaldırıp yenisini kurun.
- APK, `ev-ekrani/` altındaki her değişiklikte GitHub Actions'ta derlenir (`.github/workflows/ev-ekrani.yml`); `main` dalında sürüm olarak yayınlanır.
- Home tuşu servisi, çökme koruması ve servisi açık tutan bölüm [3d-home-tv-os](https://github.com/Rod292/3d-home-tv-os) projesinden uyarlandı (MIT lisansı, bkz. `UCUNCU-TARAF-LISANS.txt`).

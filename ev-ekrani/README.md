# Ev Ekranı

Xiaomi TV kutusu (Android TV / Google TV) için reklamsız, tamamen Türkçe ana ekran. Temeli
[cleanyourtv](https://github.com/Gathin23/cleanyourtv) ana ekranı (MIT lisansı): sol üstte selamlama, saat ve tarih,
sağ üstte Girişler / Wi-Fi / Ayarlar düğmeleri, altta cam vurgulu uygulama çubuğu ve aşağıda tüm uygulamalar.
Ortadaki boşluğa Ev Ekranı'nın kendi bölümü eklendi:

- **Solda Fenerbahçe kartı:** sıradaki maç ve geri sayım, son maç sonucu (Süper Lig, Avrupa kupaları, Türkiye Kupası).
  Karta gelip **OK**'a basınca kart döner; arkasında son maçın skoru, golleri ve dakikaları, **Maç özetini izle**
  ve **FB TV** düğmeleri var. İkisi de YouTube'u açar. **Geri** kartı eski haline çevirir.
- **Sağda 3D aile fotoğrafları:** telefondan gönderilen fotoğrafların derinliği kutunun kendisinde yapay zekâyla
  çıkarılır, fotoğraf yavaşça kayarken derinlik kazanır. Fotoğraflar internete gönderilmez. Köşede Mersin hava durumu.

## Kumanda

- Uygulama çubuğundan **Yukarı**: Fenerbahçe kartına geçer. Bir daha **Yukarı**: üstteki düğmeler.
- Kartta **Sağ**: fotoğrafa geçer; fotoğrafta **Sağ** ya da **OK** sonraki fotoğrafı gösterir.
- Bölümde **Aşağı** ya da **Geri**: uygulama çubuğuna döner.
- Uygulamaların yerini değiştirmek: uygulamanın üstünde **OK**'a basılı tutun, oklarla taşıyın, **OK** ile bırakın, bitince **Geri**. Üst çubuktaki iki oklu düğme de sıralamayı açar.

## Kurulum ve güncelleme

1. **APK'yı indirin.** Depodaki *Releases* (Sürümler) sayfasında en son `EvEkrani-0.2.x.apk` dosyası var.
2. **Kutuya yükleyin.** Kutudaki **Downloader** uygulamasına sürüm sayfasındaki APK bağlantısını yazın ya da
   APK'yı USB bellekle takıp bir dosya yöneticisiyle açın. Eski Ev Ekranı kuruluysa **üstüne kurun**; fotoğraflar kalır.
3. **Play Protect engellerse:** *Play Store → profil → Play Protect → ayarlar* bölümünden taramayı geçici olarak
   kapatın, kurun, sonra yeniden açın.
4. **Home tuşunu Ev Ekranı'na bağlayın.** Kutuda: *Ayarlar → Cihaz Tercihleri → Erişilebilirlik → Ev Ekranı Home tuşu → Aç*.
   Eski Xiaomi/Google ana ekranı silinmez, yedek olarak kalır.
5. **Fotoğraf ekleyin.** Fotoğrafa gelince altında bir adres (ör. `192.168.1.25:8080`) ve 4 haneli kod görünür.
   Telefon aynı Wi-Fi'deyken tarayıcıda o adresi açın, kodu girin, *Fotoğraf ekle* ile seçin.
   İlk fotoğrafta 3D modeli (yaklaşık 100 MB) bir kez indirilir; sonra her fotoğrafın 3D hazırlığı kutuda bir iki dakika sürer.

## Geri almak

- Home tuşunu eski ana ekrana döndürmek: *Ayarlar → Cihaz Tercihleri → Erişilebilirlik → Ev Ekranı Home tuşu → Kapat*.
- Tamamen kaldırmak: *Ayarlar → Uygulamalar → Ev Ekranı → Kaldır*.
- Uygulama üst üste çökerse Home tuşu kendiliğinden eski ana ekrana döner (2 dakika içinde 3 çökme).

## Bilgisayardan (ADB ile), isteğe bağlı

Kutuda *Geliştirici seçenekleri → USB hata ayıklama* açıkken evdeki bir bilgisayardan:

```sh
adb connect <kutunun-ip-adresi>:5555
adb install -r EvEkrani-0.2.x.apk
adb shell pm grant com.gokhan.evekrani android.permission.WRITE_SECURE_SETTINGS
adb shell settings get secure enabled_accessibility_services   # önce mevcut değeri not edin
adb shell settings put secure enabled_accessibility_services com.gokhan.evekrani/com.gokhan.evekrani.HomeButtonService
adb shell settings put secure accessibility_enabled 1
```

`WRITE_SECURE_SETTINGS` izni verilirse uygulama, kutu güncellemelerinde kapanan Home tuşu servisini kendisi yeniden açar.

## Teknik notlar

- Ana ekran cleanyourtv'nin Java kodu (`app/src/main/java/com/gathin/tvhome/`); uygulama kimliği `com.gokhan.evekrani`
  olduğu için eski Ev Ekranı'nın üstüne kurulur. Varsayılan dil Türkçe (`res/values/strings.xml`), diğer diller çıkarıldı.
- Ev Ekranı eklentileri `app/src/main/java/com/gokhan/evekrani/` (Kotlin) ve `EvWidget.java`: kutunun içindeki küçük
  sunucu (8080 portu), fotoğraf deposu ve derinlik, maç ve hava bilgisi, Home tuşu servisi.
- Bölümün arayüzü `app/src/main/assets/ui/widget.html`, telefon sayfası `telefon.html`. Bölüm saydam bir WebView'dır;
  oklar sayfada işlenir, kenara gelince odak `window.Tv` köprüsüyle ana ekrana geri verilir.
- Maç bilgileri ve goller ESPN'in herkese açık skor servisinden, hava durumu Open-Meteo'dan gelir; ikisi de anahtar
  istemez. İnternet yokken son bilgiler gösterilir. Maç özeti ve FB TV düğmeleri YouTube'da arama açar.
- 3D: Depth Anything V2 (küçük, Apache-2.0) modeli kutuda ONNX Runtime ile çalışır. Model ilk kullanımda indirilir.
- İmza: yeni sürümün eskisinin üstüne kurulabilmesi için her derleme aynı anahtarla imzalanır. Anahtar depoda tutulmaz;
  GitHub Actions önbelleğindeki hata ayıklama anahtarı kullanılır. Daha kalıcı olması için depo ayarlarına
  `EV_KEYSTORE_B64` ve `EV_KEYSTORE_PASS` gizli değerleri eklenebilir. Bir güncelleme "uygulama yüklenmedi" hatası
  verirse eski sürümü kaldırıp yenisini kurun.
- APK, `ev-ekrani/` altındaki her değişiklikte GitHub Actions'ta derlenir (`.github/workflows/ev-ekrani.yml`);
  `main` dalında sürüm olarak yayınlanır.
- Lisanslar: cleanyourtv (`CLEANYOURTV-LISANS.txt`), Outfit yazı tipi (`FONT-LICENSE-OFL.txt`), Home tuşu servisi ve
  çökme koruması [3d-home-tv-os](https://github.com/Rod292/3d-home-tv-os) projesinden (`3D-HOME-TV-OS-LISANS.txt`).

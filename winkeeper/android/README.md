# WinKeeper Android

WinKeeper'ın telefon ve tablet sürümü. Telefonda alt sekme çubuğu, tablette yan menü kullanılır.

| Bölüm | Ne yapar |
|---|---|
| Temizle | APK, küçük resim önbelleği, geçici/günlük dosyalar, boş klasörler, erişilebilen önbellekler; isteğe bağlı eski indirmeler ve 100 MB+ dosyalar |
| Kaldır | Kurulu uygulamalar (boyut, tarih, filtre, sıralama), tek tek ya da toplu kaldırma |
| Başlangıç | Açılışta başlamak için kayıtlı uygulamalar; Zorla durdur, pil kısıtlaması, kalıcı kapatma için ADB komutu |
| Analiz | Dahili depolama klasör klasör, en büyük dosyalar, silme |
| Durum | RAM, depolama, pil, işlemci, cihaz bilgisi; arka planı temizle |

## İzinler
- **Tüm dosyalara erişim**: Temizle ve Analiz için. Uygulama ilk açılışta ayar sayfasını açar.
- **Kullanım erişimi** (isteğe bağlı): uygulamaların veri ve önbellek dahil gerçek boyutu için.

## Android sınırı
Android, bir uygulamanın başka uygulamaların açılışta başlamasını kapatmasına izin vermez (root/ADB gerekir).
"Zorla durdur" yapılan uygulama, kullanıcı onu yeniden açana kadar açılışta başlamaz; kalıcı çözüm için
Başlangıç bölümündeki ADB komutu kullanılabilir.

## Derleme
GitHub Actions (`.github/workflows/winkeeper-android.yml`) her `winkeeper/android` değişikliğinde APK derler;
main dalında `winkeeper-android-v0.1.N` sürümü yayınlanır. İmza anahtarı `winkeeper.jks` (parola `winkeeper`).

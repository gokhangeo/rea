# Ataç (Python sürümü)

Windows için yüzen **sürükle-bırak rafı**: dosyaları/klasörleri geçici olarak topla,
tek seferde başka yere taşı. Electron sürümüyle aynı görünümü hedefler ama saf
**Python + Win32 (ctypes) + Pillow** ile çalışır; içinde Chromium yoktur, bu yüzden
antivirüslerin Electron paketlerine taktığı yanlış alarmlara takılmaz.

**İndir:** [sürüm sayfası](https://github.com/gokhangeo/rea/releases) → `Atac.exe`

> Bu uygulamanın Windows sürümü, kod imzalama için [SignPath Foundation](https://signpath.org)'ın ücretsiz açık kaynak programını kullanır (sertifika SignPath Foundation adına düzenlenir).
(tek dosya, kurulum yok; çift tıkla çalışır).

## Kullanım

- Ekranın sağ kenarında küçük bir **metalik ataç** durur.
- **Tıkla** → buzlu cam raf açılır/kapanır.
- **Dosya/klasör sürükleyip** rafın üzerine bırak → rafa eklenir.
- **+** ile dosya seçerek de ekleyebilirsin.
- Bir öğeye **çift tıkla** → dosyayı açar.
- Bir öğeye **tek tıkla** / **⇪ Tümü** → dosyaları panoya kopyalar; hedef klasörde
  **Ctrl+V** ile yapıştır. (Doğrudan dışarı sürükleme bir sonraki sürümde eklenecek.)
- Ataç'ı **tutup sürükleyerek** istediğin yere taşı; konumu hatırlanır.
- Saat yanındaki **tepsi simgesi**: göster/gizle, Windows ile başlat, güncelleme denetle, çıkış.
- **Arkada kal** (varsayılan): bir pencere ya da klasör açıldığında ataç onun altında kalır, işini engellemez. Öne almak için **Ctrl+Shift+Boşluk** (8 sn öne gelir). Hep üstte dursun istersen tepsiden "Arkada kal" işaretini kaldır.
- Kısayol: **Ctrl+Shift+Boşluk** → göster (arkada kal modunda: öne getir).

## Otomatik güncelleme

Uygulama açılışta GitHub Releases'te daha yeni bir `atac-py-vX.Y` + `Atac.exe` arar;
varsa indirip kendini günceller ve yeniden başlar. İnternet yoksa mevcut sürümle devam eder.

## Geliştirme

```bat
pip install -r requirements.txt
python atac.py          :: doğrudan çalıştır (güncelleme yapmaz)
build.bat               :: dist\Atac.exe (tek dosya) üretir
```

Günlük: `%APPDATA%\Atac\atac.log` (sorun olursa bu dosya işe yarar).

### Yeni sürüm yayınlama

Exe'yi bilgisayarda derlemek gerekmez; GitHub Actions Windows'ta derler
([`.github/workflows/atac-py.yml`](../.github/workflows/atac-py.yml)).

1. Değişiklikleri `main`'e gönder.
2. Yeni bir etiket gönder (bir öncekinden büyük): `git tag atac-py-v0.2 && git push origin atac-py-v0.2`
3. Actions exe'yi derler ve `atac-py-v0.2` release'ine `Atac.exe` olarak yükler.

> Not: Bu sürüm Electron sürümünden (depoda `atac/`) tamamen ayrıdır; sürüm etiketleri
> de ayrıdır (`atac-py-v*`), böylece ikisinin otomatik güncellemesi karışmaz.

# Ataç

Windows için **yüzen sürükle-bırak rafı**. Dosyaları ve klasörleri geçici olarak bir "ataça" toplarsın, sonra hepsini tek seferde başka bir yere (Dosya Gezgini, e-posta, uygulama) sürükleyip bırakırsın. Mac'teki Dropover/Yoink tarzı araçların özgün, Türkçe Windows karşılığı.

> Bağımsız, özgün bir uygulamadır; başka bir uygulamanın adını, logosunu veya varlıklarını kullanmaz.

## Ne yapar

- **Topla:** Ekranda hep üstte duran küçük ataç penceresine dosya/klasör sürükle-bırak; ya da başlıktaki **+** ile klasör/dosya seçerek ekle.
- **Taşı/kopyala:** Ataçtaki bir öğeyi ya da **"Tümünü sürükle"** düğmesini kavrayıp hedefe bırak. Windows'un kendi sürükle-bırağı devrede: bırakırken kopyalar, Shift ile taşır.
- **Yönet:** Öğeyi çift tıkla aç, × ile kaldır, **Temizle** ile hepsini boşalt.
- **Açılışta başlar:** Windows'a girince kendi açılır ve ekranda durur (tepsi simgesinden ya da `Ctrl+Shift+Boşluk` ile gizle/göster). Açılışta başlatmayı tepsi menüsünden kapatabilirsin.

## İzinsiz (yönetici gerektirmeyen) kurulum

- Kurulum `%LOCALAPPDATA%` altındaki kullanıcı klasörüne yapılır; **admin/yönetici hakkı istemez** (per-user).
- Açılışta başlatma yalnızca kullanıcı kaydına (HKCU `Run`) yazılır; yine admin gerekmez.
- İki çıktı vardır:
  - `Ataç-Kurulum-x.y.z.exe` — kurulum sihirbazı (per-user).
  - `Ataç-Tasinabilir-x.y.z.exe` — kurulum gerektirmeyen taşınabilir sürüm.

## Derleme

Windows çıktısı GitHub Actions'ta (windows-latest) üretilir; `atac.yml` iş akışına bakın. Yerelde:

```bash
cd atac
npm install
npm run dist     # dist/ altında kurulum ve taşınabilir exe
npm start        # geliştirme için çalıştır
```

## Teknik

- Electron tabanlı; ek çalışma-zamanı bağımlılığı yok.
- Dışarı sürükleme Electron `webContents.startDrag`, içeri bırakmada gerçek yol `webUtils.getPathForFile` ile alınır.
- İkon: `build/icon.png` (raf/kart yığını teması).

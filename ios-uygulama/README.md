# Rea iOS Uygulaması

Mac olmadan iPhone uygulaması (.ipa) üretmek için şablon.

- Uygulamanın arayüzü `www/` klasöründedir (HTML/CSS/JS). Rea'ya "ios-uygulama/www içine şunu yap" demek yeterli.
- Uygulama adı ve kimliği `capacitor.config.json` içindedir.
- Değişiklik depoya gönderildiğinde GitHub Actions bulutta Mac üzerinde derler ve imzasız `ReaUygulama.ipa` üretir
  (Actions > "iOS .ipa derle" > son çalışma > Artifacts).
- İndirilen `.ipa`, iPhone'daki SideStore ile kurulur (SideStore ilk sefer Windows'ta iloader ile kurulur). Alternatif: Sideloadly ile USB.

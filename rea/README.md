# Rea

Rea, `rea-agents` npm paketi olarak kurulur. Bu klasörde paketin kendisi değil,
başka bir bilgisayarda aynı kurulumu tek komutla yapmayı sağlayan betik bulunur.

- Paket: `rea-agents`
- Sürüm: `6.7.0`
- Çalışma klasörü: `%USERPROFILE%\REA`

## Kurulum

Depoyu indirdikten sonra PowerShell'de depo klasöründe:

```powershell
powershell -ExecutionPolicy Bypass -File .\rea\kurulum.ps1
```

Betik paketi kurar, Rea'yı Claude Code ve Claude masaüstüne yerel MCP olarak
kaydeder ve `rea mcp doctor` ile doğrular. Ardından Claude uygulamalarını
yeniden başlatın.

Farklı bir sürüm için: `.\rea\kurulum.ps1 -Surum 6.7.0`

Ön koşul: Node.js (LTS). Kurum ağında npm için proxy ayarı gerekebilir.

## Bu projede (bulut oturumları)

Depo kökündeki `.mcp.json`, bu depoyla açılan Claude Code oturumlarına Rea'yı
`npx -y rea-agents@6.7.0 mcp` ile otomatik ekler. Bulutta yalnızca Rea'nın
yerleşik statik araçları çalışır (exe/PE, .NET, JavaScript/Electron, SQLite);
Ghidra/IDA gerekmez. Bilgisayardaki dosyalar için Uzaktan Kontrol kullanılır.

Rea'nın `--http` modu şifresiz ve kimlik doğrulamasızdır; internete açılmaz.

## Not

API anahtarı, token veya parola gibi gizli bilgiler bu depoya konmaz.

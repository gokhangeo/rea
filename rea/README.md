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

Farklı bir sürüm için: `.\rea\kurulum.ps1 -Surum 6.7.0`

Ön koşul: Node.js (LTS). Kurum ağında npm için proxy ayarı gerekebilir.

## Not

API anahtarı, token veya parola gibi gizli bilgiler bu depoya konmaz.

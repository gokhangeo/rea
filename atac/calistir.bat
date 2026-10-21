@echo off
chcp 65001 >nul
title Atac
cd /d "%~dp0"

echo.
echo   ====== Atac - Evde Calistir ======
echo.

where node >nul 2>nul
if errorlevel 1 (
  echo   Node.js kurulu degil.
  echo   https://nodejs.org adresinden "LTS" surumunu kurun,
  echo   sonra bu dosyayi tekrar calistirin.
  echo.
  pause
  exit /b 1
)

REM --- SSL denetimine (SELF_SIGNED_CERT_IN_CHAIN) karsi guvenli cozum ---
REM Bu bilgisayarin ZATEN guvendigi kok sertifikalari bir dosyaya cikarip Node'a tanitiriz.
REM Bu, guvenligi KAPATMAZ; sadece sistemin guvendigi sertifikalara guvenir. Her Node surumunde calisir.
if not exist "%~dp0win-ca.pem" (
  echo   Sistemdeki guvenilen sertifikalar hazirlaniyor...
  powershell -NoProfile -ExecutionPolicy Bypass -Command "$ErrorActionPreference='SilentlyContinue'; $c=Get-ChildItem Cert:\LocalMachine\Root,Cert:\CurrentUser\Root; $o=foreach($x in $c){'-----BEGIN CERTIFICATE-----';[Convert]::ToBase64String($x.RawData,'InsertLineBreaks');'-----END CERTIFICATE-----'}; Set-Content -LiteralPath '%~dp0win-ca.pem' -Value $o -Encoding ascii"
)
if exist "%~dp0win-ca.pem" set "NODE_EXTRA_CA_CERTS=%~dp0win-ca.pem"

REM Node 22+ ise sistem deposunu da dogrudan kullan (eski Node tanimazsa otomatik temizlenir)
set "NODE_OPTIONS=--use-system-ca"
node -e "process.exit(0)" >nul 2>nul
if errorlevel 1 set "NODE_OPTIONS="

REM GitHub'dan en son surumu denetle ve guncelle (internet varsa)
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0guncelle.ps1"

if not exist "node_modules" (
  echo   Ilk calistirma: gerekli dosyalar indiriliyor...
  echo   Bu islem birkac dakika surebilir, lutfen bekleyin.
  echo.
  call npm install
  if errorlevel 1 (
    echo.
    echo   Kurulum tamamlanamadi.
    echo   Sertifika hatasi hala suruyorsa, antivirusunuzun
    echo   "HTTPS taramasi / SSL taramasi" ayarini gecici kapatip tekrar deneyin,
    echo   kurulum bitince geri acabilirsiniz.
    pause
    exit /b 1
  )
)

echo.
echo   Atac baslatiliyor...
call npm start

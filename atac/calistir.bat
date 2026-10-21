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

REM --- SSL denetimine (SELF_SIGNED_CERT) karsi: sistem koklerini Node'a tanit ---
if not exist "%~dp0win-ca.pem" (
  echo   Sistemdeki guvenilen sertifikalar hazirlaniyor...
  powershell -NoProfile -ExecutionPolicy Bypass -Command "$ErrorActionPreference='SilentlyContinue'; $c=Get-ChildItem Cert:\LocalMachine\Root,Cert:\CurrentUser\Root; $o=foreach($x in $c){'-----BEGIN CERTIFICATE-----';[Convert]::ToBase64String($x.RawData,'InsertLineBreaks');'-----END CERTIFICATE-----'}; Set-Content -LiteralPath '%~dp0win-ca.pem' -Value $o -Encoding ascii"
)
if exist "%~dp0win-ca.pem" set "NODE_EXTRA_CA_CERTS=%~dp0win-ca.pem"
set "NODE_OPTIONS=--use-system-ca"
node -e "process.exit(0)" >nul 2>nul
if errorlevel 1 set "NODE_OPTIONS="

REM --- Baglanti koparsa (ECONNRESET) daha dayanikli olsun ---
set "npm_config_fetch_retries=5"
set "npm_config_fetch_retry_mintimeout=20000"
set "npm_config_fetch_retry_maxtimeout=180000"
set "npm_config_fetch_timeout=600000"

REM GitHub'dan en son surumu denetle ve guncelle (internet varsa)
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0guncelle.ps1"

if exist "node_modules" goto baslat

echo   Ilk calistirma: gerekli dosyalar indiriliyor...
echo   Bu islem birkac dakika surebilir, lutfen bekleyin.
echo.
set "DENEME=0"
:kur
set /a DENEME+=1
call npm install
if not errorlevel 1 goto baslat
if %DENEME% lss 3 (
  echo.
  echo   Baglanti koptu, tekrar deneniyor... (%DENEME%/3)
  timeout /t 4 >nul
  goto kur
)
echo.
echo   Kurulum tamamlanamadi (baglanti tekrar tekrar kesildi).
echo   Oneriler:
echo     1) Antivirusun "HTTPS taramasi / SSL taramasi" ayarini gecici kapatip tekrar dene.
echo     2) Birkac dakika sonra internet kararliyken calistir.bat'i yeniden calistir.
echo     3) En dertsizi: surum sayfasindan tasinabilir "atac-0.2.3.exe" indirip cift tikla
echo        (npm/indirme gerekmez, tek dosya calisir).
echo.
pause
exit /b 1

:baslat
echo.
echo   Atac baslatiliyor...
call npm start

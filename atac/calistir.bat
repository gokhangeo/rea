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

REM Agda SSL denetimi varsa (SELF_SIGNED_CERT_IN_CHAIN), bu bilgisayarin zaten
REM guvendigi sertifikalari kullan. Guvenligi KAPATMAZ; sadece sistem deposuna guvenir.
REM Eski Node bu secenegi tanimazsa otomatik temizlenir.
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
    echo   Internet baglantinizi kontrol edin. Sertifika hatasi devam ederse,
    echo   antivirusunuzun "HTTPS taramasi / SSL taramasi" ayarini gecici kapatip
    echo   tekrar deneyin ya da OKU-BENI.txt dosyasindaki adimlari izleyin.
    pause
    exit /b 1
  )
)

echo.
echo   Atac baslatiliyor...
call npm start

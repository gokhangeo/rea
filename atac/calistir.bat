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

if not exist "node_modules" (
  echo   Ilk calistirma: gerekli dosyalar indiriliyor...
  echo   Bu islem birkac dakika surebilir, lutfen bekleyin.
  echo.
  call npm install
  if errorlevel 1 (
    echo.
    echo   Kurulum sirasinda hata olustu. Internet baglantinizi kontrol edin.
    pause
    exit /b 1
  )
)

echo.
echo   Atac baslatiliyor...
call npm start

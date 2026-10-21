@echo off
chcp 65001 >nul
title Atac - Baslangictan Cikar

set "VBS=%APPDATA%\Microsoft\Windows\Start Menu\Programs\Startup\Atac.vbs"

echo.
if exist "%VBS%" (
  del "%VBS%"
  echo   Atac, Windows acilisindan cikarildi.
) else (
  echo   Atac zaten baslangicta degil.
)
echo.
pause

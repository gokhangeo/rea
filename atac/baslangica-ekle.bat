@echo off
chcp 65001 >nul
title Atac - Baslangica Ekle
cd /d "%~dp0"

echo.
echo   ====== Atac - Windows Acilisinda Otomatik Baslat ======
echo.

if not exist "node_modules" (
  echo   Once Atac'in bir kez calismasi gerekiyor.
  echo   Lutfen ONCE "calistir.bat" dosyasina cift tiklayip
  echo   kurulumun tamamlanmasini bekleyin, sonra bu dosyayi tekrar calistirin.
  echo.
  pause
  exit /b 1
)

set "HEDEF=%~dp0calistir.bat"
set "STARTUP=%APPDATA%\Microsoft\Windows\Start Menu\Programs\Startup"
set "VBS=%STARTUP%\Atac.vbs"

> "%VBS%" echo Set s = CreateObject("WScript.Shell")
>> "%VBS%" echo s.Run "cmd /c ""%HEDEF%""", 0, False

echo   Tamamdir! Atac artik her Windows acilisinda sessizce baslayacak.
echo.
echo   Geri almak istersen "baslangictan-cikar.bat" dosyasini calistir.
echo.
pause

@echo off
chcp 65001 >nul
rem Süpürge haritasını Dreamehome hesabından çeker; çıktı cikti\harita.json ve cikti\harita.png
cd /d "%~dp0"
set PY=py -3
%PY% --version >nul 2>nul || set PY=python
%PY% --version >nul 2>nul || (
  echo Python bulunamadi. Kurmak icin: winget install -e --id Python.Python.3.12
  pause
  exit /b 1
)
echo harita-cek.py indiriliyor...
powershell -NoProfile -Command "Invoke-WebRequest -UseBasicParsing -OutFile harita-cek.py https://raw.githubusercontent.com/gokhangeo/rea/main/supurge-3d/harita-cek.py"
echo Gerekli paketler kuruluyor, birkac dakika surebilir...
%PY% -m pip install --quiet --disable-pip-version-check --only-binary=:all: numpy pillow pycryptodome mini-racer paho-mqtt cryptography requests
%PY% harita-cek.py
if exist cikti\harita.json explorer cikti
pause

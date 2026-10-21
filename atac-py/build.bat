@echo off
rem Atac.exe'yi tek dosya olarak derler. Cikti: dist\Atac.exe
rem Not: Ara dosyalar kisa bir yola yazilir; uzun yollarda PyInstaller ikon eklerken hata veriyor.
setlocal
cd /d "%~dp0"
set WORK=%TEMP%\atac-build
rem Surum: ATAC_VERSION (ornek "0.1" ya da etiket "atac-py-v0.1"); yoksa "dev".
rem "dev" derlemeler otomatik guncelleme yapmaz.
set VER=%ATAC_VERSION%
if "%VER%"=="" set VER=dev
set VER=%VER:atac-py-v=%
if not exist "%WORK%" mkdir "%WORK%"
> "%WORK%\version.txt" echo %VER%
echo Surum: %VER%
python -m pip install -r requirements.txt || goto :hata
python -c "from PIL import Image; im=Image.open('assets/atac-clip.png').convert('RGBA'); w,h=im.size; s=max(w,h); c=Image.new('RGBA',(s,s),(0,0,0,0)); c.paste(im,((s-w)//2,(s-h)//2),im); c.save(r'%WORK%-icon.ico', sizes=[(16,16),(32,32),(48,48),(256,256)])" || goto :hata
python -m PyInstaller --noconfirm --onefile --windowed --name Atac ^
  --icon "%WORK%-icon.ico" --add-data "assets;assets" --add-data "%WORK%\version.txt;." ^
  --hidden-import pystray._win32 ^
  --distpath dist --workpath "%WORK%" --specpath "%WORK%" atac.py || goto :hata
echo.
echo Hazir: %~dp0dist\Atac.exe
exit /b 0
:hata
echo Derleme basarisiz.
exit /b 1

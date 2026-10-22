@echo off
chcp 65001 >nul
rem Atac.exe'yi Nuitka ile tek dosya olarak derler (CI ile ayni yontem).
rem Nuitka Python'u gercek C'ye derler; PyInstaller bootloader'i olmadigi icin
rem antivirus yanlis-pozitifi azalir. Cikti: dist\Atac.exe
setlocal
cd /d "%~dp0"
set VER=%ATAC_VERSION%
if "%VER%"=="" set VER=dev
set VER=%VER:atac-py-v=%
> version.txt echo %VER%
echo Surum: %VER%
python -m pip install -r requirements.txt || goto :hata
python -m pip install nuitka || goto :hata
python -c "from PIL import Image; im=Image.open('assets/atac-clip.png').convert('RGBA'); w,h=im.size; s=max(w,h); c=Image.new('RGBA',(s,s),(0,0,0,0)); c.paste(im,((s-w)//2,(s-h)//2),im); c.save('icon.ico', sizes=[(16,16),(32,32),(48,48),(256,256)])" || goto :hata
python -m nuitka --onefile --windows-console-mode=disable --assume-yes-for-downloads ^
  --windows-icon-from-ico=icon.ico ^
  --company-name=gokhangeo --product-name=Atac ^
  --file-description="Atac - yuzen dosya rafi" --copyright="(c) gokhangeo" ^
  --include-data-dir=assets=assets ^
  --include-data-files=version.txt=version.txt ^
  --include-module=pystray._win32 ^
  --output-dir=dist --output-filename=Atac.exe --remove-output atac.py || goto :hata
echo.
echo Hazir: %~dp0dist\Atac.exe
exit /b 0
:hata
echo Derleme basarisiz.
exit /b 1

@echo off
rem Kutu Kurucu'yu ADB ile kurar. ADB yoksa Google'dan (platform-tools) indirip bu klasore acar.
rem Imza cakismasinda eski surumu KENDISI kaldirip yeniden kurar. Kurulum yardimcisini da kurar.
rem Kullanim: cift tiklayin. En yeni Kutu Kurucu, Kurulum yardimcisi ve Ev Ekrani GitHub'dan indirilir,
rem iki kutuya da kurulur. Bir APK'yi elle vermek icin dosyayi bunun uzerine surukleyin (o zaman indirme yapilmaz).
setlocal EnableDelayedExpansion
cd /d "%~dp0"

set ADB=adb
where adb >nul 2>&1
if errorlevel 1 (
  if not exist "%~dp0platform-tools\adb.exe" (
    echo ADB bulunamadi, Google'dan indiriliyor...
    powershell -NoProfile -ExecutionPolicy Bypass -Command "$ProgressPreference='SilentlyContinue'; Invoke-WebRequest 'https://dl.google.com/android/repository/platform-tools-latest-windows.zip' -OutFile 'platform-tools.zip'; Expand-Archive -Force 'platform-tools.zip' '.'; Remove-Item 'platform-tools.zip'"
  )
  set ADB="%~dp0platform-tools\adb.exe"
)
if not exist "%~dp0platform-tools\adb.exe" ( where adb >nul 2>&1 || ( echo ADB indirilemedi. & pause & exit /b 1 ) )

set APK=%~1
set EV=
if "%APK%"=="" (
  echo En yeni surumler GitHub'dan indiriliyor...
  del /q KutuKurucu-1.*.apk KutuKurucu-Kurulum.apk EvEkrani-*.apk >nul 2>&1
  powershell -NoProfile -ExecutionPolicy Bypass -Command "$ProgressPreference='SilentlyContinue'; [Net.ServicePointManager]::SecurityProtocol='Tls12'; $r=Invoke-RestMethod 'https://api.github.com/repos/gokhangeo/rea/releases?per_page=60' -Headers @{'User-Agent'='kutu-kur'}; function Son($on){ $r | Where-Object { $_.tag_name -match ('^'+$on+'(\d+)$') -and -not $_.draft } | Sort-Object { [int]($_.tag_name -replace '.*\.','') } | Select-Object -Last 1 }; $k=Son 'kutu-kurucu-v1\.'; $e=Son 'ev-ekrani-v0\.2\.'; foreach($a in @($k.assets)+@($e.assets)){ if($a.name -like '*.apk'){ Write-Host ('  '+$a.name); Invoke-WebRequest $a.browser_download_url -OutFile $a.name -Headers @{'User-Agent'='kutu-kur'} } }"
  for %%F in (KutuKurucu-1.*.apk) do set APK=%%F
  for %%F in (EvEkrani-*.apk) do set EV=%%F
)
if "%APK%"=="" ( echo Kutu Kurucu APK indirilemedi. Internet baglantisini kontrol edin. & pause & exit /b 1 )
set YARDIMCI=
if exist "KutuKurucu-Kurulum.apk" set YARDIMCI=KutuKurucu-Kurulum.apk
echo Kurulacak: %APK% %YARDIMCI% %EV%

for %%K in (192.168.1.106 192.168.1.174) do call :kur %%K
echo.
echo Bitti. TV'de Kutu Kurucu acilir; adres ve kod ekranda yazar.
pause
exit /b 0

:kur
set K=%1:5555
echo.
echo === %1 ===
%ADB% connect %K% | findstr /i "connected" >nul
if errorlevel 1 (
  echo %1 baglanamadi. Kutuda Gelistirici secenekleri ^> USB/Ag hata ayiklama acik mi? TV'de "izin ver" sorusu ciktiysa onaylayip tekrar calistirin.
  exit /b 0
)
%ADB% -s %K% install -r "%APK%" > kur.log 2>&1
type kur.log
findstr /i "Success" kur.log >nul
if errorlevel 1 (
  echo Eski surum farkli anahtarla imzali, kaldirilip yeniden kuruluyor...
  %ADB% -s %K% uninstall com.gokhan.kutukurucu
  %ADB% -s %K% install "%APK%"
)
del kur.log >nul 2>&1
if defined YARDIMCI (
  %ADB% -s %K% install -r "%YARDIMCI%" || ( %ADB% -s %K% uninstall com.gokhan.kutukurucu.kurulum & %ADB% -s %K% install "%YARDIMCI%" )
  %ADB% -s %K% shell appops set com.gokhan.kutukurucu.kurulum REQUEST_INSTALL_PACKAGES allow
)
if defined EV (
  echo Ev Ekrani: %EV%
  %ADB% -s %K% install -r "%EV%"
)
%ADB% -s %K% shell pm grant com.gokhan.kutukurucu android.permission.WRITE_SECURE_SETTINGS
%ADB% -s %K% shell appops set com.gokhan.kutukurucu SYSTEM_ALERT_WINDOW allow
%ADB% -s %K% shell appops set com.gokhan.kutukurucu REQUEST_INSTALL_PACKAGES allow
%ADB% -s %K% shell am start -n com.gokhan.kutukurucu/.MainActivity
exit /b 0

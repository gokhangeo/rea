@echo off
rem Kutu Kurucu'yu ADB ile bir kez kurar ve izinlerini verir. Sonra ADB kapatilabilir.
rem Kullanim: KutuKurucu-1.N.apk dosyasini bu dosyanin uzerine surukleyin.
rem Kutu adresleri asagida; gerekirse degistirin.
setlocal
set APK=%~1
if "%APK%"=="" (
  echo KutuKurucu APK dosyasini bu dosyanin uzerine surukleyin.
  pause
  exit /b 1
)
for %%K in (192.168.1.106 192.168.1.174) do call :kur %%K
echo.
echo Bitti. TV'de Kutu Kurucu'yu acin; adres ve kod ekranda yazar.
pause
exit /b 0

:kur
echo.
echo === %1 ===
adb connect %1:5555
adb -s %1:5555 install -r "%APK%"
adb -s %1:5555 shell pm grant com.gokhan.kutukurucu android.permission.WRITE_SECURE_SETTINGS
adb -s %1:5555 shell appops set com.gokhan.kutukurucu SYSTEM_ALERT_WINDOW allow
adb -s %1:5555 shell appops set com.gokhan.kutukurucu REQUEST_INSTALL_PACKAGES allow
adb -s %1:5555 shell am start -n com.gokhan.kutukurucu/.MainActivity
exit /b 0

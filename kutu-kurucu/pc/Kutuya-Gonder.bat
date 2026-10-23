@echo off
rem APK dosyalarini bu dosyanin uzerine surukleyip birakin; TV kutularina gonderilir.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0kutuya-gonder.ps1" %*

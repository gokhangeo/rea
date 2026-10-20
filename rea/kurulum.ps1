<#
  Rea kurulum betiği
  Başka bir Windows bilgisayarda Rea'yı (rea-agents) aynı sürümle kurar.
  Kullanım (PowerShell):
    powershell -ExecutionPolicy Bypass -File .\rea\kurulum.ps1
#>
param(
  [string]$Surum = "6.7.0"
)

$ErrorActionPreference = "Stop"

Write-Host "Rea kurulumu başlıyor (rea-agents@$Surum)..."

# Node.js ve npm kontrolü
$node = Get-Command node -ErrorAction SilentlyContinue
$npm = Get-Command npm -ErrorAction SilentlyContinue
if (-not $node -or -not $npm) {
  Write-Host "Node.js bulunamadı. Lütfen https://nodejs.org adresinden LTS sürümünü kurup betiği yeniden çalıştırın." -ForegroundColor Red
  exit 1
}
Write-Host "Node.js sürümü: $(node --version)"

# Kurulu sürüm kontrolü
$kurulu = npm ls -g rea-agents --depth=0 2>$null | Select-String "rea-agents@"
if ($kurulu -and $kurulu.ToString() -match "rea-agents@$([regex]::Escape($Surum))") {
  Write-Host "rea-agents@$Surum zaten kurulu." -ForegroundColor Green
} else {
  npm install -g "rea-agents@$Surum"
  if ($LASTEXITCODE -ne 0) {
    Write-Host "Kurulum başarısız oldu. Kurum ağında npm için proxy ayarı gerekebilir: npm config set proxy http://<proxy>:<port>" -ForegroundColor Red
    exit $LASTEXITCODE
  }
}

# Çalışma klasörü
$calisma = Join-Path $env:USERPROFILE "REA"
if (-not (Test-Path $calisma)) {
  New-Item -ItemType Directory -Path $calisma | Out-Null
  Write-Host "Çalışma klasörü oluşturuldu: $calisma"
}

npm ls -g rea-agents --depth=0

# Claude Code ve Claude masaüstüne yerel (stdio) MCP olarak kaydet
# Açık Claude masaüstü ayar dosyasını yeniden yazabileceği için önce tamamen kapatılmalı
Read-Host "Claude masaüstünü tamamen kapatın (sistem tepsisinden de), sonra Enter tuşuna basın"
Write-Host "Rea, Claude Code ve Claude masaüstüne kaydediliyor..."
rea setup --client claude_code --client claude_desktop --yes
if ($LASTEXITCODE -ne 0) {
  Write-Host "Kayıt başarısız oldu. Elle denemek için: rea setup" -ForegroundColor Red
  exit $LASTEXITCODE
}

# Doğrulama
rea mcp doctor
if ($LASTEXITCODE -ne 0) {
  Write-Host "Rea doğrulaması başarısız oldu." -ForegroundColor Red
  exit $LASTEXITCODE
}

Write-Host "Rea kurulumu tamamlandı. Claude Code ve Claude masaüstünü tamamen kapatıp yeniden açın." -ForegroundColor Green

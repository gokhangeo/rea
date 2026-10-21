# Atac - GitHub'dan otomatik guncelleme (Node/Git gerekmez, yalnizca PowerShell)
# calistir.bat her acilista bunu cagirir. Internet yoksa sessizce gecer.
$ErrorActionPreference = 'Stop'
$kok = Split-Path -Parent $MyInvocation.MyCommand.Path

try {
    [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

    $uzakStr = (Invoke-WebRequest -UseBasicParsing -TimeoutSec 15 `
        -Uri 'https://raw.githubusercontent.com/gokhangeo/rea/main/atac/package.json').Content
    $uzak  = ($uzakStr | ConvertFrom-Json).version
    $yerel = (Get-Content (Join-Path $kok 'package.json') -Raw | ConvertFrom-Json).version

    if ($uzak -eq $yerel) {
        Write-Host "  Atac guncel (surum $yerel)."
        exit 0
    }

    Write-Host "  Yeni surum bulundu: $yerel -> $uzak. Indiriliyor..."
    $tmp = Join-Path $env:TEMP ("atac_upd_" + [Guid]::NewGuid().ToString('N'))
    New-Item -ItemType Directory -Path $tmp | Out-Null
    $zip = Join-Path $tmp 'src.zip'

    Invoke-WebRequest -UseBasicParsing -TimeoutSec 180 `
        -Uri 'https://codeload.github.com/gokhangeo/rea/zip/refs/heads/main' -OutFile $zip
    Expand-Archive -Path $zip -DestinationPath $tmp -Force

    $kaynak = Join-Path $tmp 'rea-main\atac'
    if (-not (Test-Path $kaynak)) { throw "Indirilen pakette atac klasoru bulunamadi." }

    # Uygulama dosyalarini uzerine yaz. node_modules/dist haric; calisan .bat ve bu .ps1
    # dosyasi kendini ezmesin diye disarida tutulur (bir sonraki surumde elle yenilenir).
    Get-ChildItem -Path $kaynak -Recurse -File | ForEach-Object {
        $rel = $_.FullName.Substring($kaynak.Length).TrimStart('\')
        if ($rel -like 'node_modules\*' -or $rel -like 'dist\*' -or `
            $rel -like '*.bat' -or $rel -eq 'guncelle.ps1') { return }
        $hedef    = Join-Path $kok $rel
        $hedefDir = Split-Path -Parent $hedef
        if (-not (Test-Path $hedefDir)) { New-Item -ItemType Directory -Path $hedefDir -Force | Out-Null }
        Copy-Item -Path $_.FullName -Destination $hedef -Force
    }

    Remove-Item -Path $tmp -Recurse -Force -ErrorAction SilentlyContinue

    Write-Host "  Guncelleme tamam (surum $uzak). Bagimliliklar denetleniyor..."
    Push-Location $kok
    & npm install --no-audit --no-fund
    Pop-Location
}
catch {
    Write-Host "  Guncelleme denetlenemedi (cevrimdisi olabilirsin); mevcut surumle devam."
}

# Kutu Kurucu: APK dosyalarını evdeki TV kutularına gönderir. Kurulum TV'de kumandayla onaylanır.
# Kullanım: APK'ları Kutuya-Gonder.bat üzerine sürükleyip bırakın.
param([Parameter(ValueFromRemainingArguments = $true)][string[]]$Dosyalar)

$ErrorActionPreference = 'Stop'
$kutular = [ordered]@{ '1' = @{ Ad = 'Mi Box'; Adres = '192.168.1.106' }; '2' = @{ Ad = 'TV+ kutusu'; Adres = '192.168.1.174' } }
$kodDosyasi = Join-Path $env:LOCALAPPDATA 'KutuKurucu\kodlar.txt'
$kodlar = @{}
if (Test-Path $kodDosyasi) { Get-Content $kodDosyasi | ForEach-Object { $p = $_ -split '=', 2; if ($p.Count -eq 2) { $kodlar[$p[0]] = $p[1] } } }

$Dosyalar = @($Dosyalar | Where-Object { $_ -and (Test-Path $_) -and $_ -match '\.apk$' })
if (-not $Dosyalar) { Write-Host 'APK dosyası verilmedi. APK dosyalarını Kutuya-Gonder.bat üzerine sürükleyin.'; Read-Host 'Kapatmak için Enter'; exit 1 }

Write-Host 'Hangi kutuya gönderilsin?'
foreach ($k in $kutular.Keys) { Write-Host "  $k) $($kutular[$k].Ad) ($($kutular[$k].Adres))" }
Write-Host '  3) İkisine de'
$secim = Read-Host 'Seçim (1/2/3)'
$hedefler = switch ($secim) { '1' { @('1') } '2' { @('2') } default { @('1', '2') } }

foreach ($h in $hedefler) {
    $kutu = $kutular[$h]
    $adres = $kutu.Adres
    $kod = $kodlar[$adres]
    if (-not $kod) {
        $kod = Read-Host "$($kutu.Ad) ekranındaki 4 haneli kod (TV'de Kutu Kurucu'yu açın)"
    }
    foreach ($d in $Dosyalar) {
        $ad = [uri]::EscapeDataString((Split-Path $d -Leaf))
        Write-Host "$($kutu.Ad) <- $(Split-Path $d -Leaf) gönderiliyor..."
        $cevap = & curl.exe -s -w "`n%{http_code}" -X POST -H "x-kod: $kod" -H 'Content-Type: application/vnd.android.package-archive' `
            --data-binary "@$d" "http://${adres}:8090/api/kur?ad=$ad"
        $satirlar = $cevap -split "`n"
        $durum = $satirlar[-1]
        if ($durum -eq '200') {
            Write-Host "  Gönderildi. $($kutu.Ad) ekranında 'Yükle'ye basın." -ForegroundColor Green
            $kodlar[$adres] = $kod
        } elseif ($durum -eq '401') {
            Write-Host '  Kod yanlış. Bir dahaki sefere yeniden sorulacak.' -ForegroundColor Red
            $kodlar.Remove($adres)
            break
        } else {
            Write-Host "  Gönderilemedi ($durum): $($satirlar[0..($satirlar.Count - 2)] -join ' ')" -ForegroundColor Red
            Write-Host '  Kutu açık mı, Kutu Kurucu kurulu mu?'
        }
    }
}
New-Item -ItemType Directory -Force -Path (Split-Path $kodDosyasi) | Out-Null
$kodlar.GetEnumerator() | ForEach-Object { "$($_.Key)=$($_.Value)" } | Set-Content $kodDosyasi
Read-Host 'Bitti. Kapatmak için Enter'

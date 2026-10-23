"""Dreamehome hesabından robot süpürgenin kayıtlı ev haritasını çeker.

Çıktı (bu dosyanın yanındaki cikti/ klasörüne):
  harita.json  -> 3D aracının (index.html) okuduğu ham harita: hücre türleri, oda adları, hücre boyu
  harita.png   -> kontrol için renkli 2D önizleme

Şifre hiçbir yere kaydedilmez; yalnızca bu oturumda Dreame bulutuna giriş için kullanılır.
Haritayı çözmek için Tasshack/dreame-vacuum (MIT) projesinin "dreame" modülü
sabit bir sürümden (v2.0.2) indirilip kullanılır.
"""
from __future__ import annotations

import base64
import getpass
import io
import json
import os
import sys
import time
import urllib.request
import zipfile
import zlib

KLASOR = os.path.dirname(os.path.abspath(__file__))
ONBELLEK = os.path.join(KLASOR, ".dreame-modul")
CIKTI = os.path.join(KLASOR, "cikti")
SURUM = "v2.0.2"
ZIP_URL = f"https://github.com/Tasshack/dreame-vacuum/archive/refs/tags/{SURUM}.zip"
MODEL_ANAHTARI = "r2363a"  # Müdürüm'ün süpürgesi; bulunamazsa hesaptaki ilk süpürge alınır


def modulu_hazirla() -> None:
    hedef = os.path.join(ONBELLEK, SURUM)
    paket = os.path.join(hedef, "custom_components", "dreame_vacuum")
    if not os.path.isdir(os.path.join(paket, "dreame")):
        print(f"Harita çözücü indiriliyor ({SURUM})...")
        veri = urllib.request.urlopen(ZIP_URL, timeout=60).read()
        with zipfile.ZipFile(io.BytesIO(veri)) as z:
            on_ek = z.namelist()[0].split("/")[0] + "/"
            for ad in z.namelist():
                if not ad.startswith(on_ek + "custom_components/dreame_vacuum/dreame/") or ad.endswith("/"):
                    continue
                yol = os.path.join(hedef, *ad[len(on_ek):].split("/"))
                os.makedirs(os.path.dirname(yol), exist_ok=True)
                with open(yol, "wb") as f:
                    f.write(z.read(ad))
    # dreame_vacuum/__init__.py Home Assistant ister; bu yüzden yalnızca alt "dreame" paketi yüklenir
    sys.path.insert(0, paket)


def modeller(device_info_b64: str) -> dict:
    bilgi = json.loads(zlib.decompress(base64.b64decode(device_info_b64), zlib.MAX_WBITS | 32))
    sonuc = {}
    for k in bilgi[3]:
        i = bilgi[0][bilgi[3][k]]
        if i:
            tur = "xiaomi" if i[0] == 1 else "mova" if i[0] == 2 else "trouver" if i[0] == 3 else "dreame"
            sonuc[f"{tur}.vacuum.{k}"] = i[1]
    return sonuc


def main() -> int:
    modulu_hazirla()
    from dreame import DEVICE_INFO, DreameVacuumDevice, DreameVacuumProtocol  # noqa: E402

    print("\nDreamehome hesabınızla giriş (şifre ekranda görünmez, kaydedilmez).")
    kullanici = input("E-posta veya telefon: ").strip()
    sifre = getpass.getpass("Şifre: ")
    bolge = (input("Sunucu bölgesi [eu]: ").strip() or "eu").lower()

    protokol = DreameVacuumProtocol(
        username=kullanici, password=sifre, country=bolge, prefer_cloud=True, account_type="dreame"
    )
    protokol.cloud.login()
    if not protokol.cloud.logged_in:
        print("Giriş başarısız. E-posta/şifre ya da bölgeyi kontrol edin.")
        return 1

    cihazlar, desteksiz = protokol.cloud.get_supported_devices(modeller(DEVICE_INFO))
    if not cihazlar:
        print("Hesapta desteklenen süpürge bulunamadı.", [d.get("model") for d in desteksiz])
        return 1
    cihaz = next((d for d in cihazlar if MODEL_ANAHTARI in d["model"]), cihazlar[0])
    print(f"Süpürge: {cihaz['name']} ({cihaz['model']})")

    supurge = DreameVacuumDevice(
        cihaz["name"],
        cihaz["bindDomain"],
        " ",
        cihaz["mac"],
        kullanici,
        sifre,
        bolge,
        True,
        "dreame",
        cihaz["did"],
        protokol.cloud.auth_key,
    )
    protokol.disconnect()
    try:
        print("Süpürgeye bağlanılıyor ve harita bekleniyor (en çok 2 dakika)...")
        harita = None
        bitis = time.time() + 120
        while time.time() < bitis:
            try:
                supurge.update()
            except Exception as hata:  # bağlantı ilk denemede kurulamayabilir
                print("  bekleniyor:", hata)
            harita = supurge.get_map(0) or supurge.get_map(1)
            if harita is not None and harita.pixel_type is not None and not harita.empty_map:
                break
            supurge.update_map()
            time.sleep(5)
        if harita is None or harita.pixel_type is None:
            print("Harita alınamadı. Dreamehome uygulamasında haritanın kayıtlı olduğundan emin olun.")
            return 1
        kaydet(harita)
    finally:
        supurge.disconnect()
    return 0


def kaydet(harita) -> None:
    import numpy as np
    from PIL import Image

    piksel = np.asarray(harita.pixel_type, dtype=np.uint8).T  # [x, y] -> satır = y
    yukseklik, genislik = piksel.shape
    odalar = {}
    for sid, oda in (harita.segments or {}).items():
        odalar[str(sid)] = {"ad": oda.custom_name or oda.name or f"Oda {sid}"}

    os.makedirs(CIKTI, exist_ok=True)
    veri = {
        "kaynak": "dreamehome",
        "genislik": int(genislik),
        "yukseklik": int(yukseklik),
        "hucre_mm": int(harita.dimensions.grid_size),
        "pikseller": base64.b64encode(piksel.tobytes()).decode(),
        "odalar": odalar,
    }
    with open(os.path.join(CIKTI, "harita.json"), "w", encoding="utf-8") as f:
        json.dump(veri, f, ensure_ascii=False)

    renkler = np.zeros((256, 3), np.uint8)
    renkler[:] = (200, 200, 200)
    renkler[0] = (255, 255, 255)
    renkler[255] = renkler[251] = (60, 64, 72)
    paleti = [(156, 199, 240), (246, 196, 143), (181, 227, 168), (233, 179, 214), (242, 227, 138), (180, 170, 240)]
    for i in range(1, 64):
        for taban in (0, 100, 200):
            if taban + i < 250:
                renkler[taban + i] = paleti[i % len(paleti)]
    Image.fromarray(renkler[piksel]).save(os.path.join(CIKTI, "harita.png"))
    print(f"\nHazır: {genislik}x{yukseklik} hücre, hücre {veri['hucre_mm']} mm, {len(odalar)} oda.")
    print("Dosyalar:", CIKTI)


if __name__ == "__main__":
    sys.exit(main())

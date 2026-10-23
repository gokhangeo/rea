# Notebook Asistan

Gemini Notebook (NotebookLM) defterlerine **tarayıcı açmadan** soru soran Windows masaüstü uygulaması.

- Açılınca otomatik bağlanır; oturum yoksa/süresi dolduysa Chrome'da Google girişini kendisi açar.
- Defter listesi, arama, öneri kartları, kaynak atıfları (üzerine gelince kaynak metni), takip soruları, kopyalama, koyu/açık tema.
- **Otomatik güncelleme:** açılışta `notebook-v*` etiketli GitHub sürümlerini denetler, yeni sürüm varsa sağ altta kart gösterir; "Şimdi güncelle" ile indirip kendini yeniler.

## Yeni sürüm yayınlamak

```bash
git tag notebook-v1.1 && git push origin notebook-v1.1
# veya
gh workflow run notebook-asistan.yml -f surum=1.1 -f notlar="Yenilikler…"
```

## Yerelde çalıştırma

```bash
pip install -r requirements.txt
python app.py
```

Veriler: `%USERPROFILE%\.notebooklm\` (oturum, ayarlar, günlük).
Not: `notebooklm-py` resmi olmayan bir API'dir; Google değişiklik yaparsa güncelleme gerekebilir.

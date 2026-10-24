package com.gokhan.kutukurucu

import android.content.Context
import android.os.Build
import android.util.Log
import dadb.AdbKeyPair
import dadb.Dadb
import dadb.InstallResult
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Sessiz kurulum: Kutu Kurucu kutunun kendi ADB'sine (127.0.0.1:5555, ağ hata ayıklaması açıkken) bağlanır ve APK'yı
 * "pm install" ile kurar; Android'in "Yükle" penceresi çıkmaz. İlk bağlantıda TV bir kez "USB hata ayıklamasına izin
 * verilsin mi?" diye sorar; "Bu bilgisayardan her zaman izin ver" işaretlenince bir daha sormaz.
 * Ev Ekranı ve Kutu Kurucu güncellemeleri de böylece kendiliğinden kurulur.
 */
object Sessiz {
    private const val TAG = "KutuKurucu"
    private const val PORT = 5555

    /** hazir: bağlantı çalışıyor; izin: TV'de onay bekleniyor; kapali: ADB yok/kapalı; bilinmiyor: denenmedi. */
    @Volatile var durum = "bilinmiyor"
    @Volatile var mesaj = ""
    @Volatile private var deneniyor = false

    private fun prefs(c: Context) = c.getSharedPreferences("sessiz", Context.MODE_PRIVATE)
    fun acik(c: Context) = prefs(c).getBoolean("acik", true)
    fun acikAyarla(c: Context, ac: Boolean) = prefs(c).edit().putBoolean("acik", ac).putInt("red", 0).apply()
    fun hazir(c: Context) = acik(c) && durum == "hazir"

    fun json(c: Context): JSONObject = JSONObject().put("acik", acik(c)).put("durum", durum).put("mesaj", mesaj)

    private fun anahtar(c: Context): AdbKeyPair {
        val ozel = File(c.filesDir, "adbkey")
        val acikA = File(c.filesDir, "adbkey.pub")
        if (!ozel.exists() || !acikA.exists()) AdbKeyPair.generate(ozel, acikA)
        return AdbKeyPair.read(ozel, acikA)
    }

    /** İlk bağlantıda TV onay penceresi açılır; kullanıcıya 90 sn tanınır. */
    private fun <T> baglan(c: Context, bekle: Int = 90_000, is_: (Dadb) -> T): T {
        val d = Dadb.create("127.0.0.1", PORT, anahtar(c), connectTimeout = 3_000, socketTimeout = bekle)
        return d.use(is_)
    }

    /** Bağlantıyı dener (ayrı iş parçacığında çağırın). Gerekirse TV'de bir kez izin sorulur. */
    fun dene(c: Context, ipucuGoster: Boolean = true) {
        if (!acik(c) || deneniyor) return
        deneniyor = true
        try {
            if (durum != "hazir" && ipucuGoster) {
                durum = "izin"
                Mesaj.goster(c, "Sessiz kurulum", "Çıkan soruda \"Her zaman izin ver\"i işaretleyip Tamam'a basın", 20_000)
            }
            val cikti = baglan(c) { it.shell("echo tamam").allOutput.trim() }
            if (cikti.contains("tamam")) {
                durum = "hazir"; mesaj = ""
                prefs(c).edit().putInt("red", 0).apply()
                if (!prefs(c).getBoolean("onaylandi", false)) {
                    prefs(c).edit().putBoolean("onaylandi", true).apply()
                    Mesaj.goster(c, "✓ Sessiz kurulum açık", "Güncellemeler artık soru sormadan kurulur", 8_000)
                }
            } else {
                durum = "kapali"; mesaj = cikti
            }
        } catch (e: java.net.ConnectException) {
            durum = "kapali"
            mesaj = "Kutuda Geliştirici seçenekleri > USB/Ağ hata ayıklaması kapalı"
        } catch (e: Exception) {
            Log.w(TAG, "sessiz bağlantı", e)
            durum = if (e is java.net.SocketTimeoutException || e.cause is java.net.SocketTimeoutException) "izin" else "kapali"
            mesaj = if (durum == "izin") "TV'deki izin sorusu onaylanmadı" else (e.message ?: e.javaClass.simpleName)
            // Onaylanmayan soru her 30 dakikada bir yeniden çıkmasın: iki kez sonra yalnızca panelden denenir.
            if (durum == "izin") prefs(c).edit().putInt("red", prefs(c).getInt("red", 0) + 1).apply()
        } finally {
            deneniyor = false
        }
    }

    /** APK'yı sessizce kurar. Başarısızsa hata metni, başarılıysa null döner. */
    fun kur(c: Context, apk: File): String? = runCatching {
        baglan(c, bekle = 120_000) { d ->
            when (val r = d.install(apk, "-r")) {
                is InstallResult.Success -> null
                is InstallResult.Failure -> r.reason.trim().ifEmpty { "kurulamadı" }
            }
        }
    }.getOrElse { e ->
        durum = "kapali"; mesaj = e.message ?: e.javaClass.simpleName
        mesaj
    }

    private fun kuruluSurum(c: Context, paket: String): Int = runCatching {
        val p = c.packageManager.getPackageInfo(paket, 0)
        if (Build.VERSION.SDK_INT >= 28) p.longVersionCode.toInt() else @Suppress("DEPRECATION") p.versionCode
    }.getOrDefault(-1)

    /**
     * Ev Ekranı (kuruluysa) ve Kutu Kurucu'nun yeni sürümlerini GitHub'dan alıp sessizce kurar.
     * Kutu Kurucu en sona kalır: kurulunca bu süreç yeniden başlar.
     */
    @Synchronized
    fun guncelle(c: Context) {
        if (!acik(c)) return
        if (durum != "hazir" && prefs(c).getInt("red", 0) >= 2) return
        if (durum != "hazir") dene(c, ipucuGoster = !prefs(c).getBoolean("onaylandi", false))
        if (durum != "hazir") return
        val surumler = runCatching { JSONArray(Kurucu.getir("https://api.github.com/repos/gokhangeo/rea/releases?per_page=40")) }.getOrNull() ?: return
        val hedefler = listOf(
            Triple("com.gokhan.evekrani", Regex("^ev-ekrani-v0\\.2\\.(\\d+)$"), "Ev Ekranı 0.2."),
            Triple(c.packageName, Regex("^kutu-kurucu-v1\\.(\\d+)$"), "Kutu Kurucu 1."),
        )
        for ((paket, re, ad) in hedefler) {
            val kurulu = kuruluSurum(c, paket)
            if (kurulu < 0) continue
            var en = 0
            var url: String? = null
            for (k in 0 until surumler.length()) {
                val r = surumler.getJSONObject(k)
                if (r.optBoolean("draft") || r.optBoolean("prerelease")) continue
                val n = re.find(r.optString("tag_name"))?.groupValues?.get(1)?.toIntOrNull() ?: continue
                if (n > en) Kurucu.apkVarligi(r)?.let { en = n; url = it }
            }
            val adres = url ?: continue
            if (en <= kurulu) continue
            val dosya = File(c.cacheDir, "sessiz-$en.apk")
            try {
                Kurucu.indir(adres, dosya)
                val hata = kur(c, dosya)
                if (hata == null) Log.i(TAG, "sessiz kuruldu: $ad$en") else Log.w(TAG, "sessiz kurulamadı: $ad$en: $hata")
                if (hata == null && paket != c.packageName) Mesaj.goster(c, "✓ $ad$en kuruldu", "", 6_000, kompakt = true)
            } catch (e: Exception) {
                Log.w(TAG, "sessiz güncelleme", e)
            } finally {
                dosya.delete()
            }
        }
    }
}

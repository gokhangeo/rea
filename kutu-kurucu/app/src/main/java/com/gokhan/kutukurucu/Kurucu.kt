package com.gokhan.kutukurucu

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicInteger

/**
 * APK kurulumu: telefondan/PC'den gelen dosya ya da bir bağlantı Android'in yükleyicisine verilir. Kurulum her zaman
 * TV'de Android'in kendi "Yükle" penceresiyle onaylanır; Sessiz kurulum hazırsa (Sessiz.kt) pencere açılmaz. Sayfa her işin durumunu buradan okur.
 */
object Kurucu {
    private const val TAG = "KutuKurucu"
    private val sayac = AtomicInteger((System.currentTimeMillis() / 1000 % 100000).toInt())

    class Is(val id: Int, val ad: String) {
        @Volatile var durum = "hazirlaniyor"   // hazirlaniyor, indiriliyor, onay, kuruldu, iptal, hata
        @Volatile var mesaj = ""
        @Volatile var paket = ""
        /** Kutudaki sürüm başka anahtarla imzalı / daha yeni: sayfada "Eskisini kaldır" düğmesi çıkar. */
        @Volatile var cakisma = false
        val zaman = System.currentTimeMillis()
        fun json(): JSONObject = JSONObject().put("id", id).put("ad", ad).put("durum", durum).put("mesaj", mesaj).put("paket", paket).put("cakisma", cakisma)
    }

    private val isler = LinkedHashMap<Int, Is>()

    /** Onay penceresi arka planda açılamazsa TV'de Kutu Kurucu açılınca gösterilir. */
    private val bekleyenOnaylar = ArrayList<Intent>()

    @Synchronized
    private fun yeni(ad: String): Is {
        val i = Is(sayac.incrementAndGet(), ad)
        isler[i.id] = i
        while (isler.size > 20) isler.remove(isler.keys.first())
        return i
    }

    @Synchronized
    fun is_(id: Int): Is? = isler[id]

    @Synchronized
    fun liste(): JSONArray = JSONArray().also { a -> isler.values.reversed().forEach { a.put(it.json()) } }

    @Synchronized
    fun onayEkle(i: Intent) {
        bekleyenOnaylar.add(i)
    }

    @Synchronized
    fun onaylariAl(): List<Intent> = bekleyenOnaylar.toList().also { bekleyenOnaylar.clear() }

    /** Sayfadan gelen dosyayı doğrudan yükleyiciye akıtır; ara dosya yazılmaz. [uzunluk] bilinmiyorsa -1. */
    fun akistan(c: Context, ad: String, girdi: InputStream, uzunluk: Long): Is {
        val i = yeni(ad)
        if (Sessiz.hazir(c)) {
            val dosya = File(c.cacheDir, "gelen-${i.id}.apk")
            try {
                dosya.outputStream().use { girdi.copyTo(it, 64 * 1024) }
                sessizVeyaNormal(c, i, dosya)
            } catch (e: Exception) {
                hata(i, e)
            } finally {
                dosya.delete()
            }
            return i
        }
        try {
            kur(c, i, girdi, uzunluk)
        } catch (e: Exception) {
            hata(i, e)
        }
        return i
    }

    /** Bağlantıdaki APK'yı kutuya indirir ve kurar. GitHub sürüm sayfası bağlantısı da olur. */
    fun baglantidan(c: Context, url: String, ad: String? = null): Is {
        val i = yeni(ad ?: url.substringAfterLast('/').substringBefore('?').ifBlank { "uygulama.apk" })
        Thread {
            val dosya = File(c.cacheDir, "indirilen-${i.id}.apk")
            try {
                i.durum = "indiriliyor"
                val apkUrl = githubApk(url) ?: url
                indir(apkUrl, dosya)
                sessizVeyaNormal(c, i, dosya)
            } catch (e: Exception) {
                hata(i, e)
            } finally {
                dosya.delete()
            }
        }.start()
        return i
    }

    /** GitHub'daki en yeni "kutu-kurucu-v1.N" sürümü: (N, APK adresi). */
    fun sonSurum(): Pair<Int, String>? {
        val surumler = JSONArray(getir("https://api.github.com/repos/gokhangeo/rea/releases?per_page=40"))
        val re = Regex("^kutu-kurucu-v1\\.(\\d+)$")
        var en = 0
        var url: String? = null
        for (k in 0 until surumler.length()) {
            val r = surumler.getJSONObject(k)
            if (r.optBoolean("draft") || r.optBoolean("prerelease")) continue
            val n = re.find(r.optString("tag_name"))?.groupValues?.get(1)?.toIntOrNull() ?: continue
            if (n <= en) continue
            apkVarligi(r)?.let { en = n; url = it }
        }
        return url?.let { en to it }
    }

    fun kurulu(c: Context): Int = runCatching {
        val p = c.packageManager.getPackageInfo(c.packageName, 0)
        if (Build.VERSION.SDK_INT >= 28) p.longVersionCode.toInt() else @Suppress("DEPRECATION") p.versionCode
    }.getOrDefault(0)

    /** Son denetimde bulunan yeni sürüm numarası (yoksa 0); panel gösterir. */
    @Volatile var bilinenYeni = 0

    /** Yeni sürüm varsa TV'de bir kez küçük uyarı gösterir. Ağ işi; ayrı iş parçacığında çağrılır. */
    fun yeniSurumDenetle(c: Context) {
        val (n, _) = runCatching { sonSurum() }.getOrNull() ?: return
        if (n <= kurulu(c)) { bilinenYeni = 0; return }
        bilinenYeni = n
        val p = c.getSharedPreferences("guncelleme", Context.MODE_PRIVATE)
        if (p.getInt("uyarilan", 0) >= n) return
        if (Mesaj.goster(c, "⬆ Kutu Kurucu 1.$n hazır", "Panelde \"Kutu Kurucu'yu güncelle\"ye basın", 15_000,
                kompakt = true, ayar = Goller.kartAyari(c).copy(sureMs = 15_000))) {
            p.edit().putInt("uyarilan", n).apply()
        }
    }

    /** GitHub'daki son sürüm kuruludan yeniyse onu kurar; değilse null. */
    fun kendiniGuncelle(c: Context): Is? {
        val (en, url) = sonSurum() ?: return null
        if (en <= kurulu(c)) return null
        return baglantidan(c, url, "Kutu Kurucu 1.$en")
    }

    /** Sessiz kurulum hazırsa pencere açmadan kurar; olmazsa Android'in Yükle penceresine düşer. */
    private fun sessizVeyaNormal(c: Context, i: Is, dosya: File) {
        if (Sessiz.hazir(c)) {
            i.durum = "hazirlaniyor"
            val hata = Sessiz.kur(c, dosya)
            if (hata == null) {
                i.durum = "kuruldu"; i.mesaj = "sessiz kuruldu"
                return
            }
            if (hata.contains("INSTALL_FAILED_UPDATE_INCOMPATIBLE") || hata.contains("INCONSISTENT_CERTIFICATES") ||
                hata.contains("VERSION_DOWNGRADE")) {
                i.durum = "hata"; i.cakisma = true; i.mesaj = hata
                return
            }
            Log.w(TAG, "sessiz kurulamadı, pencereyle denenecek: $hata")
        }
        dosya.inputStream().use { kur(c, i, it, dosya.length()) }
    }

    private fun kur(c: Context, i: Is, girdi: InputStream, uzunluk: Long) {
        i.durum = "hazirlaniyor"
        val yukleyici = c.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        if (uzunluk > 0) params.setSize(uzunluk)
        val oturum = yukleyici.createSession(params)
        try {
            yukleyici.openSession(oturum).use { s ->
                s.openWrite("uygulama.apk", 0, uzunluk).use { cikis ->
                    val tampon = ByteArray(64 * 1024)
                    var kalan = if (uzunluk > 0) uzunluk else Long.MAX_VALUE
                    while (kalan > 0) {
                        val n = girdi.read(tampon, 0, minOf(tampon.size.toLong(), kalan).toInt())
                        if (n < 0) break
                        cikis.write(tampon, 0, n)
                        kalan -= n
                    }
                    if (uzunluk > 0 && kalan > 0) error("dosya yarım geldi")
                    s.fsync(cikis)
                }
                val bayrak = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
                val sonuc = Intent(c, KurulumSonucu::class.java).putExtra("is", i.id)
                s.commit(PendingIntent.getBroadcast(c, i.id, sonuc, bayrak).intentSender)
            }
        } catch (e: Exception) {
            runCatching { yukleyici.abandonSession(oturum) }
            throw e
        }
    }

    private fun hata(i: Is, e: Exception) {
        Log.w(TAG, "kurulum başarısız: ${i.ad}", e)
        i.durum = "hata"
        i.mesaj = e.message ?: e.javaClass.simpleName
    }

    /** github.com/…/releases/tag/X ya da …/releases/latest → içindeki ilk APK'nın indirme adresi. */
    private fun githubApk(url: String): String? {
        val m = Regex("^https?://github\\.com/([^/]+)/([^/]+)/releases/(tag/([^/?#]+)|latest)").find(url) ?: return null
        val (sahip, depo) = m.destructured
        val etiket = m.groupValues[4]
        val api = if (etiket.isNotEmpty()) "https://api.github.com/repos/$sahip/$depo/releases/tags/$etiket"
        else "https://api.github.com/repos/$sahip/$depo/releases/latest"
        return apkVarligi(JSONObject(getir(api))) ?: error("bu sürümde APK yok")
    }

    fun apkVarligi(surum: JSONObject): String? {
        val v = surum.optJSONArray("assets") ?: return null
        for (k in 0 until v.length()) {
            val a = v.getJSONObject(k)
            if (a.optString("name").endsWith(".apk", ignoreCase = true)) return a.getString("browser_download_url")
        }
        return null
    }

    /** Yönlendirmeler elle izlenir: kutunun DNS'i yönlendirilen adı çözemezse Dns yedeği devreye girsin. */
    private fun baglan(url: String, ek: (HttpURLConnection) -> Unit = {}): HttpURLConnection = Dns.ac(url) {
        it.connectTimeout = 15_000
        it.readTimeout = 60_000
        it.instanceFollowRedirects = false
        it.setRequestProperty("User-Agent", "KutuKurucu/1.0")
        ek(it)
    }

    fun getir(url: String): String {
        val b = baglan(url) { it.setRequestProperty("Accept", "application/vnd.github+json") }
        try {
            if (b.responseCode != 200) error("GitHub cevap vermedi (HTTP ${b.responseCode})")
            return b.inputStream.bufferedReader().readText()
        } finally {
            b.disconnect()
        }
    }

    fun indir(url: String, dosya: File) {
        var adres = url
        // HttpURLConnection http↔https yönlendirmesini kendisi izlemez.
        repeat(5) {
            val b = baglan(adres)
            try {
                val kod = b.responseCode
                if (kod in 300..399) {
                    adres = URL(URL(adres), b.getHeaderField("Location") ?: error("yönlendirme bozuk")).toString()
                    return@repeat
                }
                if (kod != 200) error("indirilemedi (HTTP $kod)")
                b.inputStream.use { g -> dosya.outputStream().use { g.copyTo(it, 64 * 1024) } }
                return
            } finally {
                b.disconnect()
            }
        }
        error("çok fazla yönlendirme")
    }
}

/** Android yükleyicisinin cevabı: onay penceresini TV'de açar, sonucu sayfa için not eder. */
class KurulumSonucu : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val i = Kurucu.is_(intent.getIntExtra("is", 0))
        val ileti = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE).orEmpty()
        intent.getStringExtra(PackageInstaller.EXTRA_PACKAGE_NAME)?.let { i?.paket = it }
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val onay = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
                onay.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                i?.durum = "onay"
                if (Izinler.ustte(context)) {
                    try {
                        context.startActivity(onay)
                        return
                    } catch (_: Exception) {
                    }
                }
                // İzin yoksa Android pencereyi açmayabilir: TV'de Kutu Kurucu açılınca gösterilir.
                Kurucu.onayEkle(onay)
                i?.mesaj = "TV'de Kutu Kurucu'yu açın, onay penceresi orada çıkacak."
                runCatching { context.startActivity(onay) }
            }
            PackageInstaller.STATUS_SUCCESS -> {
                i?.durum = "kuruldu"
                i?.mesaj = ""
            }
            PackageInstaller.STATUS_FAILURE_ABORTED -> {
                i?.durum = "iptal"
                i?.mesaj = "TV'de iptal edildi."
            }
            PackageInstaller.STATUS_FAILURE_CONFLICT -> {
                i?.durum = "hata"
                i?.cakisma = true
                i?.mesaj = "Kutudaki sürüm başka anahtarla imzalı ya da daha yeni. Aşağıdaki düğmeyle eskisini kaldırıp yeniden kurun."
            }
            PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> {
                i?.durum = "hata"
                i?.mesaj = "Bu APK bu kutuyla uyumlu değil (işlemci ya da Android sürümü). $ileti"
            }
            PackageInstaller.STATUS_FAILURE_STORAGE -> {
                i?.durum = "hata"
                i?.mesaj = "Kutuda yer yok."
            }
            PackageInstaller.STATUS_FAILURE_INVALID -> {
                i?.durum = "hata"
                i?.mesaj = "Dosya geçerli bir APK değil. $ileti"
            }
            else -> {
                i?.durum = "hata"
                i?.mesaj = ileti.ifBlank { "Kurulamadı." }
            }
        }
    }
}

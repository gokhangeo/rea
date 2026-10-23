package com.gokhan.kutukurucu.kurulum

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicInteger

/**
 * Kutu Kurucu'yu kuran, gÃ¼ncelleyen ve kaldÄ±ran yardÄ±mcÄ±. Kurulum/kaldÄ±rma her zaman TV'de Android'in kendi
 * onay penceresiyle olur; sessiz kurulum yoktur. Bu uygulama ayrÄ± bir pakettir, Kutu Kurucu silinse de kutuda kalÄ±r.
 */
object Kurucu {
    private const val TAG = "KutuKurulum"
    const val HEDEF = "com.gokhan.kutukurucu"
    private val sayac = AtomicInteger((System.currentTimeMillis() / 1000 % 100000).toInt())

    class Is(val id: Int, val ad: String) {
        @Volatile var durum = "hazirlaniyor"   // hazirlaniyor, indiriliyor, onay, kuruldu, iptal, hata
        @Volatile var mesaj = ""
        val zaman = System.currentTimeMillis()
        fun json(): JSONObject = JSONObject().put("id", id).put("ad", ad).put("durum", durum).put("mesaj", mesaj)
    }

    private val isler = LinkedHashMap<Int, Is>()

    /** Onay penceresi arka planda aÃ§Ä±lamazsa TV'de bu uygulama aÃ§Ä±lÄ±nca gÃ¶sterilir. */
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

    /** Kutudaki Kutu Kurucu'nun sÃ¼rÃ¼m kodu; kurulu deÄŸilse 0. */
    fun kuruluSurum(c: Context): Int = runCatching {
        val p = c.packageManager.getPackageInfo(HEDEF, 0)
        if (Build.VERSION.SDK_INT >= 28) p.longVersionCode.toInt() else @Suppress("DEPRECATION") p.versionCode
    }.getOrDefault(0)

    fun kuruluAd(c: Context): String? = runCatching {
        c.packageManager.getPackageInfo(HEDEF, 0).versionName
    }.getOrNull()

    /** GitHub'daki en yeni "kutu-kurucu-v1.N" sÃ¼rÃ¼mÃ¼: (numara, ad, APK adresi). */
    fun sonSurum(): Triple<Int, String, String>? {
        val surumler = JSONArray(getir("https://api.github.com/repos/gokhangeo/rea/releases?per_page=40"))
        val re = Regex("^kutu-kurucu-v1\\.(\\d+)$")
        var en = 0
        var ad = ""
        var url: String? = null
        for (k in 0 until surumler.length()) {
            val r = surumler.getJSONObject(k)
            if (r.optBoolean("draft") || r.optBoolean("prerelease")) continue
            val n = re.find(r.optString("tag_name"))?.groupValues?.get(1)?.toIntOrNull() ?: continue
            if (n <= en) continue
            apkVarligi(r)?.let { en = n; ad = r.optString("name"); url = it }
        }
        return url?.let { Triple(en, ad.ifBlank { "Kutu Kurucu 1.$en" }, it) }
    }

    /** Son sÃ¼rÃ¼mÃ¼ indirip kurar; kurulu sÃ¼rÃ¼m zaten yeni ise null. */
    fun kur(c: Context): Is? {
        if (!c.packageManager.canRequestPackageInstalls()) error("Ã–nce bu uygulamaya 'bilinmeyen uygulama yÃ¼kleme' iznini verin")
        val son = sonSurum() ?: error("GitHub'da Kutu Kurucu sÃ¼rÃ¼mÃ¼ bulunamadÄ±")
        if (son.first <= kuruluSurum(c)) return null
        return baglantidan(c, son.third, son.second)
    }

    /** TV'de bu uygulama iÃ§in "bilinmeyen uygulama yÃ¼kleme" ayar ekranÄ±nÄ± aÃ§ar. */
    fun izinEkrani(c: Context): Boolean {
        val i = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${c.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { c.startActivity(i) }.isSuccess
    }

    fun kaldir(c: Context): String? {
        if (kuruluSurum(c) == 0) return "Kutu Kurucu kutuda kurulu deÄŸil."
        @Suppress("DEPRECATION")
        val i = Intent(Intent.ACTION_DELETE, Uri.parse("package:$HEDEF")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return if (runCatching { c.startActivity(i) }.isSuccess) null else "KaldÄ±rma penceresi aÃ§Ä±lamadÄ±."
    }

    private fun baglantidan(c: Context, url: String, ad: String): Is {
        val i = yeni(ad)
        Thread {
            val dosya = File(c.cacheDir, "indirilen-${i.id}.apk")
            try {
                i.durum = "indiriliyor"
                indir(url, dosya)
                dosya.inputStream().use { kur_(c, i, it, dosya.length()) }
            } catch (e: Exception) {
                hata(i, e)
            } finally {
                dosya.delete()
            }
        }.start()
        return i
    }

    private fun kur_(c: Context, i: Is, girdi: InputStream, uzunluk: Long) {
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
                    if (uzunluk > 0 && kalan > 0) error("dosya yarÄ±m geldi")
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
        Log.w(TAG, "kurulum baÅŸarÄ±sÄ±z: ${i.ad}", e)
        i.durum = "hata"
        i.mesaj = e.message ?: e.javaClass.simpleName
    }

    private fun apkVarligi(surum: JSONObject): String? {
        val v = surum.optJSONArray("assets") ?: return null
        for (k in 0 until v.length()) {
            val a = v.getJSONObject(k)
            if (a.optString("name").endsWith(".apk", ignoreCase = true)) return a.getString("browser_download_url")
        }
        return null
    }

    private fun baglan(url: String): HttpURLConnection = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 15_000
        readTimeout = 60_000
        instanceFollowRedirects = true
        setRequestProperty("User-Agent", "KutuKurulum/1.0")
    }

    private fun getir(url: String): String {
        val b = baglan(url)
        b.setRequestProperty("Accept", "application/vnd.github+json")
        try {
            if (b.responseCode != 200) error("GitHub cevap vermedi (HTTP ${b.responseCode})")
            return b.inputStream.bufferedReader().readText()
        } finally {
            b.disconnect()
        }
    }

    private fun indir(url: String, dosya: File) {
        var adres = url
        repeat(5) {
            val b = baglan(adres)
            try {
                val kod = b.responseCode
                if (kod in 300..399) {
                    adres = URL(URL(adres), b.getHeaderField("Location") ?: error("yÃ¶nlendirme bozuk")).toString()
                    return@repeat
                }
                if (kod != 200) error("indirilemedi (HTTP $kod)")
                b.inputStream.use { g -> dosya.outputStream().use { g.copyTo(it, 64 * 1024) } }
                return
            } finally {
                b.disconnect()
            }
        }
        error("Ã§ok fazla yÃ¶nlendirme")
    }
}

/** Android yÃ¼kleyicisinin cevabÄ±: onay penceresini TV'de aÃ§ar, sonucu sayfa iÃ§in not eder. */
class KurulumSonucu : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val i = Kurucu.is_(intent.getIntExtra("is", 0))
        val ileti = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE).orEmpty()
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val onay = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
                onay.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                i?.durum = "onay"
                if (runCatching { context.startActivity(onay) }.isSuccess) return
                Kurucu.onayEkle(onay)
                i?.mesaj = "TV'de Kutu Kurucu Kurulum'u aÃ§Ä±n, onay penceresi orada Ã§Ä±kacak."
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
                i?.mesaj = "Kutudaki sÃ¼rÃ¼m baÅŸka anahtarla imzalÄ± ya da daha yeni. Ã–nce Kutu Kurucu'yu kaldÄ±rÄ±n."
            }
            PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> {
                i?.durum = "hata"
                i?.mesaj = "Bu APK bu kutuyla uyumlu deÄŸil. $ileti"
            }
            PackageInstaller.STATUS_FAILURE_STORAGE -> {
                i?.durum = "hata"
                i?.mesaj = "Kutuda yer yok."
            }
            PackageInstaller.STATUS_FAILURE_INVALID -> {
                i?.durum = "hata"
                i?.mesaj = "Dosya geÃ§erli bir APK deÄŸil. $ileti"
            }
            else -> {
                i?.durum = "hata"
                i?.mesaj = ileti.ifBlank { "KurulamadÄ±." }
            }
        }
    }
}


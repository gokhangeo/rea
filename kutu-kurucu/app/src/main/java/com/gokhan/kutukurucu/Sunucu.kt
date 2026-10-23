package com.gokhan.kutukurucu

import android.content.Context
import android.os.Build
import fi.iki.elonen.NanoHTTPD
import org.json.JSONObject
import java.io.FileInputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.URLEncoder
import kotlin.random.Random

/**
 * Evdeki ağda 8090 portunda çalışan sayfa ve API. Ev Ekranı 8080'i kullandığı için ikisi aynı kutuda çakışmaz.
 * Sayfa dışındaki her şey TV ekranında yazan 4 haneli kodu ister (x-kod başlığı ya da ?kod=).
 */
class Sunucu(private val c: Context) : NanoHTTPD(PORT) {
    private var yanlis = 0
    private var yanlisBaslangic = 0L

    override fun serve(s: IHTTPSession): Response = try {
        yonlendir(s)
    } catch (t: Throwable) {
        metin(Response.Status.INTERNAL_ERROR, t.message ?: "hata")
    }

    private fun yonlendir(s: IHTTPSession): Response {
        val uri = s.uri
        if (uri == "/" || uri == "/index.html") return sayfa()
        if (!uri.startsWith("/api/")) return metin(Response.Status.NOT_FOUND, "bulunamadı")
        if (!kodDogru(s)) return metin(Response.Status.UNAUTHORIZED, "kod yanlış")
        val paket = s.parameters["paket"]?.firstOrNull().orEmpty()
        val post = s.method == Method.POST

        return when (uri) {
            "/api/kontrol" -> json(JSONObject().put("ok", true))
            "/api/durum" -> json(
                JSONObject()
                    .put("cihaz", "${Build.MANUFACTURER} ${Build.MODEL}".trim())
                    .put("android", Build.VERSION.RELEASE)
                    .put("surum", runCatching { c.packageManager.getPackageInfo(c.packageName, 0).versionName }.getOrNull().orEmpty())
                    .put("adres", adres()?.let { "$it:$PORT" } ?: JSONObject.NULL)
                    .put("izinler", Izinler.json(c))
                    .put("isler", Kurucu.liste()),
            )
            "/api/kur" -> {
                if (!post) return metin(Response.Status.METHOD_NOT_ALLOWED, "")
                val uzunluk = s.headers["content-length"]?.toLongOrNull() ?: -1L
                if (uzunluk <= 0) return metin(Response.Status.BAD_REQUEST, "dosya boş")
                val ad = s.parameters["ad"]?.firstOrNull()?.takeIf { it.isNotBlank() } ?: "uygulama.apk"
                json(Kurucu.akistan(c, ad, s.inputStream, uzunluk).json())
            }
            "/api/bag" -> {
                if (!post) return metin(Response.Status.METHOD_NOT_ALLOWED, "")
                val url = s.parameters["url"]?.firstOrNull()?.trim().orEmpty()
                if (!url.startsWith("http://") && !url.startsWith("https://")) return metin(Response.Status.BAD_REQUEST, "bağlantı http ile başlamalı")
                json(Kurucu.baglantidan(c, url).json())
            }
            "/api/kendini-guncelle" -> {
                if (!post) return metin(Response.Status.METHOD_NOT_ALLOWED, "")
                val i = Kurucu.kendiniGuncelle(c)
                json(i?.json() ?: JSONObject().put("mesaj", "Kutu Kurucu zaten güncel."))
            }
            "/api/mesaj" -> {
                if (!post) return metin(Response.Status.METHOD_NOT_ALLOWED, "")
                val yazi = s.parameters["metin"]?.firstOrNull()?.trim().orEmpty()
                if (yazi.isEmpty()) return metin(Response.Status.BAD_REQUEST, "mesaj boş")
                val baslik = s.parameters["baslik"]?.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() } ?: "Kutu Kurucu"
                val sure = s.parameters["sure"]?.firstOrNull()?.toLongOrNull() ?: 8000L
                if (!Mesaj.goster(c, baslik, yazi, sure)) {
                    metin(Response.Status.CONFLICT, "TV'de 'üstte gösterme' izni yok")
                } else tamam()
            }
            "/api/takimlar" -> if (post) {
                val liste = s.parameters["takimlar"]?.firstOrNull().orEmpty()
                val acik = (s.parameters["acik"]?.firstOrNull() ?: "1") != "0"
                Goller.ayarla(c, liste, acik)
                json(Goller.json(c))
            } else json(Goller.json(c))
            "/api/medya" -> {
                if (!post) return metin(Response.Status.METHOD_NOT_ALLOWED, "")
                val uzunluk = s.headers["content-length"]?.toLongOrNull() ?: -1L
                if (uzunluk <= 0) return metin(Response.Status.BAD_REQUEST, "dosya boş")
                val ad = s.parameters["ad"]?.firstOrNull()?.takeIf { it.isNotBlank() } ?: "medya"
                json(Medya.kaydet(c, ad, s.inputStream, uzunluk))
            }
            "/api/uygulamalar" -> json(JSONObject().put("uygulamalar", Uygulamalar.json(c)))
            "/api/simge" -> Uygulamalar.simge(c, paket)?.let {
                newFixedLengthResponse(Response.Status.OK, "image/png", it.inputStream(), it.size.toLong())
                    .apply { addHeader("Cache-Control", "max-age=86400") }
            } ?: metin(Response.Status.NOT_FOUND, "")
            "/api/ac" -> if (Uygulamalar.ac(c, paket)) tamam() else metin(Response.Status.BAD_REQUEST, "açılamadı")
            "/api/kaldir" -> Uygulamalar.kaldir(c, paket)?.let { metin(Response.Status.BAD_REQUEST, it) } ?: tamam()
            "/api/apk" -> {
                val (dosya, ad) = Uygulamalar.apk(c, paket) ?: return metin(Response.Status.NOT_FOUND, "bulunamadı")
                newFixedLengthResponse(Response.Status.OK, "application/vnd.android.package-archive", FileInputStream(dosya), dosya.length())
                    .apply { addHeader("Content-Disposition", "attachment; filename=\"$ad\"; filename*=UTF-8''" + URLEncoder.encode(ad, "UTF-8")) }
            }
            "/api/anaekran" -> if (post) {
                val uyari = AnaEkran.sec(c, paket)
                json(AnaEkran.json(c).put("uyari", uyari ?: ""))
            } else json(AnaEkran.json(c))
            else -> metin(Response.Status.NOT_FOUND, "bulunamadı")
        }
    }

    /** Kod denemesi: 10 dakikada 30 yanlıştan sonra bir süre kimse giremez. */
    @Synchronized
    private fun kodDogru(s: IHTTPSession): Boolean {
        val simdi = System.currentTimeMillis()
        if (simdi - yanlisBaslangic > 10 * 60_000) {
            yanlis = 0
            yanlisBaslangic = simdi
        }
        if (yanlis >= 30) return false
        val kod = kod(c)
        val ok = s.headers["x-kod"] == kod || s.parameters["kod"]?.firstOrNull() == kod
        if (!ok) yanlis++
        return ok
    }

    private fun sayfa(): Response {
        val bytes = c.assets.open("sayfa.html").use { it.readBytes() }
        return newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", bytes.inputStream(), bytes.size.toLong())
            .apply { addHeader("Cache-Control", "no-cache") }
    }

    private fun tamam() = json(JSONObject().put("ok", true))
    private fun json(o: JSONObject) = newFixedLengthResponse(Response.Status.OK, "application/json; charset=utf-8", o.toString())
    private fun metin(st: Response.Status, m: String) = newFixedLengthResponse(st, "text/plain; charset=utf-8", m)

    companion object {
        const val PORT = 8090

        fun kod(c: Context): String = c.getSharedPreferences("sunucu", Context.MODE_PRIVATE).let { p ->
            p.getString("kod", null) ?: yeniKod(c)
        }

        fun yeniKod(c: Context): String = Random.nextInt(1000, 10000).toString().also {
            c.getSharedPreferences("sunucu", Context.MODE_PRIVATE).edit().putString("kod", it).apply()
        }

        /** Kutunun evdeki ağdaki adresi (Wi-Fi ya da kablo). */
        fun adres(): String? = runCatching {
            NetworkInterface.getNetworkInterfaces().toList().filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.toList() }.filterIsInstance<Inet4Address>()
                .firstOrNull { it.isSiteLocalAddress }?.hostAddress
        }.getOrNull()
    }
}

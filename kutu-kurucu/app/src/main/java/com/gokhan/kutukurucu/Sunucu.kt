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
        if (uri == "/api/kim") return json(Kutular.kim(c))
        if (!kodDogru(s)) return metin(Response.Status.UNAUTHORIZED, "kod yanlış")
        // Panel başka bir kutuyu yönetiyorsa istek oraya aktarılır.
        val kutu = (s.headers["x-kutu"] ?: s.parameters["kutu"]?.firstOrNull())?.trim().orEmpty()
        if (kutu.isNotEmpty() && kutu != adres()) {
            if (!Kutular.yerel(kutu) || !kutu.matches(Regex("^\\d{1,3}(\\.\\d{1,3}){3}$"))) return metin(Response.Status.BAD_REQUEST, "kutu adresi geçersiz")
            return Kutular.vekil(s, kutu)
        }
        val paket = s.parameters["paket"]?.firstOrNull().orEmpty()
        val post = s.method == Method.POST

        return when (uri) {
            "/api/kontrol" -> json(JSONObject().put("ok", true))
            "/api/kutular" -> json(JSONObject().put("kutular", Kutular.liste(c, s.parameters["yenile"]?.firstOrNull() == "1")))
            "/api/kodsuz" -> {
                if (!post) return metin(Response.Status.METHOD_NOT_ALLOWED, "")
                kodsuzAyarla(c, s.parameters["ac"]?.firstOrNull() != "0")
                json(JSONObject().put("kodsuz", kodsuz(c)))
            }
            "/api/durum" -> json(
                JSONObject()
                    .put("cihaz", "${Build.MANUFACTURER} ${Build.MODEL}".trim())
                    .put("android", Build.VERSION.RELEASE)
                    .put("surum", runCatching { c.packageManager.getPackageInfo(c.packageName, 0).versionName }.getOrNull().orEmpty())
                    .put("adres", adres()?.let { "$it:$PORT" } ?: JSONObject.NULL)
                    .put("izinler", Izinler.json(c))
                    .put("isler", Kurucu.liste())
                    .put("yeniSurum", Kurucu.bilinenYeni)
                    .put("kodsuz", kodsuz(c)),
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
            "/api/kendini-kaldir" -> {
                if (!post) return metin(Response.Status.METHOD_NOT_ALLOWED, "")
                Uygulamalar.kendiniKaldir(c)?.let { metin(Response.Status.CONFLICT, it) } ?: tamam()
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
                val acik = (s.parameters["acik"]?.firstOrNull() ?: "1") != "0"
                Goller.gorunumAyarla(c) { s.parameters[it]?.firstOrNull() }
                Goller.ayarla(c, s.parameters["secili"]?.firstOrNull(), s.parameters["eskiler"]?.firstOrNull(), acik)
                    ?.let { metin(Response.Status.BAD_REQUEST, it) } ?: json(Goller.json(c))
            } else json(Goller.json(c))
            "/api/gol-dene" -> {
                if (!post) return metin(Response.Status.METHOD_NOT_ALLOWED, "")
                if (Goller.dene(c)) tamam() else metin(Response.Status.CONFLICT, "TV'de 'üstte gösterme' izni yok")
            }
            "/api/ayar-gonder" -> {
                if (!post) return metin(Response.Status.METHOD_NOT_ALLOWED, "")
                val hedef = s.parameters["hedef"]?.firstOrNull()?.trim().orEmpty()
                val hedefKod = s.parameters["hedefKod"]?.firstOrNull()?.trim().orEmpty()
                ayarGonder(hedef, hedefKod)?.let { metin(Response.Status.BAD_REQUEST, it) } ?: tamam()
            }
            "/api/lig-takimlar" -> {
                val lig = s.parameters["lig"]?.firstOrNull().orEmpty()
                if (lig !in Goller.SECILEBILIR) metin(Response.Status.BAD_REQUEST, "Bilinmeyen lig")
                else runCatching { json(JSONObject().put("lig", lig).put("takimlar", Goller.ligTakimlari(lig))) }
                    .getOrElse { metin(Response.Status.INTERNAL_ERROR, "Takım listesi alınamadı (ESPN): ${it.message}") }
            }
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

    /** Gol ayarlarını evdeki diğer Kutu Kurucu'ya (8090) gönderir. Yalnızca yerel ağ adresleri. Hata varsa metni döner. */
    private fun ayarGonder(hedef: String, hedefKod: String): String? {
        val m = Regex("^(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})$").find(hedef) ?: return "Diğer kutunun IP adresini yazın (ör. 192.168.1.106)."
        val o = m.groupValues.drop(1).map { it.toInt() }
        if (o.any { it > 255 }) return "IP adresi geçersiz."
        val yerel = o[0] == 192 && o[1] == 168 || o[0] == 10 || o[0] == 172 && o[1] in 16..31
        if (!yerel) return "Yalnızca evdeki ağ adresleri (192.168.x.x gibi)."
        if (hedef == adres()) return "Bu zaten bu kutu."
        if (hedefKod.isNotEmpty() && !hedefKod.matches(Regex("\\d{4}"))) return "Kod 4 haneli olmalı."
        val sorgu = (Goller.aktarimParametreleri(c) + (if (hedefKod.isNotEmpty()) mapOf("kod" to hedefKod) else emptyMap())).entries
            .joinToString("&") { (k, v) -> k + "=" + URLEncoder.encode(v, "UTF-8") }
        val b = java.net.URL("http://$hedef:$PORT/api/takimlar?$sorgu").openConnection() as java.net.HttpURLConnection
        b.connectTimeout = 5_000; b.readTimeout = 10_000
        b.requestMethod = "POST"; b.doOutput = true
        return try {
            b.outputStream.use { }
            when (b.responseCode) {
                200 -> null
                401 -> "Diğer kutu kod istiyor; TV ekranındaki kodu yazın."
                else -> "Diğer kutu kabul etmedi (HTTP ${b.responseCode}). Orada Kutu Kurucu güncel mi?"
            }
        } catch (e: Exception) {
            "Diğer kutuya ulaşılamadı ($hedef). Açık ve Kutu Kurucu çalışıyor mu?"
        } finally { b.disconnect() }
    }

    /** Kod denemesi: 10 dakikada 30 yanlıştan sonra bir süre kimse giremez. */
    @Synchronized
    private fun kodDogru(s: IHTTPSession): Boolean {
        val simdi = System.currentTimeMillis()
        if (simdi - yanlisBaslangic > 10 * 60_000) {
            yanlis = 0
            yanlisBaslangic = simdi
        }
        if (kodsuz(c) && Kutular.yerel(s.remoteIpAddress)) return true
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

        /** Evdeki ağdan (192.168.x.x vb.) gelen isteklerde kod sorulmaz (varsayılan açık, panelden kapatılır). */
        fun kodsuz(c: Context) = c.getSharedPreferences("sunucu", Context.MODE_PRIVATE).getBoolean("kodsuz", true)
        fun kodsuzAyarla(c: Context, ac: Boolean) =
            c.getSharedPreferences("sunucu", Context.MODE_PRIVATE).edit().putBoolean("kodsuz", ac).apply()

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

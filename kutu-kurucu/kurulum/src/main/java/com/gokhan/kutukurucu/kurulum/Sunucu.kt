package com.gokhan.kutukurucu.kurulum

import android.content.Context
import fi.iki.elonen.NanoHTTPD
import org.json.JSONObject
import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * Kutu Kurucu'yu kurmak/kaldırmak için ev ağındaki telefondan/PC'den açılan küçük sayfa. Port 8091;
 * Kutu Kurucu'nun 8090'daki kendi sayfasıyla çakışmaz ve Kutu Kurucu silinse de bu sayfa çalışmaya devam eder.
 */
class Sunucu(private val c: Context) : NanoHTTPD(PORT) {

    override fun serve(s: IHTTPSession): Response = try {
        yonlendir(s)
    } catch (e: Exception) {
        metin(Response.Status.INTERNAL_ERROR, e.message ?: "hata")
    }

    private fun yonlendir(s: IHTTPSession): Response {
        val uri = s.uri.substringBefore('?')
        val post = s.method == Method.POST
        return when (uri) {
            "/", "/index.html" -> sayfa()
            "/api/durum" -> json(JSONObject()
                .put("adres", adres())
                .put("port", PORT)
                .put("kurulu", Kurucu.kuruluAd(c) ?: "")
                .put("kuruluKod", Kurucu.kuruluSurum(c))
                .put("yukleme", c.packageManager.canRequestPackageInstalls())
                .put("sonSurum", runCatching { Kurucu.sonSurum()?.let { "1.${it.first}" } }.getOrNull() ?: "")
                .put("isler", Kurucu.liste()))
            "/api/izin" -> {
                if (!post) return metin(Response.Status.METHOD_NOT_ALLOWED, "")
                if (Kurucu.izinEkrani(c)) tamam() else metin(Response.Status.CONFLICT, "Ayar ekranı açılamadı; TV'de Kutu Kurucu Kurulum'u açın.")
            }
            "/api/kur" -> {
                if (!post) return metin(Response.Status.METHOD_NOT_ALLOWED, "")
                val is_ = Kurucu.kur(c)
                json(is_?.json() ?: JSONObject().put("mesaj", "Kutu Kurucu zaten güncel."))
            }
            "/api/kaldir" -> {
                if (!post) return metin(Response.Status.METHOD_NOT_ALLOWED, "")
                Kurucu.kaldir(c)?.let { metin(Response.Status.CONFLICT, it) } ?: tamam()
            }
            else -> metin(Response.Status.NOT_FOUND, "bulunamadı")
        }
    }

    private fun sayfa(): Response = newFixedLengthResponse(
        Response.Status.OK, "text/html; charset=utf-8", c.assets.open("kurulum.html"), -1
    ).apply { addHeader("Cache-Control", "no-cache") }

    private fun tamam() = json(JSONObject().put("ok", true))
    private fun json(o: JSONObject) = newFixedLengthResponse(Response.Status.OK, "application/json; charset=utf-8", o.toString())
    private fun metin(st: Response.Status, m: String) = newFixedLengthResponse(st, "text/plain; charset=utf-8", m)

    companion object {
        const val PORT = 8091

        fun adres(): String? = runCatching {
            NetworkInterface.getNetworkInterfaces().toList().filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.toList() }.filterIsInstance<Inet4Address>()
                .firstOrNull { it.isSiteLocalAddress }?.hostAddress
        }.getOrNull()
    }
}

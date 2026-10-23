package com.gokhan.kutukurucu

import android.content.Context
import android.os.Build
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.IHTTPSession
import fi.iki.elonen.NanoHTTPD.Response
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Evdeki diğer Kutu Kurucu'lar: ağda bulunur, tek panelden yönetilir.
 * Panel her zaman kendi kutusuna istek atar; "x-kutu" başlığı (ya da ?kutu=) varsa istek o kutuya aktarılır.
 */
object Kutular {
    private fun p(c: Context) = c.getSharedPreferences("kutular", Context.MODE_PRIVATE)
    @Volatile private var son: Pair<Long, JSONArray>? = null

    fun yerel(ip: String?): Boolean {
        val o = ip?.split(".")?.mapNotNull { it.toIntOrNull() } ?: return false
        if (o.size != 4) return ip == "127.0.0.1"
        return o[0] == 192 && o[1] == 168 || o[0] == 10 || o[0] == 172 && o[1] in 16..31 || o[0] == 127
    }

    /** Kodsuz istek: /api/kim (kimlik doğrulamasız, yalnızca ad ve sürüm). */
    fun kim(c: Context): JSONObject = JSONObject()
        .put("uygulama", "kutu-kurucu")
        .put("cihaz", "${Build.MANUFACTURER} ${Build.MODEL}".trim())
        .put("surum", runCatching { c.packageManager.getPackageInfo(c.packageName, 0).versionName }.getOrNull().orEmpty())
        .put("ip", Sunucu.adres() ?: "")

    /** Bu kutu + ağda bulunan diğer Kutu Kurucu'lar (5 dk önbellek; [yenile] ile yeniden taranır). */
    fun liste(c: Context, yenile: Boolean): JSONArray {
        if (!yenile) son?.let { (t, a) -> if (System.currentTimeMillis() - t < 5 * 60_000) return a }
        val ben = Sunucu.adres()
        val bulunan = LinkedHashMap<String, JSONObject>()
        bulunan[ben ?: "bu"] = kim(c).put("bu", true)
        val adaylar = LinkedHashSet<String>()
        p(c).getString("bilinen", "").orEmpty().split(",").filter { it.isNotBlank() }.forEach { adaylar.add(it) }
        if (ben != null) {
            val on = ben.substringBeforeLast('.')
            for (i in 1..254) adaylar.add("$on.$i")
        }
        adaylar.remove(ben)
        val havuz = Executors.newFixedThreadPool(48)
        val sonuc = java.util.Collections.synchronizedMap(LinkedHashMap<String, JSONObject>())
        for (ip in adaylar) havuz.execute {
            if (!acikMi(ip)) return@execute
            runCatching {
                val o = JSONObject(getir("http://$ip:${Sunucu.PORT}/api/kim", 2_000))
                if (o.optString("uygulama") == "kutu-kurucu") sonuc[ip] = o.put("ip", ip).put("bu", false)
            }
        }
        havuz.shutdown()
        havuz.awaitTermination(15, TimeUnit.SECONDS)
        sonuc.keys.sortedBy { it.substringAfterLast('.').toIntOrNull() ?: 0 }.forEach { bulunan[it] = sonuc.getValue(it) }
        p(c).edit().putString("bilinen", sonuc.keys.joinToString(",")).apply()
        val a = JSONArray(bulunan.values.toList())
        son = System.currentTimeMillis() to a
        return a
    }

    private fun acikMi(ip: String) = runCatching {
        Socket().use { it.connect(InetSocketAddress(ip, Sunucu.PORT), 350) }; true
    }.getOrDefault(false)

    private fun getir(url: String, zaman: Int): String {
        val b = URL(url).openConnection() as HttpURLConnection
        b.connectTimeout = zaman; b.readTimeout = zaman
        try {
            if (b.responseCode != 200) error("HTTP ${b.responseCode}")
            return b.inputStream.bufferedReader().readText()
        } finally { b.disconnect() }
    }

    /** İsteği aynen diğer kutuya aktarır (yöntem, sorgu, gövde) ve cevabını geri verir. */
    fun vekil(s: IHTTPSession, hedef: String): Response {
        val sorgu = (s.queryParameterString ?: "").split("&")
            .filter { it.isNotBlank() && !it.startsWith("kutu=") }.joinToString("&")
        val url = "http://$hedef:${Sunucu.PORT}${s.uri}" + if (sorgu.isNotEmpty()) "?$sorgu" else ""
        val b = URL(url).openConnection() as HttpURLConnection
        b.connectTimeout = 5_000
        b.readTimeout = 10 * 60_000
        b.requestMethod = s.method.name
        s.headers["x-kod"]?.let { b.setRequestProperty("x-kod", it) }
        s.headers["content-type"]?.let { b.setRequestProperty("Content-Type", it) }
        val uzunluk = s.headers["content-length"]?.toLongOrNull() ?: 0L
        try {
            if (s.method == NanoHTTPD.Method.POST) {
                b.doOutput = true
                b.setFixedLengthStreamingMode(uzunluk)
                b.outputStream.use { out ->
                    val tampon = ByteArray(64 * 1024)
                    var kalan = uzunluk
                    while (kalan > 0) {
                        val n = s.inputStream.read(tampon, 0, minOf(tampon.size.toLong(), kalan).toInt())
                        if (n < 0) break
                        out.write(tampon, 0, n); kalan -= n
                    }
                }
            }
            val kod = b.responseCode
            val tip = b.contentType ?: "text/plain; charset=utf-8"
            val govde = (if (kod >= 400) b.errorStream else b.inputStream)?.use { it.readBytes() } ?: ByteArray(0)
            val metin = if (kod == 401) "Bu kutu ($hedef) kod istiyor. Orada Kutu Kurucu'yu güncelleyin."
                .toByteArray() else govde
            val st = object : Response.IStatus {
                override fun getDescription() = "$kod"
                override fun getRequestStatus() = if (kod == 401) 409 else kod
            }
            return NanoHTTPD.newFixedLengthResponse(st, if (kod == 401) "text/plain; charset=utf-8" else tip,
                metin.inputStream(), metin.size.toLong()).apply {
                b.getHeaderField("Content-Disposition")?.let { addHeader("Content-Disposition", it) }
                b.getHeaderField("Cache-Control")?.let { addHeader("Cache-Control", it) }
            }
        } catch (e: Exception) {
            return NanoHTTPD.newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain; charset=utf-8",
                "$hedef kutusuna ulaşılamadı. Açık ve Kutu Kurucu çalışıyor mu?")
        } finally { b.disconnect() }
    }
}

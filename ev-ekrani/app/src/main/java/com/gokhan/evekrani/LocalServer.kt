package com.gokhan.evekrani

import android.content.Context
import fi.iki.elonen.NanoHTTPD
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import kotlin.random.Random

/**
 * Evdeki ağda 8080 portunda çalışan küçük sunucu:
 *  - /tv/...   ana ekrandaki Fenerbahçe, fotoğraf ve hava durumu bölümü (yalnızca kutunun içinden açılır);
 *  - /         telefondan fotoğraf yükleme sayfası (ekranda yazan 4 haneli kodla);
 *  - /api/...  ikisinin de kullandığı veriler.
 */
class LocalServer(private val context: Context, private val photos: PhotoStore, private val feeds: Feeds) : NanoHTTPD(PORT) {
    val code: String = context.getSharedPreferences("server", Context.MODE_PRIVATE).let { p ->
        p.getString("code", null) ?: Random.nextInt(1000, 10000).toString().also { p.edit().putString("code", it).apply() }
    }

    override fun serve(session: IHTTPSession): Response = try {
        route(session)
    } catch (t: Throwable) {
        text(Response.Status.INTERNAL_ERROR, t.message ?: "hata")
    }

    private fun route(s: IHTTPSession): Response {
        val uri = s.uri
        val local = s.remoteIpAddress == "127.0.0.1" || s.remoteIpAddress == "::1" || s.remoteIpAddress == "0:0:0:0:0:0:0:1"
        val allowed = local || s.headers["x-kod"] == code || s.parameters["kod"]?.firstOrNull() == code

        if (uri == "/" || uri == "/index.html") return asset("ui/telefon.html")
        if (uri.startsWith("/tv/")) return if (local) asset("ui/" + uri.removePrefix("/tv/")) else text(Response.Status.FORBIDDEN, "")
        if (uri.startsWith("/photos/")) {
            val name = uri.removePrefix("/photos/")
            if (!name.matches(Regex("[a-z0-9]+\\.(jpg|depth\\.png)"))) return notFound()
            return file(File(photos.dir, name), if (name.endsWith(".jpg")) "image/jpeg" else "image/png")
        }
        if (uri == "/api/check") return if (allowed) json(JSONObject().put("ok", true)) else text(Response.Status.UNAUTHORIZED, "kod yanlış")
        if (!uri.startsWith("/api/")) return notFound()
        if (!allowed) return text(Response.Status.UNAUTHORIZED, "kod yanlış")

        return when (uri) {
            "/api/state" -> json(
                JSONObject()
                    .put("address", address()?.let { "$it:$PORT" } ?: JSONObject.NULL)
                    .put("code", if (local) code else "")
                    .put("photos", photos.list())
                    .put("status", photos.status())
                    .put("feeds", feeds.json()),
            )
            "/api/photos" -> {
                if (s.method != Method.POST) return text(Response.Status.METHOD_NOT_ALLOWED, "")
                val files = HashMap<String, String>()
                s.parseBody(files)
                val tmp = files["foto"] ?: return text(Response.Status.BAD_REQUEST, "fotoğraf yok")
                val id = photos.add(File(tmp)) ?: return text(Response.Status.BAD_REQUEST, "fotoğraf okunamadı")
                json(JSONObject().put("id", id))
            }
            "/api/photos/delete" -> {
                photos.delete(s.parameters["id"]?.firstOrNull().orEmpty())
                json(JSONObject().put("ok", true))
            }
            else -> notFound()
        }
    }

    private fun asset(path: String): Response {
        if (path.contains("..")) return notFound()
        val mime = when {
            path.endsWith(".html") -> "text/html; charset=utf-8"
            path.endsWith(".js") -> "application/javascript"
            path.endsWith(".css") -> "text/css"
            path.endsWith(".png") -> "image/png"
            path.endsWith(".ttf") -> "font/ttf"
            path.endsWith(".json") -> "application/json; charset=utf-8"
            else -> "application/octet-stream"
        }
        return runCatching {
            val bytes = context.assets.open(path).use { it.readBytes() }
            newFixedLengthResponse(Response.Status.OK, mime, bytes.inputStream(), bytes.size.toLong()).also { it.addHeader("Cache-Control", "no-cache") }
        }.getOrElse { notFound() }
    }

    private fun file(f: File, mime: String): Response =
        if (!f.exists()) notFound() else newFixedLengthResponse(Response.Status.OK, mime, FileInputStream(f), f.length()).cached()

    private fun Response.cached() = apply { addHeader("Cache-Control", "max-age=86400") }
    private fun json(o: JSONObject) = newFixedLengthResponse(Response.Status.OK, "application/json; charset=utf-8", o.toString())
    private fun text(st: Response.Status, msg: String) = newFixedLengthResponse(st, "text/plain; charset=utf-8", msg)
    private fun notFound() = text(Response.Status.NOT_FOUND, "bulunamadı")

    companion object {
        const val PORT = 8080

        /** Kutunun evdeki ağdaki adresi (Wi-Fi ya da kablo). */
        fun address(): String? = runCatching {
            NetworkInterface.getNetworkInterfaces().toList().filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.toList() }.filterIsInstance<Inet4Address>()
                .firstOrNull { it.isSiteLocalAddress }?.hostAddress
        }.getOrNull()
    }
}

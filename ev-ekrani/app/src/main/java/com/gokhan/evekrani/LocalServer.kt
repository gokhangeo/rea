package com.gokhan.evekrani

import android.content.Context
import android.os.Handler
import android.os.Looper
import fi.iki.elonen.NanoHTTPD
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import kotlin.random.Random

/**
 * Evdeki ağda 8080 portunda çalışan küçük sunucu:
 *  - /tv/...   TV ekranının kendisi (yalnızca kutunun içinden açılır);
 *  - /         telefondan fotoğraf ve uygulama çubuğu düzenleme sayfası (ekranda yazan 4 haneli kodla);
 *  - /api/...  ikisinin de kullandığı veriler.
 */
class LocalServer(private val context: Context, private val apps: AppCatalog, private val photos: PhotoStore, private val feeds: Feeds) : NanoHTTPD(PORT) {
    private val main = Handler(Looper.getMainLooper())
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
        if (uri.startsWith("/icon/")) {
            val bytes = apps.icon(uri.removePrefix("/icon/").removeSuffix(".png")) ?: return notFound()
            return newFixedLengthResponse(Response.Status.OK, "image/png", bytes.inputStream(), bytes.size.toLong()).cached()
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
                    .put("dock", apps.json(apps.dock()))
                    .put("feeds", feeds.json()),
            )
            "/api/apps" -> json(JSONObject().put("apps", apps.json(apps.installed())))
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
            "/api/dock" -> {
                if (s.method != Method.POST) return text(Response.Status.METHOD_NOT_ALLOWED, "")
                val arr = JSONArray(body(s))
                apps.setDock((0 until arr.length()).map { arr.getString(it) })
                json(JSONObject().put("ok", true))
            }
            "/api/launch" -> {
                if (!local) return text(Response.Status.FORBIDDEN, "")
                val pkg = s.parameters["pkg"]?.firstOrNull().orEmpty()
                main.post { runCatching { apps.launch(pkg) } }
                json(JSONObject().put("ok", true))
            }
            else -> notFound()
        }
    }

    private fun body(s: IHTTPSession): String {
        val len = s.headers["content-length"]?.toIntOrNull() ?: 0
        val buf = ByteArray(len)
        var read = 0
        while (read < len) {
            val n = s.inputStream.read(buf, read, len - read)
            if (n < 0) break
            read += n
        }
        return String(buf, 0, read, Charsets.UTF_8)
    }

    private fun asset(path: String): Response {
        if (path.contains("..")) return notFound()
        val mime = when {
            path.endsWith(".html") -> "text/html; charset=utf-8"
            path.endsWith(".js") -> "application/javascript"
            path.endsWith(".css") -> "text/css"
            path.endsWith(".png") -> "image/png"
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

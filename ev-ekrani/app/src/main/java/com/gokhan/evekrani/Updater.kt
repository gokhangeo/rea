package com.gokhan.evekrani

import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import org.json.JSONArray
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Kendini güncelleme: ana ekran öne geldikçe (en fazla yarım saatte bir) GitHub'daki son Ev Ekranı sürümüne bakar.
 * Yeni sürüm varsa APK'yı arka planda indirir; bitince ana ekran "Güncelle" penceresini gösterir. Kurulum Android'in
 * kendi onay penceresiyle yapılır, sessiz kurulum yoktur.
 *
 * Sürüm numarası GitHub Actions'ın derleme numarasıdır: etiket ev-ekrani-v0.2.N, uygulamanın versionCode'u N.
 */
object Updater {
    private const val TAG = "EvEkrani"
    private const val RELEASES = "https://api.github.com/repos/gokhangeo/rea/releases?per_page=15"
    private val TAG_RE = Regex("^ev-ekrani-v0\\.2\\.(\\d+)$")
    private const val CHECK_MS = 30 * 60_000L
    private const val SNOOZE_MS = 6 * 3600_000L

    /** İndirilmiş ve kurulmayı bekleyen sürüm. */
    class Ready(@JvmField val version: Int, @JvmField val notes: String)

    @Volatile private var busy = false
    private val main = Handler(Looper.getMainLooper())

    private fun prefs(c: Context) = c.getSharedPreferences("guncelleme", Context.MODE_PRIVATE)
    private fun dir(c: Context) = File(c.filesDir, "guncelleme")
    private fun apk(c: Context, version: Int) = File(dir(c), "EvEkrani-$version.apk")

    @JvmStatic
    fun currentVersion(c: Context): Int = runCatching {
        val info = c.packageManager.getPackageInfo(c.packageName, 0)
        if (Build.VERSION.SDK_INT >= 28) info.longVersionCode.toInt() else @Suppress("DEPRECATION") info.versionCode
    }.getOrDefault(0)

    /** Ana ekran öne gelince çağrılır. Yeni sürüm indirildiğinde [onReady] ana iş parçacığında çalışır. */
    @JvmStatic
    fun check(context: Context, onReady: Runnable) {
        val c = context.applicationContext
        val p = prefs(c)
        val now = System.currentTimeMillis()
        if (busy || now - p.getLong("checkedAt", 0) < CHECK_MS) return
        busy = true
        p.edit().putLong("checkedAt", now).apply()
        Thread {
            try {
                if (refresh(c)) main.post(onReady)
            } catch (e: Exception) {
                Log.w(TAG, "güncelleme denetlenemedi", e)
            } finally {
                busy = false
            }
        }.start()
    }

    /** Kurulmayı bekleyen sürüm; yoksa, ertelendiyse ya da zaten kuruluysa null. */
    @JvmStatic
    fun ready(context: Context): Ready? {
        val p = prefs(context)
        val v = p.getInt("ready", 0)
        if (v <= currentVersion(context) || !apk(context, v).isFile) return null
        if (System.currentTimeMillis() < p.getLong("snoozeUntil", 0)) return null
        return Ready(v, p.getString("notes", "").orEmpty())
    }

    /** "Sonra": pencere birkaç saat sonra yeniden gelir. */
    @JvmStatic
    fun snooze(context: Context) {
        prefs(context).edit().putLong("snoozeUntil", System.currentTimeMillis() + SNOOZE_MS).apply()
    }

    /** true: yeni bir sürüm indirildi ve kurulmaya hazır. */
    private fun refresh(c: Context): Boolean {
        val current = currentVersion(c)
        val p = prefs(c)
        val releases = JSONArray(get(RELEASES))
        var best = 0
        var url: String? = null
        var size = 0L
        var notes = ""
        for (i in 0 until releases.length()) {
            val r = releases.getJSONObject(i)
            if (r.optBoolean("draft") || r.optBoolean("prerelease")) continue
            val n = TAG_RE.find(r.optString("tag_name"))?.groupValues?.get(1)?.toIntOrNull() ?: continue
            if (n <= best) continue
            val assets = r.optJSONArray("assets") ?: continue
            for (j in 0 until assets.length()) {
                val a = assets.getJSONObject(j)
                if (!a.optString("name").endsWith(".apk")) continue
                best = n
                url = a.getString("browser_download_url")
                size = a.optLong("size")
                notes = r.optString("body").lineSequence().firstOrNull().orEmpty().trim()
                break
            }
        }
        // Eski indirmeleri temizle; kurulu olandan yeni değilse iş yok.
        dir(c).listFiles()?.forEach { if (it.name != "EvEkrani-$best.apk") it.delete() }
        if (best <= current || url == null || best == p.getInt("refused", 0)) return false
        val file = apk(c, best)
        if (p.getInt("ready", 0) == best && file.isFile && file.length() == size) return false
        download(url, file, size)
        // Başka bir paket ya da beklenmeyen sürüm çıkarsa kurulmaz.
        val info = c.packageManager.getPackageArchiveInfo(file.path, 0)
        val code = info?.let { if (Build.VERSION.SDK_INT >= 28) it.longVersionCode.toInt() else @Suppress("DEPRECATION") it.versionCode }
        if (info?.packageName != c.packageName || code != best) {
            file.delete()
            error("indirilen APK beklenen sürüm değil")
        }
        p.edit().putInt("ready", best).putString("notes", notes).remove("snoozeUntil").apply()
        Log.i(TAG, "güncelleme hazır: 0.2.$best")
        return true
    }

    private fun download(url: String, file: File, size: Long) {
        file.parentFile?.mkdirs()
        val part = File(file.path + ".part")
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 60_000
        conn.setRequestProperty("User-Agent", "EvEkrani/1.0")
        try {
            if (conn.responseCode != 200) error("HTTP ${conn.responseCode}")
            conn.inputStream.use { input -> part.outputStream().use { input.copyTo(it, 64 * 1024) } }
        } finally {
            conn.disconnect()
        }
        if (size > 0 && part.length() != size) {
            part.delete()
            error("indirme yarım kaldı")
        }
        if (!part.renameTo(file)) error("dosya taşınamadı")
    }

    private fun get(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 15_000
        conn.setRequestProperty("User-Agent", "EvEkrani/1.0")
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        try {
            if (conn.responseCode != 200) error("HTTP ${conn.responseCode}")
            return conn.inputStream.bufferedReader().readText()
        } finally {
            conn.disconnect()
        }
    }

    /**
     * "Güncelle": APK'yı Android'in yükleyicisine verir; Android kendi "Yükle" penceresini açar. Ev Ekranı'nın
     * uygulama yükleme izni yoksa önce o ayar açılır, izin verilip geri dönülünce pencere yeniden gelir.
     */
    @JvmStatic
    fun install(context: Context, ready: Ready) {
        val c = context.applicationContext
        if (Build.VERSION.SDK_INT >= 26 && !c.packageManager.canRequestPackageInstalls()) {
            val settings = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${c.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(settings)
                Toast.makeText(c, "Ev Ekranı'na uygulama yükleme izni verin, sonra Geri ile dönün.", Toast.LENGTH_LONG).show()
                return
            } catch (_: ActivityNotFoundException) {
                // Ayar ekranı yoksa yükleyici izni kendisi sorar.
            }
        }
        val file = apk(c, ready.version)
        Toast.makeText(c, "Güncelleme hazırlanıyor…", Toast.LENGTH_SHORT).show()
        Thread {
            try {
                val installer = c.packageManager.packageInstaller
                val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
                params.setAppPackageName(c.packageName)
                val id = installer.createSession(params)
                installer.openSession(id).use { s ->
                    s.openWrite("EvEkrani.apk", 0, file.length()).use { out ->
                        file.inputStream().use { it.copyTo(out, 64 * 1024) }
                        s.fsync(out)
                    }
                    val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
                    val result = Intent(c, UpdateReceiver::class.java).putExtra("version", ready.version)
                    s.commit(PendingIntent.getBroadcast(c, id, result, flags).intentSender)
                }
            } catch (e: Exception) {
                Log.e(TAG, "güncelleme başlatılamadı", e)
                main.post { Toast.makeText(c, "Güncelleme başlatılamadı.", Toast.LENGTH_LONG).show() }
            }
        }.start()
    }

    /** İmza farkı gibi kalıcı bir hatada aynı sürüm bir daha önerilmez. */
    internal fun refuse(c: Context, version: Int) {
        prefs(c).edit().putInt("refused", version).remove("ready").apply()
        apk(c, version).delete()
    }
}

/** Android yükleyicisinin cevabı: onay penceresini açar ya da hatayı söyler. */
class UpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
                context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            PackageInstaller.STATUS_SUCCESS -> Unit
            PackageInstaller.STATUS_FAILURE_ABORTED -> Unit   // "İptal" seçildi; pencere sonra yeniden gelir
            PackageInstaller.STATUS_FAILURE_CONFLICT, PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> {
                Updater.refuse(context, intent.getIntExtra("version", 0))
                Toast.makeText(context, "Güncelleme kurulamadı: yeni sürüm farklı anahtarla imzalanmış. Eski sürümü kaldırıp yenisini elle kurun.", Toast.LENGTH_LONG).show()
            }
            else -> {
                Log.w("EvEkrani", "güncelleme kurulamadı: $status ${intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)}")
                Toast.makeText(context, "Güncelleme kurulamadı.", Toast.LENGTH_LONG).show()
            }
        }
    }
}

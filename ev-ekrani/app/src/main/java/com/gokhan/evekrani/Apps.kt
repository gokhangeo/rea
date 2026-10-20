package com.gokhan.evekrani

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream

data class TvApp(val pkg: String, val label: String)

/**
 * Uygulama çubuğu: kutuda yüklü uygulamalardan seçilenler, seçilen sırayla. Liste paket adlarıyla saklanır;
 * ilk açılışta DEFAULT_DOCK içindeki adlarla eşleşen uygulamalar konur.
 */
class AppCatalog(private val context: Context) {
    private val pm = context.packageManager
    private val prefs = context.getSharedPreferences("dock", Context.MODE_PRIVATE)
    private val iconCache = HashMap<String, ByteArray>()

    fun installed(): List<TvApp> {
        val found = LinkedHashMap<String, TvApp>()
        for (category in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(category)
            for (ri in pm.queryIntentActivities(intent, 0)) {
                val pkg = ri.activityInfo.packageName
                if (pkg == context.packageName || pkg in found) continue
                found[pkg] = TvApp(pkg, ri.loadLabel(pm).toString().trim())
            }
        }
        return found.values.sortedBy { it.label.lowercase() }
    }

    fun dock(): List<TvApp> {
        val all = installed().associateBy { it.pkg }
        val saved = prefs.getString("pkgs", null)
        val pkgs = if (saved != null) {
            saved.split("\n").filter { it.isNotBlank() }
        } else {
            DEFAULT_DOCK.mapNotNull { names -> all.values.firstOrNull { app -> names.any { app.label.equals(it, ignoreCase = true) } }?.pkg }
                .also { setDock(it) }
        }
        return pkgs.mapNotNull { all[it] }
    }

    fun setDock(pkgs: List<String>) {
        prefs.edit().putString("pkgs", pkgs.distinct().joinToString("\n")).apply()
    }

    fun launch(pkg: String): Boolean {
        val intent = pm.getLeanbackLaunchIntentForPackage(pkg) ?: pm.getLaunchIntentForPackage(pkg) ?: return false
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return true
    }

    /** Afiş (16:9) varsa o, yoksa simge; PNG olarak. */
    fun icon(pkg: String): ByteArray? = synchronized(iconCache) {
        iconCache[pkg] ?: runCatching {
            val ai = pm.getApplicationInfo(pkg, 0)
            val launch = pm.getLeanbackLaunchIntentForPackage(pkg)
            val banner = launch?.let { pm.getActivityBanner(it) } ?: ai.loadBanner(pm)
            val (drawable, banner16x9) = if (banner != null) banner to true else ai.loadIcon(pm) to false
            png(drawable, if (banner16x9) 320 else 160, if (banner16x9) 180 else 160)
        }.getOrNull()?.also { iconCache[pkg] = it }
    }

    private fun png(d: Drawable, w: Int, h: Int): ByteArray {
        val bmp = if (d is BitmapDrawable && d.bitmap != null) d.bitmap else {
            Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { b ->
                val c = Canvas(b)
                d.setBounds(0, 0, w, h)
                d.draw(c)
            }
        }
        return ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
    }

    fun json(apps: List<TvApp>): JSONArray = JSONArray().apply {
        apps.forEach { put(JSONObject().put("pkg", it.pkg).put("label", it.label)) }
    }

    companion object {
        private val DEFAULT_DOCK = listOf(
            listOf("YouTube"), listOf("Netflix"), listOf("Prime Video", "Amazon Prime Video"), listOf("TRT İzle", "TRT", "tabii"),
            listOf("Disney+", "Disney Plus"), listOf("Spotify"), listOf("beIN CONNECT", "TOD"),
        )
    }
}

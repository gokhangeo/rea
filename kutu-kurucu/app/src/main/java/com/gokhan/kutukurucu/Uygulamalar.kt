package com.gokhan.kutukurucu

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File

/** Kutudaki uygulamalar: liste, simge, açma, kaldırma ve APK'sını indirme. */
object Uygulamalar {
    /** Operatör kutusunun kendi uygulamaları ve sistem uygulamaları sayfadan kaldırılamaz. */
    private val KORUMALI = Regex("turkcell|skyworth|tvplus|superonline|^com\\.android\\.|^android$|^com\\.google\\.android\\.(tvlauncher|apps\\.tv\\.launcherx|gms|gsf)")

    private val simgeler = HashMap<String, ByteArray>()

    fun korumali(c: Context, paket: String, ai: ApplicationInfo?): Boolean =
        paket == c.packageName || KORUMALI.containsMatchIn(paket) ||
            (ai != null && ai.flags and ApplicationInfo.FLAG_SYSTEM != 0)

    fun json(c: Context): JSONArray {
        val pm = c.packageManager
        val satirlar = ArrayList<JSONObject>()
        @Suppress("DEPRECATION")
        for (ai in pm.getInstalledApplications(0)) {
            val p = ai.packageName
            val acilabilir = pm.getLeanbackLaunchIntentForPackage(p) != null || pm.getLaunchIntentForPackage(p) != null
            val sistem = ai.flags and ApplicationInfo.FLAG_SYSTEM != 0
            val guncel = ai.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0
            if (!acilabilir && sistem && !guncel) continue
            val bilgi = runCatching { pm.getPackageInfo(p, 0) }.getOrNull()
            satirlar += JSONObject()
                .put("paket", p)
                .put("ad", ai.loadLabel(pm).toString())
                .put("surum", bilgi?.versionName.orEmpty())
                .put("sistem", sistem)
                .put("korumali", korumali(c, p, ai))
                .put("acilabilir", acilabilir)
                .put("parcali", !ai.splitSourceDirs.isNullOrEmpty())
                .put("boyut", File(ai.sourceDir).length())
        }
        satirlar.sortWith(compareBy<JSONObject>({ it.getBoolean("sistem") }, { it.getString("ad").lowercase() }))
        return JSONArray(satirlar)
    }

    fun simge(c: Context, paket: String): ByteArray? = synchronized(simgeler) {
        simgeler[paket] ?: runCatching {
            val d = c.packageManager.getApplicationIcon(paket)
            val b = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
            d.setBounds(0, 0, 96, 96)
            d.draw(Canvas(b))
            ByteArrayOutputStream().also { b.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        }.getOrNull()?.also { simgeler[paket] = it }
    }

    fun ac(c: Context, paket: String): Boolean {
        val pm = c.packageManager
        val i = pm.getLeanbackLaunchIntentForPackage(paket) ?: pm.getLaunchIntentForPackage(paket) ?: return false
        return runCatching { c.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
    }

    /** Android'in kaldırma penceresini TV'de açar; onay kumandayla verilir. */
    fun kaldir(c: Context, paket: String): String? {
        val ai = runCatching { c.packageManager.getApplicationInfo(paket, 0) }.getOrNull() ?: return "Uygulama bulunamadı."
        if (korumali(c, paket, ai)) return "Bu uygulama sistemin ya da operatörün; kaldırılmaz."
        @Suppress("DEPRECATION")
        val i = Intent(Intent.ACTION_UNINSTALL_PACKAGE, Uri.parse("package:$paket")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return if (runCatching { c.startActivity(i) }.isSuccess) null else "Kaldırma penceresi açılamadı."
    }

    /** Uygulamanın kurulu APK dosyası (yedek almak ya da öbür kutuya kurmak için). */
    fun apk(c: Context, paket: String): Pair<File, String>? {
        val pm = c.packageManager
        val ai = runCatching { pm.getApplicationInfo(paket, 0) }.getOrNull() ?: return null
        val surum = runCatching { pm.getPackageInfo(paket, 0).versionName }.getOrNull().orEmpty()
        val ad = (ai.loadLabel(pm).toString() + (if (surum.isNotEmpty()) "-$surum" else ""))
            .replace(Regex("[^A-Za-z0-9._-]+"), "_") + ".apk"
        return File(ai.sourceDir) to ad
    }
}

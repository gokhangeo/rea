package com.gokhan.kutukurucu

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.provider.Settings
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import org.json.JSONArray
import org.json.JSONObject

/**
 * Ana ekran seçimi. Android TV (özellikle operatör kutuları) seçilen ana ekran uygulamasını çoğu zaman yok sayar ve
 * Home tuşu yine hazır ana ekranı açar. Bu yüzden seçim Home tuşu üzerinden yapılır: AnaEkranServisi tuşu yakalar ve
 * seçilen ana ekranı açar. Hazır ana ekran seçilirse servis tuşa dokunmaz, kutu eskisi gibi çalışır.
 *
 * Ev Ekranı'nın da kendi Home tuşu servisi var (com.gokhan.evekrani); başka bir ana ekran seçilince o servis
 * kapatılır, Ev Ekranı seçilince yeniden açılır ve tuşu o yönetir. Ev Ekranı açılışta servisini kendisi geri açtığı için
 * seçim açılıştan sonra birkaç kez yeniden uygulanır (SunucuServisi).
 */
object AnaEkran {
    const val EV_EKRANI = "com.gokhan.evekrani"
    private const val EV_SERVISI = "com.gokhan.evekrani/com.gokhan.evekrani.HomeButtonService"

    private fun prefs(c: Context) = c.getSharedPreferences("anaekran", Context.MODE_PRIVATE)

    /** Seçilen ana ekranın paketi; "" ya da null: kutunun hazır ana ekranı. */
    fun secili(c: Context): String = prefs(c).getString("secili", "").orEmpty()

    private fun evdeAnaIntent() = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)

    /** Kutudaki ana ekran olabilen uygulamalar ve hangisinin hazır (sistem) ana ekranı olduğu. */
    fun json(c: Context): JSONObject {
        val pm = c.packageManager
        val adaylar = JSONArray()
        val gorulen = HashSet<String>()
        @Suppress("DEPRECATION")
        val liste = pm.queryIntentActivities(evdeAnaIntent(), PackageManager.MATCH_ALL)
        for (r in liste) {
            val p = r.activityInfo.packageName
            if (p == c.packageName || p == "com.android.settings" || p == "com.android.tv.settings" || !gorulen.add(p)) continue
            val ai = r.activityInfo.applicationInfo
            adaylar.put(
                JSONObject().put("paket", p).put("ad", r.loadLabel(pm).toString())
                    .put("sistem", ai.flags and ApplicationInfo.FLAG_SYSTEM != 0),
            )
        }
        return JSONObject()
            .put("secili", secili(c))
            .put("adaylar", adaylar)
            .put("servis", servisAcik(c))
            .put("ayarYazabilir", Izinler.ayar(c))
    }

    /** Sayfadan seçim: "" hazır ana ekrana döner. Seçilen ana ekran hemen açılır. */
    fun sec(c: Context, paket: String): String? {
        if (paket.isNotEmpty() && hedefIntent(c, paket) == null) return "Bu uygulama ana ekran olarak açılamıyor."
        prefs(c).edit().putString("secili", paket).apply()
        uygula(c)
        if (paket.isNotEmpty()) ac(c)
        return when {
            paket.isNotEmpty() && paket != EV_EKRANI && !servisAcik(c) ->
                "Seçim kaydedildi ama Home tuşu servisi kapalı. TV'de Kutu Kurucu'yu açıp \"Home tuşu servisini aç\"a basın."
            !Izinler.ayar(c) && runCatching { c.packageManager.getPackageInfo(EV_EKRANI, 0) }.isSuccess ->
                "Seçim kaydedildi. Ev Ekranı'nın Home servisini değiştirmek için ilk kurulum iznini (README) verin."
            else -> null
        }
    }

    /**
     * Servislerin seçimle uyumlu olmasını sağlar. WRITE_SECURE_SETTINGS (ADB ile bir kez verilir) yoksa yalnızca
     * Kutu Kurucu'nun servisi elle açılabilir; Ev Ekranı'nın servisine dokunulamaz.
     */
    fun uygula(c: Context) {
        if (!Izinler.ayar(c)) return
        val cr = c.contentResolver
        val ben = ComponentName(c, AnaEkranServisi::class.java).flattenToString()
        val acik = Settings.Secure.getString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
            .split(':').filter { it.isNotBlank() }.toMutableList()
        val once = acik.toList()
        val s = secili(c)
        val evKurulu = runCatching { c.packageManager.getPackageInfo(EV_EKRANI, 0) }.isSuccess
        if (s.isNotEmpty() && s != EV_EKRANI) {
            acik.removeAll { it.equals(EV_SERVISI, true) }
            if (acik.none { it.equals(ben, true) }) acik.add(ben)
        } else if (s == EV_EKRANI) {
            acik.removeAll { it.equals(ben, true) }
            if (evKurulu && acik.none { it.equals(EV_SERVISI, true) }) acik.add(EV_SERVISI)
        } else {
            // Hazır ana ekran: Home tuşunu hiçbir servis yakalamaz.
            acik.removeAll { it.equals(ben, true) || it.equals(EV_SERVISI, true) }
        }
        if (acik == once) return
        try {
            Settings.Secure.putString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, acik.joinToString(":"))
            if (acik.isNotEmpty()) Settings.Secure.putInt(cr, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
        } catch (_: SecurityException) {
        }
    }

    fun servisAcik(c: Context): Boolean {
        val ben = ComponentName(c, AnaEkranServisi::class.java).flattenToString()
        return Settings.Secure.getString(c.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
            .split(':').any { it.equals(ben, true) }
    }

    /** Home tuşunun açacağı ekran; hazır ana ekran ya da Ev Ekranı seçiliyse null (tuşa dokunulmaz). */
    fun hedef(c: Context): Intent? {
        val s = secili(c)
        if (s.isEmpty() || s == EV_EKRANI) return null
        return hedefIntent(c, s)
    }

    private fun hedefIntent(c: Context, paket: String): Intent? {
        val pm = c.packageManager
        val ana = evdeAnaIntent().setPackage(paket)
        @Suppress("DEPRECATION")
        val r = pm.queryIntentActivities(ana, 0).firstOrNull()
        val i = if (r != null) ana.setComponent(ComponentName(paket, r.activityInfo.name))
        else pm.getLeanbackLaunchIntentForPackage(paket) ?: pm.getLaunchIntentForPackage(paket)
        return i?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
    }

    /** Seçilen ana ekranı şimdi açar (hazır seçiliyse Home'a basılmış gibi). */
    fun ac(c: Context) {
        val s = secili(c)
        val i = if (s.isEmpty()) evdeAnaIntent().addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) else hedefIntent(c, s)
        runCatching { if (i != null) c.startActivity(i) }
    }
}

/** Home tuşunu seçilen ana ekrana yönlendirir; başka tuşlara ve ekran içeriğine dokunmaz. */
class AnaEkranServisi : AccessibilityService() {
    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode != KeyEvent.KEYCODE_HOME) return false
        val hedef = AnaEkran.hedef(this) ?: return false
        if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) {
            // Ev Ekranı kendini açıkken servisini geri açmış olabilir.
            AnaEkran.uygula(this)
            runCatching { startActivity(hedef) }
        }
        return true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {}
    override fun onInterrupt() {}
}

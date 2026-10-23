package com.gokhan.kutukurucu

import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import org.json.JSONObject

/** Kutu Kurucu'nun ihtiyaç duyduğu izinlerin durumu; TV ekranında ve sayfada gösterilir. */
object Izinler {
    /** Bilinmeyen kaynaklardan uygulama yükleme. */
    fun yukleme(c: Context) = c.packageManager.canRequestPackageInstalls()

    /** Diğer uygulamaların üstünde gösterme: arka plandayken onay penceresini açabilmek için. */
    fun ustte(c: Context) = Settings.canDrawOverlays(c)

    /** Erişilebilirlik ayarını kendisi yazabilme (ADB: pm grant … WRITE_SECURE_SETTINGS). */
    fun ayar(c: Context) = c.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") == PackageManager.PERMISSION_GRANTED

    fun json(c: Context): JSONObject = JSONObject()
        .put("yukleme", yukleme(c))
        .put("ustte", ustte(c))
        .put("ayar", ayar(c))
        .put("servis", AnaEkran.servisAcik(c))
}

package com.gokhan.winkeeper

import android.Manifest
import android.app.ActivityManager
import android.app.AppOpsManager
import android.app.usage.StorageStatsManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.os.Process
import android.os.StatFs
import android.os.SystemClock
import android.os.storage.StorageManager
import android.provider.Settings
import android.util.Base64
import android.webkit.JavascriptInterface
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Sayfanın çağırdığı cihaz işleri. Uzun süren işler arka planda çalışır ve sonucu
 * [MainActivity.bitti] ile sayfaya döner (window.__done(id, json)).
 */
class Kopru(private val a: MainActivity) {
    private val pm: PackageManager = a.packageManager
    private val is_ = Executors.newFixedThreadPool(2)
    private val iptal = AtomicBoolean(false)
    private val ikonlar = java.util.concurrent.ConcurrentHashMap<String, String>()

    private fun arka(id: Int, is_bloku: () -> Any) {
        is_.execute {
            val sonuc = try {
                is_bloku().toString()
            } catch (e: Throwable) {
                JSONObject().put("hata", e.message ?: e.javaClass.simpleName).toString()
            }
            a.bitti(id, sonuc)
        }
    }

    private fun ac(intent: Intent): Boolean = try {
        a.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
    } catch (e: Exception) { false }

    // ---------------- İzinler ----------------

    private fun dosyaIzni(): Boolean =
        if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager()
        else a.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

    private fun kullanimIzni(): Boolean {
        val ops = a.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val m = if (Build.VERSION.SDK_INT >= 29)
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), a.packageName)
        else @Suppress("DEPRECATION") ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), a.packageName)
        return m == AppOpsManager.MODE_ALLOWED
    }

    @JavascriptInterface
    fun izinler(): String = JSONObject()
        .put("dosya", dosyaIzni())
        .put("kullanim", kullanimIzni())
        .put("sdk", Build.VERSION.SDK_INT)
        .toString()

    @JavascriptInterface
    fun dosyaIzniIste() {
        if (Build.VERSION.SDK_INT >= 30) {
            if (!ac(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${a.packageName}"))))
                ac(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
        } else {
            a.runOnUiThread {
                a.requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE), 1)
            }
        }
    }

    @JavascriptInterface
    fun kullanimIzniIste() {
        if (!ac(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, Uri.parse("package:${a.packageName}"))))
            ac(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
    }

    // ---------------- Durum ----------------

    private fun bellek(): ActivityManager.MemoryInfo {
        val am = a.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
    }

    @JavascriptInterface
    fun durum(): String {
        val m = bellek()
        val ic = StatFs(Environment.getDataDirectory().path)
        val pil = a.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val seviye = pil?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val olcek = pil?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val durumKod = pil?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val sicaklik = (pil?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10.0
        val gerilim = (pil?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0) / 1000.0
        val saglik = when (pil?.getIntExtra(BatteryManager.EXTRA_HEALTH, 0)) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "İyi"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Aşırı ısınmış"
            BatteryManager.BATTERY_HEALTH_DEAD -> "Ölü"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Aşırı gerilim"
            BatteryManager.BATTERY_HEALTH_COLD -> "Soğuk"
            else -> "Bilinmiyor"
        }
        val cm = a.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        val ag = when {
            caps == null -> "Bağlantı yok"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Mobil veri"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            else -> "Bağlı"
        }
        val dm = a.resources.displayMetrics
        return JSONObject()
            .put("cihaz", "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}")
            .put("android", Build.VERSION.RELEASE)
            .put("sdk", Build.VERSION.SDK_INT)
            .put("islemci", if (Build.VERSION.SDK_INT >= 31) Build.SOC_MODEL else Build.HARDWARE)
            .put("cekirdek", Runtime.getRuntime().availableProcessors())
            .put("ramToplam", m.totalMem).put("ramBos", m.availMem).put("ramAz", m.lowMemory)
            .put("depoToplam", ic.totalBytes).put("depoBos", ic.availableBytes)
            .put("pil", if (seviye >= 0) seviye * 100 / olcek else -1)
            .put("sarj", durumKod == BatteryManager.BATTERY_STATUS_CHARGING || durumKod == BatteryManager.BATTERY_STATUS_FULL)
            .put("pilSicaklik", sicaklik).put("pilGerilim", gerilim).put("pilSaglik", saglik)
            .put("ag", ag)
            .put("acikKalma", SystemClock.elapsedRealtime() / 1000)
            .put("ekran", "${dm.widthPixels}×${dm.heightPixels}")
            .put("cpu", cpuYuk())
            .toString()
    }

    private var oncekiCpu: LongArray? = null

    // /proc/stat yeni Android'lerde kapalı; okunamazsa -1 döner ve sayfa göstermez.
    private fun cpuYuk(): Int = try {
        val p = File("/proc/stat").bufferedReader().use { it.readLine() }.split(Regex("\\s+")).drop(1).mapNotNull { it.toLongOrNull() }
        val bos = p[3] + p.getOrElse(4) { 0 }
        val top = p.sum()
        val o = oncekiCpu
        oncekiCpu = longArrayOf(bos, top)
        if (o == null || top == o[1]) -1 else (100 - (bos - o[0]) * 100 / (top - o[1])).toInt().coerceIn(0, 100)
    } catch (e: Exception) { -1 }

    /** Arka planda duran kullanıcı uygulamalarının işlemlerini kapatır; kazanılan belleği döner. */
    @JavascriptInterface
    fun arkaPlaniTemizle(id: Int) = arka(id) {
        val am = a.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val once = bellek().availMem
        var n = 0
        for (ai in pm.getInstalledApplications(0)) {
            if (ai.packageName == a.packageName) continue
            if (ai.flags and ApplicationInfo.FLAG_SYSTEM != 0) continue
            try { am.killBackgroundProcesses(ai.packageName); n++ } catch (_: Exception) {}
        }
        Thread.sleep(800)
        val sonra = bellek().availMem
        JSONObject().put("uygulama", n).put("kazanilan", (sonra - once).coerceAtLeast(0))
    }

    // ---------------- Uygulamalar ----------------

    private fun ikon(d: Drawable, boyut: Int = 96): String {
        val bmp = Bitmap.createBitmap(boyut, boyut, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        d.setBounds(0, 0, boyut, boyut)
        d.draw(c)
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        bmp.recycle()
        return "data:image/png;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    private fun ikonu(ai: ApplicationInfo): String = ikonlar.getOrPut(ai.packageName) {
        try { ikon(ai.loadIcon(pm)) } catch (e: Exception) { "" }
    }

    private fun acilistaBaslayanlar(): Map<String, Int> {
        val flags = PackageManager.MATCH_DISABLED_COMPONENTS
        val res = HashMap<String, Int>()
        for (eylem in listOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_LOCKED_BOOT_COMPLETED, "android.intent.action.QUICKBOOT_POWERON")) {
            val liste = if (Build.VERSION.SDK_INT >= 33)
                pm.queryBroadcastReceivers(Intent(eylem), PackageManager.ResolveInfoFlags.of(flags.toLong()))
            else @Suppress("DEPRECATION") pm.queryBroadcastReceivers(Intent(eylem), flags)
            for (r in liste) {
                val p = r.activityInfo.packageName
                res[p] = (res[p] ?: 0) + 1
            }
        }
        return res
    }

    private fun uygulamaJson(ai: ApplicationInfo, sss: StorageStatsManager?, acilis: Map<String, Int>, gucYon: PowerManager): JSONObject {
        val pi = try { pm.getPackageInfo(ai.packageName, 0) } catch (e: Exception) { null }
        var uygulama = File(ai.sourceDir ?: "").length()
        var veri = 0L
        var onbellek = 0L
        var tam = false
        if (sss != null) try {
            val st = sss.queryStatsForPackage(StorageManager.UUID_DEFAULT, ai.packageName, Process.myUserHandle())
            uygulama = st.appBytes; veri = st.dataBytes; onbellek = st.cacheBytes; tam = true
        } catch (_: Exception) {}
        val sistem = ai.flags and ApplicationInfo.FLAG_SYSTEM != 0
        return JSONObject()
            .put("pkg", ai.packageName)
            .put("ad", ai.loadLabel(pm).toString())
            .put("surum", pi?.versionName ?: "")
            .put("kurulum", pi?.firstInstallTime ?: 0)
            .put("guncelleme", pi?.lastUpdateTime ?: 0)
            .put("sistem", sistem)
            .put("guncellenmisSistem", ai.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0)
            .put("etkin", ai.enabled)
            .put("durdurulmus", ai.flags and ApplicationInfo.FLAG_STOPPED != 0)
            .put("acilis", acilis[ai.packageName] ?: 0)
            .put("pilSiniri", !gucYon.isIgnoringBatteryOptimizations(ai.packageName))
            .put("acilabilir", pm.getLaunchIntentForPackage(ai.packageName) != null)
            .put("uygulamaBoyut", uygulama).put("veriBoyut", veri).put("onbellekBoyut", onbellek)
            .put("boyut", uygulama + veri + onbellek).put("boyutTam", tam)
            .put("ikon", ikonu(ai))
    }

    /** sistem=true ise sistem uygulamaları da gelir. */
    @JavascriptInterface
    fun uygulamalar(id: Int, sistem: Boolean) = arka(id) {
        val sss = if (kullanimIzni()) a.getSystemService(Context.STORAGE_STATS_SERVICE) as StorageStatsManager else null
        val acilis = acilistaBaslayanlar()
        val guc = a.getSystemService(Context.POWER_SERVICE) as PowerManager
        val arr = JSONArray()
        val hepsi = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        for (ai in hepsi) {
            if (ai.packageName == a.packageName) continue
            val s = ai.flags and ApplicationInfo.FLAG_SYSTEM != 0
            if (s && !sistem && ai.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP == 0) continue
            arr.put(uygulamaJson(ai, sss, acilis, guc))
        }
        JSONObject().put("liste", arr).put("boyutTam", sss != null)
    }

    /** Yalnızca açılışta başlamak için kayıt olan uygulamalar. */
    @JavascriptInterface
    fun baslangic(id: Int, sistem: Boolean) = arka(id) {
        val acilis = acilistaBaslayanlar()
        val guc = a.getSystemService(Context.POWER_SERVICE) as PowerManager
        val arr = JSONArray()
        for (p in acilis.keys) {
            if (p == a.packageName) continue
            val ai = try { pm.getApplicationInfo(p, 0) } catch (e: Exception) { continue }
            val s = ai.flags and ApplicationInfo.FLAG_SYSTEM != 0
            if (s && !sistem && ai.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP == 0) continue
            arr.put(uygulamaJson(ai, null, acilis, guc))
        }
        JSONObject().put("liste", arr)
    }

    @JavascriptInterface
    fun kaldir(pkg: String) {
        if (!ac(Intent(Intent.ACTION_DELETE, Uri.parse("package:$pkg"))))
            uygulamaBilgisi(pkg)
    }

    @JavascriptInterface
    fun yuklu(pkg: String): Boolean = try { pm.getApplicationInfo(pkg, 0); true } catch (e: Exception) { false }

    @JavascriptInterface
    fun uygulamaBilgisi(pkg: String) {
        ac(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg")))
    }

    @JavascriptInterface
    fun uygulamayiAc(pkg: String) {
        pm.getLaunchIntentForPackage(pkg)?.let { ac(it) }
    }

    @JavascriptInterface
    fun pilAyarlari(pkg: String) {
        // Uygulamaya özel pil sayfası her üreticide yok; sırayla dener.
        if (Build.VERSION.SDK_INT >= 31 && ac(Intent("android.settings.VIEW_ADVANCED_POWER_USAGE_DETAIL", Uri.parse("package:$pkg")))) return
        if (ac(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))) return
        uygulamaBilgisi(pkg)
    }

    @JavascriptInterface
    fun depolamaAyarlari() {
        if (!ac(Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS))) ac(Intent(Settings.ACTION_SETTINGS))
    }

    @JavascriptInterface
    fun ayarAc(eylem: String) {
        if (!ac(Intent(eylem))) ac(Intent(Settings.ACTION_SETTINGS))
    }

    @JavascriptInterface
    fun kopyala(metin: String) {
        val cb = a.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cb.setPrimaryClip(ClipData.newPlainText("WinKeeper", metin))
    }

    @JavascriptInterface
    fun bildir(metin: String) {
        a.runOnUiThread { Toast.makeText(a, metin, Toast.LENGTH_SHORT).show() }
    }

    // ---------------- Temizle ----------------

    private val kok: File get() = Environment.getExternalStorageDirectory()

    private fun guvenli(f: File): Boolean {
        val yol = try { f.canonicalPath } catch (e: Exception) { return false }
        val k = kok.canonicalPath
        val kendi = a.cacheDir.canonicalPath
        val kendiDis = a.externalCacheDir?.canonicalPath
        return (yol.startsWith("$k/") && yol != k) || yol.startsWith(kendi) || (kendiDis != null && yol.startsWith(kendiDis))
    }

    private fun boyut(f: File): Long {
        if (!f.isDirectory) return f.length()
        var t = 0L
        f.listFiles()?.forEach { t += boyut(it) }
        return t
    }

    private class Kategori(val id: String, val ad: String, val aciklama: String, val secili: Boolean) {
        val dosyalar = ArrayList<File>()
        var toplam = 0L
        fun ekle(f: File, b: Long) { dosyalar.add(f); toplam += b }
    }

    @JavascriptInterface
    fun iptalEt() { iptal.set(true) }

    @JavascriptInterface
    fun tara(id: Int) = arka(id) {
        iptal.set(false)
        val apk = Kategori("apk", "Kurulum dosyaları (APK)", "İndirilmiş, büyük olasılıkla zaten kurulmuş APK dosyaları.", true)
        val kucuk = Kategori("thumb", "Küçük resim önbelleği", "Galeri küçük resimleri; gerektiğinde yeniden oluşturulur.", true)
        val gecici = Kategori("tmp", "Geçici ve günlük dosyalar", ".tmp, .log, .temp ve yarım kalmış indirmeler.", true)
        val bos = Kategori("empty", "Boş klasörler", "İçinde hiçbir şey olmayan klasörler.", true)
        val onbellek = Kategori("cache", "Uygulama önbellekleri", "Erişilebilen uygulama önbellek klasörleri ve WinKeeper'ın kendi önbelleği.", true)
        val eski = Kategori("old", "Eski indirmeler (90+ gün)", "İndirilenler klasöründe 3 aydır dokunulmamış dosyalar. Gözden geçirin.", false)
        val buyuk = Kategori("big", "Büyük dosyalar (100 MB+)", "Videolar, arşivler, yedekler. Silmeden önce gözden geçirin.", false)

        val simdi = System.currentTimeMillis()
        val indirilen = File(kok, Environment.DIRECTORY_DOWNLOADS).canonicalPath
        var sayac = 0

        fun gez(d: File, derinlik: Int) {
            if (iptal.get() || derinlik > 24) return
            val cocuklar = d.listFiles() ?: return
            if (cocuklar.isEmpty() && derinlik > 0) { bos.ekle(d, 0); return }
            for (f in cocuklar) {
                if (iptal.get()) return
                val ad = f.name.lowercase()
                if (f.isDirectory) {
                    // Android/data ve obb diğer uygulamalara ait; Android 11+ zaten kapalı.
                    if (derinlik == 0 && ad == "android") {
                        File(f, "data").listFiles()?.forEach { pd ->
                            val c = File(pd, "cache")
                            if (c.isDirectory) { val b = boyut(c); if (b > 0) onbellek.ekle(c, b) }
                        }
                        continue
                    }
                    if (ad == ".thumbnails" || ad == ".thumbs") { val b = boyut(f); if (b > 0) kucuk.ekle(f, b); continue }
                    gez(f, derinlik + 1)
                    continue
                }
                val b = f.length()
                if (++sayac % 300 == 0) a.ilerleme("tara", JSONObject().put("sayi", sayac).put("yol", f.parent ?: "").toString())
                when {
                    ad.endsWith(".apk") || ad.endsWith(".apks") || ad.endsWith(".xapk") -> apk.ekle(f, b)
                    ad.endsWith(".tmp") || ad.endsWith(".temp") || ad.endsWith(".log") || ad.endsWith(".crdownload") || ad.endsWith(".part") -> gecici.ekle(f, b)
                    b >= 100L * 1024 * 1024 -> buyuk.ekle(f, b)
                    f.parentFile?.canonicalPath == indirilen && simdi - f.lastModified() > 90L * 86400000 -> eski.ekle(f, b)
                }
            }
        }

        if (dosyaIzni()) gez(kok, 0)
        listOfNotNull(a.cacheDir, a.externalCacheDir).forEach { c ->
            c.listFiles()?.forEach { f -> val b = boyut(f); if (b > 0) onbellek.ekle(f, b) }
        }

        val arr = JSONArray()
        for (k in listOf(onbellek, kucuk, gecici, apk, bos, eski, buyuk)) {
            val dosyalar = JSONArray()
            k.dosyalar.sortedByDescending { if (it.isDirectory) 0L else it.length() }.forEach { f ->
                dosyalar.put(JSONObject().put("yol", f.path).put("ad", f.name).put("boyut", if (f.isDirectory) boyut(f) else f.length())
                    .put("klasor", f.isDirectory).put("tarih", f.lastModified()))
            }
            arr.put(JSONObject().put("id", k.id).put("ad", k.ad).put("aciklama", k.aciklama).put("secili", k.secili)
                .put("toplam", k.toplam).put("dosyalar", dosyalar))
        }
        JSONObject().put("kategoriler", arr).put("izin", dosyaIzni()).put("taranan", sayac).put("iptal", iptal.get())
    }

    private fun silYol(f: File): Long {
        if (!guvenli(f) || !f.exists()) return 0
        if (f.isDirectory) {
            var t = 0L
            f.listFiles()?.forEach { t += silYol(it) }
            f.delete()
            return t
        }
        val b = f.length()
        return if (f.delete()) b else 0
    }

    /** yollarJson: ["/storage/emulated/0/...", ...] */
    @JavascriptInterface
    fun temizle(id: Int, yollarJson: String) = arka(id) {
        val yollar = JSONArray(yollarJson)
        var bosalan = 0L
        var silinen = 0
        var hata = 0
        for (i in 0 until yollar.length()) {
            val f = File(yollar.getString(i))
            val vardi = f.exists()
            bosalan += silYol(f)
            if (vardi && !f.exists()) silinen++ else if (vardi) hata++
            if (i % 50 == 0) a.ilerleme("temizle", JSONObject().put("i", i).put("n", yollar.length()).toString())
        }
        JSONObject().put("bosalan", bosalan).put("silinen", silinen).put("hata", hata)
    }

    // ---------------- Analiz ----------------

    private val boyutOnbellek = java.util.concurrent.ConcurrentHashMap<String, LongArray>() // yol -> [boyut, dosyaSayisi]

    private fun olc(f: File, derinlik: Int = 0): LongArray {
        boyutOnbellek[f.path]?.let { return it }
        if (iptal.get()) return longArrayOf(0, 0)
        val r = if (!f.isDirectory) longArrayOf(f.length(), 1) else {
            var b = 0L; var n = 0L
            if (derinlik < 40) f.listFiles()?.forEach { val c = olc(it, derinlik + 1); b += c[0]; n += c[1] }
            longArrayOf(b, n)
        }
        if (f.isDirectory) boyutOnbellek[f.path] = r
        return r
    }

    @JavascriptInterface
    fun analizKok(): String = kok.path

    @JavascriptInterface
    fun analiz(id: Int, yol: String, yenile: Boolean) = arka(id) {
        iptal.set(false)
        if (yenile) boyutOnbellek.clear()
        val d = File(yol.ifEmpty { kok.path })
        if (!guvenli(d) && d.canonicalPath != kok.canonicalPath) throw IllegalArgumentException("Bu klasöre erişilemiyor")
        val ogeler = JSONArray()
        var toplam = 0L
        val cocuklar = d.listFiles() ?: emptyArray()
        cocuklar.forEachIndexed { i, f ->
            a.ilerleme("analiz", JSONObject().put("i", i).put("n", cocuklar.size).put("ad", f.name).toString())
            val c = olc(f)
            toplam += c[0]
            ogeler.put(JSONObject().put("ad", f.name).put("yol", f.path).put("klasor", f.isDirectory)
                .put("boyut", c[0]).put("sayi", c[1]).put("tarih", f.lastModified()))
        }
        val dosyaSistemi = StatFs(kok.path)
        JSONObject().put("yol", d.path).put("kok", kok.path).put("toplam", toplam).put("ogeler", ogeler)
            .put("diskToplam", dosyaSistemi.totalBytes).put("diskBos", dosyaSistemi.availableBytes)
            .put("izin", dosyaIzni())
    }

    /** Paylaşılan depolamadaki en büyük dosyalar. */
    @JavascriptInterface
    fun buyukDosyalar(id: Int, adet: Int) = arka(id) {
        iptal.set(false)
        val liste = ArrayList<File>()
        fun gez(d: File, derinlik: Int) {
            if (iptal.get() || derinlik > 30) return
            d.listFiles()?.forEach { f -> if (f.isDirectory) gez(f, derinlik + 1) else if (f.length() > 5L * 1024 * 1024) liste.add(f) }
        }
        if (dosyaIzni()) gez(kok, 0)
        val arr = JSONArray()
        liste.sortedByDescending { it.length() }.take(adet).forEach { f ->
            arr.put(JSONObject().put("ad", f.name).put("yol", f.path).put("boyut", f.length()).put("tarih", f.lastModified()))
        }
        JSONObject().put("liste", arr)
    }

    @JavascriptInterface
    fun sil(id: Int, yol: String) = arka(id) {
        val f = File(yol)
        val b = silYol(f)
        boyutOnbellek.keys.removeAll { yol.startsWith(it) || it.startsWith(yol) }
        JSONObject().put("ok", !f.exists()).put("bosalan", b)
    }
}

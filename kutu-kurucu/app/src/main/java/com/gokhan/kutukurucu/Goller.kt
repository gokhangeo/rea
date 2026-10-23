package com.gokhan.kutukurucu

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.view.Gravity
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.Normalizer

/**
 * Seçilen takımların maçlarını ESPN'in herkese açık skor servisinden izler; gol olunca ekrana mesaj bırakır.
 * Anahtar istemez. İlk turda yalnızca skorlar kaydedilir, geçmiş goller bildirilmez.
 */
object Goller {
    private const val TAG = "KutuKurucu"
    private const val P = "gol"
    private const val BASE = "https://site.api.espn.com/apis/site/v2/sports/soccer"
    /** Sayfada seçilebilen ligler (ESPN kodu → ad). Takım listesi buradan gelir; elle yazılmaz. */
    val SECILEBILIR = linkedMapOf(
        "tur.1" to "Süper Lig",
        "eng.1" to "Premier League (İngiltere)",
        "esp.1" to "LaLiga (İspanya)",
        "ger.1" to "Bundesliga (Almanya)",
        "ita.1" to "Serie A (İtalya)",
        "fra.1" to "Ligue 1 (Fransa)",
        "ned.1" to "Eredivisie (Hollanda)",
        "por.1" to "Primeira Liga (Portekiz)",
        "bel.1" to "Pro League (Belçika)",
        "sco.1" to "Premiership (İskoçya)",
        "uefa.champions" to "Şampiyonlar Ligi",
        "uefa.europa" to "Avrupa Ligi",
        "uefa.europa.conf" to "Konferans Ligi",
        "fifa.world" to "Dünya Kupası (milli takımlar)",
        "uefa.nations" to "Uluslar Ligi (milli takımlar)",
    )

    /** Takımın kendi liginden başka her zaman taranan kupalar (takımlar buralarda da oynar). */
    private val KUPALAR = linkedMapOf(
        "uefa.champions" to "Şampiyonlar Ligi",
        "uefa.europa" to "Avrupa Ligi",
        "uefa.europa.conf" to "Konferans Ligi",
        "tur.cup" to "Türkiye Kupası",
    )

    /** Seçili takım: ESPN takım kimliğiyle eşleşir (ad benzetmesi yok). */
    data class Takim(val id: String, val ad: String, val lig: String) {
        fun json(): JSONObject = JSONObject().put("id", id).put("ad", ad).put("lig", lig)
            .put("ligAd", SECILEBILIR[lig] ?: lig)
    }

    private val takimOnbellek = HashMap<String, Pair<Long, JSONArray>>()

    @Volatile private var calisiyor = false

    private fun p(c: Context) = c.getSharedPreferences(P, Context.MODE_PRIVATE)

    /** 1.6 ve öncesinde elle yazılmış adlar; sayfadan silinene kadar eskisi gibi ad benzetmesiyle izlenir. */
    fun eskiAdlar(c: Context): List<String> = p(c).getString("takimlar", "").orEmpty()
        .split(",").map { it.trim() }.filter { it.isNotEmpty() }

    fun secili(c: Context): List<Takim> {
        val a = runCatching { JSONArray(p(c).getString("secili", "[]") ?: "[]") }.getOrDefault(JSONArray())
        return (0 until a.length()).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            val id = o.optString("id"); val lig = o.optString("lig")
            if (id.isBlank() || lig.isBlank()) null else Takim(id, o.optString("ad", id), lig)
        }
    }

    fun acik(c: Context) = p(c).getBoolean("acik", true)

    /** Gol olunca ses çalsın mı (varsayılan açık). */
    fun ses(c: Context) = p(c).getBoolean("ses", true)
    fun sesAyarla(c: Context, acik: Boolean) = p(c).edit().putBoolean("ses", acik).apply()

    // Kart görünümü ve ses düzeyi (panelden).
    private val KONUMLAR = mapOf(
        "sagUst" to (Gravity.TOP or Gravity.END), "solUst" to (Gravity.TOP or Gravity.START),
        "sagAlt" to (Gravity.BOTTOM or Gravity.END), "solAlt" to (Gravity.BOTTOM or Gravity.START),
    )
    private val BOYUTLAR = mapOf("kucuk" to 0.85f, "orta" to 1f, "buyuk" to 1.3f)
    private val SES_DUZEY = mapOf("cokDusuk" to 0.12f, "dusuk" to 0.3f, "orta" to 0.55f, "yuksek" to 1f)
    private val SURELER = setOf(6, 10, 15, 20, 30)

    private fun konum(c: Context) = p(c).getString("konum", "sagUst")!!.takeIf { it in KONUMLAR } ?: "sagUst"
    private fun boyut(c: Context) = p(c).getString("boyut", "orta")!!.takeIf { it in BOYUTLAR } ?: "orta"
    private fun sesDuzey(c: Context) = p(c).getString("sesDuzey", "dusuk")!!.takeIf { it in SES_DUZEY } ?: "dusuk"
    private fun sure(c: Context) = p(c).getInt("sure", 12).takeIf { it in SURELER } ?: 12
    fun logo(c: Context) = p(c).getBoolean("logo", true)

    fun kartAyari(c: Context) = Mesaj.KartAyari(KONUMLAR.getValue(konum(c)), BOYUTLAR.getValue(boyut(c)), sure(c) * 1000L)
    fun sesCal(c: Context) { if (ses(c)) GolSesi.cal(c, SES_DUZEY.getValue(sesDuzey(c))) }

    /** Panelden gelen görünüm ayarları; bilinmeyen değerler yok sayılır. */
    fun gorunumAyarla(c: Context, al: (String) -> String?) {
        val e = p(c).edit()
        al("ses")?.let { e.putBoolean("ses", it != "0") }
        al("logo")?.let { e.putBoolean("logo", it != "0") }
        al("konum")?.takeIf { it in KONUMLAR }?.let { e.putString("konum", it) }
        al("boyut")?.takeIf { it in BOYUTLAR }?.let { e.putString("boyut", it) }
        al("sesDuzey")?.takeIf { it in SES_DUZEY }?.let { e.putString("sesDuzey", it) }
        al("sure")?.toIntOrNull()?.takeIf { it in SURELER }?.let { e.putInt("sure", it) }
        e.apply()
    }

    /** Diğer kutuya gönderilecek tüm ayarlar (/api/takimlar POST parametreleri). */
    fun aktarimParametreleri(c: Context): Map<String, String> = mapOf(
        "secili" to JSONArray(secili(c).map { JSONObject().put("id", it.id).put("ad", it.ad).put("lig", it.lig) }).toString(),
        "eskiler" to eskiAdlar(c).joinToString(","),
        "acik" to if (acik(c)) "1" else "0",
        "ses" to if (ses(c)) "1" else "0",
        "logo" to if (logo(c)) "1" else "0",
        "konum" to konum(c), "boyut" to boyut(c), "sesDuzey" to sesDuzey(c), "sure" to sure(c).toString(),
    )

    /** Sayfadaki "Dene" düğmesi: örnek gol bildirimi (ve açıksa sesi). */
    fun dene(c: Context): Boolean {
        val ayar = kartAyari(c)
        val ok = Mesaj.goster(c, "⚽ GOL!  67'  ·  Deneme", "Ev Sahibi 2 - 1 Deplasman", 6_000,
            kompakt = true, ek = "Golcü Oyuncu (P) · Ev Sahibi", ayar = ayar.copy(sureMs = minOf(ayar.sureMs, 8_000)))
        sesCal(c)
        return ok
    }

    /**
     * [secimJson]: [{"id":"134","ad":"VfB Stuttgart","lig":"ger.1"}, ...]. Yalnızca bilinen liglerden ve
     * sayısal kimlikli takımlar kabul edilir. [eskiler]: tutulacak eski elle yazılmış adlar (silinenler düşer).
     */
    fun ayarla(c: Context, secimJson: String?, eskiler: String?, acik: Boolean): String? {
        val liste = ArrayList<Takim>()
        if (secimJson != null) {
            val a = runCatching { JSONArray(secimJson) }.getOrNull() ?: return "Takım listesi okunamadı."
            for (i in 0 until a.length()) {
                val o = a.optJSONObject(i) ?: continue
                val id = o.optString("id").trim(); val lig = o.optString("lig").trim()
                if (!id.matches(Regex("\\d{1,9}")) || lig !in SECILEBILIR) continue
                if (liste.any { it.id == id }) continue
                liste.add(Takim(id, o.optString("ad", id).take(60), lig))
            }
        } else liste.addAll(secili(c))
        val eski = if (eskiler != null) eskiler.split(",").map { it.trim() }.filter { it in eskiAdlar(c) } else eskiAdlar(c)
        p(c).edit()
            .putString("secili", JSONArray(liste.take(30).map { it.json() }).toString())
            .putString("takimlar", eski.joinToString(", "))
            .putBoolean("acik", acik).apply()
        if (!acik || (liste.isEmpty() && eski.isEmpty())) p(c).edit().remove("skorlar").apply()
        return null
    }

    fun json(c: Context) = JSONObject()
        .put("secili", JSONArray(secili(c).map { it.json() }))
        .put("eskiler", JSONArray(eskiAdlar(c)))
        .put("acik", acik(c))
        .put("ses", ses(c))
        .put("logo", logo(c))
        .put("konum", konum(c)).put("boyut", boyut(c)).put("sesDuzey", sesDuzey(c)).put("sure", sure(c))
        .put("ligler", JSONArray(SECILEBILIR.map { (k, v) -> JSONObject().put("kod", k).put("ad", v) }))

    /** Bir ligin takımları (ESPN). 6 saat önbellekte tutulur. */
    fun ligTakimlari(lig: String): JSONArray {
        require(lig in SECILEBILIR) { "Bilinmeyen lig" }
        synchronized(takimOnbellek) {
            takimOnbellek[lig]?.let { (t, a) -> if (System.currentTimeMillis() - t < 6 * 3600_000L) return a }
        }
        val kok = JSONObject(get("$BASE/$lig/teams"))
        val ham = kok.optJSONArray("sports")?.optJSONObject(0)?.optJSONArray("leagues")?.optJSONObject(0)
            ?.optJSONArray("teams") ?: JSONArray()
        val liste = (0 until ham.length()).mapNotNull { i ->
            val t = ham.optJSONObject(i)?.optJSONObject("team") ?: return@mapNotNull null
            val id = t.optString("id"); if (id.isBlank()) return@mapNotNull null
            JSONObject().put("id", id).put("ad", ad(t))
        }.sortedBy { sadeles(it.optString("ad")) }
        val a = JSONArray(liste)
        synchronized(takimOnbellek) { takimOnbellek[lig] = System.currentTimeMillis() to a }
        return a
    }

    /** SunucuServisi düzenli olarak çağırır; ağ işi ayrı iş parçacığında yapılır. */
    fun dongu(c: Context) {
        if (calisiyor || !acik(c)) return
        val secili = secili(c)
        val eski = eskiAdlar(c)
        if (secili.isEmpty() && eski.isEmpty()) return
        calisiyor = true
        val uygulama = c.applicationContext
        Thread {
            try {
                tara(uygulama, secili, eski)
            } catch (t: Throwable) {
                Log.w(TAG, "gol servisi okunamadı", t)
            } finally {
                calisiyor = false
            }
        }.start()
    }

    private fun tara(c: Context, secili: List<Takim>, eskiAdlar: List<String>) {
        val eski = runCatching { JSONObject(p(c).getString("skorlar", "{}") ?: "{}") }.getOrDefault(JSONObject())
        val yeni = JSONObject()
        val idler = secili.map { it.id }.toSet()
        // Taranacak ligler: seçili takımların ligleri + kupalar (+ eski adlar için Süper Lig).
        val ligler = LinkedHashMap<String, String>()
        secili.forEach { ligler[it.lig] = SECILEBILIR[it.lig] ?: it.lig }
        if (eskiAdlar.isNotEmpty()) ligler["tur.1"] = "Süper Lig"
        ligler.putAll(KUPALAR)
        for ((lig, ligAdi) in ligler) {
            val kok = runCatching { JSONObject(get("$BASE/$lig/scoreboard")) }.getOrNull() ?: continue
            val olaylar = kok.optJSONArray("events") ?: continue
            for (k in 0 until olaylar.length()) {
                val e = olaylar.getJSONObject(k)
                val comp = e.optJSONArray("competitions")?.optJSONObject(0) ?: continue
                val durum = comp.optJSONObject("status") ?: e.optJSONObject("status")
                val tip = durum?.optJSONObject("type")
                val yarida = tip?.optString("state") == "in"
                val bitti = tip?.optBoolean("completed") == true
                if (!yarida && !bitti) continue
                val takimlar = comp.optJSONArray("competitors") ?: continue
                var evSahibi: JSONObject? = null
                var deplasman: JSONObject? = null
                for (i in 0 until takimlar.length()) {
                    val x = takimlar.getJSONObject(i)
                    if (x.optString("homeAway") == "home") evSahibi = x else deplasman = x
                }
                val ev = evSahibi ?: continue
                val dep = deplasman ?: continue
                val evAd = ad(ev.optJSONObject("team"))
                val depAd = ad(dep.optJSONObject("team"))
                val evId = ev.optJSONObject("team")?.optString("id") ?: ev.optString("id")
                val depId = dep.optJSONObject("team")?.optString("id") ?: dep.optString("id")
                val ilgili = evId in idler || depId in idler ||
                    eskiAdlar.any { s -> benzer(s, evAd) || benzer(s, depAd) }
                if (!ilgili) continue

                val hs = skor(ev)
                val as_ = skor(dep)
                val anahtar = e.optString("id").ifBlank { "$evAd-$depAd" }
                val skorYazi = "$hs-$as_"
                yeni.put(anahtar, skorYazi)
                if (!yarida) continue

                val onceki = eski.optString(anahtar, "")
                if (onceki.isEmpty() || toplam(onceki) >= toplam(skorYazi)) continue
                val fark = toplam(skorYazi) - toplam(onceki)
                var detay = comp
                if (golSayisi(comp) < toplam(skorYazi)) {
                    // Golcü bilgisi skordan biraz geç gelebiliyor: bir kez 15 sn bekleyip tekrar bak.
                    Thread.sleep(15_000)
                    detay = runCatching {
                        val ev2 = JSONObject(get("$BASE/$lig/scoreboard")).optJSONArray("events")
                        (0 until (ev2?.length() ?: 0)).map { ev2!!.getJSONObject(it) }
                            .firstOrNull { it.optString("id") == e.optString("id") }
                            ?.optJSONArray("competitions")?.optJSONObject(0)
                    }.getOrNull() ?: comp
                }
                val goller = sonGoller(detay, fark, evId, evAd, depAd)
                val dakika = goller.lastOrNull()?.first?.ifBlank { null }
                    ?: durum?.optString("displayClock").orEmpty()
                val baslik = buildString {
                    append(if (fark > 1) "⚽ GOL! GOL!" else "⚽ GOL!")
                    if (dakika.isNotBlank()) append("  ").append(dakika)
                    append("  ·  ").append(ligAdi)
                }
                val alt = "$evAd $hs - $as_ $depAd"
                val ek = goller.joinToString("   ") { (dk, kim) -> if (goller.size > 1 && dk.isNotBlank()) "$kim $dk" else kim }
                val lEv = if (logo(c)) logoAl(ev.optJSONObject("team")) else null
                val lDep = if (logo(c)) logoAl(dep.optJSONObject("team")) else null
                Mesaj.goster(c, baslik, alt, 12_000, kompakt = true, ek = ek, ayar = kartAyari(c), logoEv = lEv, logoDep = lDep)
                sesCal(c)
            }
        }
        // Eski maçları biriktirmemek için son 300 kayıt tutulur.
        val temiz = JSONObject()
        val adlar = yeni.keys().asSequence().toList().takeLast(300)
        adlar.forEach { temiz.put(it, yeni.opt(it)) }
        p(c).edit().putString("skorlar", temiz.toString()).apply()
    }

    /**
     * Maçın son [adet] golü: (dakika, "⚽ Golcü (P)" / "Golcü (k.k.)" + takım adı).
     * ESPN golcü bilgisini bazen skordan birkaç saniye sonra ekler; yoksa boş liste döner.
     */
    private fun sonGoller(comp: JSONObject, adet: Int, evId: String, evAd: String, depAd: String): List<Pair<String, String>> {
        val d = comp.optJSONArray("details") ?: return emptyList()
        val hepsi = mutableListOf<Pair<String, String>>()
        for (i in 0 until d.length()) {
            val x = d.optJSONObject(i) ?: continue
            if (!x.optBoolean("scoringPlay")) continue
            val dk = x.optJSONObject("clock")?.optString("displayValue").orEmpty()
            val oyuncu = x.optJSONArray("athletesInvolved")?.optJSONObject(0)
            var kim = oyuncu?.optString("shortName").orEmpty().ifBlank { oyuncu?.optString("displayName").orEmpty() }
            if (kim.isBlank()) kim = "Gol"
            if (x.optBoolean("ownGoal")) kim += " (k.k.)" else if (x.optBoolean("penaltyKick")) kim += " (P)"
            val takim = if (x.optJSONObject("team")?.optString("id") == evId) evAd else depAd
            hepsi.add(dk to "$kim · $takim")
        }
        return hepsi.takeLast(adet.coerceAtLeast(1))
    }

    private val logolar = HashMap<String, Bitmap?>()

    /** Takım logosu (ESPN), küçültülüp bellekte tutulur. Ağ işi; ana iş parçacığında çağrılmaz. */
    private fun logoAl(t: JSONObject?): Bitmap? {
        val url = t?.optString("logo").orEmpty().ifBlank {
            t?.optJSONArray("logos")?.optJSONObject(0)?.optString("href").orEmpty()
        }
        if (!url.startsWith("https://")) return null
        synchronized(logolar) { if (logolar.containsKey(url)) return logolar[url] }
        val bmp = runCatching {
            val b = URL(url).openConnection() as HttpURLConnection
            b.connectTimeout = 8_000; b.readTimeout = 10_000
            try {
                if (b.responseCode != 200) null
                else BitmapFactory.decodeStream(b.inputStream)?.let { Bitmap.createScaledBitmap(it, 96, 96, true) }
            } finally { b.disconnect() }
        }.getOrNull()
        synchronized(logolar) { if (logolar.size > 80) logolar.clear(); logolar[url] = bmp }
        return bmp
    }

    private fun golSayisi(comp: JSONObject): Int {
        val d = comp.optJSONArray("details") ?: return 0
        return (0 until d.length()).count { d.optJSONObject(it)?.optBoolean("scoringPlay") == true }
    }

    private fun ad(t: JSONObject?): String = t?.optString("displayName").orEmpty()
        .ifBlank { t?.optString("shortDisplayName").orEmpty() }
        .ifBlank { t?.optString("name").orEmpty() }

    private fun skor(c: JSONObject): Int {
        val s = c.opt("score")
        val ham = when (s) {
            is JSONObject -> s.optString("displayValue", s.optString("value"))
            else -> s?.toString().orEmpty()
        }
        return ham.substringBefore('.').toIntOrNull() ?: 0
    }

    private fun toplam(s: String): Int = s.split("-").sumOf { it.trim().toIntOrNull() ?: 0 }

    /** "Fenerbahçe" ~ "Fenerbahce" gibi yazım farklarını yok sayar. */
    private fun benzer(secili: String, ad: String): Boolean {
        val a = sadeles(secili)
        val b = sadeles(ad)
        if (a.length < 3 || b.isEmpty()) return false
        return b.contains(a) || a.contains(b)
    }

    private fun sadeles(s: String): String {
        val harf = s.replace('ı', 'i').replace('İ', 'i').replace('ş', 's').replace('Ş', 's')
            .replace('ğ', 'g').replace('Ğ', 'g').replace('ü', 'u').replace('Ü', 'u')
            .replace('ö', 'o').replace('Ö', 'o').replace('ç', 'c').replace('Ç', 'c')
        return Normalizer.normalize(harf, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .lowercase()
            .replace(Regex("[^a-z0-9]"), "")
    }

    private fun get(url: String): String {
        val b = URL(url).openConnection() as HttpURLConnection
        b.connectTimeout = 10_000
        b.readTimeout = 15_000
        b.setRequestProperty("User-Agent", "KutuKurucu/1.0")
        try {
            if (b.responseCode != 200) error("HTTP ${b.responseCode}")
            return b.inputStream.bufferedReader().readText()
        } finally {
            b.disconnect()
        }
    }
}

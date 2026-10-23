package com.gokhan.kutukurucu

import android.content.Context
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
    private val LIGLER = linkedMapOf(
        "tur.1" to "Süper Lig",
        "uefa.champions" to "Şampiyonlar Ligi",
        "uefa.europa" to "Avrupa Ligi",
        "uefa.europa.conf" to "Konferans Ligi",
        "tur.cup" to "Türkiye Kupası",
    )

    @Volatile private var calisiyor = false

    private fun p(c: Context) = c.getSharedPreferences(P, Context.MODE_PRIVATE)

    fun takimlar(c: Context): List<String> = p(c).getString("takimlar", "").orEmpty()
        .split(",").map { it.trim() }.filter { it.isNotEmpty() }

    fun acik(c: Context) = p(c).getBoolean("acik", true)

    fun ayarla(c: Context, ham: String, acik: Boolean) {
        val liste = ham.split(",", "\n").map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(20)
        p(c).edit().putString("takimlar", liste.joinToString(", ")).putBoolean("acik", acik).apply()
        if (!acik || liste.isEmpty()) p(c).edit().remove("skorlar").apply()
    }

    fun json(c: Context) = JSONObject()
        .put("takimlar", JSONArray(takimlar(c)))
        .put("acik", acik(c))
        .put("ligler", JSONArray(LIGLER.values))

    /** SunucuServisi düzenli olarak çağırır; ağ işi ayrı iş parçacığında yapılır. */
    fun dongu(c: Context) {
        if (calisiyor || !acik(c)) return
        val secili = takimlar(c)
        if (secili.isEmpty()) return
        calisiyor = true
        val uygulama = c.applicationContext
        Thread {
            try {
                tara(uygulama, secili)
            } catch (t: Throwable) {
                Log.w(TAG, "gol servisi okunamadı", t)
            } finally {
                calisiyor = false
            }
        }.start()
    }

    private fun tara(c: Context, secili: List<String>) {
        val eski = runCatching { JSONObject(p(c).getString("skorlar", "{}") ?: "{}") }.getOrDefault(JSONObject())
        val yeni = JSONObject()
        for ((lig, ligAdi) in LIGLER) {
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
                val ilgili = secili.any { s -> benzer(s, evAd) || benzer(s, depAd) }
                if (!ilgili) continue

                val hs = skor(ev)
                val as_ = skor(dep)
                val anahtar = e.optString("id").ifBlank { "$evAd-$depAd" }
                val skorYazi = "$hs-$as_"
                yeni.put(anahtar, skorYazi)
                if (!yarida) continue

                val onceki = eski.optString(anahtar, "")
                if (onceki.isEmpty() || toplam(onceki) >= toplam(skorYazi)) continue
                val dakika = durum?.optString("displayClock").orEmpty()
                val baslik = if (toplam(skorYazi) - toplam(onceki) > 1) "⚽ GOL! ⚽ GOL!" else "⚽ GOL!"
                val alt = buildString {
                    append("$evAd $hs - $as_ $depAd")
                    append('\n')
                    append(ligAdi)
                    if (dakika.isNotBlank()) append(" · ").append(dakika)
                }
                Mesaj.goster(c, baslik, alt, 12_000)
            }
        }
        // Eski maçları biriktirmemek için son 300 kayıt tutulur.
        val temiz = JSONObject()
        val adlar = yeni.keys().asSequence().toList().takeLast(300)
        adlar.forEach { temiz.put(it, yeni.opt(it)) }
        p(c).edit().putString("skorlar", temiz.toString()).apply()
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

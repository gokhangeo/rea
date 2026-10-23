package com.gokhan.evekrani

import android.content.Context
import android.text.Html
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.random.Random

/**
 * Günün menüsü: her gün yemek.com'dan 5 tarif (resim, malzemeler, yapılışı). Tarif listesi sitenin herkese açık
 * site haritasından (recipe-sitemapN.xml), tarifin kendisi sayfadaki schema.org Recipe verisinden okunur.
 * Gün İstanbul saatine göre değişir; seçim güne bağlıdır, aynı gün iki kutu aynı menüyü gösterir.
 * İnternet yoksa ya da site değişirse sayfa kutudaki yedek listeyi (yemekler.json) kullanır.
 */
class Menu(context: Context) {
    private val prefs = context.getSharedPreferences("menu", Context.MODE_PRIVATE)
    @Volatile private var busy = false
    @Volatile private var failedAt = 0L

    /** Bugünün menüsü hazırsa {g, m:[...]}; değilse arka planda hazırlanır ve {g, m:[]} döner. */
    fun json(): JSONObject {
        val g = today()
        val saved = prefs.getString("menu", null)?.let { runCatching { JSONObject(it) }.getOrNull() }
        if (saved != null && saved.optLong("g") == g && saved.optJSONArray("m")?.length() == COUNT) return saved
        val now = System.currentTimeMillis()
        if (!busy && now - failedAt > 10 * 60_000L) {
            busy = true
            Thread {
                try {
                    refresh(g)
                } catch (e: Exception) {
                    failedAt = System.currentTimeMillis()
                    Log.w(TAG, "günün menüsü alınamadı", e)
                } finally {
                    busy = false
                }
            }.start()
        }
        return JSONObject().put("g", g).put("m", JSONArray())
    }

    private fun today(): Long = (System.currentTimeMillis() + 3 * 3600_000L) / 86_400_000L

    private fun refresh(g: Long) {
        val rnd = Random(g * 7919 + 17)
        val picked = JSONArray()
        val seen = HashSet<String>()
        // Önce günün site haritası; yetmezse bir başkası.
        for (attempt in 0 until 3) {
            val page = 1 + rnd.nextInt(SITEMAPS)
            val urls = Regex("<loc>(https://yemek\\.com/tarif/[^<]+)</loc>").findAll(get("https://yemek.com/sitemaps/recipe-sitemap$page.xml"))
                .map { it.groupValues[1] }.toMutableList()
            urls.shuffle(rnd)
            var tried = 0
            for (u in urls) {
                if (picked.length() >= COUNT || tried >= 14) break
                if (!seen.add(u)) continue
                tried++
                runCatching { recipe(u) }.onFailure { Log.w(TAG, "tarif okunamadı: $u", it) }.getOrNull()?.let { picked.put(it) }
            }
            if (picked.length() >= COUNT) break
        }
        if (picked.length() < COUNT) error("yalnızca ${picked.length()} tarif bulundu")
        prefs.edit().putString("menu", JSONObject().put("g", g).put("m", picked).toString()).apply()
        Log.i(TAG, "günün menüsü hazır")
    }

    /** Tarif sayfasındaki schema.org Recipe; resmi, en az 3 malzemesi ve 2 adımı yoksa null. */
    private fun recipe(url: String): JSONObject? {
        val html = get(url)
        val r = Regex("<script[^>]*application/ld\\+json[^>]*>(.*?)</script>", RegexOption.DOT_MATCHES_ALL)
            .findAll(html).mapNotNull { findRecipe(it.groupValues[1].trim()) }.firstOrNull() ?: return null
        val name = clean(r.optString("name")).removeSuffix(" Tarifi").trim()
        val image = image(r.opt("image")) ?: return null
        val malzeme = strings(r.opt("recipeIngredient")).map(::clean).filter { it.length > 1 }.take(18)
        val adimlar = steps(r.opt("recipeInstructions")).map(::clean).filter { it.length > 2 }.take(12)
        if (name.isEmpty() || malzeme.size < 3 || adimlar.size < 2) return null
        val o = JSONObject().put("ad", name).put("wiki", url).put("resim", image).put("kaynak", "yemek.com")
            .put("adres", url).put("malzeme", JSONArray(malzeme)).put("yapilis", JSONArray(adimlar))
        minutes(r.optString("totalTime").ifEmpty { r.optString("cookTime") })?.let { o.put("sure", "$it dk") }
        val yield_ = r.opt("recipeYield")?.let { if (it is JSONArray) it.optString(0) else it.toString() }.orEmpty()
        if (yield_.contains("kişi")) Regex("\\d+").find(yield_)?.value?.toIntOrNull()?.takeIf { it in 1..30 }?.let { o.put("kisi", it) }
        else if (yield_.isNotBlank() && yield_.length < 30) o.put("miktar", yield_.trim())
        return o
    }

    private fun findRecipe(text: String): JSONObject? {
        val root: Any = runCatching { if (text.startsWith("[")) JSONArray(text) else JSONObject(text) }.getOrNull() ?: return null
        val queue = ArrayDeque<Any>().apply { add(root) }
        while (queue.isNotEmpty()) {
            when (val x = queue.removeFirst()) {
                is JSONArray -> for (i in 0 until x.length()) queue.add(x.get(i))
                is JSONObject -> {
                    val t = x.opt("@type")
                    if (t == "Recipe" || (t is JSONArray && (0 until t.length()).any { t.optString(it) == "Recipe" })) return x
                    x.optJSONArray("@graph")?.let { queue.add(it) }
                }
            }
        }
        return null
    }

    private fun image(v: Any?): String? {
        val all = ArrayList<String>()
        fun add(x: Any?) {
            when (x) {
                is String -> all.add(x)
                is JSONObject -> add(x.opt("url"))
                is JSONArray -> for (i in 0 until x.length()) add(x.opt(i))
            }
        }
        add(v)
        val https = all.filter { it.startsWith("https://") }
        return https.firstOrNull { it.contains("940x") } ?: https.firstOrNull { it.contains("600x") } ?: https.lastOrNull()
    }

    private fun strings(v: Any?): List<String> = when (v) {
        is JSONArray -> (0 until v.length()).map { v.optString(it) }
        is String -> v.split('\n')
        else -> emptyList()
    }

    private fun steps(v: Any?): List<String> {
        val out = ArrayList<String>()
        fun add(x: Any?) {
            when (x) {
                is String -> out.addAll(x.split('\n'))
                is JSONArray -> for (i in 0 until x.length()) add(x.opt(i))
                is JSONObject -> if (x.has("itemListElement")) add(x.opt("itemListElement")) else add(x.optString("text"))
            }
        }
        add(v)
        // Tek uzun paragrafsa cümlelere böl.
        if (out.size == 1 && out[0].length > 200) return out[0].split(Regex("(?<=\\.)\\s+")).filter { it.length > 2 }
        return out
    }

    private fun clean(s: String): String =
        Html.fromHtml(s, Html.FROM_HTML_MODE_LEGACY).toString()
            .replace(Regex("\\(\\((.*?)\\)\\)"), "($1)").replace(Regex("\\s+"), " ").trim()

    /** ISO 8601 süre (PT1H15M) → dakika. */
    private fun minutes(iso: String): Int? {
        val m = Regex("P(?:\\d+D)?T?(?:(\\d+)H)?(?:(\\d+)M)?").find(iso) ?: return null
        val total = (m.groupValues[1].toIntOrNull() ?: 0) * 60 + (m.groupValues[2].toIntOrNull() ?: 0)
        return total.takeIf { it in 1..1440 }
    }

    private fun get(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 20_000
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 9; Android TV) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Safari/537.36 EvEkrani")
        conn.setRequestProperty("Accept-Language", "tr-TR,tr;q=0.9")
        try {
            if (conn.responseCode != 200) error("HTTP ${conn.responseCode}")
            return conn.inputStream.bufferedReader().readText()
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        private const val TAG = "EvEkrani"
        private const val COUNT = 5
        /** recipe-sitemap1..30 dolu (her biri 1000 tarif); 31. ve sonrası daha az. */
        private const val SITEMAPS = 30
    }
}

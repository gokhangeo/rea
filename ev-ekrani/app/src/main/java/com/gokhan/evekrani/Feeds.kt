package com.gokhan.evekrani

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant

/**
 * Hava durumu (Open-Meteo) ve Fenerbahçe maçları (ESPN'in herkese açık skor servisi). İkisi de anahtar istemez.
 * Son başarılı sonuç saklanır; internet giderse ekranda eski bilgi kalır.
 */
class Feeds(context: Context) {
    private val prefs = context.getSharedPreferences("feeds", Context.MODE_PRIVATE)
    @Volatile private var weatherAt = 0L
    @Volatile private var matchesAt = 0L

    fun json(): JSONObject {
        val now = System.currentTimeMillis()
        if (now - weatherAt > 15 * 60_000L) { weatherAt = now; Thread { refreshWeather() }.start() }
        if (now - matchesAt > 30 * 60_000L) { matchesAt = now; Thread { refreshMatches() }.start() }
        return JSONObject()
            .put("weather", prefs.getString("weather", null)?.let(::JSONObject) ?: JSONObject.NULL)
            .put("matches", prefs.getString("matches", null)?.let(::JSONObject) ?: JSONObject.NULL)
    }

    private fun refreshWeather() = runCatching {
        val j = JSONObject(get("https://api.open-meteo.com/v1/forecast?latitude=36.81&longitude=34.64&current=temperature_2m,weather_code&timezone=Europe%2FIstanbul"))
        val c = j.getJSONObject("current")
        prefs.edit().putString("weather", JSONObject().put("temp", c.getDouble("temperature_2m")).put("code", c.getInt("weather_code")).toString()).apply()
    }.onFailure { weatherAt = 0L; Log.w(TAG, "hava durumu alınamadı", it) }

    private fun refreshMatches() = runCatching {
        val teamId = prefs.getString("espnTeam", null) ?: findTeam().also { prefs.edit().putString("espnTeam", it).apply() }
        val events = LinkedHashMap<String, JSONObject>()
        for ((league, name) in LEAGUES) {
            for (suffix in listOf("", "?fixture=true")) {
                val body = runCatching { get("$BASE/$league/teams/$teamId/schedule$suffix") }.getOrNull() ?: continue
                val arr = JSONObject(body).optJSONArray("events") ?: continue
                for (i in 0 until arr.length()) parse(arr.getJSONObject(i), league, name)?.let { events[it.getString("id")] = it }
            }
        }
        if (events.isEmpty()) error("maç bulunamadı")
        val sorted = events.values.sortedBy { it.getLong("t") }
        val now = System.currentTimeMillis()
        val upcoming = sorted.filter { !it.getBoolean("done") && it.getLong("t") > now - 3 * 3600_000L }.take(3)
        val past = sorted.filter { it.getBoolean("done") }.takeLast(2).reversed()
        // Kartın arka yüzü için son maçın golleri; alınamazsa kart yalnızca skoru gösterir.
        past.firstOrNull()?.let { m -> runCatching { m.put("goals", goals(m)) }.onFailure { Log.w(TAG, "goller alınamadı", it) } }
        prefs.edit().putString("matches", JSONObject().put("next", JSONArray(upcoming)).put("last", JSONArray(past)).put("updated", now).toString()).apply()
    }.onFailure { matchesAt = now() - 25 * 60_000L; Log.w(TAG, "maçlar alınamadı", it) }

    private fun now() = System.currentTimeMillis()

    private fun findTeam(): String {
        val teams = JSONObject(get("$BASE/tur.1/teams")).getJSONArray("sports").getJSONObject(0)
            .getJSONArray("leagues").getJSONObject(0).getJSONArray("teams")
        for (i in 0 until teams.length()) {
            val t = teams.getJSONObject(i).getJSONObject("team")
            if (t.optString("displayName").contains("Fenerbah", ignoreCase = true)) return t.getString("id")
        }
        error("Fenerbahçe bulunamadı")
    }

    /**
     * Maçın golleri, dakika sırasıyla: {min: "23'", name, side: home|away, note: pen|kk|""}.
     * ESPN'in maç özeti servisinden (header.competitions[0].details; yoksa keyEvents).
     */
    private fun goals(m: JSONObject): JSONArray {
        val j = JSONObject(get("$BASE/${m.getString("league")}/summary?event=${m.getString("id")}"))
        val comp = j.optJSONObject("header")?.optJSONArray("competitions")?.optJSONObject(0)
        val homeId = m.optString("hid")
        val out = JSONArray()
        val details = comp?.optJSONArray("details")
        if (details != null && details.length() > 0) {
            for (i in 0 until details.length()) {
                val d = details.getJSONObject(i)
                if (!d.optBoolean("scoringPlay")) continue
                val who = d.optJSONArray("athletesInvolved")?.optJSONObject(0)
                val name = who?.optString("shortName")?.ifEmpty { null } ?: who?.optString("displayName").orEmpty()
                out.put(goal(d.optJSONObject("clock")?.optString("displayValue").orEmpty(), name,
                    d.optJSONObject("team")?.optString("id") == homeId,
                    d.optBoolean("penaltyKick"), d.optBoolean("ownGoal")))
            }
            return out
        }
        val events = j.optJSONArray("keyEvents") ?: return out
        for (i in 0 until events.length()) {
            val e = events.getJSONObject(i)
            if (!e.optBoolean("scoringPlay")) continue
            val type = e.optJSONObject("type")?.optString("text").orEmpty()
            val name = e.optJSONArray("participants")?.optJSONObject(0)?.optJSONObject("athlete")?.optString("displayName").orEmpty()
            out.put(goal(e.optJSONObject("clock")?.optString("displayValue").orEmpty(), name,
                e.optJSONObject("team")?.optString("id") == homeId,
                type.contains("Penalty", true), type.contains("Own", true)))
        }
        return out
    }

    private fun goal(min: String, name: String, home: Boolean, pen: Boolean, own: Boolean) = JSONObject()
        .put("min", min).put("name", name).put("side", if (home) "home" else "away")
        .put("note", if (own) "kk" else if (pen) "pen" else "")

    private fun parse(e: JSONObject, league: String, competition: String): JSONObject? {
        val comp = e.optJSONArray("competitions")?.optJSONObject(0) ?: return null
        val teams = comp.optJSONArray("competitors") ?: return null
        var home: JSONObject? = null
        var away: JSONObject? = null
        for (i in 0 until teams.length()) {
            val c = teams.getJSONObject(i)
            if (c.optString("homeAway") == "home") home = c else away = c
        }
        if (home == null || away == null) return null
        val state = (comp.optJSONObject("status") ?: e.optJSONObject("status"))?.optJSONObject("type")
        val done = state?.optBoolean("completed") == true
        val t = runCatching { Instant.parse(e.getString("date").let { if (it.length == 17) it.replace("Z", ":00Z") else it }).toEpochMilli() }.getOrNull() ?: return null
        return JSONObject()
            .put("id", e.optString("id"))
            .put("t", t)
            .put("home", turkish(home.optJSONObject("team")?.optString("displayName").orEmpty()))
            .put("away", turkish(away.optJSONObject("team")?.optString("displayName").orEmpty()))
            .put("hs", score(home))
            .put("as", score(away))
            .put("done", done)
            .put("live", state?.optString("state") == "in")
            .put("comp", competition)
            .put("league", league)
            .put("hid", home.optJSONObject("team")?.optString("id").orEmpty())
            .put("venue", comp.optJSONObject("venue")?.optString("fullName").orEmpty())
    }

    private fun score(c: JSONObject): String {
        val s = c.opt("score") ?: return ""
        return when (s) {
            is JSONObject -> s.optString("displayValue", s.optString("value"))
            else -> s.toString()
        }.substringBefore(".")
    }

    private fun turkish(name: String): String = NAMES.entries.fold(name) { acc, (from, to) -> acc.replace(from, to) }

    private fun get(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 15_000
        conn.setRequestProperty("User-Agent", "EvEkrani/1.0")
        try {
            if (conn.responseCode != 200) error("HTTP ${conn.responseCode}")
            return conn.inputStream.bufferedReader().readText()
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        private const val TAG = "EvEkrani"
        private const val BASE = "https://site.api.espn.com/apis/site/v2/sports/soccer"
        private val LEAGUES = listOf(
            "tur.1" to "Süper Lig",
            "uefa.champions" to "Şampiyonlar Ligi",
            "uefa.europa" to "Avrupa Ligi",
            "uefa.europa.conf" to "Konferans Ligi",
            "tur.cup" to "Türkiye Kupası",
        )
        private val NAMES = linkedMapOf(
            "Fenerbahce" to "Fenerbahçe", "Besiktas" to "Beşiktaş", "Goztepe" to "Göztepe", "Caykur Rizespor" to "Ç. Rizespor",
            "Istanbul Basaksehir" to "Başakşehir", "Basaksehir" to "Başakşehir", "Kasimpasa" to "Kasımpaşa", "Eyupspor" to "Eyüpspor",
            "Genclerbirligi" to "Gençlerbirliği", "Karagumruk" to "Karagümrük", "Kocaelispor" to "Kocaelispor", "Antalyaspor" to "Antalyaspor",
        )
    }
}

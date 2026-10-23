package com.gokhan.evekrani

import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.Socket
import java.net.URL
import java.net.UnknownHostException
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLSocketFactory

/**
 * Kutunun DNS'i bir adı (ör. release-assets.githubusercontent.com) çözemezse adres Cloudflare/Google'ın
 * HTTPS üzerinden DNS hizmetinden sorulur ve bağlantı o IP'ye, sertifika asıl ada göre doğrulanarak kurulur.
 */
object Dns {
    private val onbellek = HashMap<String, String>()

    /** Bağlantıyı açar ve cevap kodunu okur; ad çözülemezse yedek DNS ile bir kez daha dener. */
    fun ac(url: String, ayarla: (HttpURLConnection) -> Unit): HttpURLConnection {
        val u = URL(url)
        val b = (u.openConnection() as HttpURLConnection).also(ayarla)
        try {
            b.responseCode
            return b
        } catch (e: UnknownHostException) {
            b.disconnect()
            val ip = coz(u.host) ?: throw e
            Log.i("Dns", "${u.host} yedek DNS ile çözüldü: $ip")
            val ipUrl = URL(u.protocol, ip, u.port, u.file)
            val c = (ipUrl.openConnection() as HttpURLConnection).also(ayarla)
            c.setRequestProperty("Host", u.host)
            if (c is HttpsURLConnection) {
                c.sslSocketFactory = SniFabrikasi(HttpsURLConnection.getDefaultSSLSocketFactory(), u.host)
                val varsayilan = HttpsURLConnection.getDefaultHostnameVerifier()
                c.setHostnameVerifier { _, oturum -> varsayilan.verify(u.host, oturum) }
            }
            c.responseCode
            return c
        }
    }

    @Synchronized
    private fun coz(ad: String): String? {
        onbellek[ad]?.let { return it }
        for (s in listOf("https://1.1.1.1/dns-query?name=$ad&type=A", "https://8.8.8.8/resolve?name=$ad&type=A")) {
            try {
                val b = URL(s).openConnection() as HttpURLConnection
                b.connectTimeout = 8_000
                b.readTimeout = 8_000
                b.setRequestProperty("Accept", "application/dns-json")
                val j = try { JSONObject(b.inputStream.bufferedReader().readText()) } finally { b.disconnect() }
                val cevap = j.optJSONArray("Answer") ?: continue
                for (i in 0 until cevap.length()) {
                    val a = cevap.getJSONObject(i)
                    if (a.optInt("type") == 1) return a.getString("data").also { onbellek[ad] = it }
                }
            } catch (e: Exception) {
                Log.w("Dns", "yedek DNS: $s", e)
            }
        }
        return null
    }

    /** TLS el sıkışmasında sunucuya asıl adı (SNI) söyler; bağlantı IP'ye kurulsa da doğru sertifika gelir. */
    private class SniFabrikasi(private val d: SSLSocketFactory, private val ad: String) : SSLSocketFactory() {
        override fun getDefaultCipherSuites(): Array<String> = d.defaultCipherSuites
        override fun getSupportedCipherSuites(): Array<String> = d.supportedCipherSuites
        override fun createSocket(s: Socket, host: String, port: Int, autoClose: Boolean): Socket = d.createSocket(s, ad, port, autoClose)
        override fun createSocket(host: String, port: Int): Socket = d.createSocket(ad, port)
        override fun createSocket(host: String, port: Int, la: InetAddress, lp: Int): Socket = d.createSocket(host, port, la, lp)
        override fun createSocket(host: InetAddress, port: Int): Socket = d.createSocket(host, port)
        override fun createSocket(a: InetAddress, port: Int, la: InetAddress, lp: Int): Socket = d.createSocket(a, port, la, lp)
    }
}

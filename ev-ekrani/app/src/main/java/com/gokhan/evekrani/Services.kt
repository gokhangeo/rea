package com.gokhan.evekrani

import android.content.Context
import android.util.Log
import fi.iki.elonen.NanoHTTPD

/**
 * Ev Ekranı eklentilerinin girişi: ana ekran (MainActivity) açılırken bir kez çağrılır.
 * Telefon sayfasını ve ekrandaki Fenerbahçe/fotoğraf bölümünü besleyen sunucuyu kurar, Home tuşu
 * servisini açık tutar ve yarım kalan derinlik haritalarına devam eder.
 */
object Services {
    private var server: LocalServer? = null

    @JvmStatic
    @Synchronized
    fun start(context: Context) {
        CrashGuard.install(context)
        HomeButtonGuard.ensure(context)
        if (server != null) return
        val app = context.applicationContext
        val photos = PhotoStore(app)
        server = LocalServer(app, photos, Feeds(app)).also {
            runCatching { it.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false) }
                .onFailure { e -> Log.e("EvEkrani", "sunucu açılamadı", e) }
        }
        photos.resume()
    }

    /** Ekrandaki bölümün adresi. */
    const val WIDGET_URL = "http://127.0.0.1:${LocalServer.PORT}/tv/widget.html"
}

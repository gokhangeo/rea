package com.gokhan.evekrani

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout

/**
 * Ana ekran: tüm arayüz bir WebView'da, kutunun içindeki sunucudan (/tv/index.html) açılır.
 * WebView çökerse yenisi kurulur; Home tuşu ve uygulamalar çalışmaya devam eder.
 */
class MainActivity : Activity() {
    private lateinit var root: FrameLayout
    private var web: WebView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashGuard.install(this)
        HomeButtonGuard.ensure(this)
        Services.start(applicationContext)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        setContentView(root)
        newWebView()
    }

    private fun newWebView() {
        web?.let { root.removeView(it); it.destroy() }
        val w = WebView(this).apply {
            setBackgroundColor(Color.BLACK)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            // Sahne JS ile ekrana sığdırılır; WebView'ın kendi yakınlaştırması ve yazı büyütmesi devre dışı.
            settings.useWideViewPort = false
            settings.loadWithOverviewMode = false
            settings.textZoom = 100
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            isFocusable = true
            isFocusableInTouchMode = true
            webChromeClient = WebChromeClient()
            webViewClient = object : WebViewClient() {
                override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                    Log.w("EvEkrani", "WebView çöktü, yeniden kuruluyor")
                    root.post { newWebView() }
                    return true
                }

                override fun onReceivedError(view: WebView, errorCode: Int, description: String?, failingUrl: String?) {
                    // Sunucu daha açılmadıysa kısa süre sonra yeniden dene.
                    view.postDelayed({ view.loadUrl(URL) }, 1500)
                }
            }
        }
        root.addView(w, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        w.requestFocus()
        w.loadUrl(URL)
        web = w
    }

    override fun onResume() {
        super.onResume()
        web?.evaluateJavascript("window.gorundu && gorundu()", null)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // Geri tuşu: ana ekrandan çıkılmaz, açık bir menü varsa kapanır.
        if (event.keyCode == KeyEvent.KEYCODE_BACK) {
            if (event.action == KeyEvent.ACTION_UP) web?.evaluateJavascript("window.geri && geri()", null)
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onDestroy() {
        web?.destroy()
        super.onDestroy()
    }

    companion object {
        private const val URL = "http://127.0.0.1:${LocalServer.PORT}/tv/index.html"
    }
}

/** Sunucu ve fotoğraf deposu uygulama boyunca tek kez kurulur. */
object Services {
    private var server: LocalServer? = null

    @Synchronized
    fun start(context: android.content.Context) {
        if (server != null) return
        val photos = PhotoStore(context)
        server = LocalServer(context, AppCatalog(context), photos, Feeds(context)).also {
            runCatching { it.start(fi.iki.elonen.NanoHTTPD.SOCKET_READ_TIMEOUT, false) }
                .onFailure { e -> Log.e("EvEkrani", "sunucu açılamadı", e) }
        }
        photos.resume()
    }
}

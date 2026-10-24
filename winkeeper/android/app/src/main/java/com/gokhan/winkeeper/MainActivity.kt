package com.gokhan.winkeeper

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONObject

/**
 * Tek ekran: arayüz assets/index.html içinde (masaüstü WinKeeper'ın görünümü), cihaz işleri [Kopru]'de.
 * Telefonda alt sekme çubuğu, tablette yan menü; düzeni sayfanın kendisi ekran genişliğine göre seçer.
 */
class MainActivity : Activity() {
    lateinit var web: WebView
        private set

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.parseColor("#0B1226")
        window.navigationBarColor = Color.parseColor("#0B1226")
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)

        web = WebView(this).apply {
            setBackgroundColor(Color.parseColor("#0B1226"))
            overScrollMode = View.OVER_SCROLL_NEVER
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.textZoom = 100
            webViewClient = object : WebViewClient() {
                // Sayfa dışına çıkılmaz.
                override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean = true
            }
            webChromeClient = WebChromeClient()
            addJavascriptInterface(Kopru(this@MainActivity), "Android")
        }
        setContentView(web)
        if (savedInstanceState != null) web.restoreState(savedInstanceState)
        else web.loadUrl("file:///android_asset/index.html")
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        web.saveState(outState)
    }

    override fun onResume() {
        super.onResume()
        // Ayarlardan izin verip dönünce sayfa izinleri yeniden okusun.
        if (::web.isInitialized) web.evaluateJavascript("window.__resume && window.__resume()", null)
    }

    @Deprecated("Geri tuşu sayfaya sorulur; açık pencere ya da alt klasör varsa önce o kapanır.")
    override fun onBackPressed() {
        web.evaluateJavascript("(window.__back && window.__back()) ? '1' : '0'") { r ->
            if (r?.contains("1") != true) {
                @Suppress("DEPRECATION")
                super.onBackPressed()
            }
        }
    }

    /** Arka plandaki işin sonucunu sayfaya iletir. */
    fun bitti(id: Int, json: String) {
        val js = "window.__done && window.__done($id, ${JSONObject.quote(json)})"
        runOnUiThread { if (!isDestroyed) web.evaluateJavascript(js, null) }
    }

    fun ilerleme(kanal: String, json: String) {
        val js = "window.__progress && window.__progress(${JSONObject.quote(kanal)}, ${JSONObject.quote(json)})"
        runOnUiThread { if (!isDestroyed) web.evaluateJavascript(js, null) }
    }
}

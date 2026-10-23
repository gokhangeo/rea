package com.gokhan.kutukurucu

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.VideoView
import org.json.JSONObject
import java.io.File
import java.io.InputStream

/**
 * Telefondan gelen fotoğraf/videoyu kutuya alır ve tam ekran gösterir.
 * Dosyalar uygulamanın kendi klasöründe tutulur, bir günden eski olanlar silinir.
 */
object Medya {
    private const val TAG = "KutuKurucu"
    private val VIDEO = Regex("\\.(mp4|mkv|webm|mov|m4v|avi|ts|3gp)$", RegexOption.IGNORE_CASE)

    fun videoMu(yol: String) = VIDEO.containsMatchIn(yol)

    /** Sayfadan gelen dosyayı diske yazar, sonra TV'de tam ekran açar. */
    fun kaydet(c: Context, ad: String, girdi: InputStream, uzunluk: Long): JSONObject {
        val klasor = File(c.filesDir, "medya").apply { mkdirs() }
        val sinir = System.currentTimeMillis() - 24 * 3600_000L
        klasor.listFiles()?.forEach { if (it.lastModified() < sinir) it.delete() }

        val guvenli = ad.replace(Regex("[^\\p{L}\\p{N}._-]"), "_").takeLast(80).ifBlank { "medya" }
        val dosya = File(klasor, "${System.currentTimeMillis()}-$guvenli")
        try {
            girdi.use { g -> dosya.outputStream().use { g.copyTo(it, 64 * 1024) } }
            if (uzunluk > 0 && dosya.length() < uzunluk) error("dosya yarım geldi")
        } catch (e: Exception) {
            dosya.delete()
            throw e
        }
        goster(c, dosya.absolutePath)
        return JSONObject().put("ok", true).put("ad", dosya.name).put("video", videoMu(dosya.name))
    }

    fun goster(c: Context, yol: String) {
        val niyet = Intent(c, MedyaGoster::class.java)
            .putExtra("yol", yol)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        runCatching { c.startActivity(niyet) }
            .onFailure { Log.w(TAG, "medya açılamadı", it) }
    }
}

/** Fotoğrafı ya da videoyu tam ekran gösterir; kumandada GERİ kapatır, video bitince kendiliğinden kapanır. */
class MedyaGoster : Activity() {
    private var video: VideoView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val yol = intent.getStringExtra("yol") ?: return bitir()
        val dosya = File(yol)
        if (!dosya.exists()) return bitir()

        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY

        val kok = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }

        if (Medya.videoMu(yol)) {
            val v = VideoView(this)
            video = v
            v.setVideoURI(Uri.fromFile(dosya))
            v.setOnPreparedListener { it.isLooping = false; v.start() }
            v.setOnCompletionListener { bitir() }
            v.setOnErrorListener { _, _, _ -> ipucu(kok, "Video oynatılamadı."); true }
            kok.addView(v, FrameLayout.LayoutParams(-1, -1))
        } else {
            val resim = ImageView(this).apply {
                setImageURI(Uri.fromFile(dosya))
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
            kok.addView(resim, FrameLayout.LayoutParams(-1, -1))
        }

        kok.addView(TextView(this).apply {
            text = "Kapatmak için GERİ"
            setTextColor(Color.parseColor("#B3FFFFFF"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setPadding(24, 16, 24, 16)
        }, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.END))

        setContentView(kok)
    }

    private fun ipucu(kok: FrameLayout, yazi: String) {
        kok.addView(TextView(this).apply {
            text = yazi
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
        }, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
    }

    private fun bitir() {
        runCatching { video?.stopPlayback() }
        finish()
    }

    override fun onDestroy() {
        runCatching { video?.stopPlayback() }
        video = null
        super.onDestroy()
    }
}

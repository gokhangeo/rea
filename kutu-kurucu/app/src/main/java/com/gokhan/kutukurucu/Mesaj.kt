package com.gokhan.kutukurucu

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Ekranda ne açık olursa olsun (kanal, uygulama, oyun) üstte görünen mesaj katmanı.
 * Gol bildirimleri ve telefondan gönderilen mesajlar buradan çıkar; süre bitince kendiliğinden kaybolur.
 * Diğer uygulamaların üstüne çizmek için "üstte gösterme" izni gerekir (Izinler.ustte).
 */
object Mesaj {
    /** Küçük kartın yeri, büyüklüğü ve ekranda kalma süresi (panelden ayarlanır). */
    data class KartAyari(val gravity: Int, val olcek: Float, val sureMs: Long)

    private val ana = Handler(Looper.getMainLooper())
    private var pencere: WindowManager? = null
    private var gorunen: View? = null
    private var zamanlayici: Runnable? = null

    /** Mesajı gösterir. İzin yoksa false döner. */
    fun goster(
        c: Context, baslik: String, metin: String, sureMs: Long = 8000, kompakt: Boolean = false, ek: String = "",
        ayar: KartAyari? = null, logoEv: Bitmap? = null, logoDep: Bitmap? = null,
    ): Boolean {
        val sure = ayar?.sureMs ?: sureMs
        val yer = ayar?.gravity ?: (Gravity.TOP or Gravity.END)
        val olcek = ayar?.olcek ?: 1f
        if (!Settings.canDrawOverlays(c)) return false
        val uygulama = c.applicationContext
        ana.post {
            val wm = pencere ?: (uygulama.getSystemService(Context.WINDOW_SERVICE) as WindowManager).also { pencere = it }
            gizle(hemen = true)
            val kart = if (kompakt) kucukKart(uygulama, baslik, metin, ek, olcek, logoEv, logoDep) else kart(uygulama, baslik, metin)
            val dis = FrameLayout(uygulama).apply {
                if (kompakt) setPadding(dp(uygulama, 32), dp(uygulama, 8), dp(uygulama, 32), dp(uygulama, 8))
                else setPadding(dp(uygulama, 36), dp(uygulama, 28), dp(uygulama, 36), 0)
                val w = if (kompakt) FrameLayout.LayoutParams.WRAP_CONTENT else FrameLayout.LayoutParams.MATCH_PARENT
                addView(kart, FrameLayout.LayoutParams(w, FrameLayout.LayoutParams.WRAP_CONTENT))
            }
            val p = WindowManager.LayoutParams(
                if (kompakt) WindowManager.LayoutParams.WRAP_CONTENT else WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                android.graphics.PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = if (kompakt) yer else Gravity.TOP or Gravity.CENTER_HORIZONTAL
                y = dp(uygulama, if (kompakt) 16 else 24)
            }
            if (runCatching { wm.addView(dis, p) }.isFailure) return@post
            dis.alpha = 0f
            dis.animate().alpha(1f).setDuration(200).start()
            gorunen = dis
            val kapat = Runnable { gizle(hemen = false) }
            zamanlayici = kapat
            ana.postDelayed(kapat, sure.coerceIn(1500, 60_000))
        }
        return true
    }

    /** Görünen mesajı kapatır (yeni mesaj gelince de çağrılır). */
    fun gizle(hemen: Boolean) {
        zamanlayici?.let { ana.removeCallbacks(it) }
        zamanlayici = null
        val v = gorunen ?: return
        gorunen = null
        if (hemen) {
            runCatching { pencere?.removeView(v) }
        } else {
            v.animate().alpha(0f).setDuration(220).withEndAction {
                runCatching { pencere?.removeView(v) }
            }.start()
        }
    }

    private fun kart(c: Context, baslik: String, metin: String) = LinearLayout(c).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(c, 28), dp(c, 18), dp(c, 28), dp(c, 20))
        background = GradientDrawable().apply {
            cornerRadius = dp(c, 18).toFloat()
            setColor(Color.parseColor("#F20B2E5C"))
            setStroke(dp(c, 2), Color.parseColor("#FFD200"))
        }
        addView(TextView(c).apply {
            text = baslik
            setTextColor(Color.parseColor("#FFD200"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            typeface = Typeface.DEFAULT_BOLD
        })
        addView(TextView(c).apply {
            text = metin
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
            setPadding(0, dp(c, 4), 0, 0)
        })
    }

    /** Gol için küçük kart: sağ üst köşe, ekranın en fazla ~%40'ı. */
    private fun kucukKart(c: Context, baslik: String, metin: String, ek: String, olcek: Float, logoEv: Bitmap?, logoDep: Bitmap?) = LinearLayout(c).apply {
        orientation = LinearLayout.VERTICAL
        fun d(v: Int) = (v * olcek * c.resources.displayMetrics.density).toInt()
        setPadding(d(14), d(8), d(16), d(10))
        background = GradientDrawable().apply {
            cornerRadius = d(12).toFloat()
            setColor(Color.parseColor("#E60B2E5C"))
            setStroke(dp(c, 1), Color.parseColor("#FFD200"))
        }
        val enFazla = (c.resources.displayMetrics.widthPixels * 0.42).toInt()
        addView(TextView(c).apply {
            text = baslik
            setTextColor(Color.parseColor("#FFD200"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f * olcek)
            typeface = Typeface.DEFAULT_BOLD
            maxWidth = enFazla
        })
        val satir = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, d(3), 0, 0)
        }
        fun logo(b: Bitmap?) {
            if (b == null) return
            satir.addView(ImageView(c).apply { setImageBitmap(b); scaleType = ImageView.ScaleType.FIT_CENTER },
                LinearLayout.LayoutParams(d(22), d(22)).apply { marginEnd = d(6); marginStart = if (satir.childCount > 0) d(6) else 0 })
        }
        logo(logoEv)
        satir.addView(TextView(c).apply {
            text = metin
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f * olcek)
            typeface = Typeface.DEFAULT_BOLD
            maxWidth = enFazla - d(70)
        })
        logo(logoDep)
        addView(satir)
        if (ek.isNotBlank()) addView(TextView(c).apply {
            text = ek
            setTextColor(Color.parseColor("#D6E2F0"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f * olcek)
            setPadding(0, d(3), 0, 0)
            maxWidth = enFazla
        })
    }

    private fun dp(c: Context, v: Int) = (v * c.resources.displayMetrics.density).toInt()
}

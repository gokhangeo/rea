package com.gokhan.kutukurucu

import android.content.Context
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
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Ekranda ne açık olursa olsun (kanal, uygulama, oyun) üstte görünen mesaj katmanı.
 * Gol bildirimleri ve telefondan gönderilen mesajlar buradan çıkar; süre bitince kendiliğinden kaybolur.
 * Diğer uygulamaların üstüne çizmek için "üstte gösterme" izni gerekir (Izinler.ustte).
 */
object Mesaj {
    private val ana = Handler(Looper.getMainLooper())
    private var pencere: WindowManager? = null
    private var gorunen: View? = null
    private var zamanlayici: Runnable? = null

    /** Mesajı gösterir. İzin yoksa false döner. */
    fun goster(c: Context, baslik: String, metin: String, sureMs: Long = 8000): Boolean {
        if (!Settings.canDrawOverlays(c)) return false
        val uygulama = c.applicationContext
        ana.post {
            val wm = pencere ?: (uygulama.getSystemService(Context.WINDOW_SERVICE) as WindowManager).also { pencere = it }
            gizle(hemen = true)
            val kart = kart(uygulama, baslik, metin)
            val dis = FrameLayout(uygulama).apply {
                setPadding(dp(uygulama, 36), dp(uygulama, 28), dp(uygulama, 36), 0)
                addView(kart, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))
            }
            val p = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                android.graphics.PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                y = dp(uygulama, 24)
            }
            if (runCatching { wm.addView(dis, p) }.isFailure) return@post
            dis.alpha = 0f
            dis.animate().alpha(1f).setDuration(200).start()
            gorunen = dis
            val kapat = Runnable { gizle(hemen = false) }
            zamanlayici = kapat
            ana.postDelayed(kapat, sureMs.coerceIn(1500, 60_000))
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

    private fun dp(c: Context, v: Int) = (v * c.resources.displayMetrics.density).toInt()
}

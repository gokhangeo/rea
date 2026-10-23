package com.gokhan.kutukurucu

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/**
 * TV ekranı: adresi ve kodu büyük yazar, eksik izinleri kumandayla verdirir, son kurulumları gösterir.
 * Asıl kullanım telefondaki/PC'deki sayfadan olur.
 */
class MainActivity : Activity() {
    private lateinit var adres: TextView
    private lateinit var kod: TextView
    private lateinit var izinler: LinearLayout
    private lateinit var isler: TextView
    private val ana = Handler(Looper.getMainLooper())
    private val yenile = object : Runnable {
        override fun run() {
            guncelle()
            ana.postDelayed(this, 3000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val kok = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(48), dp(32), dp(48), dp(32))
            setBackgroundColor(Color.parseColor("#0B2E5C"))
        }
        kok.addView(yazi("Kutu Kurucu", 34f, "#FFD200", true))
        kok.addView(yazi("Telefonda ya da bilgisayarda tarayıcıya şu adresi yazın:", 18f, "#C8D6EA"))
        adres = yazi("", 40f, "#FFFFFF", true).also { kok.addView(it) }
        kod = yazi("", 30f, "#FFFFFF", true).also { kok.addView(it) }
        izinler = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(16), 0, 0) }
        kok.addView(izinler)
        isler = yazi("", 16f, "#C8D6EA").also { it.setPadding(0, dp(16), 0, 0); kok.addView(it) }
        setContentView(ScrollView(this).apply { setBackgroundColor(Color.parseColor("#0B2E5C")); addView(kok) })
        SunucuServisi.baslat(this)
    }

    override fun onResume() {
        super.onResume()
        SunucuServisi.baslat(this)
        AnaEkran.uygula(this)
        // Arka planda açılamayan onay pencereleri.
        Kurucu.onaylariAl().forEach { runCatching { startActivity(it) } }
        ana.post(yenile)
    }

    override fun onPause() {
        ana.removeCallbacks(yenile)
        super.onPause()
    }

    private fun guncelle() {
        adres.text = Sunucu.adres()?.let { "http://$it:${Sunucu.PORT}" } ?: "Ağ bağlantısı yok"
        kod.text = "Kod: ${Sunucu.kod(this)}"

        val odak = izinler.findFocus()?.tag
        izinler.removeAllViews()
        satir(Izinler.yukleme(this), "Uygulama yükleme izni", "İzin ver") {
            ayarAc(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")))
        }
        satir(Izinler.ustte(this), "Arka planda onay penceresi açabilme", "İzin ver") {
            ayarAc(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }
        val secili = AnaEkran.secili(this)
        if (secili.isNotEmpty() && secili != AnaEkran.EV_EKRANI) {
            satir(AnaEkran.servisAcik(this), "Home tuşu servisi (ana ekran seçimi için)", "Aç") {
                ayarAc(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }
        izinler.addView(dugme("Kodu değiştir", "kod") {
            Sunucu.yeniKod(this)
            guncelle()
        })
        if (odak != null) izinler.findViewWithTag<View>(odak)?.requestFocus()

        val liste = Kurucu.liste()
        isler.text = if (liste.length() == 0) "Henüz kurulum yok." else buildString {
            append("Son işler:\n")
            for (k in 0 until minOf(5, liste.length())) {
                val i = liste.getJSONObject(k)
                append("• ").append(i.getString("ad")).append(": ").append(DURUM[i.getString("durum")] ?: i.getString("durum"))
                i.optString("mesaj").takeIf { it.isNotBlank() }?.let { append(" (").append(it).append(")") }
                append('\n')
            }
        }
    }

    private fun satir(tamam: Boolean, ad: String, dugmeYazi: String, ac: () -> Unit) {
        val s = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        s.addView(yazi((if (tamam) "✔ " else "✘ ") + ad, 20f, if (tamam) "#7CE38B" else "#FF8A80"), LinearLayout.LayoutParams(0, -2, 1f))
        if (!tamam) s.addView(dugme(dugmeYazi, ad, ac))
        izinler.addView(s)
    }

    private fun ayarAc(i: Intent) {
        try {
            startActivity(i)
        } catch (_: Exception) {
            Toast.makeText(this, "Bu kutuda ayar ekranı yok; README'deki ilk kurulum komutunu kullanın.", Toast.LENGTH_LONG).show()
        }
    }

    private fun dugme(yazi: String, etiket: String, tik: () -> Unit) = Button(this).apply {
        text = yazi
        tag = etiket
        isAllCaps = false
        setTextColor(Color.parseColor("#0B2E5C"))
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
        background = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), kutu("#FFD200"))
            addState(intArrayOf(), kutu("#C8D6EA"))
        }
        setPadding(dp(20), dp(8), dp(20), dp(8))
        setOnClickListener { tik() }
    }

    private fun kutu(renk: String) = GradientDrawable().apply { setColor(Color.parseColor(renk)); cornerRadius = dp(10).toFloat() }

    private fun yazi(t: String, sp: Float, renk: String, kalin: Boolean = false) = TextView(this).apply {
        text = t
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        setTextColor(Color.parseColor(renk))
        if (kalin) typeface = Typeface.DEFAULT_BOLD
        setPadding(0, dp(4), 0, dp(4))
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    companion object {
        val DURUM = mapOf(
            "hazirlaniyor" to "hazırlanıyor", "indiriliyor" to "indiriliyor", "onay" to "TV'de onay bekliyor",
            "kuruldu" to "kuruldu", "iptal" to "iptal edildi", "hata" to "kurulamadı",
        )
    }
}

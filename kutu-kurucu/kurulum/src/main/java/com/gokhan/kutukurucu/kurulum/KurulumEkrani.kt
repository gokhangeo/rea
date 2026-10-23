package com.gokhan.kutukurucu.kurulum

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/**
 * TV ekranı: adresi gösterir ve arka planda açılamayan onay pencerelerini öne getirir.
 * Kurulum/kaldırma komutları telefondan ya da PC'den 8091'deki sayfadan verilir.
 */
class KurulumEkrani : Activity() {
    private lateinit var adres: TextView
    private lateinit var durum: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val kutu = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0b2e5c"))
            setPadding(48, 48, 48, 48)
        }
        kutu.addView(TextView(this).apply {
            text = "Kutu Kurucu Kurulum"
            setTextColor(Color.WHITE)
            textSize = 28f
            setTypeface(typeface, Typeface.BOLD)
        })
        adres = TextView(this).apply {
            setTextColor(Color.parseColor("#ffd200"))
            textSize = 24f
            setPadding(0, 24, 0, 0)
        }
        kutu.addView(adres)
        kutu.addView(TextView(this).apply {
            text = "Telefondan ya da bilgisayardan bu adresi açın: Kutu Kurucu'yu kur, güncelle ya da kaldır."
            setTextColor(Color.parseColor("#c8d6ea"))
            textSize = 16f
            setPadding(0, 16, 0, 0)
        })
        durum = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 18f
            setPadding(0, 24, 0, 0)
        }
        kutu.addView(durum)
        kutu.addView(Button(this).apply {
            text = "Bekleyen onayı göster"
            setOnClickListener { onaylariGoster() }
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = 32
        })
        kutu.gravity = Gravity.START
        setContentView(kutu)
        SunucuServisi.baslat(this)
    }

    override fun onResume() {
        super.onResume()
        yaz()
        onaylariGoster()
    }

    private fun yaz() {
        val ip = Sunucu.adres()
        adres.text = if (ip != null) "http://$ip:${Sunucu.PORT}" else "Ağ bekleniyor…"
        val kurulu = Kurucu.kuruluAd(this)
        durum.text = if (kurulu != null) "Kutudaki Kutu Kurucu sürümü: $kurulu" else "Kutu Kurucu kutuda kurulu değil."
    }

    private fun onaylariGoster() {
        val onaylar = Kurucu.onaylariAl()
        onaylar.firstOrNull()?.let { ilk ->
            runCatching { startActivity(ilk) }
            onaylar.drop(1).forEach { Kurucu.onayEkle(it) }
        }
    }
}

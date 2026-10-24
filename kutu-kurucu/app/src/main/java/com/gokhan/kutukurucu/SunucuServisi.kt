package com.gokhan.kutukurucu

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import fi.iki.elonen.NanoHTTPD

/** Sayfa sunucusunu kutu açık olduğu sürece çalıştıran ön plan servisi. */
class SunucuServisi : Service() {
    private var sunucu: Sunucu? = null
    private val ana = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(KANAL, "Kutu Kurucu", NotificationManager.IMPORTANCE_MIN))
        val bildirim = Notification.Builder(this, KANAL)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Kutu Kurucu açık")
            .setContentText(Sunucu.adres()?.let { "http://$it:${Sunucu.PORT}" } ?: "")
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34) startForeground(1, bildirim, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(1, bildirim)
        sunucu = Sunucu(applicationContext).also {
            runCatching { it.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false) }
                .onFailure { e -> Log.e("KutuKurucu", "sunucu açılamadı", e) }
        }
        // Ev Ekranı açılışta Home servisini kendisi geri açar; seçim biraz sonra yeniden uygulanır.
        for (ms in longArrayOf(0, 15_000, 60_000)) ana.postDelayed({ AnaEkran.uygula(this) }, ms)
        // Seçili takımların gollerini izle (20 saniyede bir, yalnızca takım seçiliyse ağa çıkar).
        ana.post(object : Runnable {
            override fun run() {
                Goller.dongu(this@SunucuServisi)
                ana.postDelayed(this, 20_000)
            }
        })
        // Sessiz kurulum: açılıştan 40 sn sonra bağlantı denenir, sonra 30 dakikada bir Ev Ekranı/Kutu Kurucu güncellenir.
        ana.postDelayed(object : Runnable {
            override fun run() {
                val c = applicationContext
                Thread { runCatching { Sessiz.guncelle(c) } }.start()
                ana.postDelayed(this, 30 * 60_000L)
            }
        }, 40_000)
        // Yeni Kutu Kurucu sürümü: açılıştan 2 dk sonra, sonra 6 saatte bir bakılır.
        ana.postDelayed(object : Runnable {
            override fun run() {
                val c = applicationContext
                Thread { runCatching { Kurucu.yeniSurumDenetle(c) } }.start()
                ana.postDelayed(this, 6 * 3600_000L)
            }
        }, 120_000)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_STICKY

    override fun onDestroy() {
        sunucu?.stop()
        ana.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val KANAL = "sunucu"

        fun baslat(c: Context) {
            runCatching { c.startForegroundService(Intent(c, SunucuServisi::class.java)) }
                .onFailure { Log.e("KutuKurucu", "servis başlatılamadı", it) }
        }
    }
}

/** Kutu açılınca ya da Kutu Kurucu güncellenince sunucuyu başlatır; seçili ana ekran varsa onu açar. */
class Acilis : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        SunucuServisi.baslat(context)
        if (intent.action == Intent.ACTION_BOOT_COMPLETED && AnaEkran.hedef(context) != null) AnaEkran.ac(context)
    }
}

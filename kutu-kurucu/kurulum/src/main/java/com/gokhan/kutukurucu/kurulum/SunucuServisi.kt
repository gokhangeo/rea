package com.gokhan.kutukurucu.kurulum

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import fi.iki.elonen.NanoHTTPD

/** Kurulum sayfasını kutunun gücü açık olduğu sürece çalıştıran ön plan servisi. */
class SunucuServisi : Service() {
    private var sunucu: Sunucu? = null

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(KANAL, "Kutu Kurucu Kurulum", NotificationManager.IMPORTANCE_MIN))
        val bildirim = Notification.Builder(this, KANAL)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Kutu Kurucu Kurulum açık")
            .setContentText(Sunucu.adres()?.let { "http://$it:${Sunucu.PORT}" } ?: "")
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34) startForeground(1, bildirim, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(1, bildirim)
        sunucu = Sunucu(applicationContext).also {
            runCatching { it.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false) }
                .onFailure { e -> Log.e("KutuKurulum", "sunucu açılamadı", e) }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_STICKY

    override fun onDestroy() {
        sunucu?.stop()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val KANAL = "sunucu"

        fun baslat(c: Context) {
            runCatching { c.startForegroundService(Intent(c, SunucuServisi::class.java)) }
                .onFailure { Log.e("KutuKurulum", "servis başlatılamadı", it) }
        }
    }
}

/** Kutu açılınca ya da bu uygulama güncellenince sayfayı yeniden başlatır. */
class Acilis : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        SunucuServisi.baslat(context)
    }
}

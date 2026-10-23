package com.gokhan.kutukurucu

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log

/**
 * Gol olunca kısa düdük + boru sesi çalar (res/raw/gol.wav). Kanalın sesini kesmez, üstüne karışır;
 * TV'nin medya ses düzeyiyle çalar.
 */
object GolSesi {
    @Volatile private var calan: MediaPlayer? = null

    fun cal(c: Context) {
        runCatching {
            calan?.release()
            val mp = MediaPlayer()
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            c.applicationContext.resources.openRawResourceFd(R.raw.gol).use { fd ->
                mp.setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
            }
            mp.setOnCompletionListener { it.release(); if (calan === it) calan = null }
            mp.prepare()
            mp.start()
            calan = mp
        }.onFailure { Log.w("KutuKurucu", "gol sesi çalınamadı", it) }
    }
}

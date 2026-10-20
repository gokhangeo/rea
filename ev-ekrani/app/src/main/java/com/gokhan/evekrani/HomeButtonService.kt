package com.gokhan.evekrani

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.gathin.tvhome.MainActivity

/**
 * Android TV, seçilen ana ekran uygulamasını yok sayar; hazır ana ekran kurulu kalır ve bu servis devralır:
 *  - kumandanın Home tuşu Ev Ekranı'nı açar;
 *  - hazır ana ekran yine de açılırsa (açılış vb.) hemen Ev Ekranı ile değiştirilir.
 * Yalnızca hazır ana ekranların olayları gelir (servis ayarındaki packageNames).
 */
class HomeButtonService : AccessibilityService() {
    override fun onServiceConnected() {
        super.onServiceConnected()
        CrashGuard.install(this)
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode != KeyEvent.KEYCODE_HOME) return false
        // Ev Ekranı sürekli çöküyorsa Home tuşunu hazır ana ekrana bırak (CrashGuard).
        if (CrashGuard.crashLooping(this)) return false
        if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) openHome()
        return true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && event.packageName in STOCK_HOMES && !CrashGuard.crashLooping(this)) openHome()
    }

    override fun onInterrupt() {}

    private fun openHome() {
        startActivity(
            Intent(this, MainActivity::class.java)
                .setAction(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED),
        )
    }

    companion object {
        private val STOCK_HOMES = setOf("com.google.android.tvlauncher", "com.google.android.apps.tv.launcherx")
    }
}

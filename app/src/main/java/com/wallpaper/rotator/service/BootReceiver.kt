package com.wallpaper.rotator.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.wallpaper.rotator.WallpaperRotatorApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val app = context.applicationContext as WallpaperRotatorApp
        val prefs = app.preferencesManager
        val pending = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val onSchedule = prefs.rotateOnSchedule.first()
                val interval = prefs.rotationIntervalHours.first()
                RotationScheduler.syncPeriodicRotation(context, onSchedule, interval)

                val rotateOnBoot = prefs.rotateOnBoot.first()
                if (rotateOnBoot) {
                    RotationScheduler.triggerImmediate(context)
                }
            } finally {
                pending.finish()
            }
        }
    }
}

package com.wallpaper.rotator.service

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object RotationScheduler {

    private const val PERIODIC_WORK_NAME = "wallpaper_rotation_periodic"
    private const val IMMEDIATE_WORK_NAME = "wallpaper_rotation_immediate"

    fun scheduleRotation(context: Context, intervalHours: Float) {
        val intervalMinutes = (intervalHours * 60).toLong().coerceAtLeast(15)

        val request = PeriodicWorkRequestBuilder<WallpaperRotationWorker>(
            intervalMinutes, TimeUnit.MINUTES
        ).setConstraints(
            Constraints.Builder()
                .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                .build()
        ).build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun syncPeriodicRotation(context: Context, enabled: Boolean, intervalHours: Float) {
        if (enabled) {
            scheduleRotation(context, intervalHours)
        } else {
            cancelRotation(context)
        }
    }

    fun cancelRotation(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_WORK_NAME)
    }

    fun triggerImmediate(context: Context) {
        val request = OneTimeWorkRequestBuilder<WallpaperRotationWorker>().build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            IMMEDIATE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }
}

package com.wallpaper.rotator

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import com.wallpaper.rotator.data.db.AppDatabase
import com.wallpaper.rotator.data.repository.PhotoRepository
import com.wallpaper.rotator.ml.CropEngine
import com.wallpaper.rotator.ml.PoseDetector
import com.wallpaper.rotator.service.RotationScheduler
import com.wallpaper.rotator.service.ScreenUnlockReceiver
import com.wallpaper.rotator.util.PreferencesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class WallpaperRotatorApp : Application() {

    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }
    val photoRepository: PhotoRepository by lazy { PhotoRepository(database.photoMetadataDao(), this) }
    val preferencesManager: PreferencesManager by lazy { PreferencesManager(this) }
    val poseDetector: PoseDetector by lazy { PoseDetector() }
    val cropEngine: CropEngine by lazy { CropEngine(poseDetector) }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var screenUnlockReceiver: ScreenUnlockReceiver? = null

    override fun onCreate() {
        super.onCreate()
        RotationScheduler.scheduleRotation(this, 6f)

        // Monitor "Rotate on unlock" preference and register/unregister receiver as needed
        appScope.launch {
            try {
                preferencesManager.rotateOnUnlock.collect { rotateOnUnlock ->
                    if (rotateOnUnlock) {
                        registerScreenUnlockReceiver()
                    } else {
                        unregisterScreenUnlockReceiver()
                    }
                }
            } catch (e: Exception) {
                // Silently fail if preferences cannot be read
            }
        }
    }

    private fun registerScreenUnlockReceiver() {
        if (screenUnlockReceiver != null) return // Already registered

        screenUnlockReceiver = ScreenUnlockReceiver {
            // Trigger immediate rotation when screen is unlocked
            RotationScheduler.triggerImmediate(applicationContext)
        }

        val intentFilter = IntentFilter(Intent.ACTION_USER_PRESENT)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            registerReceiver(screenUnlockReceiver, intentFilter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(screenUnlockReceiver, intentFilter)
        }
    }

    private fun unregisterScreenUnlockReceiver() {
        if (screenUnlockReceiver != null) {
            try {
                unregisterReceiver(screenUnlockReceiver)
            } catch (e: IllegalArgumentException) {
                // Receiver was not registered
            }
            screenUnlockReceiver = null
        }
    }
}

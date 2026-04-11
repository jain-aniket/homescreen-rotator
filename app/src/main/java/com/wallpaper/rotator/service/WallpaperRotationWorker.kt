package com.wallpaper.rotator.service

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.wallpaper.rotator.WallpaperRotatorApp
import com.wallpaper.rotator.util.WallpaperApply
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class WallpaperRotationWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val app = applicationContext as WallpaperRotatorApp
            val repository = app.photoRepository
            val prefs = app.preferencesManager

            val lastIndex = prefs.lastRotatedIndex.first()
            val result = repository.getNextForRotation(lastIndex) ?: return@withContext Result.success()

            val (photo, _) = result
            val ok = WallpaperApply.applyPhotoAsWallpaper(applicationContext, photo, repository, prefs)
            if (!ok) Result.retry() else Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}

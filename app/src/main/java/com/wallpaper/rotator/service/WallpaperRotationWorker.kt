package com.wallpaper.rotator.service

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.RectF
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.wallpaper.rotator.WallpaperRotatorApp
import com.wallpaper.rotator.util.DisplayUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.ceil
import kotlin.math.max

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

            val (photo, newIndex) = result
            val file = File(photo.filePath)
            if (!file.exists()) return@withContext Result.retry()

            val original = BitmapFactory.decodeFile(file.absolutePath) ?: return@withContext Result.retry()

            val crop = RectF(photo.cropX, photo.cropY, photo.cropX + photo.cropWidth, photo.cropY + photo.cropHeight)
            val cropLeft = crop.left.toInt().coerceIn(0, original.width - 1)
            val cropTop = crop.top.toInt().coerceIn(0, original.height - 1)
            val cropW = crop.width().toInt().coerceAtMost(original.width - cropLeft)
            val cropH = crop.height().toInt().coerceAtMost(original.height - cropTop)

            if (cropW <= 0 || cropH <= 0) {
                original.recycle()
                return@withContext Result.failure()
            }

            val cropped = Bitmap.createBitmap(original, cropLeft, cropTop, cropW, cropH)
            if (cropped !== original) original.recycle()

            val (targetW, targetH) = DisplayUtils.getWallpaperDimensions(applicationContext)

            // Scale preserving aspect ratio, filling the target (may overshoot one dimension).
            // Ceil after multiply: truncating with toInt() can make scaledW < targetW (or height),
            // which breaks the center-crop createBitmap step ("x + width must be <= bitmap.width()").
            val scaleRatio = max(
                targetW.toDouble() / cropped.width,
                targetH.toDouble() / cropped.height
            )
            val scaledW = ceil(cropped.width * scaleRatio).toInt()
            val scaledH = ceil(cropped.height * scaleRatio).toInt()
            val scaled = Bitmap.createScaledBitmap(cropped, scaledW, scaledH, true)
            if (scaled !== cropped) cropped.recycle()

            // Center-crop to exact screen dimensions
            val cropX = (scaledW - targetW) / 2
            val cropY = (scaledH - targetH) / 2
            val wallpaperBitmap = Bitmap.createBitmap(scaled, cropX, cropY, targetW, targetH)
            if (wallpaperBitmap !== scaled) scaled.recycle()

            val wallpaperManager = WallpaperManager.getInstance(applicationContext)
            wallpaperManager.setBitmap(wallpaperBitmap, null, true, WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK)
            wallpaperBitmap.recycle()

            prefs.setLastRotatedIndex(newIndex)
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}

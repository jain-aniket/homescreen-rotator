package com.wallpaper.rotator.util

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.RectF
import com.wallpaper.rotator.data.db.PhotoMetadata
import com.wallpaper.rotator.data.repository.PhotoRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.ceil
import kotlin.math.max

object WallpaperApply {

    /**
     * Sets home + lock wallpaper from [photo]'s crop. Only succeeds if [photo] is in the enabled
     * rotation queue; updates [PreferencesManager.setLastRotatedIndex] to that queue index.
     */
    suspend fun applyPhotoAsWallpaper(
        context: Context,
        photo: PhotoMetadata,
        repository: PhotoRepository,
        prefs: PreferencesManager
    ): Boolean = withContext(Dispatchers.IO) {
        val index = repository.indexInEnabledQueue(photo.photoId) ?: return@withContext false

        val file = File(photo.filePath)
        if (!file.exists()) return@withContext false

        val original = BitmapFactory.decodeFile(file.absolutePath) ?: return@withContext false

        val crop = RectF(photo.cropX, photo.cropY, photo.cropX + photo.cropWidth, photo.cropY + photo.cropHeight)
        val cropLeft = crop.left.toInt().coerceIn(0, original.width - 1)
        val cropTop = crop.top.toInt().coerceIn(0, original.height - 1)
        val cropW = crop.width().toInt().coerceAtMost(original.width - cropLeft)
        val cropH = crop.height().toInt().coerceAtMost(original.height - cropTop)

        if (cropW <= 0 || cropH <= 0) {
            original.recycle()
            return@withContext false
        }

        val cropped = Bitmap.createBitmap(original, cropLeft, cropTop, cropW, cropH)
        if (cropped !== original) original.recycle()

        val (targetW, targetH) = DisplayUtils.getWallpaperDimensions(context)

        val scaleRatio = max(
            targetW.toDouble() / cropped.width,
            targetH.toDouble() / cropped.height
        )
        val scaledW = ceil(cropped.width * scaleRatio).toInt()
        val scaledH = ceil(cropped.height * scaleRatio).toInt()
        val scaled = Bitmap.createScaledBitmap(cropped, scaledW, scaledH, true)
        if (scaled !== cropped) cropped.recycle()

        val cropX = (scaledW - targetW) / 2
        val cropY = (scaledH - targetH) / 2
        val wallpaperBitmap = Bitmap.createBitmap(scaled, cropX, cropY, targetW, targetH)
        if (wallpaperBitmap !== scaled) scaled.recycle()

        try {
            val wallpaperManager = WallpaperManager.getInstance(context)
            wallpaperManager.setBitmap(
                wallpaperBitmap,
                null,
                true,
                WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
            )
        } finally {
            wallpaperBitmap.recycle()
        }

        prefs.setLastRotatedIndex(index)
        true
    }
}

package com.wallpaper.rotator.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.wallpaper.rotator.data.db.PhotoMetadata
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Writes a small cropped JPEG for the library grid. Uses one subsampled decode of the source file
 * (never full-resolution), then crops and optionally scales — no duplicate full decodes.
 */
object ThumbnailGenerator {

    private const val MAX_DECODE_SIDE = 900
    private const val MAX_THUMB_SIDE = 448
    private const val JPEG_QUALITY = 80

    fun thumbnailFile(context: Context, photoId: Long): File =
        File(File(context.filesDir, "thumbnails"), "$photoId.jpg")

    /**
     * @return true if the thumbnail file was written successfully
     */
    fun writeThumbnail(context: Context, photo: PhotoMetadata): Boolean {
        val path = photo.filePath
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        val ow = bounds.outWidth
        val oh = bounds.outHeight
        if (ow <= 0 || oh <= 0) return false

        var sample = 1
        while (max(ow, oh) / sample > MAX_DECODE_SIDE) {
            sample *= 2
        }

        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = sample
        }
        val decoded = BitmapFactory.decodeFile(path, decodeOpts) ?: return false

        return try {
            val x = (photo.cropX * decoded.width / ow.toFloat()).roundToInt()
                .coerceIn(0, max(0, decoded.width - 1))
            val y = (photo.cropY * decoded.height / oh.toFloat()).roundToInt()
                .coerceIn(0, max(0, decoded.height - 1))
            val w = (photo.cropWidth * decoded.width / ow.toFloat()).roundToInt()
                .coerceAtLeast(1)
                .coerceAtMost(decoded.width - x)
            val h = (photo.cropHeight * decoded.height / oh.toFloat()).roundToInt()
                .coerceAtLeast(1)
                .coerceAtMost(decoded.height - y)

            var cropped = Bitmap.createBitmap(decoded, x, y, w, h)
            if (cropped !== decoded) decoded.recycle()

            val maxDim = max(cropped.width, cropped.height)
            if (maxDim > MAX_THUMB_SIDE) {
                val scale = MAX_THUMB_SIDE.toFloat() / maxDim
                val scaled = Bitmap.createScaledBitmap(
                    cropped,
                    max(1, (cropped.width * scale).roundToInt()),
                    max(1, (cropped.height * scale).roundToInt()),
                    true
                )
                if (scaled !== cropped) cropped.recycle()
                cropped = scaled
            }

            val out = thumbnailFile(context, photo.photoId)
            out.parentFile?.mkdirs()
            out.outputStream().use { os ->
                cropped.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, os)
            }
            cropped.recycle()
            true
        } catch (_: Exception) {
            if (!decoded.isRecycled) decoded.recycle()
            false
        }
    }
}

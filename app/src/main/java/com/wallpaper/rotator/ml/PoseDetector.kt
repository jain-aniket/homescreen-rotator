package com.wallpaper.rotator.ml

import android.graphics.Bitmap
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PoseDetector {
    private val options = PoseDetectorOptions.Builder()
        .setDetectorMode(PoseDetectorOptions.SINGLE_IMAGE_MODE)
        .build()

    private val detector = PoseDetection.getClient(options)

    suspend fun detectPoses(bitmap: Bitmap): List<Pose> = withContext(Dispatchers.IO) {
        val image = InputImage.fromBitmap(bitmap, 0)
        try {
            val pose = Tasks.await(detector.process(image))
            if (pose.allPoseLandmarks.isNotEmpty()) listOf(pose) else emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun close() {
        detector.close()
    }
}

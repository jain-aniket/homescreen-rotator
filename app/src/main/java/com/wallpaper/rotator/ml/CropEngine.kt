package com.wallpaper.rotator.ml

import android.graphics.Bitmap
import android.graphics.PointF
import android.graphics.RectF
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseLandmark
import com.wallpaper.rotator.data.db.SubjectType

class CropEngine(private val poseDetector: PoseDetector) {

    data class CropResult(val cropBox: RectF, val subjectType: SubjectType)

    suspend fun calculateAutoCrop(
        bitmap: Bitmap,
        wallpaperAspectRatio: Float
    ): CropResult {
        val imageW = bitmap.width.toFloat()
        val imageH = bitmap.height.toFloat()

        val poses = poseDetector.detectPoses(bitmap)

        if (poses.isEmpty()) {
            return CropResult(landscapeCrop(imageW, imageH, wallpaperAspectRatio), SubjectType.LANDSCAPE)
        }

        val pose = poses[0]
        val hasFace = hasFaceDetected(pose)

        if (!hasFace) {
            val bounds = bodyBoundingBox(pose, imageW, imageH)
            return CropResult(
                fitToAspectRatio(bounds, wallpaperAspectRatio, imageW, imageH),
                SubjectType.FULL_BODY
            )
        }

        return if (isFullBodyDetected(pose, imageH)) {
            val bounds = fullBodyBounds(pose, imageW, imageH)
            CropResult(
                fitToAspectRatio(bounds, wallpaperAspectRatio, imageW, imageH),
                SubjectType.FULL_BODY
            )
        } else {
            val bounds = faceBounds(pose, imageW, imageH)
            CropResult(
                fitToAspectRatio(bounds, wallpaperAspectRatio, imageW, imageH),
                SubjectType.FACE
            )
        }
    }

    private fun hasFaceDetected(pose: Pose): Boolean {
        val nose = pose.getPoseLandmark(PoseLandmark.NOSE)
        val leftEye = pose.getPoseLandmark(PoseLandmark.LEFT_EYE)
        val rightEye = pose.getPoseLandmark(PoseLandmark.RIGHT_EYE)

        return (nose?.inFrameLikelihood ?: 0f) > 0.5f &&
                (leftEye?.inFrameLikelihood ?: 0f) > 0.5f &&
                (rightEye?.inFrameLikelihood ?: 0f) > 0.5f
    }

    private fun isFullBodyDetected(pose: Pose, imageHeight: Float): Boolean {
        val lowerLimbs = listOf(
            PoseLandmark.LEFT_KNEE, PoseLandmark.RIGHT_KNEE,
            PoseLandmark.LEFT_ANKLE, PoseLandmark.RIGHT_ANKLE
        )
        val hasLowerBody = lowerLimbs.any { type ->
            (pose.getPoseLandmark(type)?.inFrameLikelihood ?: 0f) > 0.3f
        }
        if (!hasLowerBody) return false

        val faceCenter = getFaceCenter(pose) ?: return false
        val lowestLimb = getLowestLimb(pose) ?: return false
        val verticalDistance = lowestLimb.y - faceCenter.y
        return verticalDistance > imageHeight * 0.25f
    }

    private fun getFaceCenter(pose: Pose): PointF? {
        val nose = pose.getPoseLandmark(PoseLandmark.NOSE) ?: return null
        return PointF(nose.position.x, nose.position.y)
    }

    private fun getLowestLimb(pose: Pose): PointF? {
        val candidates = listOf(
            PoseLandmark.LEFT_KNEE, PoseLandmark.RIGHT_KNEE,
            PoseLandmark.LEFT_ANKLE, PoseLandmark.RIGHT_ANKLE
        )
        var lowest: PointF? = null
        for (type in candidates) {
            val lm = pose.getPoseLandmark(type) ?: continue
            if (lm.inFrameLikelihood > 0.3f) {
                val pos = lm.position
                if (lowest == null || pos.y > lowest.y) {
                    lowest = PointF(pos.x, pos.y)
                }
            }
        }
        return lowest
    }

    private fun landscapeCrop(imageW: Float, imageH: Float, ar: Float): RectF {
        val targetW: Float
        val targetH: Float
        if (imageH / imageW > ar) {
            targetW = imageW
            targetH = imageW * ar
        } else {
            targetH = imageH
            targetW = imageH / ar
        }
        val left = (imageW - targetW) / 2f
        val top = (imageH - targetH) / 2f
        return RectF(left, top, left + targetW, top + targetH)
    }

    private fun bodyBoundingBox(pose: Pose, imageW: Float, imageH: Float): RectF {
        var minX = imageW; var minY = imageH; var maxX = 0f; var maxY = 0f
        for (lm in pose.allPoseLandmarks) {
            if (lm.inFrameLikelihood > 0.3f) {
                minX = minOf(minX, lm.position.x)
                minY = minOf(minY, lm.position.y)
                maxX = maxOf(maxX, lm.position.x)
                maxY = maxOf(maxY, lm.position.y)
            }
        }
        val pad = maxOf(maxX - minX, maxY - minY) * 0.15f
        return RectF(
            (minX - pad).coerceAtLeast(0f),
            (minY - pad).coerceAtLeast(0f),
            (maxX + pad).coerceAtMost(imageW),
            (maxY + pad).coerceAtMost(imageH)
        )
    }

    private fun fullBodyBounds(pose: Pose, imageW: Float, imageH: Float): RectF {
        val bounds = bodyBoundingBox(pose, imageW, imageH)
        val h = bounds.height()
        return RectF(
            bounds.left,
            (bounds.top - h * 0.1f).coerceAtLeast(0f),
            bounds.right,
            (bounds.bottom + h * 0.05f).coerceAtMost(imageH)
        )
    }

    private fun faceBounds(pose: Pose, imageW: Float, imageH: Float): RectF {
        val landmarks = listOf(
            PoseLandmark.NOSE, PoseLandmark.LEFT_EYE, PoseLandmark.RIGHT_EYE,
            PoseLandmark.LEFT_EAR, PoseLandmark.RIGHT_EAR,
            PoseLandmark.LEFT_SHOULDER, PoseLandmark.RIGHT_SHOULDER
        )
        val points = landmarks.mapNotNull { pose.getPoseLandmark(it)?.position }
        if (points.isEmpty()) return landscapeCrop(imageW, imageH, 1.5f)

        var minX = imageW; var minY = imageH; var maxX = 0f; var maxY = 0f
        for (p in points) {
            minX = minOf(minX, p.x); minY = minOf(minY, p.y)
            maxX = maxOf(maxX, p.x); maxY = maxOf(maxY, p.y)
        }

        val nose = pose.getPoseLandmark(PoseLandmark.NOSE)
        val leftEye = pose.getPoseLandmark(PoseLandmark.LEFT_EYE)
        val eyeToNose = if (nose != null && leftEye != null) {
            kotlin.math.abs(nose.position.y - leftEye.position.y)
        } else 30f
        val headTop = minY - eyeToNose * 2.5f

        val padX = (maxX - minX) * 0.2f
        return RectF(
            (minX - padX).coerceAtLeast(0f),
            headTop.coerceAtLeast(0f),
            (maxX + padX).coerceAtMost(imageW),
            (maxY + padX).coerceAtMost(imageH)
        )
    }

    private fun fitToAspectRatio(
        bounds: RectF, ar: Float, imageW: Float, imageH: Float
    ): RectF {
        val cx = bounds.centerX()
        val cy = bounds.centerY()

        var w = bounds.width().coerceAtLeast(imageW * 0.3f)
        var h = bounds.height().coerceAtLeast(imageH * 0.3f)

        val currentAR = h / w
        if (currentAR < ar) h = w * ar else w = h / ar

        // Clamp to image bounds while maintaining AR
        if (w > imageW) {
            w = imageW
            h = w * ar
        }
        if (h > imageH) {
            h = imageH
            w = h / ar
        }

        var left = cx - w / 2f
        var top = cy - h / 2f
        var right = left + w
        var bottom = top + h

        // Adjust for bounds violations, prioritizing staying within image
        if (left < 0f) {
            left = 0f
            right = w
        }
        if (top < 0f) {
            top = 0f
            bottom = h
        }
        if (right > imageW) {
            right = imageW
            left = (imageW - w).coerceAtLeast(0f)
        }
        if (bottom > imageH) {
            bottom = imageH
            top = (imageH - h).coerceAtLeast(0f)
        }

        return RectF(
            left.coerceAtLeast(0f),
            top.coerceAtLeast(0f),
            right.coerceAtMost(imageW),
            bottom.coerceAtMost(imageH)
        )
    }
}

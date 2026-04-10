package com.wallpaper.rotator.util

import android.content.Context
import android.graphics.Point
import android.os.Build
import android.view.WindowManager

object DisplayUtils {
    fun getWallpaperDimensions(context: Context): Pair<Int, Int> {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = wm.currentWindowMetrics.bounds
            Pair(bounds.width(), bounds.height())
        } else {
            @Suppress("DEPRECATION")
            val display = wm.defaultDisplay
            val size = Point()
            @Suppress("DEPRECATION")
            display.getRealSize(size)
            Pair(size.x, size.y)
        }
    }

    fun getWallpaperAspectRatio(context: Context): Float {
        val (w, h) = getWallpaperDimensions(context)
        return h.toFloat() / w.toFloat()
    }

    fun getAspectRatioString(context: Context): String {
        val (w, h) = getWallpaperDimensions(context)
        val g = gcd(w, h)
        val simplifiedW = w / g
        val simplifiedH = h / g

        // Try to map to a standard aspect ratio within 2% tolerance
        // Ratios are stored as (height, width) to match portrait aspect ratio (h/w > 1)
        val standardRatios = listOf(
            Pair(9, 20),   // 20:9 phones
            Pair(9, 19),   // 19:9
            Pair(9, 18),   // 18:9
            Pair(9, 16),   // 16:9
            Pair(9, 21),   // 21:9
            Pair(2, 3),    // 3:2
            Pair(3, 5),    // 5:3
            Pair(3, 4),    // 4:3
            Pair(1, 2)     // 2:1
        )

        val actualRatio = simplifiedH.toFloat() / simplifiedW.toFloat()
        val closestRatio = standardRatios.minByOrNull { (shortSide, longSide) ->
            val stdRatio = longSide.toFloat() / shortSide.toFloat()
            kotlin.math.abs(actualRatio - stdRatio) / stdRatio
        }

        return if (closestRatio != null && kotlin.math.abs(
                (closestRatio.second.toFloat() / closestRatio.first.toFloat()) - actualRatio
            ) / actualRatio < 0.02f
        ) {
            // Within 2% of a standard ratio, use the standard ratio (display as long:short)
            "${closestRatio.second}:${closestRatio.first}"
        } else {
            // Use the simplified GCD ratio
            "$simplifiedW:$simplifiedH"
        }
    }

    private fun gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)
}

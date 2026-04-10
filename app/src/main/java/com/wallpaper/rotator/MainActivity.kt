package com.wallpaper.rotator

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.wallpaper.rotator.service.RotationScheduler
import com.wallpaper.rotator.ui.navigation.NavGraph
import com.wallpaper.rotator.ui.theme.WallpaperRotatorTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Ensure periodic rotation is scheduled when the app starts
        // (in case it was lost after an app update or system maintenance)
        lifecycleScope.launch {
            try {
                val app = application as WallpaperRotatorApp
                val prefs = app.preferencesManager
                val interval = prefs.rotationIntervalHours.first()
                RotationScheduler.scheduleRotation(this@MainActivity, interval)
            } catch (e: Exception) {
                // Silently fail if preferences cannot be read
            }
        }

        setContent {
            WallpaperRotatorTheme {
                val navController = rememberNavController()
                NavGraph(navController = navController)
            }
        }
    }
}

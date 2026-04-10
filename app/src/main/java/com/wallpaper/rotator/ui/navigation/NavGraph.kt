package com.wallpaper.rotator.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.wallpaper.rotator.ui.crop.CropEditorScreen
import com.wallpaper.rotator.ui.import_photos.ImportScreen
import com.wallpaper.rotator.ui.library.LibraryScreen
import com.wallpaper.rotator.ui.settings.SettingsScreen

sealed class Screen(val route: String) {
    data object Library : Screen("library")
    data object Import : Screen("import")
    data object CropEditor : Screen("crop_editor/{photoId}") {
        fun createRoute(photoId: Long) = "crop_editor/$photoId"
    }
    data object Settings : Screen("settings")
}

@Composable
fun NavGraph(navController: NavHostController) {
    NavHost(navController = navController, startDestination = Screen.Library.route) {

        composable(Screen.Library.route) {
            LibraryScreen(
                onNavigateToImport = { navController.navigate(Screen.Import.route) },
                onNavigateToCrop = { photoId ->
                    navController.navigate(Screen.CropEditor.createRoute(photoId))
                },
                onNavigateToSettings = { navController.navigate(Screen.Settings.route) }
            )
        }

        composable(Screen.Import.route) {
            ImportScreen(
                onNavigateBack = { navController.popBackStack() },
                onImportComplete = {
                    navController.popBackStack(Screen.Library.route, inclusive = false)
                }
            )
        }

        composable(
            route = Screen.CropEditor.route,
            arguments = listOf(navArgument("photoId") { type = NavType.LongType })
        ) { backStackEntry ->
            val photoId = backStackEntry.arguments?.getLong("photoId") ?: return@composable
            CropEditorScreen(
                photoId = photoId,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}

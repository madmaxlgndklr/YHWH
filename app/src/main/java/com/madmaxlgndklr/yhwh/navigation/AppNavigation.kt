package com.madmaxlgndklr.yhwh.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.madmaxlgndklr.yhwh.ui.screen.GameScreen
import com.madmaxlgndklr.yhwh.ui.screen.SettingsScreen

private object Routes {
    const val GAME = "game"
    const val SETTINGS = "settings"
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Routes.GAME) {
        composable(Routes.GAME) {
            GameScreen(onNavigateToSettings = { navController.navigate(Routes.SETTINGS) })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen()
        }
    }
}

package com.madmaxlgndklr.yhwh.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.madmaxlgndklr.yhwh.ui.GameViewModel
import com.madmaxlgndklr.yhwh.ui.screen.GameScreen
import com.madmaxlgndklr.yhwh.ui.screen.SettingsScreen

private object Routes {
    const val GAME = "game"
    const val SETTINGS = "settings"
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val gameViewModel: GameViewModel = viewModel()

    NavHost(navController = navController, startDestination = Routes.GAME) {
        composable(Routes.GAME) {
            GameScreen(
                onNavigateToSettings = { navController.navigate(Routes.SETTINGS) },
                viewModel = gameViewModel
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(viewModel = gameViewModel)
        }
    }
}

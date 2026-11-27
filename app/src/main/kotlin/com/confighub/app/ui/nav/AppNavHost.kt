package com.confighub.app.ui.nav

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.confighub.app.ui.detail.DetailScreen
import com.confighub.app.ui.home.HomeScreen
import com.confighub.app.ui.settings.SettingsScreen
import com.confighub.app.ui.sources.SourcesScreen

@Composable
fun AppNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenSources = { navController.navigate(Routes.SOURCES) },
                onOpenDetail = { hash -> navController.navigate("detail/$hash") },
            )
        }
        composable(
            route = Routes.DETAIL,
            arguments = listOf(navArgument("hash") { type = NavType.StringType }),
        ) {
            DetailScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onOpenSources = { navController.navigate(Routes.SOURCES) },
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.SOURCES) {
            SourcesScreen(onBack = { navController.popBackStack() })
        }
    }
}

object Routes {
    const val HOME = "home"
    const val DETAIL = "detail/{hash}"
    const val SETTINGS = "settings"
    const val SOURCES = "sources"
}

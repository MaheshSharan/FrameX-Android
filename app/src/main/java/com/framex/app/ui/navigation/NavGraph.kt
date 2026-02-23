package com.framex.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.framex.app.ui.screens.AboutScreen
import com.framex.app.ui.screens.AppearanceScreen
import com.framex.app.ui.screens.DashboardScreen
import com.framex.app.ui.screens.OnboardingScreen
import com.framex.app.ui.screens.OverlayCustomizationScreen
import com.framex.app.ui.screens.PermissionsScreen
import com.framex.app.ui.screens.SplashScreen

sealed class Screen(val route: String) {
    object Splash : Screen("splash")
    object Onboarding : Screen("onboarding")
    object Dashboard : Screen("dashboard")
    object Appearance : Screen("appearance")
    object OverlayCustomization : Screen("overlay_customization")
    object Permissions : Screen("permissions")
    object About : Screen("about")
}

@Composable
fun FrameXNavGraph(
    navController: NavHostController = rememberNavController(),
    startDestination: String = Screen.Splash.route
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Screen.Splash.route) {
            SplashScreen(
                onNavigateToOnboarding = { navController.navigate(Screen.Onboarding.route) { popUpTo(0) } },
                onNavigateToDashboard = { navController.navigate(Screen.Dashboard.route) { popUpTo(0) } }
            )
        }
        composable(Screen.Onboarding.route) {
            OnboardingScreen(
                onFinishOnboarding = { navController.navigate(Screen.Dashboard.route) { popUpTo(0) } }
            )
        }
        composable(Screen.Dashboard.route) {
            DashboardScreen(
                onNavigateToAppearance = { navController.navigate(Screen.Appearance.route) },
                onNavigateToOverlayCustomization = { navController.navigate(Screen.OverlayCustomization.route) },
                onNavigateToPermissions = { navController.navigate(Screen.Permissions.route) },
                onNavigateToAbout = { navController.navigate(Screen.About.route) }
            )
        }
        composable(Screen.Appearance.route) {
            AppearanceScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable(Screen.OverlayCustomization.route) {
            OverlayCustomizationScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable(Screen.Permissions.route) {
            PermissionsScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable(Screen.About.route) {
            AboutScreen(onNavigateBack = { navController.popBackStack() })
        }
    }
}

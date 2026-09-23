package com.framex.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.framex.app.ui.screens.about.AboutRoute
import com.framex.app.ui.screens.appearance.AppearanceRoute
import com.framex.app.ui.screens.dashboard.DashboardRoute
import com.framex.app.ui.screens.onboarding.OnboardingRoute
import com.framex.app.ui.screens.overlay.OverlayCustomizationRoute
import com.framex.app.ui.screens.permissions.PermissionsRoute
import com.framex.app.ui.screens.splash.SplashRoute
import com.framex.app.ui.screens.thermal.ThermalDiagnosticsRoute
import com.framex.app.ui.screens.performance.PerformanceRoute

sealed class Screen(val route: String) {
    object Splash : Screen("splash")
    object Onboarding : Screen("onboarding")
    object Dashboard : Screen("dashboard")
    object Appearance : Screen("appearance")
    object OverlayCustomization : Screen("overlay_customization")
    object Permissions : Screen("permissions")
    object About : Screen("about")
    object Performance : Screen("performance")
    object ThermalDiagnostics : Screen("thermal_diagnostics")
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
            SplashRoute(
                onNavigateToOnboarding = { navController.navigate(Screen.Onboarding.route) { popUpTo(0) } },
                onNavigateToDashboard = { navController.navigate(Screen.Dashboard.route) { popUpTo(0) } }
            )
        }
        composable(Screen.Onboarding.route) {
            OnboardingRoute(
                onFinishOnboarding = { navController.navigate(Screen.Dashboard.route) { popUpTo(0) } }
            )
        }
        composable(Screen.Dashboard.route) {
            DashboardRoute(
                onNavigateToAppearance = { navController.navigate(Screen.Appearance.route) },
                onNavigateToOverlayCustomization = { navController.navigate(Screen.OverlayCustomization.route) },
                onNavigateToPermissions = { navController.navigate(Screen.Permissions.route) },
                onNavigateToAbout = { navController.navigate(Screen.About.route) },
                onNavigateToPerformance = { navController.navigate(Screen.Performance.route) },
                onNavigateToThermalDiagnostics = { navController.navigate(Screen.ThermalDiagnostics.route) }
            )
        }
        composable(Screen.Appearance.route) {
            AppearanceRoute(onNavigateBack = { navController.safePopBackStack() })
        }
        composable(Screen.OverlayCustomization.route) {
            OverlayCustomizationRoute(onNavigateBack = { navController.safePopBackStack() })
        }
        composable(Screen.Permissions.route) {
            PermissionsRoute(onNavigateBack = { navController.safePopBackStack() })
        }
        composable(Screen.About.route) {
            AboutRoute(onNavigateBack = { navController.safePopBackStack() })
        }
        composable(Screen.Performance.route) {
            PerformanceRoute(onNavigateBack = { navController.safePopBackStack() })
        }
        composable(Screen.ThermalDiagnostics.route) {
            ThermalDiagnosticsRoute(onNavigateBack = { navController.safePopBackStack() })
        }
    }
}

private fun NavHostController.safePopBackStack() {
    if (previousBackStackEntry != null) {
        popBackStack()
    }
}

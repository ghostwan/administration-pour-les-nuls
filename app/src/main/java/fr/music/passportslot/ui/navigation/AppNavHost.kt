package fr.music.passportslot.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import fr.music.passportslot.ui.screens.captcha.CaptchaScreen
import fr.music.passportslot.ui.screens.home.HomeScreen
import fr.music.passportslot.ui.screens.results.ResultsScreen
import fr.music.passportslot.ui.screens.settings.SettingsScreen

@Composable
fun AppNavHost(
    navController: NavHostController,
    startDestination: String = Screen.Home.route
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Screen.Home.route) { backStackEntry ->
            // Check if returning from captcha screen with success
            val captchaResult = backStackEntry.savedStateHandle
                .get<String>("captcha_result")

            HomeScreen(
                onNavigateToResults = {
                    navController.navigate(Screen.Results.route)
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                },
                onNavigateToCaptcha = {
                    navController.navigate(Screen.Captcha.route)
                },
                captchaResult = captchaResult
            )

            // Clear the flag after reading it
            LaunchedEffect(captchaResult) {
                if (captchaResult != null) {
                    backStackEntry.savedStateHandle.remove<String>("captcha_result")
                }
            }
        }

        composable(Screen.Results.route) {
            ResultsScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.Captcha.route) {
            CaptchaScreen(
                onCaptchaCompleted = { hasCaptchaJwt ->
                    // "solved" = a real captcha JWT was captured, safe to auto-retry
                    // "skipped" = no captcha was needed, don't auto-retry
                    val result = if (hasCaptchaJwt) "solved" else "skipped"
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set("captcha_result", result)
                    navController.popBackStack()
                },
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}

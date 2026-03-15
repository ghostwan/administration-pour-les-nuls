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
            val captchaCompleted = backStackEntry.savedStateHandle
                .get<Boolean>("captcha_completed") == true

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
                captchaJustCompleted = captchaCompleted
            )

            // Clear the flag after reading it
            LaunchedEffect(captchaCompleted) {
                if (captchaCompleted) {
                    backStackEntry.savedStateHandle.remove<Boolean>("captcha_completed")
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
                onCaptchaCompleted = {
                    // Pop back to home and set a flag for auto-retry
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set("captcha_completed", true)
                    navController.popBackStack()
                },
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}

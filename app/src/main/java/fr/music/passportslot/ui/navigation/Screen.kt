package fr.music.passportslot.ui.navigation

/**
 * Navigation routes for the app.
 */
sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Results : Screen("results")
    data object Settings : Screen("settings")
    data object Captcha : Screen("captcha")
}

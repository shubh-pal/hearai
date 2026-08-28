package com.hearai.app.ui.nav

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.hearai.app.ui.screens.apikey.ApiKeySetupScreen
import com.hearai.app.ui.screens.history.SessionDetailScreen
import com.hearai.app.ui.screens.history.SessionHistoryScreen
import com.hearai.app.ui.screens.home.HomeScreen
import com.hearai.app.ui.screens.permissions.PermissionsScreen
import com.hearai.app.ui.screens.settings.SettingsScreen
import com.hearai.app.ui.screens.summaries.SummariesScreen
import com.hearai.app.ui.screens.transcript.LiveTranscriptScreen
import com.hearai.app.ui.screens.welcome.WelcomeScreen

object Routes {
    const val WELCOME = "welcome"
    const val API_KEY_SETUP = "api_key_setup"
    const val PERMISSIONS = "permissions"
    const val HOME = "home"
    const val LIVE_TRANSCRIPT = "live_transcript"
    const val SUMMARIES = "summaries"
    const val HISTORY = "history"
    const val SESSION_DETAIL = "history/{sessionId}"
    const val SETTINGS = "settings"

    fun sessionDetail(sessionId: String) = "history/$sessionId"
}

@Composable
fun HearAiNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = Routes.WELCOME) {
        composable(Routes.WELCOME) {
            WelcomeScreen(onGetStarted = { navController.navigate(Routes.API_KEY_SETUP) })
        }
        composable(Routes.API_KEY_SETUP) {
            ApiKeySetupScreen(onContinue = { navController.navigate(Routes.PERMISSIONS) })
        }
        composable(Routes.PERMISSIONS) {
            PermissionsScreen(
                onDone = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.WELCOME) { inclusive = true }
                    }
                },
            )
        }
        composable(Routes.HOME) {
            HomeScreen(
                onOpenTranscript = { navController.navigate(Routes.LIVE_TRANSCRIPT) },
                onOpenSummaries = { navController.navigate(Routes.SUMMARIES) },
                onOpenHistory = { navController.navigate(Routes.HISTORY) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }
        composable(Routes.LIVE_TRANSCRIPT) {
            LiveTranscriptScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.SUMMARIES) {
            SummariesScreen(
                onBack = { navController.popBackStack() },
                onOpenSession = { sessionId -> navController.navigate(Routes.sessionDetail(sessionId)) },
            )
        }
        composable(Routes.HISTORY) {
            SessionHistoryScreen(
                onBack = { navController.popBackStack() },
                onOpenSession = { sessionId -> navController.navigate(Routes.sessionDetail(sessionId)) },
            )
        }
        composable(
            Routes.SESSION_DETAIL,
            arguments = listOf(navArgument("sessionId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val id = backStackEntry.arguments?.getString("sessionId").orEmpty()
            SessionDetailScreen(sessionId = id, onBack = { navController.popBackStack() })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}

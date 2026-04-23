package app.conectx.presentation.navigation

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.conectx.presentation.activation.ActivationScreen
import app.conectx.presentation.activation.OnboardingScreen
import app.conectx.presentation.chat.ChatScreen
import app.conectx.presentation.common.PermissionGate
import app.conectx.presentation.settings.SettingsScreen
import app.conectx.presentation.squad.SquadListScreen
import app.conectx.service.MeshService

@Composable
fun ConectxNavGraph() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Screen.Onboarding.route
    ) {
        composable(Screen.Onboarding.route) {
            OnboardingScreen(
                onOnboarded = {
                    navController.navigate(Screen.SquadList.route) {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Activation.route) {
            ActivationScreen(
                onActivated = {
                    navController.navigate(Screen.SquadList.route) {
                        popUpTo(Screen.Activation.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.SquadList.route) {
            val context = LocalContext.current
            PermissionGate(onAllGranted = {
                val intent = MeshService.startIntent(context)
                if (Build.VERSION.SDK_INT >= 26) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }) {
                SquadListScreen(
                    onSquadSelected = { squadId ->
                        navController.navigate(Screen.Chat.createRoute(squadId))
                    },
                    onSettingsClick = {
                        navController.navigate(Screen.Settings.route)
                    },
                    onUpgrade = {
                        navController.navigate(Screen.Activation.route)
                    }
                )
            }
        }

        composable(
            route = Screen.Chat.route,
            arguments = listOf(navArgument("squadId") { type = NavType.StringType })
        ) { backStackEntry ->
            val squadId = backStackEntry.arguments?.getString("squadId") ?: return@composable
            ChatScreen(
                squadId = squadId,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onBack = {
                    if (!navController.popBackStack(Screen.SquadList.route, inclusive = false)) {
                        navController.navigate(Screen.Onboarding.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                },
                onUpgrade = {
                    navController.navigate(Screen.Activation.route)
                }
            )
        }
    }
}

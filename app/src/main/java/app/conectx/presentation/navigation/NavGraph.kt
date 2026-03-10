package app.conectx.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.conectx.presentation.activation.ActivationScreen
import app.conectx.presentation.chat.ChatScreen
import app.conectx.presentation.common.PermissionGate
import app.conectx.presentation.location.LocationScreen
import app.conectx.presentation.settings.SettingsScreen
import app.conectx.presentation.squad.SquadListScreen

@Composable
fun ConectxNavGraph() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Screen.Activation.route
    ) {
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
            PermissionGate(onAllGranted = { /* permissions granted, mesh can start */ }) {
                SquadListScreen(
                    onSquadSelected = { squadId ->
                        navController.navigate(Screen.Chat.createRoute(squadId))
                    },
                    onSettingsClick = {
                        navController.navigate(Screen.Settings.route)
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
                onLocationClick = {
                    navController.navigate(Screen.Location.createRoute(squadId))
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.Location.route,
            arguments = listOf(navArgument("squadId") { type = NavType.StringType })
        ) { backStackEntry ->
            val squadId = backStackEntry.arguments?.getString("squadId") ?: return@composable
            LocationScreen(
                squadId = squadId,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onBack = {
                    // If signed out, go back to activation; otherwise just pop
                    if (!navController.popBackStack(Screen.SquadList.route, inclusive = false)) {
                        navController.navigate(Screen.Activation.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                }
            )
        }
    }
}

package app.conectx.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import android.os.Build
import androidx.compose.ui.platform.LocalContext
import app.conectx.presentation.activation.ActivationScreen
import app.conectx.presentation.activation.OnboardingScreen
import app.conectx.presentation.chat.ChatScreen
import app.conectx.presentation.common.PermissionGate
import app.conectx.presentation.conversation.ConversationListScreen
import app.conectx.presentation.conversation.DirectChatScreen
import app.conectx.presentation.location.LocationScreen
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
                    },
                    onConversationsClick = {
                        navController.navigate(Screen.ConversationList.route)
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

        composable(Screen.ConversationList.route) {
            ConversationListScreen(
                onConversationSelected = { peerId ->
                    navController.navigate(Screen.DirectChat.createRoute(peerId))
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.DirectChat.route,
            arguments = listOf(navArgument("peerId") { type = NavType.StringType })
        ) { backStackEntry ->
            val peerId = backStackEntry.arguments?.getString("peerId") ?: return@composable
            DirectChatScreen(
                peerId = peerId,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onBack = {
                    // If signed out, go back to onboarding; otherwise just pop
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

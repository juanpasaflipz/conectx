package app.conectx.presentation.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import android.os.Build
import app.conectx.presentation.activation.ActivationScreen
import app.conectx.presentation.activation.OnboardingScreen
import app.conectx.presentation.chat.ChatScreen
import app.conectx.presentation.common.PermissionGate
import app.conectx.presentation.conversation.ConversationListScreen
import app.conectx.presentation.conversation.DirectChatScreen
import app.conectx.presentation.location.LocationScreen
import app.conectx.presentation.more.MeshStatsScreen
import app.conectx.presentation.more.MoreScreen
import app.conectx.presentation.more.PrivacyDashboardScreen
import app.conectx.presentation.schedule.ScheduleScreen
import app.conectx.presentation.settings.SettingsScreen
import app.conectx.presentation.squad.MeetupScreen
import app.conectx.presentation.squad.SquadListScreen
import app.conectx.presentation.stadium.StadiumDetailScreen
import app.conectx.presentation.stadium.StadiumListScreen
import app.conectx.presentation.stadium.TransportScreen
import app.conectx.service.MeshService

// Routes where bottom nav should be hidden (detail screens)
private val hideBottomNavRoutes = setOf(
    Screen.Onboarding.route,
    Screen.Activation.route,
    Screen.Chat.route,
    Screen.Location.route,
    Screen.Meetup.route
)

@Composable
fun ConectxNavGraph() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val showBottomNav = currentRoute != null && currentRoute !in hideBottomNavRoutes

    Scaffold(
        bottomBar = {
            if (showBottomNav) {
                ConectxBottomNav(navController)
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Onboarding.route,
            modifier = Modifier.padding(padding)
        ) {
            // ── Auth flow (outside tabs) ──────────────────────────────

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

            // ── Tab 1: Squads ─────────────────────────────────────────

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
                    onLocationClick = {
                        navController.navigate(Screen.Location.createRoute(squadId))
                    },
                    onMeetupClick = {
                        navController.navigate(Screen.Meetup.createRoute(squadId))
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

            composable(
                route = Screen.Meetup.route,
                arguments = listOf(navArgument("squadId") { type = NavType.StringType })
            ) { backStackEntry ->
                val squadId = backStackEntry.arguments?.getString("squadId") ?: return@composable
                MeetupScreen(
                    squadId = squadId,
                    onBack = { navController.popBackStack() }
                )
            }

            // ── Tab 2: Calendario ─────────────────────────────────────

            composable(Screen.Schedule.route) {
                ScheduleScreen()
            }

            // ── Tab 3: Estadio ────────────────────────────────────────

            composable(Screen.StadiumList.route) {
                StadiumListScreen(
                    onStadiumSelected = { stadiumId ->
                        navController.navigate(Screen.StadiumDetail.createRoute(stadiumId))
                    }
                )
            }

            composable(
                route = Screen.StadiumDetail.route,
                arguments = listOf(navArgument("stadiumId") { type = NavType.StringType })
            ) { backStackEntry ->
                val stadiumId = backStackEntry.arguments?.getString("stadiumId") ?: return@composable
                StadiumDetailScreen(
                    stadiumId = stadiumId,
                    onTransportClick = {
                        navController.navigate(Screen.Transport.createRoute(stadiumId))
                    },
                    onBack = { navController.popBackStack() }
                )
            }

            composable(
                route = Screen.Transport.route,
                arguments = listOf(navArgument("stadiumId") { type = NavType.StringType })
            ) { backStackEntry ->
                val stadiumId = backStackEntry.arguments?.getString("stadiumId") ?: return@composable
                TransportScreen(
                    stadiumId = stadiumId,
                    onBack = { navController.popBackStack() }
                )
            }

            // ── Tab 4: Mas ────────────────────────────────────────────

            composable(Screen.More.route) {
                MoreScreen(
                    onSettingsClick = { navController.navigate(Screen.Settings.route) },
                    onPrivacyClick = { navController.navigate(Screen.Privacy.route) },
                    onMeshStatsClick = { navController.navigate(Screen.MeshStats.route) },
                    onUpgrade = { navController.navigate(Screen.Activation.route) }
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
                    },
                    onConversationsClick = {
                        navController.navigate(Screen.ConversationList.route)
                    }
                )
            }

            composable(Screen.Privacy.route) {
                PrivacyDashboardScreen(
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

            composable(Screen.MeshStats.route) {
                MeshStatsScreen(
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}

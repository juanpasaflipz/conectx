package app.conectx.presentation.navigation

sealed class Screen(val route: String) {
    // Auth flow (outside bottom nav)
    data object Onboarding : Screen("onboarding")
    data object Activation : Screen("activation")

    // Tab 1: Squads
    data object SquadList : Screen("squad_list")
    data object Chat : Screen("chat/{squadId}") {
        fun createRoute(squadId: String) = "chat/$squadId"
    }
    data object Location : Screen("location/{squadId}") {
        fun createRoute(squadId: String) = "location/$squadId"
    }
    data object Meetup : Screen("meetup/{squadId}") {
        fun createRoute(squadId: String) = "meetup/$squadId"
    }

    // Tab 2: Calendario
    data object Schedule : Screen("schedule")
    data object MatchDetail : Screen("match_detail/{matchId}") {
        fun createRoute(matchId: String) = "match_detail/$matchId"
    }

    // Tab 3: Estadio
    data object StadiumList : Screen("stadium_list")
    data object StadiumDetail : Screen("stadium_detail/{stadiumId}") {
        fun createRoute(stadiumId: String) = "stadium_detail/$stadiumId"
    }
    data object Transport : Screen("transport/{stadiumId}") {
        fun createRoute(stadiumId: String) = "transport/$stadiumId"
    }

    // Tab 4: Mas
    data object More : Screen("more")
    data object Settings : Screen("settings")
    data object ConversationList : Screen("conversations")
    data object DirectChat : Screen("direct_chat/{peerId}") {
        fun createRoute(peerId: String) = "direct_chat/$peerId"
    }
    data object Privacy : Screen("privacy")
    data object MeshStats : Screen("mesh_stats")
}

package app.conectx.presentation.navigation

sealed class Screen(val route: String) {
    data object Onboarding : Screen("onboarding")
    data object Activation : Screen("activation")
    data object SquadList : Screen("squad_list")
    data object Chat : Screen("chat/{squadId}") {
        fun createRoute(squadId: String) = "chat/$squadId"
    }
    data object Location : Screen("location/{squadId}") {
        fun createRoute(squadId: String) = "location/$squadId"
    }
    data object Settings : Screen("settings")
    data object ConversationList : Screen("conversations")
    data object DirectChat : Screen("direct_chat/{peerId}") {
        fun createRoute(peerId: String) = "direct_chat/$peerId"
    }
}

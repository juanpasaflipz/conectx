package app.conectx.domain.model

/** Defines squad/member limits per tier. */
sealed interface UserTier {
    val maxSquads: Int
    val maxMembersPerSquad: Int

    data object Free : UserTier {
        override val maxSquads = 1
        override val maxMembersPerSquad = 8
    }

    data object Paid : UserTier {
        override val maxSquads = Int.MAX_VALUE
        override val maxMembersPerSquad = 25
    }
}

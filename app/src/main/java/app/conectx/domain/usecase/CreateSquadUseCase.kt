package app.conectx.domain.usecase

import app.conectx.data.repository.ActivationRepository
import app.conectx.domain.model.Squad
import app.conectx.domain.model.UserTier
import app.conectx.domain.repository.SquadRepository
import kotlinx.coroutines.flow.first
import java.util.UUID
import javax.inject.Inject

sealed interface CreateSquadResult {
    data class Success(val squad: Squad) : CreateSquadResult
    data object SquadLimitReached : CreateSquadResult
}

class CreateSquadUseCase @Inject constructor(
    private val squadRepository: SquadRepository,
    private val activationRepository: ActivationRepository
) {
    suspend operator fun invoke(name: String, creatorId: String): CreateSquadResult {
        val tier = activationRepository.userTier.first()
        val currentCount = squadRepository.getAllSquads().first().size

        if (currentCount >= tier.maxSquads) {
            return CreateSquadResult.SquadLimitReached
        }

        val squad = Squad(
            id = UUID.randomUUID().toString(),
            name = name,
            inviteCode = generateInviteCode(),
            memberIds = listOf(creatorId),
            createdAt = System.currentTimeMillis()
        )
        squadRepository.insertSquad(squad)
        return CreateSquadResult.Success(squad)
    }

    /**
     * Generates a 6-character invite code like "AZT-7K3".
     * Uses an unambiguous character set (no I/O/0/1).
     */
    private fun generateInviteCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val part1 = (1..3).map { chars.random() }.joinToString("")
        val part2 = (1..3).map { chars.random() }.joinToString("")
        return "$part1-$part2"
    }
}

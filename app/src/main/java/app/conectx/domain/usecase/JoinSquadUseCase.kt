package app.conectx.domain.usecase

import app.conectx.data.repository.ActivationRepository
import app.conectx.domain.model.Squad
import app.conectx.domain.model.UserTier
import app.conectx.domain.repository.SquadRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

sealed interface JoinSquadResult {
    data class Success(val squad: Squad) : JoinSquadResult
    data object NotFound : JoinSquadResult
    data object MemberLimitReached : JoinSquadResult
}

class JoinSquadUseCase @Inject constructor(
    private val squadRepository: SquadRepository,
    private val activationRepository: ActivationRepository
) {
    /**
     * Joins a squad by invite code. Checks member count against the
     * current user's tier limit before adding.
     */
    suspend operator fun invoke(inviteCode: String, memberId: String): JoinSquadResult {
        val code = inviteCode.trim().uppercase()
        val squad = squadRepository.getSquadByInviteCode(code) ?: return JoinSquadResult.NotFound

        val tier = activationRepository.userTier.first()
        if (squad.memberIds.size >= tier.maxMembersPerSquad) {
            return JoinSquadResult.MemberLimitReached
        }

        squadRepository.addMember(squad.id, memberId)
        val updated = squadRepository.getSquadById(squad.id)!!
        return JoinSquadResult.Success(updated)
    }
}

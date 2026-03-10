package app.conectx.domain.usecase

import app.conectx.domain.model.Squad
import app.conectx.domain.repository.SquadRepository
import javax.inject.Inject

class JoinSquadUseCase @Inject constructor(
    private val squadRepository: SquadRepository
) {
    /**
     * Joins a squad by invite code. Returns the squad if found and joined,
     * null if the code doesn't match any known squad.
     *
     * The squad must already exist in Room — either created locally or
     * received via a SQUAD_META sync record from a nearby peer.
     */
    suspend operator fun invoke(inviteCode: String, memberId: String): Squad? {
        val code = inviteCode.trim().uppercase()
        val squad = squadRepository.getSquadByInviteCode(code) ?: return null
        squadRepository.addMember(squad.id, memberId)
        return squadRepository.getSquadById(squad.id)
    }
}

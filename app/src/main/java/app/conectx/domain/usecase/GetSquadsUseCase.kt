package app.conectx.domain.usecase

import app.conectx.domain.model.Squad
import app.conectx.domain.repository.SquadRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetSquadsUseCase @Inject constructor(
    private val squadRepository: SquadRepository
) {
    operator fun invoke(): Flow<List<Squad>> = squadRepository.getAllSquads()
}

package app.conectx.domain.repository

import app.conectx.domain.model.Squad
import kotlinx.coroutines.flow.Flow

interface SquadRepository {
    fun getAllSquads(): Flow<List<Squad>>
    suspend fun getSquadById(id: String): Squad?
    suspend fun getSquadByInviteCode(code: String): Squad?
    suspend fun insertSquad(squad: Squad)
    suspend fun addMember(squadId: String, memberId: String): Boolean
    suspend fun removeMember(squadId: String, memberId: String): Boolean
    suspend fun deleteSquad(id: String)
}

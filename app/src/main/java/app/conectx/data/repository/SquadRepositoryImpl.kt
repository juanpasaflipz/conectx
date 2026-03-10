package app.conectx.data.repository

import app.conectx.data.local.db.dao.SquadDao
import app.conectx.data.local.db.entity.SquadEntity
import app.conectx.domain.model.Squad
import app.conectx.domain.repository.SquadRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SquadRepositoryImpl @Inject constructor(
    private val squadDao: SquadDao
) : SquadRepository {

    override fun getAllSquads(): Flow<List<Squad>> {
        return squadDao.getAll().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getSquadById(id: String): Squad? {
        return squadDao.getById(id)?.toDomain()
    }

    override suspend fun getSquadByInviteCode(code: String): Squad? {
        return squadDao.getByInviteCode(code)?.toDomain()
    }

    override suspend fun insertSquad(squad: Squad) {
        squadDao.insert(squad.toEntity())
    }

    override suspend fun addMember(squadId: String, memberId: String): Boolean {
        val entity = squadDao.getById(squadId) ?: return false
        val current = entity.memberIds.split(",").filter { it.isNotBlank() }
        if (memberId in current) return true // already a member
        val updated = (current + memberId).joinToString(",")
        squadDao.insert(entity.copy(memberIds = updated))
        return true
    }

    override suspend fun removeMember(squadId: String, memberId: String): Boolean {
        val entity = squadDao.getById(squadId) ?: return false
        val current = entity.memberIds.split(",").filter { it.isNotBlank() }
        if (memberId !in current) return true // already gone
        val updated = (current - memberId).joinToString(",")
        squadDao.insert(entity.copy(memberIds = updated))
        return true
    }

    override suspend fun deleteSquad(id: String) {
        squadDao.deleteById(id)
    }

    private fun SquadEntity.toDomain() = Squad(
        id = id,
        name = name,
        inviteCode = inviteCode,
        memberIds = memberIds.split(",").filter { it.isNotBlank() },
        createdAt = createdAt
    )

    private fun Squad.toEntity() = SquadEntity(
        id = id,
        name = name,
        inviteCode = inviteCode,
        memberIds = memberIds.joinToString(","),
        createdAt = createdAt
    )
}

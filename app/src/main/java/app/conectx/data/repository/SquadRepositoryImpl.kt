package app.conectx.data.repository

import android.util.Log
import app.conectx.data.local.db.dao.SquadDao
import app.conectx.data.local.db.entity.SquadEntity
import app.conectx.domain.model.Squad
import app.conectx.domain.repository.SquadRepository
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Squad repository with Firebase fallback for invite-code lookups.
 *
 * When a squad is created it's written to both Room (local) and Firebase
 * RTDB (/invites/{code}). When joining by code, if Room doesn't have the
 * squad (e.g. Device 2 hasn't received the mesh broadcast yet), we check
 * Firebase. This bridges the gap between pre-match online setup and
 * in-stadium offline mesh sync.
 */
@Singleton
class SquadRepositoryImpl @Inject constructor(
    private val squadDao: SquadDao,
    private val database: FirebaseDatabase
) : SquadRepository {

    companion object {
        private const val TAG = "SquadRepo"
        private const val INVITES_REF = "invites"
    }

    override fun getAllSquads(): Flow<List<Squad>> {
        return squadDao.getAll().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getSquadById(id: String): Squad? {
        return squadDao.getById(id)?.toDomain()
    }

    /**
     * Looks up a squad by invite code. Checks local Room first, then
     * falls back to Firebase RTDB so join works even if the mesh hasn't
     * synced the squad yet.
     */
    override suspend fun getSquadByInviteCode(code: String): Squad? {
        // Local first
        squadDao.getByInviteCode(code)?.let { return it.toDomain() }

        // Firebase fallback
        return try {
            val snapshot = database.reference
                .child(INVITES_REF)
                .child(code)
                .get()
                .await()

            if (!snapshot.exists()) return null

            val squad = Squad(
                id = snapshot.child("squadId").getValue(String::class.java) ?: return null,
                name = snapshot.child("name").getValue(String::class.java) ?: "Squad",
                inviteCode = code,
                memberIds = emptyList(),
                createdAt = snapshot.child("createdAt").getValue(Long::class.java) ?: System.currentTimeMillis()
            )

            // Cache in Room so future lookups are instant
            squadDao.insert(squad.toEntity())
            Log.d(TAG, "Fetched squad '${squad.name}' ($code) from Firebase")
            squad
        } catch (e: Exception) {
            Log.w(TAG, "Firebase invite lookup failed for $code", e)
            null
        }
    }

    /**
     * Inserts a squad locally and publishes the invite code to Firebase
     * so other devices can find it before the mesh connects.
     */
    override suspend fun insertSquad(squad: Squad) {
        squadDao.insert(squad.toEntity())
        publishInviteToFirebase(squad)
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

    /**
     * Writes squad invite info to Firebase RTDB at /invites/{code}.
     * Fire-and-forget — if it fails (no internet), the squad still
     * exists locally and will propagate via mesh.
     */
    private fun publishInviteToFirebase(squad: Squad) {
        try {
            val data = mapOf(
                "squadId" to squad.id,
                "name" to squad.name,
                "createdAt" to squad.createdAt
            )
            database.reference
                .child(INVITES_REF)
                .child(squad.inviteCode)
                .setValue(data)
                .addOnSuccessListener {
                    Log.d(TAG, "Published invite ${squad.inviteCode} to Firebase")
                }
                .addOnFailureListener { e ->
                    Log.w(TAG, "Failed to publish invite to Firebase", e)
                }
        } catch (e: Exception) {
            Log.w(TAG, "publishInviteToFirebase failed", e)
        }
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

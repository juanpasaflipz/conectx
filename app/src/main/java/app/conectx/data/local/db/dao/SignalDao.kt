package app.conectx.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.conectx.data.local.db.entity.SignalIdentityEntity
import app.conectx.data.local.db.entity.SignalPreKeyEntity
import app.conectx.data.local.db.entity.SignalSessionEntity
import app.conectx.data.local.db.entity.SignalSignedPreKeyEntity

@Dao
interface SignalDao {

    // ── Identity keys ────────────────────────────────────────────────

    @Query("SELECT * FROM signal_identities WHERE address = :address")
    suspend fun getIdentity(address: String): SignalIdentityEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveIdentity(entity: SignalIdentityEntity)

    @Query("SELECT trusted FROM signal_identities WHERE address = :address")
    suspend fun isTrustedIdentity(address: String): Boolean?

    // ── Pre-keys ─────────────────────────────────────────────────────

    @Query("SELECT * FROM signal_pre_keys WHERE preKeyId = :preKeyId")
    suspend fun getPreKey(preKeyId: Int): SignalPreKeyEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun storePreKey(entity: SignalPreKeyEntity)

    @Query("DELETE FROM signal_pre_keys WHERE preKeyId = :preKeyId")
    suspend fun removePreKey(preKeyId: Int)

    @Query("SELECT EXISTS(SELECT 1 FROM signal_pre_keys WHERE preKeyId = :preKeyId)")
    suspend fun containsPreKey(preKeyId: Int): Boolean

    // ── Signed pre-keys ──────────────────────────────────────────────

    @Query("SELECT * FROM signal_signed_pre_keys WHERE signedPreKeyId = :id")
    suspend fun getSignedPreKey(id: Int): SignalSignedPreKeyEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun storeSignedPreKey(entity: SignalSignedPreKeyEntity)

    @Query("SELECT * FROM signal_signed_pre_keys")
    suspend fun getAllSignedPreKeys(): List<SignalSignedPreKeyEntity>

    @Query("DELETE FROM signal_signed_pre_keys WHERE signedPreKeyId = :id")
    suspend fun removeSignedPreKey(id: Int)

    @Query("SELECT EXISTS(SELECT 1 FROM signal_signed_pre_keys WHERE signedPreKeyId = :id)")
    suspend fun containsSignedPreKey(id: Int): Boolean

    // ── Sessions ─────────────────────────────────────────────────────

    @Query("SELECT * FROM signal_sessions WHERE address = :address AND deviceId = :deviceId")
    suspend fun getSession(address: String, deviceId: Int): SignalSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun storeSession(entity: SignalSessionEntity)

    @Query("SELECT EXISTS(SELECT 1 FROM signal_sessions WHERE address = :address AND deviceId = :deviceId)")
    suspend fun containsSession(address: String, deviceId: Int): Boolean

    @Query("SELECT deviceId FROM signal_sessions WHERE address = :address")
    suspend fun getSubDeviceSessions(address: String): List<Int>

    @Query("DELETE FROM signal_sessions WHERE address = :address AND deviceId = :deviceId")
    suspend fun deleteSession(address: String, deviceId: Int)

    @Query("DELETE FROM signal_sessions WHERE address = :address")
    suspend fun deleteAllSessions(address: String)
}

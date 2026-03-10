package app.conectx.service

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.conectx.data.local.db.dao.SyncRecordDao
import app.conectx.domain.model.RecordType
import app.conectx.domain.model.SyncRecord
import app.conectx.transport.firebase.FirebasePlugin
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * WorkManager worker for periodic background sync via Firebase.
 *
 * Runs every 15 minutes (minimum WorkManager interval) when the device
 * has internet connectivity. Its job:
 *
 * 1. Push any locally-stored records that haven't been sent to Firebase
 *    (e.g., messages sent while offline that were only delivered via mesh).
 *
 * This ensures that even if the MeshService is killed by the system,
 * records still make it to Firebase for squad members who are online.
 */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val syncRecordDao: SyncRecordDao,
    private val firebasePlugin: FirebasePlugin
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "SyncWorker"
        const val WORK_NAME = "conectx_background_sync"
    }

    override suspend fun doWork(): Result {
        if (!firebasePlugin.isAvailable) {
            Log.d(TAG, "No internet — skipping sync")
            return Result.success()
        }

        return try {
            syncPendingRecords()
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Sync failed", e)
            Result.retry()
        }
    }

    /**
     * Pushes recent records to Firebase RTDB.
     * Firebase's setValue is idempotent (same ID = overwrite), so
     * re-pushing an already-synced record is harmless.
     */
    private suspend fun syncPendingRecords() {
        val squadIds = syncRecordDao.getAllSquadIds()
        var totalSynced = 0

        for (squadId in squadIds) {
            // Get the last 50 records per squad — a reasonable batch size
            val records = syncRecordDao.getRecordsForSquad(squadId)
                .takeLast(50)
                .filter { it.type != RecordType.SYNC_OFFER.name }

            for (entity in records) {
                val record = SyncRecord(
                    id = entity.id,
                    squadId = entity.squadId,
                    authorId = entity.authorId,
                    lamportClock = entity.lamportClock,
                    timestamp = entity.timestamp,
                    type = RecordType.valueOf(entity.type),
                    payload = entity.payload,
                    signature = entity.signature
                )
                firebasePlugin.broadcastToFirebase(record)
                totalSynced++
            }
        }

        Log.d(TAG, "Synced $totalSynced records across ${squadIds.size} squad(s)")
    }
}

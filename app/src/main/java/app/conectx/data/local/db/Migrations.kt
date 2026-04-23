package app.conectx.data.local.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v1 → v2: add firebaseSyncedAt to sync_records for Firebase RTDB idempotency
 * (allows SyncWorker to retry un-synced records without duplicating pushes).
 *
 * v1 shipped on Play Store 0.3.0 (`b18c019`) with only the original 4 tables
 * — message / squad / peer / sync_records. This migration is additive-only.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE sync_records ADD COLUMN firebaseSyncedAt INTEGER DEFAULT NULL"
        )
    }
}

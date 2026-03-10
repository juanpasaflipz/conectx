package app.conectx.presentation.location

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.conectx.data.local.db.dao.SyncRecordDao
import app.conectx.domain.model.LocationPing
import app.conectx.sync.PayloadCodec
import app.conectx.sync.SyncEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Represents a squad member's latest known location.
 */
data class MemberLocation(
    val authorId: String,
    val authorName: String,
    val ping: LocationPing,
    val timestamp: Long
)

@HiltViewModel
class LocationViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val syncRecordDao: SyncRecordDao,
    private val syncEngine: SyncEngine,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val squadId: String = checkNotNull(savedStateHandle["squadId"])

    // ── Form state ──────────────────────────────────────────────────
    private val _section = MutableStateFlow("")
    val section: StateFlow<String> = _section.asStateFlow()

    private val _row = MutableStateFlow("")
    val row: StateFlow<String> = _row.asStateFlow()

    private val _seat = MutableStateFlow("")
    val seat: StateFlow<String> = _seat.asStateFlow()

    private val _note = MutableStateFlow("")
    val note: StateFlow<String> = _note.asStateFlow()

    private val _shared = MutableStateFlow(false)
    val shared: StateFlow<Boolean> = _shared.asStateFlow()

    // ── Squad member locations ──────────────────────────────────────
    // Decode all LOCATION sync records, keep only the latest per author.
    val memberLocations: StateFlow<List<MemberLocation>> = syncRecordDao
        .getLocationPingsForSquad(squadId)
        .map { records ->
            records
                .mapNotNull { entity ->
                    try {
                        val payload = PayloadCodec.decodeLocation(entity.payload)
                        MemberLocation(
                            authorId = entity.authorId,
                            authorName = payload.authorName,
                            ping = payload.ping,
                            timestamp = entity.timestamp
                        )
                    } catch (_: Exception) {
                        null // skip malformed records
                    }
                }
                // Group by author and keep only the most recent ping
                .groupBy { it.authorId }
                .map { (_, pings) -> pings.first() } // already sorted DESC by lamportClock
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ── Actions ─────────────────────────────────────────────────────

    fun updateSection(value: String) { _section.value = value }
    fun updateRow(value: String) { _row.value = value }
    fun updateSeat(value: String) { _seat.value = value }
    fun updateNote(value: String) { _note.value = value }

    fun shareLocation() {
        val sectionVal = _section.value.trim()
        if (sectionVal.isBlank()) return

        viewModelScope.launch {
            val ping = LocationPing(
                section = sectionVal,
                row = _row.value.trim().ifEmpty { null },
                seat = _seat.value.trim().ifEmpty { null },
                note = _note.value.trim().ifEmpty { null },
                battery = getBatteryLevel()
            )
            syncEngine.sendLocationPing(squadId, ping)
            _shared.value = true
        }
    }

    fun dismissSharedConfirmation() {
        _shared.value = false
    }

    private fun getBatteryLevel(): Int {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus = appContext.registerReceiver(null, filter)
        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        return if (level >= 0 && scale > 0) (level * 100) / scale else -1
    }
}

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

/** UI model for a squad member's last-known location. */
data class MemberLocation(
    val authorId: String,
    val authorName: String,
    val ping: LocationPing,
    val timestamp: Long
)

@HiltViewModel
class LocationViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val syncEngine: SyncEngine,
    private val syncRecordDao: SyncRecordDao,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val squadId: String = checkNotNull(savedStateHandle["squadId"])

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

    /**
     * Reactive stream of squad members' latest location pings.
     * De-duplicates by authorId so only the most recent ping per member shows.
     */
    val memberLocations: StateFlow<List<MemberLocation>> = syncRecordDao
        .getLocationPingsForSquad(squadId)
        .map { records ->
            records
                .filter { it.authorId != syncEngine.localUserId }
                .groupBy { it.authorId }
                .mapNotNull { (authorId, recs) ->
                    val latest = recs.maxByOrNull { it.lamportClock } ?: return@mapNotNull null
                    val payload = PayloadCodec.decodeLocation(latest.payload)
                    MemberLocation(
                        authorId = authorId,
                        authorName = payload.authorName,
                        ping = payload.ping,
                        timestamp = latest.timestamp
                    )
                }
                .sortedByDescending { it.timestamp }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun updateSection(value: String) { _section.value = value }
    fun updateRow(value: String) { _row.value = value }
    fun updateSeat(value: String) { _seat.value = value }
    fun updateNote(value: String) { _note.value = value }

    fun shareLocation() {
        viewModelScope.launch {
            val ping = LocationPing(
                section = _section.value.trim(),
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
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        return if (level >= 0 && scale > 0) (level * 100) / scale else -1
    }
}

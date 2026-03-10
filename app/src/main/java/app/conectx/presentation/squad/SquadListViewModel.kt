package app.conectx.presentation.squad

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.conectx.domain.model.Squad
import app.conectx.domain.usecase.CreateSquadUseCase
import app.conectx.domain.usecase.GetSquadsUseCase
import app.conectx.domain.usecase.JoinSquadUseCase
import app.conectx.sync.PayloadCodec
import app.conectx.sync.SyncEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SquadListUiState(
    val showCreateDialog: Boolean = false,
    val showJoinDialog: Boolean = false,
    val createdSquad: Squad? = null,
    val joinError: Boolean = false
)

@HiltViewModel
class SquadListViewModel @Inject constructor(
    getSquadsUseCase: GetSquadsUseCase,
    private val createSquadUseCase: CreateSquadUseCase,
    private val joinSquadUseCase: JoinSquadUseCase,
    private val syncEngine: SyncEngine
) : ViewModel() {

    val squads: StateFlow<List<Squad>> = getSquadsUseCase()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _uiState = MutableStateFlow(SquadListUiState())
    val uiState: StateFlow<SquadListUiState> = _uiState.asStateFlow()

    fun showCreateDialog() {
        _uiState.value = _uiState.value.copy(
            showCreateDialog = true,
            createdSquad = null
        )
    }

    fun showJoinDialog() {
        _uiState.value = _uiState.value.copy(
            showJoinDialog = true,
            joinError = false
        )
    }

    fun dismissDialogs() {
        _uiState.value = SquadListUiState()
    }

    fun createSquad(name: String) {
        viewModelScope.launch {
            val squad = createSquadUseCase(name, syncEngine.localUserId)
            syncEngine.broadcastSquadMeta(PayloadCodec.SquadAction.CREATE, squad)
            _uiState.value = _uiState.value.copy(createdSquad = squad)
        }
    }

    fun joinSquad(code: String) {
        viewModelScope.launch {
            val squad = joinSquadUseCase(code, syncEngine.localUserId)
            if (squad != null) {
                syncEngine.broadcastSquadMeta(PayloadCodec.SquadAction.JOIN, squad)
                _uiState.value = SquadListUiState() // dismiss
            } else {
                _uiState.value = _uiState.value.copy(joinError = true)
            }
        }
    }
}

package app.conectx.presentation.squad

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.conectx.data.repository.ActivationRepository
import app.conectx.domain.model.Squad
import app.conectx.domain.model.UserTier
import app.conectx.domain.usecase.CreateSquadResult
import app.conectx.domain.usecase.CreateSquadUseCase
import app.conectx.domain.usecase.GetSquadsUseCase
import app.conectx.domain.usecase.JoinSquadResult
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
    val joinError: Boolean = false,
    val showSquadLimitDialog: Boolean = false,
    val showMemberLimitDialog: Boolean = false
)

@HiltViewModel
class SquadListViewModel @Inject constructor(
    getSquadsUseCase: GetSquadsUseCase,
    private val createSquadUseCase: CreateSquadUseCase,
    private val joinSquadUseCase: JoinSquadUseCase,
    private val syncEngine: SyncEngine,
    activationRepository: ActivationRepository
) : ViewModel() {

    val squads: StateFlow<List<Squad>> = getSquadsUseCase()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val userTier: StateFlow<UserTier> = activationRepository.userTier
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserTier.Free)

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
            when (val result = createSquadUseCase(name, syncEngine.localUserId)) {
                is CreateSquadResult.Success -> {
                    syncEngine.broadcastSquadMeta(PayloadCodec.SquadAction.CREATE, result.squad)
                    _uiState.value = _uiState.value.copy(createdSquad = result.squad)
                }
                is CreateSquadResult.SquadLimitReached -> {
                    _uiState.value = _uiState.value.copy(
                        showCreateDialog = false,
                        showSquadLimitDialog = true
                    )
                }
            }
        }
    }

    fun joinSquad(code: String) {
        viewModelScope.launch {
            when (val result = joinSquadUseCase(code, syncEngine.localUserId)) {
                is JoinSquadResult.Success -> {
                    syncEngine.broadcastSquadMeta(PayloadCodec.SquadAction.JOIN, result.squad)
                    _uiState.value = SquadListUiState() // dismiss
                }
                is JoinSquadResult.NotFound -> {
                    _uiState.value = _uiState.value.copy(joinError = true)
                }
                is JoinSquadResult.MemberLimitReached -> {
                    _uiState.value = _uiState.value.copy(
                        showJoinDialog = false,
                        showMemberLimitDialog = true
                    )
                }
            }
        }
    }
}

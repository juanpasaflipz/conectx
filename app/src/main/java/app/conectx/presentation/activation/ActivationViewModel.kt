package app.conectx.presentation.activation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.annotation.StringRes
import app.conectx.R
import app.conectx.data.repository.ActivationRepository
import app.conectx.sync.SyncEngine
import app.conectx.transport.nearby.NearbyPlugin
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ActivationUiState(
    val code: String = "",
    val username: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    @StringRes val errorRes: Int? = null
)

@HiltViewModel
class ActivationViewModel @Inject constructor(
    private val activationRepository: ActivationRepository,
    private val syncEngine: SyncEngine,
    private val nearbyPlugin: NearbyPlugin
) : ViewModel() {

    private val _uiState = MutableStateFlow(ActivationUiState())
    val uiState: StateFlow<ActivationUiState> = _uiState.asStateFlow()

    /** Whether the user has already activated (skip to squads). */
    val isAlreadyActivated: StateFlow<Boolean?> = activationRepository.isActivated
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun updateCode(value: String) {
        _uiState.value = _uiState.value.copy(
            code = value.uppercase().take(12),
            error = null,
            errorRes = null
        )
    }

    fun updateUsername(value: String) {
        _uiState.value = _uiState.value.copy(
            username = value.take(24),
            error = null,
            errorRes = null
        )
    }

    fun activate(onSuccess: () -> Unit) {
        val state = _uiState.value
        if (state.code.isBlank()) {
            _uiState.value = state.copy(errorRes = R.string.activation_error_code)
            return
        }
        if (state.username.isBlank()) {
            _uiState.value = state.copy(errorRes = R.string.activation_error_username)
            return
        }
        if (state.isLoading) return

        _uiState.value = state.copy(isLoading = true, error = null, errorRes = null)

        viewModelScope.launch {
            val error = activationRepository.activate(state.code, state.username)
            if (error != null) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = error)
            } else {
                // Wire user identity into the sync engine and nearby plugin
                syncEngine.localUserName = state.username.trim()
                nearbyPlugin.configure(state.username.trim())
                _uiState.value = _uiState.value.copy(isLoading = false)
                onSuccess()
            }
        }
    }
}

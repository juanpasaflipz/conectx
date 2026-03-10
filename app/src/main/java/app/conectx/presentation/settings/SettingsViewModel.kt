package app.conectx.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.conectx.data.repository.ActivationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val activationRepository: ActivationRepository
) : ViewModel() {

    val username: StateFlow<String?> = activationRepository.username
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val passType: StateFlow<String?> = activationRepository.passType
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val passExpiry: StateFlow<Long?> = activationRepository.passExpiry
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun signOut(onSignedOut: () -> Unit) {
        viewModelScope.launch {
            activationRepository.clearActivation()
            onSignedOut()
        }
    }
}

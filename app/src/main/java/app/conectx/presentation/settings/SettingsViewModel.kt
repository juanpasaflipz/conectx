package app.conectx.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.conectx.data.repository.ActivationRepository
import app.conectx.domain.model.UserTier
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
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

    val isFreeUser: StateFlow<Boolean> = activationRepository.userTier
        .map { it is UserTier.Free }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    fun signOut(onSignedOut: () -> Unit) {
        viewModelScope.launch {
            activationRepository.clearActivation()
            onSignedOut()
        }
    }
}

package app.conectx.presentation.activation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.conectx.data.repository.ActivationRepository
import app.conectx.sync.SyncEngine
import app.conectx.transport.nearby.NearbyPlugin
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val activationRepository: ActivationRepository,
    private val syncEngine: SyncEngine,
    private val nearbyPlugin: NearbyPlugin
) : ViewModel() {

    /** null = still loading, true = already onboarded, false = needs onboarding */
    val hasUsername: StateFlow<Boolean?> = activationRepository.username
        .map { it != null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun onboardFree(username: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            activationRepository.onboardFree(username)
            syncEngine.localUserName = username
            nearbyPlugin.configure(username)
            onSuccess()
        }
    }
}

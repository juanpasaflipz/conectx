package app.conectx.presentation.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.conectx.data.local.preferences.UserPreferences
import app.conectx.data.local.preferences.dataStore
import app.conectx.data.repository.ActivationRepository
import app.conectx.domain.model.UserTier
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val activationRepository: ActivationRepository,
    @ApplicationContext private val context: Context
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

    val themeMode: StateFlow<String> = context.dataStore.data
        .map { prefs -> prefs[UserPreferences.THEME_MODE] ?: "system" }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "system")

    fun setThemeMode(mode: String) {
        viewModelScope.launch {
            context.dataStore.edit { prefs ->
                prefs[UserPreferences.THEME_MODE] = mode
            }
        }
    }

    fun signOut(onSignedOut: () -> Unit) {
        viewModelScope.launch {
            activationRepository.clearActivation()
            onSignedOut()
        }
    }
}

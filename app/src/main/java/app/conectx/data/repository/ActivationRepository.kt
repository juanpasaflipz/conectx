package app.conectx.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import app.conectx.data.local.preferences.UserPreferences
import app.conectx.data.remote.supabase.ActivationApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages activation state: verifies codes via Supabase, persists
 * activation status + user identity in DataStore.
 */
@Singleton
class ActivationRepository @Inject constructor(
    private val activationApi: ActivationApi,
    private val dataStore: DataStore<Preferences>
) {
    /** Reactive stream of whether the user has activated. */
    val isActivated: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[UserPreferences.IS_ACTIVATED] == true
    }

    val userId: Flow<String?> = dataStore.data.map { prefs ->
        prefs[UserPreferences.USER_ID]
    }

    val username: Flow<String?> = dataStore.data.map { prefs ->
        prefs[UserPreferences.USERNAME]
    }

    val passType: Flow<String?> = dataStore.data.map { prefs ->
        prefs[UserPreferences.PASS_TYPE]
    }

    val passExpiry: Flow<Long?> = dataStore.data.map { prefs ->
        prefs[UserPreferences.PASS_EXPIRY]
    }

    /**
     * Verifies an activation code and saves the result locally.
     * Returns a user-friendly error message on failure, null on success.
     */
    suspend fun activate(code: String, username: String): String? {
        val result = activationApi.verify(code)

        if (result.isFailure) {
            return "Sin conexion a internet. Intentalo de nuevo."
        }

        val activation = result.getOrThrow()

        if (!activation.valid) {
            return activation.message.ifEmpty { "Codigo invalido" }
        }

        // Persist activation state
        dataStore.edit { prefs ->
            prefs[UserPreferences.IS_ACTIVATED] = true
            prefs[UserPreferences.ACTIVATION_CODE] = code.trim().uppercase()
            prefs[UserPreferences.USER_ID] = activation.userId
            prefs[UserPreferences.USERNAME] = username.trim()
            prefs[UserPreferences.PASS_TYPE] = activation.passType
            prefs[UserPreferences.PASS_EXPIRY] = activation.expiresAt
        }

        return null // success
    }

    /**
     * Check if the pass is still valid (not expired).
     */
    suspend fun isPassValid(): Boolean {
        var expiry = 0L
        dataStore.data.collect { prefs ->
            expiry = prefs[UserPreferences.PASS_EXPIRY] ?: 0L
            return@collect
        }
        return expiry == 0L || System.currentTimeMillis() < expiry
    }

    /**
     * Clears activation state (sign out).
     */
    suspend fun clearActivation() {
        dataStore.edit { prefs ->
            prefs.remove(UserPreferences.IS_ACTIVATED)
            prefs.remove(UserPreferences.ACTIVATION_CODE)
            prefs.remove(UserPreferences.USER_ID)
            prefs.remove(UserPreferences.USERNAME)
            prefs.remove(UserPreferences.PASS_TYPE)
            prefs.remove(UserPreferences.PASS_EXPIRY)
        }
    }
}

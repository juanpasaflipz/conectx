package app.conectx.data.local.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_prefs")

object UserPreferences {
    val ACTIVATION_CODE = stringPreferencesKey("activation_code")
    val USER_ID = stringPreferencesKey("user_id")
    val USERNAME = stringPreferencesKey("username")
    val IS_ACTIVATED = booleanPreferencesKey("is_activated")
    val PASS_TYPE = stringPreferencesKey("pass_type")       // "mundial" or "partido"
    val PASS_EXPIRY = longPreferencesKey("pass_expiry")     // epoch millis
}

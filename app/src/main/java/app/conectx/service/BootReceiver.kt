package app.conectx.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.datastore.preferences.core.booleanPreferencesKey
import app.conectx.data.local.preferences.dataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * Restarts MeshService after device reboot if the user is activated.
 * This ensures mesh networking resumes automatically on match day
 * even if the user doesn't manually open the app.
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        // Check if user is activated before starting the service
        val isActivated = runBlocking {
            try {
                val prefs = context.dataStore.data.first()
                prefs[booleanPreferencesKey("is_activated")] == true
            } catch (_: Exception) {
                false
            }
        }

        if (!isActivated) {
            Log.d(TAG, "Not activated — skipping mesh service start")
            return
        }

        Log.d(TAG, "Boot completed — starting MeshService")
        try {
            val serviceIntent = MeshService.startIntent(context)
            if (Build.VERSION.SDK_INT >= 26) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start MeshService on boot", e)
        }
    }
}

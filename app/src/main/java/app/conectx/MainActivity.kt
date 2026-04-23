package app.conectx

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.lifecycleScope
import app.conectx.data.local.preferences.UserPreferences
import app.conectx.data.local.preferences.dataStore
import app.conectx.presentation.common.ProminentDisclosure
import app.conectx.presentation.navigation.ConectxNavGraph
import app.conectx.presentation.theme.ConectxTheme
import app.conectx.service.MeshService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    // Permissions needed for Nearby Connections (BLE + WiFi P2P)
    private val requiredPermissions: Array<String>
        get() = buildList {
            // Location — required for BLE scanning on Android 12+
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)

            // Bluetooth (Android 12+)
            if (Build.VERSION.SDK_INT >= 31) {
                add(Manifest.permission.BLUETOOTH_ADVERTISE)
                add(Manifest.permission.BLUETOOTH_CONNECT)
                add(Manifest.permission.BLUETOOTH_SCAN)
            }

            // Nearby WiFi Devices (Android 13+)
            if (Build.VERSION.SDK_INT >= 33) {
                add(Manifest.permission.NEARBY_WIFI_DEVICES)
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toTypedArray()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            Log.d(TAG, "All permissions granted — starting mesh service")
            startMeshService()
        } else {
            val denied = permissions.filter { !it.value }.keys
            Log.w(TAG, "Permissions denied: $denied")
            // The app still works without mesh (Firebase fallback),
            // but P2P won't be available. UI can prompt later.
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Let Compose handle all window insets (keyboard, nav bar, status bar)
        // so that imePadding() in ChatScreen actually works.
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val themeModeFlow = dataStore.data.map { prefs ->
            prefs[UserPreferences.THEME_MODE] ?: "system"
        }
        val disclosureAcceptedFlow = dataStore.data.map { prefs ->
            prefs[UserPreferences.DISCLOSURE_ACCEPTED] ?: false
        }

        setContent {
            val themeMode by themeModeFlow.collectAsState(initial = "system")
            val disclosureAccepted by disclosureAcceptedFlow.collectAsState(initial = true) // default true to avoid flash
            val darkTheme = when (themeMode) {
                "dark" -> true
                "light" -> false
                else -> isSystemInDarkTheme()
            }

            ConectxTheme(darkTheme = darkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (!disclosureAccepted) {
                        ProminentDisclosure(onAccepted = {
                            lifecycleScope.launch {
                                dataStore.edit { prefs ->
                                    prefs[UserPreferences.DISCLOSURE_ACCEPTED] = true
                                }
                                requestPermissionsIfNeeded()
                            }
                        })
                    } else {
                        ConectxNavGraph()
                    }
                }
            }
        }

        // Check disclosure state — only request permissions if already accepted
        lifecycleScope.launch {
            val accepted = disclosureAcceptedFlow.first()
            if (accepted) {
                requestPermissionsIfNeeded()
            }
        }
    }

    private fun requestPermissionsIfNeeded() {
        val missing = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isEmpty()) {
            startMeshService()
        } else {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    private fun startMeshService() {
        try {
            val intent = MeshService.startIntent(this)
            if (Build.VERSION.SDK_INT >= 26) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            Log.d(TAG, "MeshService start requested")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start MeshService", e)
        }
    }

}

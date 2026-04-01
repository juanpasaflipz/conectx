package app.conectx.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import app.conectx.MainActivity
import app.conectx.R
import app.conectx.crypto.SignalSessionManager
import app.conectx.sync.SyncEngine
import app.conectx.transport.TransportManager
import app.conectx.transport.nearby.PeerTracker
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Foreground service that keeps Nearby Connections alive when the app
 * is backgrounded. Required on Android 8+ for long-running BLE/WiFi work.
 *
 * Starts both the transport layer and the sync engine.
 * Uses FOREGROUND_SERVICE_CONNECTED_DEVICE type (Android 14+).
 *
 * The notification updates live with peer count so the user knows
 * how many friends are connected to the mesh.
 */
@AndroidEntryPoint
class MeshService : Service() {

    companion object {
        private const val TAG = "MeshService"
        private const val CHANNEL_ID = "mesh_service"
        private const val NOTIFICATION_ID = 1

        const val ACTION_START = "app.conectx.action.START_MESH"
        const val ACTION_STOP = "app.conectx.action.STOP_MESH"

        fun startIntent(context: Context): Intent =
            Intent(context, MeshService::class.java).apply { action = ACTION_START }

        fun stopIntent(context: Context): Intent =
            Intent(context, MeshService::class.java).apply { action = ACTION_STOP }
    }

    @Inject lateinit var transportManager: TransportManager
    @Inject lateinit var syncEngine: SyncEngine
    @Inject lateinit var peerTracker: PeerTracker
    @Inject lateinit var signalSessionManager: SignalSessionManager

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                startMesh()
                return START_STICKY
            }
        }
    }

    private fun startMesh() {
        val notification = buildNotification(0)

        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        acquireWakeLock()

        serviceScope.launch {
            try {
                signalSessionManager.initialize()
                Log.d(TAG, "Signal Protocol initialized")
                transportManager.startAll()
                syncEngine.start()
                Log.d(TAG, "Transport + sync engine started")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start", e)
            }
        }

        // Update notification with live peer count
        serviceScope.launch {
            peerTracker.connectedPeers
                .map { it.size }
                .distinctUntilChanged()
                .collect { count ->
                    updateNotification(count)
                }
        }
    }

    override fun onDestroy() {
        Log.d(TAG, "Destroying MeshService")
        syncEngine.stop()
        serviceScope.launch {
            try {
                transportManager.stopAll()
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping transport", e)
            }
        }
        releaseWakeLock()
        serviceScope.cancel()
        super.onDestroy()
    }

    // ── Notification ─────────────────────────────────────────────────

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_mesh),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Mantiene la conexion con tus amigos en el estadio"
            setShowBadge(false)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(peerCount: Int): Notification {
        val contentText = if (peerCount > 0) {
            "$peerCount conectado(s) al mesh"
        } else {
            "Buscando amigos cercanos…"
        }

        // Tap notification → open app
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openPending = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Stop action in notification
        val stopPending = PendingIntent.getService(
            this, 1, stopIntent(this),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentIntent(openPending)
            .setOngoing(true)
            .addAction(
                Notification.Action.Builder(
                    null,
                    "Detener",
                    stopPending
                ).build()
            )
            .build()
    }

    private fun updateNotification(peerCount: Int) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(peerCount))
    }

    // ── Wake Lock ────────────────────────────────────────────────────
    // Prevents the CPU from sleeping so Nearby Connections can maintain
    // BLE scanning/advertising. This is critical in stadium scenarios
    // where users have the phone in their pocket.

    private fun acquireWakeLock() {
        if (wakeLock != null) return
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "conectx:mesh_service"
        ).apply {
            acquire(4 * 60 * 60 * 1000L) // 4 hours max — one match duration
        }
        Log.d(TAG, "Wake lock acquired")
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
                Log.d(TAG, "Wake lock released")
            }
        }
        wakeLock = null
    }
}

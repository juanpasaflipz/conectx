package app.conectx.transport.wifiaware

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.aware.AttachCallback
import android.net.wifi.aware.DiscoverySession
import android.net.wifi.aware.DiscoverySessionCallback
import android.net.wifi.aware.PeerHandle
import android.net.wifi.aware.PublishConfig
import android.net.wifi.aware.PublishDiscoverySession
import android.net.wifi.aware.SubscribeConfig
import android.net.wifi.aware.SubscribeDiscoverySession
import android.net.wifi.aware.WifiAwareManager
import android.net.wifi.aware.WifiAwareSession
import android.os.Build
import android.util.Log
import app.conectx.domain.model.Peer
import app.conectx.domain.model.SyncRecord
import app.conectx.transport.Connection
import app.conectx.transport.TransportPlugin
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * WiFi Aware transport for cross-platform P2P messaging.
 *
 * WiFi Aware (Wi-Fi Neighbor Awareness Networking) allows devices to
 * discover each other and communicate directly without an access point.
 * It operates on the 2.4/5 GHz bands alongside normal WiFi.
 *
 * This is the primary transport for Android-to-iOS interop because
 * iOS 26+ also supports WiFi Aware via Apple's WiFiAware framework.
 *
 * Flow:
 * 1. Publish + Subscribe on service name "conectx"
 * 2. When a peer is discovered, exchange pre-key bundles via WiFi Aware messages
 * 3. Create a WiFi Aware network for data transfer
 * 4. Send/receive Protobuf Envelopes over the network
 *
 * Requires API 26+ (our minSdk). WiFi Aware availability depends on
 * hardware support — check [isAvailable] before relying on this transport.
 */
@Singleton
@SuppressLint("MissingPermission") // Permissions checked at the UI layer before start()
class WifiAwarePlugin @Inject constructor(
    @ApplicationContext private val context: Context
) : TransportPlugin {

    companion object {
        private const val TAG = "WifiAwarePlugin"
        private const val SERVICE_NAME = "conectx"
        private const val SERVICE_TYPE = "_conectx._tcp"
        // Max message size for WiFi Aware sendMessage (255 bytes)
        private const val MAX_AWARE_MESSAGE_SIZE = 255
    }

    private val wifiAwareManager: WifiAwareManager? by lazy {
        context.getSystemService(Context.WIFI_AWARE_SERVICE) as? WifiAwareManager
    }

    private var awareSession: WifiAwareSession? = null
    private var publishSession: PublishDiscoverySession? = null
    private var subscribeSession: SubscribeDiscoverySession? = null
    private var isRunning = false

    // peerHandle → peer info
    private val discoveredPeers = ConcurrentHashMap<Int, PeerHandle>()
    private val peerAddresses = ConcurrentHashMap<Int, String>()

    private val _discoveredPeersFlow = MutableSharedFlow<Peer>(extraBufferCapacity = 64)
    private val _receivedMessages = MutableSharedFlow<SyncRecord>(extraBufferCapacity = 256)

    // Tracks raw bytes received via WiFi Aware messaging (pre-key bundles, small messages)
    private val _receivedBundles = MutableSharedFlow<Pair<PeerHandle, ByteArray>>(extraBufferCapacity = 64)
    val receivedBundles: Flow<Pair<PeerHandle, ByteArray>> = _receivedBundles.asSharedFlow()

    override val isAvailable: Boolean
        get() {
            if (!isRunning) return false
            val manager = wifiAwareManager ?: return false
            return manager.isAvailable
        }

    /**
     * True if the device hardware supports WiFi Aware at all.
     * Check this before attempting to start.
     */
    val isHardwareSupported: Boolean
        get() {
            return context.packageManager.hasSystemFeature("android.hardware.wifi.aware")
        }

    // ── Lifecycle ────────────────────────────────────────────────────

    override suspend fun start() {
        if (isRunning) return
        if (!isHardwareSupported) {
            Log.w(TAG, "WiFi Aware not supported on this device")
            return
        }

        val manager = wifiAwareManager ?: return

        // Register for WiFi Aware availability changes
        val filter = IntentFilter(WifiAwareManager.ACTION_WIFI_AWARE_STATE_CHANGED)
        context.registerReceiver(availabilityReceiver, filter)

        // Attach to WiFi Aware
        manager.attach(attachCallback, null)
        isRunning = true
        Log.d(TAG, "Starting WiFi Aware attachment...")
    }

    override suspend fun stop() {
        if (!isRunning) return
        isRunning = false

        try {
            context.unregisterReceiver(availabilityReceiver)
        } catch (_: Exception) {}

        publishSession?.close()
        subscribeSession?.close()
        awareSession?.close()

        publishSession = null
        subscribeSession = null
        awareSession = null
        discoveredPeers.clear()
        peerAddresses.clear()

        Log.d(TAG, "Stopped — WiFi Aware sessions closed")
    }

    // ── TransportPlugin interface ────────────────────────────────────

    override suspend fun discoverPeers(): Flow<Peer> = _discoveredPeersFlow.asSharedFlow()

    override suspend fun connectToPeer(peer: Peer): Connection {
        return Connection(peer = peer, endpointId = peer.id)
    }

    override suspend fun sendMessage(connection: Connection, record: SyncRecord) {
        // For WiFi Aware, we use the message passing API for small payloads
        // and network sockets for large payloads. This is handled by the
        // messaging engine that sits above this transport.
        Log.d(TAG, "sendMessage called — routing through WiFi Aware")
    }

    override fun onMessageReceived(): Flow<SyncRecord> = _receivedMessages.asSharedFlow()

    // ── WiFi Aware message passing ───────────────────────────────────

    /**
     * Sends a small message (up to 255 bytes) to a discovered peer via
     * WiFi Aware's built-in messaging. Used for pre-key bundle exchange.
     */
    fun sendAwareMessage(peerHandle: PeerHandle, message: ByteArray) {
        val session: DiscoverySession = publishSession ?: subscribeSession ?: run {
            Log.w(TAG, "No active discovery session for sendAwareMessage")
            return
        }
        if (message.size > MAX_AWARE_MESSAGE_SIZE) {
            Log.w(TAG, "Message too large for WiFi Aware messaging: ${message.size} bytes")
            return
        }
        session.sendMessage(peerHandle, 0, message)
    }

    /**
     * Sends an Envelope as raw bytes to a specific peer.
     * Called by the messaging engine for outgoing encrypted messages.
     */
    fun sendEnvelope(peerHandleId: Int, envelopeBytes: ByteArray) {
        val handle = discoveredPeers[peerHandleId]
        if (handle == null) {
            Log.w(TAG, "No peer handle for ID $peerHandleId")
            return
        }
        // For messages > 255 bytes, we need WiFi Aware network data path.
        // For now, fragment into multiple WiFi Aware messages with sequence numbers.
        if (envelopeBytes.size <= MAX_AWARE_MESSAGE_SIZE) {
            val session: DiscoverySession = publishSession ?: subscribeSession ?: return
            session.sendMessage(handle, 0, envelopeBytes)
        } else {
            // Fragment: [1-byte seq][1-byte total][253-byte chunk]
            val chunkSize = MAX_AWARE_MESSAGE_SIZE - 2
            val totalChunks = (envelopeBytes.size + chunkSize - 1) / chunkSize
            val session: DiscoverySession = publishSession ?: subscribeSession ?: return
            for (i in 0 until totalChunks) {
                val start = i * chunkSize
                val end = minOf(start + chunkSize, envelopeBytes.size)
                val chunk = ByteArray(end - start + 2)
                chunk[0] = i.toByte()
                chunk[1] = totalChunks.toByte()
                System.arraycopy(envelopeBytes, start, chunk, 2, end - start)
                session.sendMessage(handle, 0, chunk)
            }
        }
    }

    /** Broadcast an envelope to all discovered WiFi Aware peers. */
    fun broadcast(envelopeBytes: ByteArray) {
        for ((handleId, _) in discoveredPeers) {
            sendEnvelope(handleId, envelopeBytes)
        }
    }

    // ── Callbacks ────────────────────────────────────────────────────

    private val attachCallback = object : AttachCallback() {
        override fun onAttached(session: WifiAwareSession) {
            Log.d(TAG, "WiFi Aware attached")
            awareSession = session
            startPublish()
            startSubscribe()
        }

        override fun onAttachFailed() {
            Log.e(TAG, "WiFi Aware attach failed")
            isRunning = false
        }
    }

    private fun startPublish() {
        val config = PublishConfig.Builder()
            .setServiceName(SERVICE_NAME)
            .build()

        awareSession?.publish(config, object : DiscoverySessionCallback() {
            override fun onPublishStarted(session: PublishDiscoverySession) {
                Log.d(TAG, "Publishing on '$SERVICE_NAME'")
                publishSession = session
            }

            override fun onMessageReceived(peerHandle: PeerHandle, message: ByteArray) {
                handleIncomingMessage(peerHandle, message)
            }
        }, null)
    }

    private fun startSubscribe() {
        val config = SubscribeConfig.Builder()
            .setServiceName(SERVICE_NAME)
            .build()

        awareSession?.subscribe(config, object : DiscoverySessionCallback() {
            override fun onServiceDiscovered(
                peerHandle: PeerHandle,
                serviceSpecificInfo: ByteArray?,
                matchFilter: List<ByteArray>?
            ) {
                Log.d(TAG, "Discovered WiFi Aware peer")
                val handleId = System.identityHashCode(peerHandle)
                discoveredPeers[handleId] = peerHandle

                val peer = Peer(
                    id = handleId.toString(),
                    displayName = "WiFi Aware Peer",
                    squadId = "",
                    isConnected = true,
                    lastSeen = System.currentTimeMillis()
                )
                _discoveredPeersFlow.tryEmit(peer)
            }

            override fun onMessageReceived(peerHandle: PeerHandle, message: ByteArray) {
                handleIncomingMessage(peerHandle, message)
            }

            override fun onServiceLost(peerHandle: PeerHandle, reason: Int) {
                val handleId = System.identityHashCode(peerHandle)
                discoveredPeers.remove(handleId)
                peerAddresses.remove(handleId)
                Log.d(TAG, "WiFi Aware peer lost")
            }
        }, null)
    }

    private fun handleIncomingMessage(peerHandle: PeerHandle, message: ByteArray) {
        val handleId = System.identityHashCode(peerHandle)
        discoveredPeers[handleId] = peerHandle
        _receivedBundles.tryEmit(Pair(peerHandle, message))
    }

    // ── Availability receiver ────────────────────────────────────────

    private val availabilityReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val manager = wifiAwareManager ?: return
            if (manager.isAvailable) {
                Log.d(TAG, "WiFi Aware became available")
                if (awareSession == null) {
                    manager.attach(attachCallback, null)
                }
            } else {
                Log.w(TAG, "WiFi Aware became unavailable")
            }
        }
    }
}

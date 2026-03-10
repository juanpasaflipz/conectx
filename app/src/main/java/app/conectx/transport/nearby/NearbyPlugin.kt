package app.conectx.transport.nearby

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import app.conectx.domain.model.Peer
import app.conectx.domain.model.SyncRecord
import app.conectx.transport.Connection
import app.conectx.transport.TransportPlugin
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Nearby Connections transport using P2P_CLUSTER strategy.
 *
 * All Conectx devices use a single service ID ("app.conectx") so every
 * device in BLE/WiFi range can discover each other. Squad-level filtering
 * happens in the sync layer — the mesh carries all traffic.
 *
 * On start(), the plugin simultaneously advertises (makes this device
 * discoverable) and discovers (scans for others). When a peer is found,
 * it auto-requests a connection. Both sides auto-accept (same service ID
 * = same app = trusted). Once connected, SyncRecords flow as BYTES payloads.
 *
 * Gossip relay: when a record arrives from peer A, it's forwarded to all
 * other connected peers (B, C, …) that haven't seen it. This creates a
 * multi-hop mesh where messages propagate even if devices aren't in
 * direct range of the original sender.
 */
@Singleton
class NearbyPlugin @Inject constructor(
    @ApplicationContext private val context: Context,
    private val peerTracker: PeerTracker,
    private val meshRouter: MeshRouter
) : TransportPlugin {

    companion object {
        private const val TAG = "NearbyPlugin"
        private const val SERVICE_ID = "app.conectx"
    }

    private val connectionsClient: ConnectionsClient by lazy {
        Nearby.getConnectionsClient(context)
    }

    // Display name advertised to peers. Set via configure() before start().
    private var localName = "conectx-user"
    private var isRunning = false

    // endpointId → display name learned during discovery or connection initiation
    private val endpointNames = ConcurrentHashMap<String, String>()

    // Emits peers as they're discovered (before connection is established)
    private val _discoveredPeers = MutableSharedFlow<Peer>(extraBufferCapacity = 64)

    // Emits deserialized records received from connected peers
    private val _receivedMessages = MutableSharedFlow<SyncRecord>(extraBufferCapacity = 256)

    override val isAvailable: Boolean
        get() = isRunning

    /**
     * True when the WiFi adapter is enabled (hardware on).
     * Nearby Connections needs WiFi Direct for the data channel —
     * BLE alone handles discovery but can't transfer payloads reliably.
     */
    val isWifiEnabled: Boolean
        get() {
            val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            return wifiManager?.isWifiEnabled == true
        }

    /**
     * Set the local display name before calling start().
     * This is the name other peers see during discovery.
     */
    fun configure(userName: String) {
        localName = userName
    }

    // ── Lifecycle ────────────────────────────────────────────────────

    override suspend fun start() {
        if (isRunning) return
        isRunning = true
        startAdvertising()
        startDiscovery()
        Log.d(TAG, "Started — advertising + discovering as '$localName'")
    }

    override suspend fun stop() {
        if (!isRunning) return
        isRunning = false
        connectionsClient.stopAdvertising()
        connectionsClient.stopDiscovery()
        connectionsClient.stopAllEndpoints()
        endpointNames.clear()
        peerTracker.clear()
        Log.d(TAG, "Stopped — all connections closed")
    }

    // ── TransportPlugin interface ────────────────────────────────────

    override suspend fun discoverPeers(): Flow<Peer> = _discoveredPeers.asSharedFlow()

    override suspend fun connectToPeer(peer: Peer): Connection {
        // peer.id holds the Nearby endpoint ID set during discovery
        connectionsClient.requestConnection(localName, peer.id, connectionCallback)
            .addOnFailureListener { e ->
                Log.w(TAG, "requestConnection to ${peer.id} failed", e)
            }
        return Connection(peer = peer, endpointId = peer.id)
    }

    override suspend fun sendMessage(connection: Connection, record: SyncRecord) {
        val bytes = SyncRecordSerializer.serialize(record)
        meshRouter.markSeen(record.id)
        connectionsClient.sendPayload(connection.endpointId, Payload.fromBytes(bytes))
            .addOnFailureListener { e ->
                Log.w(TAG, "sendPayload to ${connection.endpointId} failed", e)
            }
    }

    override fun onMessageReceived(): Flow<SyncRecord> = _receivedMessages.asSharedFlow()

    // ── Broadcast (send to ALL connected peers) ──────────────────────

    /**
     * Sends a record to every connected peer. Used for outgoing messages
     * authored by the local user (as opposed to relayed messages).
     */
    fun broadcast(record: SyncRecord) {
        val bytes = SyncRecordSerializer.serialize(record)
        meshRouter.markSeen(record.id)
        for (endpointId in peerTracker.connectedEndpoints()) {
            connectionsClient.sendPayload(endpointId, Payload.fromBytes(bytes))
                .addOnFailureListener { e ->
                    Log.w(TAG, "broadcast to $endpointId failed", e)
                }
        }
    }

    // ── Internal: Advertising ────────────────────────────────────────

    private fun startAdvertising() {
        val options = AdvertisingOptions.Builder()
            .setStrategy(Strategy.P2P_CLUSTER)
            .build()

        connectionsClient.startAdvertising(localName, SERVICE_ID, connectionCallback, options)
            .addOnSuccessListener { Log.d(TAG, "Advertising started") }
            .addOnFailureListener { e -> Log.e(TAG, "Advertising failed", e) }
    }

    // ── Internal: Discovery ──────────────────────────────────────────

    private fun startDiscovery() {
        val options = DiscoveryOptions.Builder()
            .setStrategy(Strategy.P2P_CLUSTER)
            .build()

        connectionsClient.startDiscovery(SERVICE_ID, discoveryCallback, options)
            .addOnSuccessListener { Log.d(TAG, "Discovery started") }
            .addOnFailureListener { e -> Log.e(TAG, "Discovery failed", e) }
    }

    // ── Callback: Endpoint Discovery ─────────────────────────────────

    private val discoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            Log.d(TAG, "Endpoint found: $endpointId (${info.endpointName})")
            endpointNames[endpointId] = info.endpointName

            val peer = Peer(
                id = endpointId,
                displayName = info.endpointName,
                squadId = "", // resolved during sync handshake
                isConnected = false,
                lastSeen = System.currentTimeMillis()
            )
            _discoveredPeers.tryEmit(peer)

            // Auto-connect: in a stadium mesh, we want maximum connectivity
            connectionsClient.requestConnection(localName, endpointId, connectionCallback)
                .addOnFailureListener { e ->
                    Log.w(TAG, "Auto-connect to $endpointId failed", e)
                }
        }

        override fun onEndpointLost(endpointId: String) {
            Log.d(TAG, "Endpoint lost: $endpointId")
            endpointNames.remove(endpointId)
        }
    }

    // ── Callback: Connection Lifecycle ────────────────────────────────

    private val connectionCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            // Both sides see this. We auto-accept because same SERVICE_ID = same app.
            Log.d(TAG, "Connection initiated: $endpointId (${info.endpointName})")
            endpointNames[endpointId] = info.endpointName
            connectionsClient.acceptConnection(endpointId, payloadCallback)
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            if (result.status.isSuccess) {
                Log.d(TAG, "Connected to $endpointId")
                val name = endpointNames[endpointId] ?: "Unknown"
                val peer = Peer(
                    id = endpointId,
                    displayName = name,
                    squadId = "",
                    isConnected = true,
                    lastSeen = System.currentTimeMillis()
                )
                peerTracker.addPeer(endpointId, peer)
            } else {
                Log.w(TAG, "Connection to $endpointId failed: ${result.status}")
                endpointNames.remove(endpointId)
            }
        }

        override fun onDisconnected(endpointId: String) {
            Log.d(TAG, "Disconnected from $endpointId")
            endpointNames.remove(endpointId)
            peerTracker.removePeer(endpointId)
        }
    }

    // ── Callback: Payload ────────────────────────────────────────────

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            val bytes = payload.asBytes() ?: return
            try {
                val record = SyncRecordSerializer.deserialize(bytes)

                // markSeen returns true only the first time — dedup + loop prevention
                if (meshRouter.markSeen(record.id)) {
                    _receivedMessages.tryEmit(record)

                    // Gossip: relay to every connected peer except the sender
                    val targets = meshRouter.relayTargets(
                        senderEndpointId = endpointId,
                        allConnected = peerTracker.connectedEndpoints()
                    )
                    for (targetId in targets) {
                        connectionsClient.sendPayload(targetId, Payload.fromBytes(bytes))
                            .addOnFailureListener { e ->
                                Log.w(TAG, "Relay to $targetId failed", e)
                            }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to deserialize payload from $endpointId", e)
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            // BYTES payloads arrive in a single chunk — nothing to track
        }
    }
}

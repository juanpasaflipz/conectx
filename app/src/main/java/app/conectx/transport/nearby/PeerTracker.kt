package app.conectx.transport.nearby

import app.conectx.domain.model.Peer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tracks currently connected peers and their connection state.
 * Indexed by Nearby Connections endpoint ID for fast lookup.
 */
@Singleton
class PeerTracker @Inject constructor() {

    // endpointId → Peer
    private val peers = ConcurrentHashMap<String, Peer>()
    private val _connectedPeers = MutableStateFlow<List<Peer>>(emptyList())

    val connectedPeers: Flow<List<Peer>> = _connectedPeers.asStateFlow()

    val connectedCount: Flow<Int> = _connectedPeers.map { it.size }

    fun addPeer(endpointId: String, peer: Peer) {
        peers[endpointId] = peer
        emitSnapshot()
    }

    fun removePeer(endpointId: String) {
        peers.remove(endpointId)
        emitSnapshot()
    }

    fun getPeer(endpointId: String): Peer? = peers[endpointId]

    fun connectedEndpoints(): Set<String> = peers.keys.toSet()

    fun clear() {
        peers.clear()
        emitSnapshot()
    }

    private fun emitSnapshot() {
        _connectedPeers.value = peers.values.toList()
    }
}

package app.conectx.transport

import app.conectx.domain.model.Peer
import app.conectx.domain.model.SyncRecord
import kotlinx.coroutines.flow.Flow

interface TransportPlugin {
    val isAvailable: Boolean
    suspend fun start()
    suspend fun stop()
    suspend fun discoverPeers(): Flow<Peer>
    suspend fun connectToPeer(peer: Peer): Connection
    suspend fun sendMessage(connection: Connection, record: SyncRecord)
    fun onMessageReceived(): Flow<SyncRecord>
}

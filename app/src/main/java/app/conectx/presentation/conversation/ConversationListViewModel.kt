package app.conectx.presentation.conversation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.conectx.data.local.db.dao.ConversationDao
import app.conectx.data.local.db.entity.ConversationEntity
import app.conectx.transport.TransportManager
import app.conectx.transport.nearby.PeerTracker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class ConversationListViewModel @Inject constructor(
    conversationDao: ConversationDao,
    peerTracker: PeerTracker,
    transportManager: TransportManager
) : ViewModel() {

    val conversations: StateFlow<List<ConversationEntity>> = conversationDao
        .getAllConversations()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val connectedPeerCount: StateFlow<Int> = peerTracker.connectedPeers
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val isWifiAwareAvailable: StateFlow<Boolean> = peerTracker.connectedPeers
        .map { transportManager.wifiAwarePlugin.isAvailable }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
}

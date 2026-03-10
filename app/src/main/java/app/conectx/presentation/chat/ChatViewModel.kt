package app.conectx.presentation.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.conectx.domain.model.Message
import app.conectx.domain.repository.MessageRepository
import app.conectx.domain.repository.SquadRepository
import app.conectx.sync.SyncEngine
import app.conectx.transport.TransportManager
import app.conectx.transport.nearby.PeerTracker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    messageRepository: MessageRepository,
    private val squadRepository: SquadRepository,
    private val syncEngine: SyncEngine,
    peerTracker: PeerTracker,
    transportManager: TransportManager
) : ViewModel() {

    val squadId: String = checkNotNull(savedStateHandle["squadId"])
    val localUserId: String = syncEngine.localUserId

    private val _squadName = MutableStateFlow("Chat")
    val squadName: StateFlow<String> = _squadName.asStateFlow()

    val messages: StateFlow<List<Message>> = messageRepository
        .getMessagesForSquad(squadId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val connectedPeerCount: StateFlow<Int> = peerTracker.connectedPeers
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** True when no transport can reach anyone (no mesh peers, no internet) */
    val isOffline: StateFlow<Boolean> = connectedPeerCount
        .map { peers -> peers == 0 && !transportManager.firebasePlugin.isAvailable }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** True when mesh is active but Firebase/internet is not */
    val isMeshOnly: StateFlow<Boolean> = connectedPeerCount
        .map { peers -> peers > 0 && !transportManager.firebasePlugin.isAvailable }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    init {
        viewModelScope.launch {
            val squad = squadRepository.getSquadById(squadId)
            _squadName.value = squad?.name ?: "Chat"
        }
    }

    fun sendMessage(text: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            syncEngine.sendChat(squadId, text.trim())
        }
    }
}

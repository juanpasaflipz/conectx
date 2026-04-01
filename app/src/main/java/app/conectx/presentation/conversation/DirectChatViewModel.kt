package app.conectx.presentation.conversation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.conectx.data.local.db.dao.ConversationDao
import app.conectx.data.local.db.dao.DirectMessageDao
import app.conectx.data.local.db.entity.DirectMessageEntity
import app.conectx.sync.SyncEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DirectChatViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    directMessageDao: DirectMessageDao,
    private val conversationDao: ConversationDao,
    private val syncEngine: SyncEngine
) : ViewModel() {

    val peerId: String = checkNotNull(savedStateHandle["peerId"])

    private val _peerName = MutableStateFlow("")
    val peerName: StateFlow<String> = _peerName.asStateFlow()

    val messages: StateFlow<List<DirectMessageEntity>> = directMessageDao
        .getMessagesForPeer(peerId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            val conversation = conversationDao.getConversation(peerId)
            _peerName.value = conversation?.peerDisplayName ?: peerId.take(8)
            // Mark as read when opening
            conversationDao.markRead(peerId)
        }
    }

    fun sendMessage(text: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            syncEngine.sendDirectMessage(peerId, text.trim())
        }
    }
}

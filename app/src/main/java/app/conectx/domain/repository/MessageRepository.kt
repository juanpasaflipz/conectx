package app.conectx.domain.repository

import app.conectx.domain.model.Message
import kotlinx.coroutines.flow.Flow

interface MessageRepository {
    fun getMessagesForSquad(squadId: String): Flow<List<Message>>
    suspend fun insertMessage(message: Message)
    suspend fun getMessageById(id: String): Message?
}

package app.conectx.data.repository

import app.conectx.data.local.db.dao.MessageDao
import app.conectx.data.local.db.entity.MessageEntity
import app.conectx.domain.model.Message
import app.conectx.domain.repository.MessageRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessageRepositoryImpl @Inject constructor(
    private val messageDao: MessageDao
) : MessageRepository {

    override fun getMessagesForSquad(squadId: String): Flow<List<Message>> {
        return messageDao.getMessagesForSquad(squadId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun insertMessage(message: Message) {
        messageDao.insert(message.toEntity())
    }

    override suspend fun getMessageById(id: String): Message? {
        return messageDao.getById(id)?.toDomain()
    }

    private fun MessageEntity.toDomain() = Message(
        id = id,
        squadId = squadId,
        authorId = authorId,
        authorName = authorName,
        text = text,
        lamportClock = lamportClock,
        timestamp = timestamp
    )

    private fun Message.toEntity() = MessageEntity(
        id = id,
        squadId = squadId,
        authorId = authorId,
        authorName = authorName,
        text = text,
        lamportClock = lamportClock,
        timestamp = timestamp
    )
}

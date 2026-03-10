package app.conectx.transport.nearby

import app.conectx.domain.model.RecordType
import app.conectx.domain.model.SyncRecord
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

/**
 * Serializes SyncRecords to/from ByteArray for Nearby Connections payloads.
 *
 * Wire format (all fields sequential, big-endian):
 *   writeUTF(id) | writeUTF(squadId) | writeUTF(authorId) |
 *   writeLong(lamportClock) | writeLong(timestamp) | writeUTF(type.name) |
 *   writeInt(payload.size) + payload bytes |
 *   writeInt(signature.size) + signature bytes
 *
 * Uses DataOutputStream.writeUTF which prefixes strings with 2-byte length.
 * Our strings are short IDs/UUIDs so the 64KB UTF limit is never hit.
 */
object SyncRecordSerializer {

    fun serialize(record: SyncRecord): ByteArray {
        val baos = ByteArrayOutputStream()
        DataOutputStream(baos).use { out ->
            out.writeUTF(record.id)
            out.writeUTF(record.squadId)
            out.writeUTF(record.authorId)
            out.writeLong(record.lamportClock)
            out.writeLong(record.timestamp)
            out.writeUTF(record.type.name)
            out.writeInt(record.payload.size)
            out.write(record.payload)
            out.writeInt(record.signature.size)
            out.write(record.signature)
        }
        return baos.toByteArray()
    }

    fun deserialize(bytes: ByteArray): SyncRecord {
        DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            return SyncRecord(
                id = input.readUTF(),
                squadId = input.readUTF(),
                authorId = input.readUTF(),
                lamportClock = input.readLong(),
                timestamp = input.readLong(),
                type = RecordType.valueOf(input.readUTF()),
                payload = ByteArray(input.readInt()).also { input.readFully(it) },
                signature = ByteArray(input.readInt()).also { input.readFully(it) }
            )
        }
    }
}

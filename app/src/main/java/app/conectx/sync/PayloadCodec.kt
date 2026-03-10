package app.conectx.sync

import app.conectx.domain.model.LocationPing
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

/**
 * Encodes/decodes the payload bytes inside a SyncRecord for each RecordType.
 *
 * Each type has its own compact binary format using DataOutputStream.
 * Strings are length-prefixed via writeUTF (2-byte length + UTF-8 bytes).
 */
object PayloadCodec {

    // ── CHAT ──────────────────────────────────────────────────────────

    data class ChatPayload(val authorName: String, val text: String)

    fun encodeChat(authorName: String, text: String): ByteArray {
        val baos = ByteArrayOutputStream()
        DataOutputStream(baos).use { out ->
            out.writeUTF(authorName)
            out.writeUTF(text)
        }
        return baos.toByteArray()
    }

    fun decodeChat(bytes: ByteArray): ChatPayload {
        DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            return ChatPayload(
                authorName = input.readUTF(),
                text = input.readUTF()
            )
        }
    }

    // ── LOCATION ──────────────────────────────────────────────────────
    // Includes authorName so the UI can display who shared the location
    // without needing to join against another table.

    data class LocationPayload(val authorName: String, val ping: LocationPing)

    fun encodeLocation(authorName: String, ping: LocationPing): ByteArray {
        val baos = ByteArrayOutputStream()
        DataOutputStream(baos).use { out ->
            out.writeUTF(authorName)
            out.writeUTF(ping.section)
            out.writeUTF(ping.row ?: "")
            out.writeUTF(ping.seat ?: "")
            out.writeUTF(ping.note ?: "")
            out.writeInt(ping.battery)
        }
        return baos.toByteArray()
    }

    fun decodeLocation(bytes: ByteArray): LocationPayload {
        DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            return LocationPayload(
                authorName = input.readUTF(),
                ping = LocationPing(
                    section = input.readUTF(),
                    row = input.readUTF().ifEmpty { null },
                    seat = input.readUTF().ifEmpty { null },
                    note = input.readUTF().ifEmpty { null },
                    battery = input.readInt()
                )
            )
        }
    }

    // ── SYNC_OFFER ────────────────────────────────────────────────────

    data class SyncOfferPayload(
        val squadClocks: Map<String, Long>,
        val publicKey: ByteArray
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is SyncOfferPayload) return false
            return squadClocks == other.squadClocks && publicKey.contentEquals(other.publicKey)
        }
        override fun hashCode(): Int = squadClocks.hashCode() * 31 + publicKey.contentHashCode()
    }

    fun encodeSyncOffer(squadClocks: Map<String, Long>, publicKey: ByteArray = ByteArray(0)): ByteArray {
        val baos = ByteArrayOutputStream()
        DataOutputStream(baos).use { out ->
            out.writeInt(squadClocks.size)
            for ((squadId, clock) in squadClocks) {
                out.writeUTF(squadId)
                out.writeLong(clock)
            }
            out.writeInt(publicKey.size)
            if (publicKey.isNotEmpty()) {
                out.write(publicKey)
            }
        }
        return baos.toByteArray()
    }

    fun decodeSyncOffer(bytes: ByteArray): SyncOfferPayload {
        val squadClocks = mutableMapOf<String, Long>()
        DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            val count = input.readInt()
            repeat(count) {
                val squadId = input.readUTF()
                val clock = input.readLong()
                squadClocks[squadId] = clock
            }
            // Read public key — backward compatible with old format (no key)
            val publicKey = if (input.available() > 0) {
                val keySize = input.readInt()
                if (keySize > 0) ByteArray(keySize).also { input.readFully(it) }
                else ByteArray(0)
            } else {
                ByteArray(0)
            }
            return SyncOfferPayload(squadClocks, publicKey)
        }
    }

    // ── SQUAD_META ────────────────────────────────────────────────────
    // Propagates squad lifecycle events through the mesh so devices
    // that weren't online during creation can learn about squads.

    enum class SquadAction { CREATE, JOIN, LEAVE }

    data class SquadMetaPayload(
        val action: SquadAction,
        val squadName: String,
        val inviteCode: String,
        val memberName: String
    )

    fun encodeSquadMeta(payload: SquadMetaPayload): ByteArray {
        val baos = ByteArrayOutputStream()
        DataOutputStream(baos).use { out ->
            out.writeUTF(payload.action.name)
            out.writeUTF(payload.squadName)
            out.writeUTF(payload.inviteCode)
            out.writeUTF(payload.memberName)
        }
        return baos.toByteArray()
    }

    fun decodeSquadMeta(bytes: ByteArray): SquadMetaPayload {
        DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            return SquadMetaPayload(
                action = SquadAction.valueOf(input.readUTF()),
                squadName = input.readUTF(),
                inviteCode = input.readUTF(),
                memberName = input.readUTF()
            )
        }
    }
}

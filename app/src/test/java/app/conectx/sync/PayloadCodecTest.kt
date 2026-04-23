package app.conectx.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PayloadCodecTest {

    // ── CHAT ──────────────────────────────────────────────────────────

    @Test
    fun `chat round-trip preserves all fields`() {
        val encoded = PayloadCodec.encodeChat("Juan", "Vamos Mexico!")
        val decoded = PayloadCodec.decodeChat(encoded)

        assertEquals("Juan", decoded.authorName)
        assertEquals("Vamos Mexico!", decoded.text)
    }

    @Test
    fun `chat with unicode preserves text`() {
        val encoded = PayloadCodec.encodeChat("Carlos", "Gol!!!")
        val decoded = PayloadCodec.decodeChat(encoded)

        assertEquals("Carlos", decoded.authorName)
        assertEquals("Gol!!!", decoded.text)
    }

    @Test
    fun `chat with empty text`() {
        val encoded = PayloadCodec.encodeChat("User", "")
        val decoded = PayloadCodec.decodeChat(encoded)

        assertEquals("User", decoded.authorName)
        assertEquals("", decoded.text)
    }

    // ── SQUAD_META ────────────────────────────────────────────────────

    @Test
    fun `squadMeta round-trip CREATE`() {
        val meta = PayloadCodec.SquadMetaPayload(
            action = PayloadCodec.SquadAction.CREATE,
            squadName = "Azteca Crew",
            inviteCode = "AZT-7K3",
            memberName = "Juan"
        )
        val encoded = PayloadCodec.encodeSquadMeta(meta)
        val decoded = PayloadCodec.decodeSquadMeta(encoded)

        assertEquals(PayloadCodec.SquadAction.CREATE, decoded.action)
        assertEquals("Azteca Crew", decoded.squadName)
        assertEquals("AZT-7K3", decoded.inviteCode)
        assertEquals("Juan", decoded.memberName)
    }

    @Test
    fun `squadMeta round-trip JOIN`() {
        val meta = PayloadCodec.SquadMetaPayload(
            action = PayloadCodec.SquadAction.JOIN,
            squadName = "Team B",
            inviteCode = "TB-123",
            memberName = "Carlos"
        )
        val encoded = PayloadCodec.encodeSquadMeta(meta)
        val decoded = PayloadCodec.decodeSquadMeta(encoded)

        assertEquals(PayloadCodec.SquadAction.JOIN, decoded.action)
        assertEquals("Carlos", decoded.memberName)
    }

    @Test
    fun `squadMeta round-trip LEAVE`() {
        val meta = PayloadCodec.SquadMetaPayload(
            action = PayloadCodec.SquadAction.LEAVE,
            squadName = "Squad X",
            inviteCode = "SX-999",
            memberName = "Ana"
        )
        val encoded = PayloadCodec.encodeSquadMeta(meta)
        val decoded = PayloadCodec.decodeSquadMeta(encoded)

        assertEquals(PayloadCodec.SquadAction.LEAVE, decoded.action)
        assertEquals("Ana", decoded.memberName)
    }

    // ── SYNC_OFFER ────────────────────────────────────────────────────

    @Test
    fun `syncOffer round-trip with empty map`() {
        val encoded = PayloadCodec.encodeSyncOffer(emptyMap())
        val decoded = PayloadCodec.decodeSyncOffer(encoded)

        assertTrue(decoded.squadClocks.isEmpty())
    }

    @Test
    fun `syncOffer round-trip with multiple squads`() {
        val clocks = mapOf("squad-1" to 5L, "squad-2" to 12L, "squad-3" to 0L)
        val encoded = PayloadCodec.encodeSyncOffer(clocks)
        val decoded = PayloadCodec.decodeSyncOffer(encoded)

        assertEquals(3, decoded.squadClocks.size)
        assertEquals(5L, decoded.squadClocks["squad-1"])
        assertEquals(12L, decoded.squadClocks["squad-2"])
        assertEquals(0L, decoded.squadClocks["squad-3"])
    }
}

package app.conectx.transport.ble

import java.util.UUID

/**
 * BLE GATT constants matching iOS BLEConstants.swift exactly.
 *
 * Both platforms advertise/scan the same service UUID and use the same
 * characteristic layout, enabling cross-platform mesh communication.
 *
 * UUID scheme: C0EC10xx-0000-0000-0000-434F4E454354
 *   "C0EC" = Conectx prefix, "434F4E454354" = "CONECT" in ASCII
 */
object BleConstants {

    // ── GATT Service & Characteristics ────────────────────────────────

    /** Primary Conectx BLE service — all devices advertise and scan for this. */
    val SERVICE_UUID: UUID =
        UUID.fromString("C0EC1001-0000-0000-0000-434F4E454354")

    /** Write characteristic — centrals send messages by writing here. */
    val WRITE_CHARACTERISTIC_UUID: UUID =
        UUID.fromString("C0EC1002-0000-0000-0000-434F4E454354")

    /** Notify characteristic — peripherals push messages to subscribed centrals. */
    val NOTIFY_CHARACTERISTIC_UUID: UUID =
        UUID.fromString("C0EC1003-0000-0000-0000-434F4E454354")

    /** Standard Client Characteristic Configuration Descriptor UUID.
     *  Required on Android for notify characteristics (iOS adds it automatically). */
    val CCCD_UUID: UUID =
        UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    // ── MTU ───────────────────────────────────────────────────────────

    /** MTU we request from the remote device. iOS typically accepts 512. */
    const val PREFERRED_MTU = 517

    /** Usable payload per ATT write = negotiated MTU minus 3 bytes ATT header. */
    const val ATT_HEADER_SIZE = 3

    /** Absolute minimum MTU per BLE spec. */
    const val MIN_MTU = 23

    /** Default usable payload when MTU negotiation hasn't completed yet. */
    const val DEFAULT_PAYLOAD_SIZE = MIN_MTU - ATT_HEADER_SIZE  // 20 bytes

    /** iOS advertises maxMessageSize = 512. Messages under this don't need fragmentation
     *  when MTU negotiation succeeds (512 + 3 ≤ 517). */
    const val MAX_MESSAGE_SIZE = 512

    // ── Reconnection ──────────────────────────────────────────────────

    /** Max auto-reconnect attempts before giving up on a peripheral. */
    const val MAX_RECONNECT_ATTEMPTS = 5

    /** Base delay between reconnect attempts (doubled each attempt). */
    const val RECONNECT_BASE_DELAY_MS = 1_000L

    // ── Endpoint prefix ───────────────────────────────────────────────

    /** Prefix for BLE peer endpoint IDs to avoid collision with Nearby endpoint IDs.
     *  Format: "ble:AA:BB:CC:DD:EE:FF" */
    const val ENDPOINT_PREFIX = "ble:"

    // ── Scan/Advertise timing ─────────────────────────────────────────

    /** How long to wait for scan results before restarting scan (ms). */
    const val SCAN_RESTART_INTERVAL_MS = 30_000L

    /** Stale fragment timeout — discard partial reassembly buffers after this. */
    const val FRAGMENT_TIMEOUT_MS = 10_000L
}

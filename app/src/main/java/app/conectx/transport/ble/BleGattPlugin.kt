@file:Suppress("DEPRECATION") // BLE APIs deprecated in API 33 but required for backward compat (minSdk 26)

package app.conectx.transport.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import app.conectx.domain.model.Peer
import app.conectx.domain.model.SyncRecord
import app.conectx.transport.Connection
import app.conectx.transport.TransportPlugin
import app.conectx.transport.nearby.MeshRouter
import app.conectx.transport.nearby.SyncRecordSerializer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Raw BLE GATT transport plugin for cross-platform mesh with iOS.
 *
 * Runs a dual-role architecture:
 * - **Peripheral** (GATT server + advertiser): accepts connections from
 *   iOS/Android centrals, receives writes on the write characteristic,
 *   sends data via notify on the notify characteristic.
 * - **Central** (scanner + connector): discovers peripherals advertising
 *   the Conectx service UUID, connects, writes to their write characteristic,
 *   subscribes to their notify characteristic.
 *
 * Uses the same service/characteristic UUIDs as iOS CoreBluetooth so both
 * platforms discover and communicate with each other seamlessly.
 *
 * This plugin runs **alongside** NearbyPlugin (not replacing it). Two Android
 * devices may discover each other via both Nearby and BLE — MeshRouter.markSeen()
 * deduplicates at the record UUID level.
 */
@Singleton
@SuppressLint("MissingPermission") // Permissions checked at the UI layer before start()
class BleGattPlugin @Inject constructor(
    @ApplicationContext private val context: Context,
    private val meshRouter: MeshRouter
) : TransportPlugin {

    companion object {
        private const val TAG = "BleGattPlugin"
    }

    private val bluetoothManager: BluetoothManager by lazy {
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    }
    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        bluetoothManager.adapter
    }

    private var localName = "conectx-user"
    private var isRunning = false

    // ── Peripheral state ──────────────────────────────────────────────
    private var gattServer: BluetoothGattServer? = null
    private var advertiser: BluetoothLeAdvertiser? = null
    private var notifyCharacteristic: BluetoothGattCharacteristic? = null

    // Devices that have subscribed to notifications (enabled CCCD)
    private val subscribedCentrals = ConcurrentHashMap.newKeySet<BluetoothDevice>()

    // ── Central state ─────────────────────────────────────────────────
    private var scanner: BluetoothLeScanner? = null

    // deviceAddress → active GATT connection
    private val centralConnections = ConcurrentHashMap<String, BluetoothGatt>()

    // deviceAddress → negotiated ATT payload size (MTU - 3)
    private val negotiatedPayloads = ConcurrentHashMap<String, Int>()

    // deviceAddress → write characteristic on the remote peripheral
    private val remoteWriteChars = ConcurrentHashMap<String, BluetoothGattCharacteristic>()

    // deviceAddress → reconnect attempt count
    private val reconnectAttempts = ConcurrentHashMap<String, Int>()

    // ── Prepared (long) writes from iOS ───────────────────────────────
    // iOS CoreBluetooth splits large .withResponse writes into Prepare Write
    // requests. We accumulate them here and assemble on Execute Write.
    private val preparedWriteBuffers = ConcurrentHashMap<String, ByteArray>()

    // ── Flows ─────────────────────────────────────────────────────────
    private val _discoveredPeers = MutableSharedFlow<Peer>(extraBufferCapacity = 64)
    private val _receivedMessages = MutableSharedFlow<SyncRecord>(extraBufferCapacity = 256)

    // ── TransportPlugin interface ─────────────────────────────────────

    override val isAvailable: Boolean
        get() = isRunning && bluetoothAdapter?.isEnabled == true

    fun configure(userName: String) {
        localName = userName
    }

    override suspend fun start() {
        if (isRunning) return
        val adapter = bluetoothAdapter ?: run {
            Log.w(TAG, "Bluetooth not available on this device")
            return
        }
        if (!adapter.isEnabled) {
            Log.w(TAG, "Bluetooth is disabled")
            return
        }

        isRunning = true
        startPeripheral()
        startCentral()
        Log.d(TAG, "Started — GATT server + scanner as '$localName'")
    }

    override suspend fun stop() {
        if (!isRunning) return
        isRunning = false

        stopCentral()
        stopPeripheral()
        preparedWriteBuffers.clear()
        Log.d(TAG, "Stopped")
    }

    override suspend fun discoverPeers(): Flow<Peer> = _discoveredPeers.asSharedFlow()

    override suspend fun connectToPeer(peer: Peer): Connection {
        // BLE connections are managed automatically by the scanner callback
        return Connection(peer = peer, endpointId = peer.id)
    }

    override suspend fun sendMessage(connection: Connection, record: SyncRecord) {
        sendToDevice(connection.endpointId, record)
    }

    override fun onMessageReceived(): Flow<SyncRecord> = _receivedMessages.asSharedFlow()

    // ── Broadcast (send to ALL connected BLE peers) ───────────────────

    /**
     * Sends a record to every connected BLE peer — both central connections
     * (we write to their write characteristic) and subscribed centrals
     * (we notify via our notify characteristic).
     */
    fun broadcast(record: SyncRecord) {
        val bytes = SyncRecordSerializer.serialize(record)
        meshRouter.markSeen(record.id)

        // Send to peripherals we're connected to as central (write raw bytes)
        for ((address, gatt) in centralConnections) {
            val writeChar = remoteWriteChars[address] ?: continue
            writeChar.value = bytes
            gatt.writeCharacteristic(writeChar)
        }

        // Notify centrals subscribed to our server (send raw bytes)
        notifySubscribers(bytes)
    }

    // ═══════════════════════════════════════════════════════════════════
    // PERIPHERAL ROLE — GATT Server + Advertiser
    // ═══════════════════════════════════════════════════════════════════

    private fun startPeripheral() {
        // Build GATT service with write + notify characteristics
        val service = BluetoothGattService(
            BleConstants.SERVICE_UUID,
            BluetoothGattService.SERVICE_TYPE_PRIMARY
        )

        // Write characteristic: centrals write messages here
        val writeChar = BluetoothGattCharacteristic(
            BleConstants.WRITE_CHARACTERISTIC_UUID,
            BluetoothGattCharacteristic.PROPERTY_WRITE or
                BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
            BluetoothGattCharacteristic.PERMISSION_WRITE
        )

        // Notify characteristic: server pushes messages to subscribed centrals
        val notifyChar = BluetoothGattCharacteristic(
            BleConstants.NOTIFY_CHARACTERISTIC_UUID,
            BluetoothGattCharacteristic.PROPERTY_NOTIFY or
                BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_READ
        )

        // CCCD descriptor — required on Android for the central to enable notifications
        val cccd = BluetoothGattDescriptor(
            BleConstants.CCCD_UUID,
            BluetoothGattDescriptor.PERMISSION_READ or
                BluetoothGattDescriptor.PERMISSION_WRITE
        )
        notifyChar.addDescriptor(cccd)

        service.addCharacteristic(writeChar)
        service.addCharacteristic(notifyChar)

        notifyCharacteristic = notifyChar

        gattServer = bluetoothManager.openGattServer(context, gattServerCallback)?.also { server ->
            server.addService(service)
            Log.d(TAG, "GATT server opened with Conectx service")
        } ?: run {
            Log.e(TAG, "Failed to open GATT server")
            return
        }

        startAdvertising()
    }

    private fun startAdvertising() {
        advertiser = bluetoothAdapter?.bluetoothLeAdvertiser ?: run {
            Log.w(TAG, "BLE advertiser not available")
            return
        }

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setConnectable(true)
            .setTimeout(0) // advertise indefinitely
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .build()

        val data = AdvertiseData.Builder()
            .addServiceUuid(ParcelUuid(BleConstants.SERVICE_UUID))
            .setIncludeDeviceName(false) // save ad space
            .build()

        advertiser?.startAdvertising(settings, data, advertiseCallback)
    }

    private fun stopPeripheral() {
        advertiser?.stopAdvertising(advertiseCallback)
        advertiser = null

        gattServer?.close()
        gattServer = null
        notifyCharacteristic = null
        subscribedCentrals.clear()
    }

    /**
     * Sends data to all centrals that have enabled notifications on our
     * notify characteristic.
     */
    private fun notifySubscribers(data: ByteArray) {
        val server = gattServer ?: return
        val characteristic = notifyCharacteristic ?: return

        for (device in subscribedCentrals) {
            characteristic.value = data
            server.notifyCharacteristicChanged(device, characteristic, false)
        }
    }

    // ── GATT Server Callback ──────────────────────────────────────────

    private val gattServerCallback = object : BluetoothGattServerCallback() {

        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            val address = device.address
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.d(TAG, "Central connected: $address")
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.d(TAG, "Central disconnected: $address")
                    subscribedCentrals.remove(device)
                    negotiatedPayloads.remove(address)
                }
            }
        }

        override fun onMtuChanged(device: BluetoothDevice, mtu: Int) {
            val payloadSize = mtu - BleConstants.ATT_HEADER_SIZE
            negotiatedPayloads[device.address] = payloadSize
            Log.d(TAG, "Server MTU changed: ${device.address} → $mtu (payload=$payloadSize)")
        }

        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray?
        ) {
            if (characteristic.uuid == BleConstants.WRITE_CHARACTERISTIC_UUID && value != null) {
                if (responseNeeded) {
                    gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
                }

                if (preparedWrite) {
                    // iOS long write: accumulate chunks, assemble on Execute Write
                    val existing = preparedWriteBuffers[device.address] ?: ByteArray(0)
                    val combined = ByteArray(maxOf(existing.size, offset + value.size))
                    existing.copyInto(combined)
                    value.copyInto(combined, offset)
                    preparedWriteBuffers[device.address] = combined
                } else {
                    // Single write — process immediately (raw SyncRecord bytes)
                    handleIncomingData(device.address, value)
                }
            } else {
                if (responseNeeded) {
                    gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, 0, null)
                }
            }
        }

        override fun onExecuteWrite(device: BluetoothDevice, requestId: Int, execute: Boolean) {
            if (execute) {
                // Long write complete — process accumulated data
                val data = preparedWriteBuffers.remove(device.address)
                if (data != null) {
                    handleIncomingData(device.address, data)
                }
            } else {
                // Write cancelled
                preparedWriteBuffers.remove(device.address)
            }
            gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
        }

        override fun onDescriptorWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            descriptor: BluetoothGattDescriptor,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray?
        ) {
            if (descriptor.uuid == BleConstants.CCCD_UUID) {
                if (value.contentEquals(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)) {
                    subscribedCentrals.add(device)
                    Log.d(TAG, "Central ${device.address} subscribed to notifications")
                } else if (value.contentEquals(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE)) {
                    subscribedCentrals.remove(device)
                    Log.d(TAG, "Central ${device.address} unsubscribed from notifications")
                }
                if (responseNeeded) {
                    gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
                }
            }
        }

        override fun onCharacteristicReadRequest(
            device: BluetoothDevice,
            requestId: Int,
            offset: Int,
            characteristic: BluetoothGattCharacteristic
        ) {
            // iOS may read the notify characteristic to check availability
            if (characteristic.uuid == BleConstants.NOTIFY_CHARACTERISTIC_UUID) {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, ByteArray(0))
            } else {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, 0, null)
            }
        }
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            Log.d(TAG, "BLE advertising started")
        }

        override fun onStartFailure(errorCode: Int) {
            Log.e(TAG, "BLE advertising failed: errorCode=$errorCode")
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // CENTRAL ROLE — Scanner + Connector
    // ═══════════════════════════════════════════════════════════════════

    private fun startCentral() {
        scanner = bluetoothAdapter?.bluetoothLeScanner ?: run {
            Log.w(TAG, "BLE scanner not available")
            return
        }

        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(BleConstants.SERVICE_UUID))
            .build()

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
            .build()

        scanner?.startScan(listOf(filter), settings, scanCallback)
        Log.d(TAG, "BLE scanning started")
    }

    private fun stopCentral() {
        scanner?.stopScan(scanCallback)
        scanner = null

        for ((_, gatt) in centralConnections) {
            gatt.close()
        }
        centralConnections.clear()
        remoteWriteChars.clear()
        reconnectAttempts.clear()
    }

    // ── Scan Callback ─────────────────────────────────────────────────

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device ?: return
            val address = device.address
            val endpointId = BleConstants.ENDPOINT_PREFIX + address

            // Already connected or connecting
            if (centralConnections.containsKey(address)) return

            Log.d(TAG, "Discovered BLE peripheral: $address (rssi=${result.rssi})")

            val peer = Peer(
                id = endpointId,
                displayName = device.name ?: "BLE-$address",
                squadId = "",
                isConnected = false,
                lastSeen = System.currentTimeMillis()
            )
            _discoveredPeers.tryEmit(peer)

            // Auto-connect
            connectToPeripheral(device)
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "BLE scan failed: errorCode=$errorCode")
        }
    }

    // ── GATT Client Callback ──────────────────────────────────────────

    private fun connectToPeripheral(device: BluetoothDevice) {
        val address = device.address
        if (centralConnections.containsKey(address)) return

        Log.d(TAG, "Connecting to peripheral: $address")
        // autoConnect=false for faster initial connection
        device.connectGatt(context, false, object : BluetoothGattCallback() {

            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        Log.d(TAG, "Connected to peripheral: $address")
                        centralConnections[address] = gatt
                        reconnectAttempts.remove(address)
                        // Request high MTU first, then discover services on MTU callback
                        gatt.requestMtu(BleConstants.PREFERRED_MTU)
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        Log.d(TAG, "Disconnected from peripheral: $address")
                        centralConnections.remove(address)
                        remoteWriteChars.remove(address)
                        negotiatedPayloads.remove(address)
                        gatt.close()

                        if (isRunning) {
                            attemptReconnect(device)
                        }
                    }
                }
            }

            override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    val payloadSize = mtu - BleConstants.ATT_HEADER_SIZE
                    negotiatedPayloads[address] = payloadSize
                    Log.d(TAG, "Client MTU negotiated: $address → $mtu (payload=$payloadSize)")
                } else {
                    Log.w(TAG, "MTU negotiation failed for $address, using default")
                }
                // Discover services regardless of MTU result
                gatt.discoverServices()
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    Log.w(TAG, "Service discovery failed for $address: status=$status")
                    return
                }

                val service = gatt.getService(BleConstants.SERVICE_UUID)
                if (service == null) {
                    Log.w(TAG, "Conectx service not found on $address")
                    return
                }

                // Cache the remote write characteristic for sending messages
                val writeChar = service.getCharacteristic(BleConstants.WRITE_CHARACTERISTIC_UUID)
                if (writeChar != null) {
                    // Use WRITE_TYPE_DEFAULT (.withResponse) — required for long writes
                    // that exceed MTU. BLE stack handles segmentation automatically.
                    writeChar.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                    remoteWriteChars[address] = writeChar
                    Log.d(TAG, "Found write characteristic on $address")
                }

                // Subscribe to the remote notify characteristic
                val notifyChar = service.getCharacteristic(BleConstants.NOTIFY_CHARACTERISTIC_UUID)
                if (notifyChar != null) {
                    gatt.setCharacteristicNotification(notifyChar, true)

                    // Write to CCCD to enable notifications on the remote side
                    val cccd = notifyChar.getDescriptor(BleConstants.CCCD_UUID)
                    if (cccd != null) {
                        cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        gatt.writeDescriptor(cccd)
                    }
                    Log.d(TAG, "Subscribed to notify characteristic on $address")
                }
            }

            @Deprecated("Required for API < 33 compat", ReplaceWith("onCharacteristicChanged(gatt, characteristic, value)"))
            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic
            ) {
                // Incoming notification from a peripheral's notify characteristic
                if (characteristic.uuid == BleConstants.NOTIFY_CHARACTERISTIC_UUID) {
                    val value = characteristic.value ?: return
                    handleIncomingData(address, value)
                }
            }
        })
    }

    // ── Reconnection ──────────────────────────────────────────────────

    private fun attemptReconnect(device: BluetoothDevice) {
        val address = device.address
        val attempts = reconnectAttempts.getOrDefault(address, 0)

        if (attempts >= BleConstants.MAX_RECONNECT_ATTEMPTS) {
            Log.d(TAG, "Max reconnect attempts reached for $address, giving up")
            reconnectAttempts.remove(address)
            return
        }

        reconnectAttempts[address] = attempts + 1
        val delay = BleConstants.RECONNECT_BASE_DELAY_MS * (1L shl attempts)
        Log.d(TAG, "Reconnecting to $address in ${delay}ms (attempt ${attempts + 1})")

        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            if (isRunning && !centralConnections.containsKey(address)) {
                connectToPeripheral(device)
            }
        }, delay)
    }

    // ═══════════════════════════════════════════════════════════════════
    // SHARED — Fragment handling and data flow
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Called when a complete message arrives from either role:
     * - Peripheral: a central wrote to our write characteristic
     * - Central: a peripheral notified us on its notify characteristic
     *
     * Data is raw SyncRecord bytes (no fragmentation header) — same format
     * iOS sends. Deserializes, deduplicates via MeshRouter, emits to the
     * received messages flow, and gossip-relays to other BLE peers.
     */
    private fun handleIncomingData(deviceAddress: String, data: ByteArray) {
        try {
            val record = SyncRecordSerializer.deserialize(data)
            val endpointId = BleConstants.ENDPOINT_PREFIX + deviceAddress

            if (meshRouter.markSeen(record.id)) {
                _receivedMessages.tryEmit(record)

                // Gossip relay: send to all other BLE peers
                relayToOthers(endpointId, data)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to deserialize BLE message from $deviceAddress (${data.size} bytes)", e)
        }
    }

    /**
     * Relays raw bytes to all connected BLE peers except the sender.
     * This creates multi-hop mesh propagation within BLE range.
     */
    private fun relayToOthers(senderEndpointId: String, data: ByteArray) {
        // Relay to peripherals we're connected to as central
        for ((address, gatt) in centralConnections) {
            val peerEndpoint = BleConstants.ENDPOINT_PREFIX + address
            if (peerEndpoint == senderEndpointId) continue

            val writeChar = remoteWriteChars[address] ?: continue
            writeChar.value = data
            gatt.writeCharacteristic(writeChar)
        }

        // Relay to subscribed centrals via notify (exclude sender)
        val server = gattServer ?: return
        val characteristic = notifyCharacteristic ?: return

        for (device in subscribedCentrals) {
            val peerEndpoint = BleConstants.ENDPOINT_PREFIX + device.address
            if (peerEndpoint == senderEndpointId) continue

            characteristic.value = data
            server.notifyCharacteristicChanged(device, characteristic, false)
        }
    }

    /**
     * Sends a serialized record to a specific BLE device (by endpoint ID).
     */
    private fun sendToDevice(endpointId: String, record: SyncRecord) {
        val bytes = SyncRecordSerializer.serialize(record)
        meshRouter.markSeen(record.id)

        if (!endpointId.startsWith(BleConstants.ENDPOINT_PREFIX)) return
        val address = endpointId.removePrefix(BleConstants.ENDPOINT_PREFIX)

        // Try as central connection first
        val gatt = centralConnections[address]
        val writeChar = remoteWriteChars[address]
        if (gatt != null && writeChar != null) {
            writeChar.value = bytes
            gatt.writeCharacteristic(writeChar)
            return
        }

        // Fall back to notify if this device is a subscribed central
        val server = gattServer ?: return
        val characteristic = notifyCharacteristic ?: return
        val device = subscribedCentrals.find { it.address == address } ?: return
        characteristic.value = bytes
        server.notifyCharacteristicChanged(device, characteristic, false)
    }
}

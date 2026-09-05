package com.rescuenet.app.data.mesh

import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.content.pm.PackageManager
import android.os.ParcelUuid
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Real BLE peer discovery + relay, scoped honestly to what standard phone BLE can actually
 * do (Part 7): tens-to-~100m range per hop, one small message per GATT write, multi-hop via
 * repeated relay rather than long range. This is the "closest technically valid prototype"
 * called for when the ideal (long-range mesh hardware) isn't available — see the Part 7
 * architecture doc for the LoRa/satellite integration points this is designed to be
 * replaced/supplemented by.
 *
 * Protocol, deliberately simple for a prototype:
 *  1. Every device advertises the RescueNet service UUID and runs a GATT server exposing
 *     its outbox manifest (message UUIDs it holds) and a write-only push characteristic.
 *  2. Every device also scans for that service UUID. On discovering a peer, it connects as
 *     GATT client and pushes any locally queued message to it.
 *  3. Received pushes are surfaced via [observeIncomingMessages] for [MeshRelayManager] to
 *     persist, re-broadcast (hop count permitting), and eventually hand to the sync queue.
 *     Duplicate delivery is expected and handled by UUID dedup at the persistence layer,
 *     not by this transport.
 */
@Singleton
class BleTransportProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) : TransportProvider {

    override val kind = TransportKind.BLE

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val bluetoothManager by lazy { context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager }
    private val adapter get() = bluetoothManager?.adapter

    private var advertiser: BluetoothLeAdvertiser? = null
    private var scanner: BluetoothLeScanner? = null
    private var gattServer: BluetoothGattServer? = null
    private var running = false

    private val peers = ConcurrentHashMap<String, PeerInfo>()
    private val _peersFlow = MutableStateFlow<List<PeerInfo>>(emptyList())
    private val _incoming = MutableSharedFlow<NetworkMessagePayload>(extraBufferCapacity = 64)

    /** Set by MeshRelayManager so the GATT server/client can see what this device already
     *  holds — injecting behavior rather than a repository dependency keeps this class free
     *  of a circular dependency on the persistence layer. */
    var localOutboxProvider: suspend () -> List<NetworkMessagePayload> = { emptyList() }

    override fun isSupported(): Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE) && adapter != null

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private fun permissionOkOrLegacy(permission: String): Boolean =
        hasPermission(permission) || android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S

    private fun canUseBluetooth(): Boolean =
        permissionOkOrLegacy(android.Manifest.permission.BLUETOOTH_SCAN) &&
            permissionOkOrLegacy(android.Manifest.permission.BLUETOOTH_ADVERTISE) &&
            permissionOkOrLegacy(android.Manifest.permission.BLUETOOTH_CONNECT)

    override fun start() {
        if (running) return
        if (!isSupported() || !canUseBluetooth()) return
        val btAdapter = adapter ?: return
        if (!btAdapter.isEnabled) return

        running = true
        startGattServer()
        startAdvertising(btAdapter)
        startScanning(btAdapter)
        startStalePeerPruning()
    }

    override fun stop() {
        if (!running) return
        running = false
        try { advertiser?.stopAdvertising(advertiseCallback) } catch (e: SecurityException) { }
        try { scanner?.stopScan(scanCallback) } catch (e: SecurityException) { }
        try { gattServer?.close() } catch (e: SecurityException) { }
        gattServer = null
        peers.clear()
        _peersFlow.value = emptyList()
    }

    override fun observePeers(): Flow<List<PeerInfo>> = _peersFlow

    override fun observeIncomingMessages(): Flow<NetworkMessagePayload> = _incoming.asSharedFlow()

    override suspend fun broadcast(message: NetworkMessagePayload) {
        // Opportunistic immediate push to everyone currently known, so a freshly created
        // report doesn't have to wait for the next scan-triggered exchange in connectAndSync().
        if (!canUseBluetooth()) return
        peers.keys.toList().forEach { address ->
            val device = adapter?.getRemoteDevice(address) ?: return@forEach
            try {
                pushMessageTo(device, message)
            } catch (e: SecurityException) {
                // Permission revoked mid-flight — skip this peer, others may still succeed.
            }
        }
    }

    // ---------------- Advertising ----------------

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartFailure(errorCode: Int) {
            // Non-fatal: this node simply won't be visible to others until the next attempt.
        }
    }

    private fun startAdvertising(btAdapter: BluetoothAdapter) {
        advertiser = btAdapter.bluetoothLeAdvertiser ?: return
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED) // battery-aware, Part 30
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
            .setConnectable(true)
            .build()
        val data = AdvertiseData.Builder()
            .addServiceUuid(ParcelUuid(BleMeshProtocol.SERVICE_UUID))
            .setIncludeDeviceName(false) // avoid leaking any user-identifying device name
            .build()
        try {
            advertiser?.startAdvertising(settings, data, advertiseCallback)
        } catch (e: SecurityException) { }
    }

    // ---------------- Scanning ----------------

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val address = result.device.address ?: return
            val now = System.currentTimeMillis()
            val isNewPeer = !peers.containsKey(address)
            peers[address] = PeerInfo(address, TransportKind.BLE, result.rssi, now)
            _peersFlow.value = peers.values.toList()

            if (isNewPeer) {
                scope.launch { connectAndSync(result.device) }
            }
        }
        override fun onScanFailed(errorCode: Int) {
            // Non-fatal: this node simply won't discover others until the next attempt.
        }
    }

    private fun startScanning(btAdapter: BluetoothAdapter) {
        scanner = btAdapter.bluetoothLeScanner ?: return
        val filter = ScanFilter.Builder().setServiceUuid(ParcelUuid(BleMeshProtocol.SERVICE_UUID)).build()
        // Battery-aware default (Part 30); MeshRelayManager may request more aggressive
        // scanning by restarting with SCAN_MODE_LOW_LATENCY while an active queue is pending.
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_POWER).build()
        try {
            scanner?.startScan(listOf(filter), settings, scanCallback)
        } catch (e: SecurityException) { }
    }

    private fun startStalePeerPruning() {
        scope.launch {
            while (running) {
                delay(10_000)
                val now = System.currentTimeMillis()
                val before = peers.size
                peers.entries.removeAll { now - it.value.lastSeenEpochMs > BleMeshProtocol.STALE_PEER_TIMEOUT_MS }
                if (peers.size != before) _peersFlow.value = peers.values.toList()
            }
        }
    }

    // ---------------- GATT client (push outbox to a newly seen peer) ----------------

    private suspend fun connectAndSync(device: BluetoothDevice) {
        if (!canUseBluetooth()) return
        try {
            val localOutbox = localOutboxProvider()
            if (localOutbox.isEmpty()) return // nothing to offer this peer right now

            // Prototype simplification: push our outbox and rely on UUID dedup at the
            // persistence layer (MeshRelayManager) to ignore anything the peer already has,
            // rather than reading the peer's manifest characteristic first to compute a diff.
            // A production build would do the manifest-diff to save radio time; noted as a
            // follow-up, not implemented here, for prototype clarity.
            localOutbox.take(BleMeshProtocol.MAX_ADVERTISED_IDS).forEach { pushMessageTo(device, it) }
        } catch (e: SecurityException) {
        } catch (e: Exception) {
            // Connection dropped, GATT busy, etc. — the next scan cycle retries naturally.
        }
    }

    private suspend fun pushMessageTo(device: BluetoothDevice, message: NetworkMessagePayload) {
        suspendCancellableCoroutine<Unit> { cont ->
            var gattRef: BluetoothGatt? = null
            val callback = object : BluetoothGattCallback() {
                override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
                    when (newState) {
                        BluetoothProfile.STATE_CONNECTED -> {
                            try { g.discoverServices() } catch (e: SecurityException) { }
                        }
                        BluetoothProfile.STATE_DISCONNECTED -> {
                            if (cont.isActive) cont.resume(Unit)
                            try { g.close() } catch (e: SecurityException) { }
                        }
                    }
                }
                override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
                    val characteristic = g.getService(BleMeshProtocol.SERVICE_UUID)
                        ?.getCharacteristic(BleMeshProtocol.PUSH_MESSAGE_CHARACTERISTIC_UUID)
                    if (characteristic == null) {
                        try { g.disconnect() } catch (e: SecurityException) { }
                        return
                    }
                    characteristic.value = MeshWireFormat.serialize(message)
                    try {
                        g.writeCharacteristic(characteristic)
                    } catch (e: SecurityException) {
                        try { g.disconnect() } catch (e2: SecurityException) { }
                    }
                }
                override fun onCharacteristicWrite(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
                    try { g.disconnect() } catch (e: SecurityException) { }
                }
            }
            try {
                gattRef = device.connectGatt(context, false, callback)
            } catch (e: SecurityException) {
                if (cont.isActive) cont.resume(Unit)
            }
            cont.invokeOnCancellation { try { gattRef?.close() } catch (e: SecurityException) { } }
        }
    }

    // ---------------- GATT server (answers other devices connecting to us) ----------------

    private fun startGattServer() {
        val callback = object : BluetoothGattServerCallback() {
            override fun onCharacteristicWriteRequest(
                device: BluetoothDevice, requestId: Int, characteristic: BluetoothGattCharacteristic,
                preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray,
            ) {
                if (characteristic.uuid == BleMeshProtocol.PUSH_MESSAGE_CHARACTERISTIC_UUID) {
                    MeshWireFormat.deserialize(value)?.let { message ->
                        if (message.ttl > 0) _incoming.tryEmit(message)
                    }
                }
                if (responseNeeded) {
                    try {
                        gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, null)
                    } catch (e: SecurityException) { }
                }
            }

            override fun onCharacteristicReadRequest(device: BluetoothDevice, requestId: Int, offset: Int, characteristic: BluetoothGattCharacteristic) {
                if (characteristic.uuid == BleMeshProtocol.OUTBOX_MANIFEST_CHARACTERISTIC_UUID) {
                    val manifest = runBlocking {
                        localOutboxProvider().take(BleMeshProtocol.MAX_ADVERTISED_IDS).joinToString(",") { it.messageUuid }
                    }
                    try {
                        gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, manifest.toByteArray())
                    } catch (e: SecurityException) { }
                }
            }
        }

        try {
            gattServer = bluetoothManager?.openGattServer(context, callback)
            val service = BluetoothGattService(BleMeshProtocol.SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)
            service.addCharacteristic(
                BluetoothGattCharacteristic(
                    BleMeshProtocol.OUTBOX_MANIFEST_CHARACTERISTIC_UUID,
                    BluetoothGattCharacteristic.PROPERTY_READ,
                    BluetoothGattCharacteristic.PERMISSION_READ,
                )
            )
            service.addCharacteristic(
                BluetoothGattCharacteristic(
                    BleMeshProtocol.PUSH_MESSAGE_CHARACTERISTIC_UUID,
                    BluetoothGattCharacteristic.PROPERTY_WRITE,
                    BluetoothGattCharacteristic.PERMISSION_WRITE,
                )
            )
            gattServer?.addService(service)
        } catch (e: SecurityException) { }
    }
}

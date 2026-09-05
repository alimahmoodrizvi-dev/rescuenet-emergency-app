package com.rescuenet.app.data.mesh

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pManager
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wi-Fi Direct peer discovery only, in this phase. Wi-Fi Direct offers longer range than BLE
 * (best case ~200m, open field) so it's valuable for widening the set of visible nearby
 * nodes shown to the user (Part 4 — "RescueNet Nodes Nearby"), but actual data transfer over
 * Wi-Fi Direct requires negotiating a P2P group and opening a socket, which is materially
 * more code than BLE's GATT write for a single small message.
 *
 * Given BLE already provides a complete, working store-and-forward path (see
 * BleTransportProvider), this class intentionally stops at discovery: peers found here are
 * merged into the node count and peer list, but [broadcast] is a documented no-op. Wiring
 * Wi-Fi Direct sockets for actual relay — useful mainly for larger payloads BLE's small
 * writes aren't a good fit for — is flagged as a Phase 10 follow-up rather than left
 * unmentioned.
 */
@Singleton
class WifiDirectTransportProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) : TransportProvider {

    override val kind = TransportKind.WIFI_DIRECT

    private val manager by lazy { context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager }
    private var channel: WifiP2pManager.Channel? = null
    private var receiver: BroadcastReceiver? = null
    private var running = false

    private val _peersFlow = MutableStateFlow<List<PeerInfo>>(emptyList())
    private val _incoming = MutableSharedFlow<NetworkMessagePayload>(extraBufferCapacity = 1)

    override fun isSupported(): Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI_DIRECT) && manager != null

    private fun hasPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val nearbyWifi = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.NEARBY_WIFI_DEVICES) == PackageManager.PERMISSION_GRANTED
        } else true
        return fine && nearbyWifi
    }

    override fun start() {
        if (running || !isSupported() || !hasPermission()) return
        val mgr = manager ?: return
        channel = mgr.initialize(context, context.mainLooper, null)

        receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (intent.action == WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION) {
                    try {
                        mgr.requestPeers(channel) { peerList ->
                            val now = System.currentTimeMillis()
                            _peersFlow.value = peerList.deviceList.map { device: WifiP2pDevice ->
                                PeerInfo(peerId = device.deviceAddress, transport = TransportKind.WIFI_DIRECT, approxRssi = null, lastSeenEpochMs = now)
                            }
                        }
                    } catch (e: SecurityException) { }
                }
            }
        }
        // API 33+ requires an explicit exported flag for dynamically registered receivers;
        // ContextCompat handles the pre-33 fallback automatically.
        ContextCompat.registerReceiver(
            context, receiver,
            IntentFilter(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        try {
            mgr.discoverPeers(channel, object : WifiP2pManager.ActionListener {
                override fun onSuccess() { }
                override fun onFailure(reasonCode: Int) { /* non-fatal: discovery just won't run this cycle */ }
            })
        } catch (e: SecurityException) { }

        running = true
    }

    override fun stop() {
        if (!running) return
        running = false
        receiver?.let { try { context.unregisterReceiver(it) } catch (e: IllegalArgumentException) { } }
        receiver = null
        try { manager?.stopPeerDiscovery(channel, null) } catch (e: SecurityException) { }
        _peersFlow.value = emptyList()
    }

    override fun observePeers(): Flow<List<PeerInfo>> = _peersFlow

    override fun observeIncomingMessages(): Flow<NetworkMessagePayload> = _incoming.asSharedFlow()

    override suspend fun broadcast(message: NetworkMessagePayload) {
        // Documented no-op — see class doc. BleTransportProvider carries actual message
        // relay for this phase; Wi-Fi Direct here only widens peer visibility.
    }
}

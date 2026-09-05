package com.rescuenet.app.data.mesh

import java.util.Base64
import com.rescuenet.app.data.battery.BatteryAwareScanPolicy
import com.rescuenet.app.data.battery.BatteryLevelProvider
import com.rescuenet.app.data.local.dao.NetworkMessageDao
import com.rescuenet.app.data.local.dao.SyncQueueDao
import com.rescuenet.app.data.local.entity.NetworkMessageEntity
import com.rescuenet.app.data.offline.OfflineQueueManager
import com.rescuenet.app.data.repository.UserRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Ties the transport layer (BLE today; Wi-Fi Direct for peer visibility; LoRa/satellite in
 * the future per Part 7) to local persistence and de-duplication. This is the concrete
 * implementation of the Part 21 pipeline:
 *
 *   Local emergency queue -> Peer-to-peer communication -> Synchronization engine -> Backend
 *
 * De-dup rule (Part 21 — "never duplicate emergency incidents"): the first copy of a given
 * `messageUuid` this device sees wins; every later copy (possibly arriving via a different
 * peer, transport, or hop count) is ignored at the database layer (`insertIfNew`), not just
 * hidden in the UI.
 *
 * Scope note: only compact, text-based report payloads are relayed over the mesh — photos
 * and voice notes are excluded (BLE's small-write model isn't a fit for them; see
 * BleTransportProvider's wire-format comment) and instead sync directly once real internet
 * returns.
 *
 * Phase 10: transports are now started/stopped according to [BatteryAwareScanPolicy] rather
 * than running unconditionally for the app's whole foreground lifetime (Part 30).
 */
@Singleton
class MeshRelayManager @Inject constructor(
    private val bleTransport: BleTransportProvider,
    private val wifiDirectTransport: WifiDirectTransportProvider,
    private val networkMessageDao: NetworkMessageDao,
    private val syncQueueDao: SyncQueueDao,
    private val offlineQueueManager: OfflineQueueManager,
    private val userRepository: UserRepository,
    private val batteryLevelProvider: BatteryLevelProvider,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val transports: List<TransportProvider> = listOf(bleTransport, wifiDirectTransport)
    private var started = false
    private var transportsCurrentlyRunning = false

    fun start() {
        if (started) return
        started = true

        bleTransport.localOutboxProvider = { buildOutboxSnapshot() }

        transports.forEach { transport ->
            scope.launch {
                transport.observeIncomingMessages().collect { message -> handleIncoming(message) }
            }
        }

        scope.launch { startTransportsIfPolicyAllows() }
        startBatteryAwarePolicyLoop()
    }

    fun stop() {
        started = false
        stopTransports()
    }

    /** Union of peers seen across every transport, deduped by peerId — this is the number
     *  shown as "RescueNet Nodes Nearby" on the home screen (Part 4). */
    fun observeNearbyNodeCount(): Flow<Int> =
        combine(transports.map { it.observePeers() }) { peerLists ->
            peerLists.flatMap { it }.map { it.peerId }.distinct().size
        }

    fun observeRelayedMessages(): Flow<List<NetworkMessageEntity>> = networkMessageDao.observeAll()

    /**
     * Called by IncidentRepository/FamilyRepository whenever something is queued locally.
     * Encrypts the plaintext payload (MeshMessageCrypto — see its DEMO-ONLY key warning),
     * stores it, and pushes it out over every available transport immediately, in addition
     * to whatever a newly-discovered peer will pull from [buildOutboxSnapshot] later. Also
     * re-checks the battery policy — a freshly queued report can wake scanning back up even
     * if it had been paused for battery conservation (Part 30: "a queued report always
     * outweighs battery conservation" — see BatteryAwareScanPolicy).
     */
    suspend fun queueForRelay(eventUuid: String, plaintextJson: String) {
        val alreadyQueued = networkMessageDao.exists(eventUuid)
        if (alreadyQueued) return

        val deviceId = userRepository.ensureProfileExists().deviceUuid
        val encrypted = MeshMessageCrypto.encrypt(plaintextJson.toByteArray(Charsets.UTF_8))
        val entity = NetworkMessageEntity(
            messageUuid = eventUuid,
            originDeviceId = deviceId,
            hopCount = 0,
            ttl = BleMeshProtocol.DEFAULT_TTL_HOPS,
            encryptedPayloadBase64 = Base64.getEncoder().encodeToString(encrypted),
            createdAtEpochMs = System.currentTimeMillis(),
        )
        networkMessageDao.insertIfNew(entity)

        startTransportsIfPolicyAllows()

        val payload = entity.toPayload()
        transports.forEach { transport ->
            scope.launch {
                try { transport.broadcast(payload) } catch (e: Exception) { /* best-effort */ }
            }
        }
    }

    // ---------------- Battery-aware duty cycling (Part 30) ----------------

    private fun startBatteryAwarePolicyLoop() {
        scope.launch {
            while (started) {
                delay(60_000) // re-evaluate roughly once a minute — no need to be finer-grained
                if (started) startTransportsIfPolicyAllows(orStopIfNotAllowed = true)
            }
        }
    }

    /** Starts transports if [BatteryAwareScanPolicy] currently allows it; if
     *  [orStopIfNotAllowed] is set (used by the periodic loop, not by one-off callers like
     *  queueForRelay), also stops them when the policy no longer allows scanning. */
    private suspend fun startTransportsIfPolicyAllows(orStopIfNotAllowed: Boolean = false) {
        val hasPending = hasPendingOutbox()
        val allowed = BatteryAwareScanPolicy.shouldScan(batteryLevelProvider.currentState(), hasPending)

        if (allowed && !transportsCurrentlyRunning) {
            transports.forEach { it.start() }
            transportsCurrentlyRunning = true
        } else if (!allowed && orStopIfNotAllowed && transportsCurrentlyRunning) {
            stopTransports()
        }
    }

    private fun stopTransports() {
        transports.forEach { it.stop() }
        transportsCurrentlyRunning = false
    }

    private suspend fun hasPendingOutbox(): Boolean =
        networkMessageDao.getUndelivered().isNotEmpty() || syncQueueDao.getAll().isNotEmpty()

    // ---------------- Incoming message handling ----------------

    private suspend fun handleIncoming(message: NetworkMessagePayload) {
        val entity = NetworkMessageEntity(
            messageUuid = message.messageUuid,
            originDeviceId = message.originDeviceId,
            hopCount = message.hopCount,
            ttl = message.ttl,
            encryptedPayloadBase64 = Base64.getEncoder().encodeToString(message.encryptedBytes),
            createdAtEpochMs = message.createdAtEpochMs,
        )
        val rowId = networkMessageDao.insertIfNew(entity)
        val isNewToUs = rowId != -1L
        if (!isNewToUs) return // dedup: we already had this event, don't relay or re-sync it

        // A message we've never seen before might mean this device is now the one that can
        // finally get it to the internet — nudge the sync worker to check.
        offlineQueueManager.scheduleSyncAsSoonAsOnline()

        // Continue the gossip if the hop budget allows (Part 7's bounded flood, not infinite relay).
        if (message.ttl > 0) {
            transports.forEach { transport ->
                scope.launch {
                    try { transport.broadcast(message) } catch (e: Exception) { }
                }
            }
        }
    }

    /** What this device offers newly-discovered BLE peers: everything not yet confirmed
     *  delivered to the backend, so the gossip keeps propagating until *some* device in the
     *  chain reaches connectivity (Part 21). */
    private suspend fun buildOutboxSnapshot(): List<NetworkMessagePayload> =
        networkMessageDao.getUndelivered().map { it.toPayload() }

    private fun NetworkMessageEntity.toPayload() = NetworkMessagePayload(
        messageUuid = messageUuid,
        originDeviceId = originDeviceId,
        hopCount = hopCount,
        ttl = ttl,
        createdAtEpochMs = createdAtEpochMs,
        encryptedBytes = Base64.getDecoder().decode(encryptedPayloadBase64),
    )
}

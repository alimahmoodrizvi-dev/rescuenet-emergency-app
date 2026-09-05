package com.rescuenet.app.data.mesh

import kotlinx.coroutines.flow.Flow

/** A nearby device seen over any transport. `transport` lets the UI/debug screens show
 *  which radio found it; relay logic treats all transports uniformly. */
data class PeerInfo(
    val peerId: String,
    val transport: TransportKind,
    val approxRssi: Int?,
    val lastSeenEpochMs: Long,
)

enum class TransportKind { BLE, WIFI_DIRECT, LORA, SATELLITE }

/** Wire format for a relayed emergency event. Mirrors the Part 9 `NetworkMessages` schema:
 *  `messageUuid` is the de-duplication key end-to-end (device -> mesh -> backend). */
data class NetworkMessagePayload(
    val messageUuid: String,
    val originDeviceId: String,
    val hopCount: Int,
    val ttl: Int,
    val createdAtEpochMs: Long,
    val encryptedBytes: ByteArray,
) {
    override fun equals(other: Any?): Boolean = other is NetworkMessagePayload && other.messageUuid == messageUuid
    override fun hashCode(): Int = messageUuid.hashCode()
}

/**
 * Abstraction over a physical relay radio (Part 7). Today only [TransportKind.BLE] and a
 * discovery-only [TransportKind.WIFI_DIRECT] implementation exist; LoRa and satellite
 * gateways (Part 7 — "future integration") plug in later as additional implementations of
 * this same interface without any change to [MeshRelayManager] or the repositories that
 * feed it.
 *
 * Every implementation must be honest about range: this is short-range, multi-hop,
 * store-and-forward relay between nearby phones — not long-distance communication.
 */
interface TransportProvider {
    val kind: TransportKind

    /** True if the device hardware/OS supports this transport at all. */
    fun isSupported(): Boolean

    /** Begins advertising this device's presence and scanning for peers. Safe to call
     *  repeatedly; implementations should no-op if already running. */
    fun start()

    fun stop()

    fun observePeers(): Flow<List<PeerInfo>>

    /** Best-effort push of a message to whichever peers are currently reachable. Does not
     *  guarantee delivery — callers rely on [MeshRelayManager]'s persisted queue + gossip
     *  re-attempts, not on this call succeeding synchronously. */
    suspend fun broadcast(message: NetworkMessagePayload)

    fun observeIncomingMessages(): Flow<NetworkMessagePayload>
}

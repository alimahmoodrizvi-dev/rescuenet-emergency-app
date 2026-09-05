package com.rescuenet.app.data.mesh

import java.util.UUID

/**
 * BLE identifiers for the RescueNet relay protocol. The service UUID is what lets phones
 * recognize each other as RescueNet nodes (vs. every other BLE device around them) during
 * scanning/advertising.
 */
object BleMeshProtocol {
    val SERVICE_UUID: UUID = UUID.fromString("6e400000-b5a3-f393-e0a9-e50e24dcca9e")

    /** Peripheral (GATT server) exposes the peer's outbox as a comma-separated list of
     *  message UUIDs it currently holds, capped to [MAX_ADVERTISED_IDS] for demo simplicity —
     *  a production build would chunk this over multiple reads instead of truncating. */
    val OUTBOX_MANIFEST_CHARACTERISTIC_UUID: UUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e")

    /** Central (GATT client) writes a single serialized [NetworkMessagePayload] here to push
     *  a message the peripheral doesn't yet have. One message per write for demo simplicity;
     *  a production build would negotiate MTU and support larger/chunked payloads (e.g. for
     *  photos), which is why media is excluded from mesh relay in this phase — see
     *  MeshRelayManager. */
    val PUSH_MESSAGE_CHARACTERISTIC_UUID: UUID = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e")

    const val MAX_ADVERTISED_IDS = 40
    const val DEFAULT_TTL_HOPS = 8
    const val STALE_PEER_TIMEOUT_MS = 30_000L
}

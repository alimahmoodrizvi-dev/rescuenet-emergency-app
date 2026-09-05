package com.rescuenet.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Local mirror of the Part 9 `NetworkMessages` table — every relayed emergency event this
 * device has originated, received, or is holding to forward on to the next peer/backend.
 * `messageUuid` is the mesh-wide de-duplication key (see BleTransportProvider's wire format
 * and MeshRelayManager's dedup-on-insert).
 */
@Entity(tableName = "network_messages")
data class NetworkMessageEntity(
    @PrimaryKey val messageUuid: String,
    val originDeviceId: String,
    val hopCount: Int,
    val ttl: Int,
    val encryptedPayloadBase64: String,
    val createdAtEpochMs: Long,
    val deliveredToBackendAtEpochMs: Long? = null,
)

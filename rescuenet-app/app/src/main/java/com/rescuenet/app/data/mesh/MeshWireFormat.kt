package com.rescuenet.app.data.mesh

/**
 * Compact, delimiter-based serialization for fitting one [NetworkMessagePayload] into a
 * single BLE characteristic write — not a general-purpose format, just enough for the
 * prototype's one-message-per-write model (see BleMeshProtocol's doc comment on why photos/
 * voice notes are excluded from mesh relay entirely).
 *
 * Extracted out of [BleTransportProvider] into its own object (no Android/Bluetooth API
 * dependency) so the header-parsing and hop-count/TTL bookkeeping — the part most worth
 * getting right, since a bug here would silently corrupt or infinite-loop the mesh — can be
 * exercised by plain JVM unit tests (app/src/test/.../MeshWireFormatTest.kt).
 */
object MeshWireFormat {

    fun serialize(m: NetworkMessagePayload): ByteArray {
        val header = "${m.messageUuid}|${m.originDeviceId}|${m.hopCount}|${m.ttl}|${m.createdAtEpochMs}|"
        return header.toByteArray(Charsets.UTF_8) + m.encryptedBytes
    }

    /** Returns null on any malformed input rather than throwing — a corrupt or truncated BLE
     *  write must never crash the receiving device; see the mesh's overall "no fake
     *  functionality" stance in AGENTS/README: a bad packet is silently dropped, not
     *  fabricated into a fake message. */
    fun deserialize(bytes: ByteArray): NetworkMessagePayload? {
        return try {
            val text = String(bytes, Charsets.UTF_8)
            var delimiterCount = 0
            var headerEnd = -1
            for (i in text.indices) {
                if (text[i] == '|') {
                    delimiterCount++
                    if (delimiterCount == 5) { headerEnd = i; break }
                }
            }
            if (headerEnd == -1) return null
            val header = text.substring(0, headerEnd).split("|")
            val bodyStartByteOffset = header.sumOf { it.toByteArray(Charsets.UTF_8).size + 1 }
            NetworkMessagePayload(
                messageUuid = header[0],
                originDeviceId = header[1],
                hopCount = header[2].toInt() + 1, // one more hop as it passes through us
                ttl = header[3].toInt() - 1,       // decrement — enforces Part 7's hop-count bound
                createdAtEpochMs = header[4].toLong(),
                encryptedBytes = bytes.copyOfRange(bodyStartByteOffset, bytes.size),
            )
        } catch (e: Exception) {
            null
        }
    }
}

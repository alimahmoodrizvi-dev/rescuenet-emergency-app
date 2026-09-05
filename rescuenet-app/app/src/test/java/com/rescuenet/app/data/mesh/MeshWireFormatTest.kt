package com.rescuenet.app.data.mesh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** See the execution caveat in OfflineAiHeuristicsTest.kt — applies equally here. */
class MeshWireFormatTest {

    private fun samplePayload(hopCount: Int = 0, ttl: Int = BleMeshProtocol.DEFAULT_TTL_HOPS) = NetworkMessagePayload(
        messageUuid = "evt-1234",
        originDeviceId = "device-abc",
        hopCount = hopCount,
        ttl = ttl,
        createdAtEpochMs = 1_700_000_000_000L,
        encryptedBytes = MeshMessageCrypto.encrypt("test payload".toByteArray(Charsets.UTF_8)),
    )

    @Test
    fun `serialize then deserialize round-trips the message uuid, origin, and timestamp`() {
        val original = samplePayload()
        val bytes = MeshWireFormat.serialize(original)
        val result = MeshWireFormat.deserialize(bytes)

        assertEquals(original.messageUuid, result?.messageUuid)
        assertEquals(original.originDeviceId, result?.originDeviceId)
        assertEquals(original.createdAtEpochMs, result?.createdAtEpochMs)
    }

    @Test
    fun `deserialize increments hop count by one, since it just passed through this device`() {
        val original = samplePayload(hopCount = 2)
        val result = MeshWireFormat.deserialize(MeshWireFormat.serialize(original))

        assertEquals(3, result?.hopCount)
    }

    @Test
    fun `deserialize decrements ttl by one, enforcing the bounded-flood hop limit`() {
        val original = samplePayload(ttl = 5)
        val result = MeshWireFormat.deserialize(MeshWireFormat.serialize(original))

        assertEquals(4, result?.ttl)
    }

    @Test
    fun `a message at ttl 1 decrements to 0, the point at which relay must stop`() {
        val original = samplePayload(ttl = 1)
        val result = MeshWireFormat.deserialize(MeshWireFormat.serialize(original))

        assertEquals(0, result?.ttl)
        // MeshRelayManager/BleTransportProvider check `ttl > 0` before re-broadcasting or
        // even accepting a message — a ttl of exactly 0 here documents the boundary they
        // rely on, not just an implementation detail of this class.
    }

    @Test
    fun `encrypted payload bytes survive the round trip byte-for-byte`() {
        val original = samplePayload()
        val result = MeshWireFormat.deserialize(MeshWireFormat.serialize(original))

        assertEquals(original.encryptedBytes.toList(), result?.encryptedBytes?.toList())
    }

    @Test
    fun `malformed input with too few delimiters returns null instead of throwing`() {
        val garbage = "not-a-valid-header-at-all".toByteArray(Charsets.UTF_8)
        assertNull(MeshWireFormat.deserialize(garbage))
    }

    @Test
    fun `header with non-numeric hop count returns null instead of crashing the app`() {
        val malformed = "evt-1|device-1|not-a-number|8|1700000000000|".toByteArray(Charsets.UTF_8) +
            "somebytes".toByteArray(Charsets.UTF_8)
        assertNull(MeshWireFormat.deserialize(malformed))
    }

    @Test
    fun `empty byte array returns null instead of throwing`() {
        assertNull(MeshWireFormat.deserialize(ByteArray(0)))
    }

    @Test
    fun `a message uuid or device id containing no special characters serializes and parses cleanly`() {
        // Regression guard: the wire format uses '|' as a delimiter, so IDs must never
        // contain '|' — UUIDs and this app's device IDs never do, but this test documents
        // that assumption explicitly rather than leaving it implicit.
        val original = samplePayload().copy(messageUuid = "abc-def-123-456", originDeviceId = "device-xyz-789")
        val result = MeshWireFormat.deserialize(MeshWireFormat.serialize(original))

        assertEquals("abc-def-123-456", result?.messageUuid)
        assertEquals("device-xyz-789", result?.originDeviceId)
    }
}

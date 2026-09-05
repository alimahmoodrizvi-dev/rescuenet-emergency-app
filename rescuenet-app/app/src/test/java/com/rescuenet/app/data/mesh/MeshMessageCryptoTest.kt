package com.rescuenet.app.data.mesh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * See the execution caveat in OfflineAiHeuristicsTest.kt — applies equally here. This class
 * was specifically refactored (android.util.Base64 -> java.util.Base64) to remove its only
 * Android-framework dependency so it genuinely *could* run as a plain JVM test; only the
 * inability to invoke a JVM/Gradle toolchain in this particular sandbox prevented actually
 * doing so here.
 */
class MeshMessageCryptoTest {

    @Test
    fun `encrypt then decrypt round-trips to the original plaintext`() {
        val original = "Mere ghar mein pani aa raha hai. Hum 6 log hain."
        val encrypted = MeshMessageCrypto.encrypt(original.toByteArray(Charsets.UTF_8))
        val decrypted = String(MeshMessageCrypto.decrypt(encrypted), Charsets.UTF_8)

        assertEquals(original, decrypted)
    }

    @Test
    fun `base64 helper functions round-trip`() {
        val original = """{"event_uuid":"abc-123","type":"FLOOD","people_count":6}"""
        val encoded = MeshMessageCrypto.encryptToBase64(original)
        val decoded = MeshMessageCrypto.decryptFromBase64(encoded)

        assertEquals(original, decoded)
    }

    @Test
    fun `encrypting the same plaintext twice produces different ciphertext`() {
        // A random IV per call is what makes this true — if it weren't, an eavesdropper on
        // the mesh could correlate identical reports, which would leak information even
        // without breaking the encryption itself.
        val plaintext = "same message".toByteArray(Charsets.UTF_8)
        val first = MeshMessageCrypto.encrypt(plaintext)
        val second = MeshMessageCrypto.encrypt(plaintext)

        assertNotEquals(first.toList(), second.toList())
    }

    @Test
    fun `decrypting corrupted ciphertext throws rather than silently returning garbage`() {
        // AES-GCM's authentication tag must make tampering detectable — a relayed message
        // that was corrupted or tampered with in transit must fail loudly, not be silently
        // "decrypted" into wrong-but-plausible-looking data.
        val encrypted = MeshMessageCrypto.encrypt("original".toByteArray(Charsets.UTF_8))
        val tampered = encrypted.copyOf().also { it[it.size - 1] = (it[it.size - 1] + 1).toByte() }

        assertThrows(Exception::class.java) {
            MeshMessageCrypto.decrypt(tampered)
        }
    }

    @Test
    fun `empty plaintext round-trips too`() {
        val encrypted = MeshMessageCrypto.encrypt(ByteArray(0))
        val decrypted = MeshMessageCrypto.decrypt(encrypted)

        assertEquals(0, decrypted.size)
    }
}

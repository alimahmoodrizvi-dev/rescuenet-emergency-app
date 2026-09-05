package com.rescuenet.app.data.mesh

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Encrypts relayed message payloads so an intermediate relaying phone can pass a message
 * along without being able to read its contents (Part 11 — "relaying devices cannot read
 * report contents, only route them").
 *
 * Deliberately uses `java.util.Base64` (available since API 26, matching this app's minSdk)
 * rather than `android.util.Base64` — functionally equivalent on-device, but this keeps the
 * class free of any Android-framework dependency, so it can be exercised by plain JVM unit
 * tests (see app/src/test/.../MeshMessageCryptoTest.kt) without Robolectric or a device.
 *
 * ⚠️ DEMO-ONLY LIMITATION, stated plainly per the project's own honesty rules: this uses a
 * single shared AES key baked into the app, which is NOT how this should work in production.
 * A real deployment needs one of:
 *   (a) hybrid encryption to the command center's public key, so only the backend can ever
 *       decrypt relayed reports, or
 *   (b) per-device key exchange (e.g. ECDH) so only the intended recipient can decrypt.
 * The shared-key approach here proves out the "relay without reading" data flow and the
 * AES-GCM mechanics end-to-end, but any relaying RescueNet install could theoretically
 * decrypt with the same baked-in key. Do not ship this as-is — swap `NETWORK_DEMO_KEY` for
 * real key provisioning (hybrid encryption to a real backend keypair, or per-device ECDH)
 * before any real deployment. Unlike the Room encryption-at-rest gap this file used to be
 * grouped with (now fixed — see di/DatabaseModule.kt), this one remains open: it needs
 * backend-side key infrastructure that doesn't exist yet, not just a client-side change.
 */
object MeshMessageCrypto {

    // 256-bit placeholder key — see the DEMO-ONLY warning above.
    private val NETWORK_DEMO_KEY = byteArrayOf(
        0x4e, 0x18, 0x2c, 0x7a, 0x91.toByte(), 0x33, 0x5f, 0x0d,
        0xa2.toByte(), 0x66, 0x1e, 0x88.toByte(), 0x3c, 0x77, 0x09, 0x54,
        0x1f, 0x62, 0xb0.toByte(), 0x2a, 0x45, 0x93.toByte(), 0x0b, 0x6d,
        0xd4.toByte(), 0x38, 0x7c, 0x11, 0x59, 0xe0.toByte(), 0x2f, 0x86.toByte(),
    )
    private const val GCM_TAG_BITS = 128
    private const val IV_BYTES = 12

    fun encrypt(plaintext: ByteArray): ByteArray {
        val iv = ByteArray(IV_BYTES).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(NETWORK_DEMO_KEY, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
        val encrypted = cipher.doFinal(plaintext)
        return iv + encrypted // IV prefixed so decrypt() is self-contained
    }

    fun decrypt(ivAndCiphertext: ByteArray): ByteArray {
        val iv = ivAndCiphertext.copyOfRange(0, IV_BYTES)
        val ciphertext = ivAndCiphertext.copyOfRange(IV_BYTES, ivAndCiphertext.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(NETWORK_DEMO_KEY, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(ciphertext)
    }

    fun encryptToBase64(plaintext: String): String =
        Base64.getEncoder().encodeToString(encrypt(plaintext.toByteArray(Charsets.UTF_8)))

    fun decryptFromBase64(encoded: String): String =
        String(decrypt(Base64.getDecoder().decode(encoded)), Charsets.UTF_8)
}

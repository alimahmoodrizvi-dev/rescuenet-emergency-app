package com.rescuenet.app.data.security

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.SecureRandom
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Generates and stores the passphrase used to encrypt the local Room database at rest
 * (Part 11/19 — sensitive local data must be encrypted). The passphrase itself never leaves
 * the device and is never hard-coded: it's a random 256-bit value generated on first launch,
 * then stored in `EncryptedSharedPreferences`, which wraps it with an AES-256-GCM key held in
 * the Android Keystore — meaning the passphrase is only ever recoverable on this specific
 * device, not by copying the app's files elsewhere.
 *
 * This closes the gap flagged since Phase 3's `di/DatabaseModule.kt`: Room now runs on top
 * of SQLCipher (see `DatabaseModule.provideDatabase`) using the passphrase from here.
 */
@Singleton
class SecurePassphraseProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val encryptedPrefs by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "rescuenet_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    /** Returns the existing passphrase, or generates and persists a new one on first call.
     *  Callers (SQLCipher's SupportFactory) are expected to zero the returned array after
     *  use — see DatabaseModule's `clearPassphrase = true`. */
    fun getOrCreatePassphrase(): ByteArray {
        val existing = encryptedPrefs.getString(PASSPHRASE_KEY, null)
        if (existing != null) return Base64.getDecoder().decode(existing)

        val newPassphrase = ByteArray(32).also { SecureRandom().nextBytes(it) }
        encryptedPrefs.edit()
            .putString(PASSPHRASE_KEY, Base64.getEncoder().encodeToString(newPassphrase))
            .apply()
        return newPassphrase
    }

    companion object {
        private const val PASSPHRASE_KEY = "rescuenet_db_passphrase_v1"
    }
}

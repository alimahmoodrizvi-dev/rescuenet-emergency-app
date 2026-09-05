package com.rescuenet.app.di

import android.content.Context
import androidx.room.Room
import com.rescuenet.app.data.local.RescueNetDatabase
import com.rescuenet.app.data.local.dao.FamilyMemberDao
import com.rescuenet.app.data.local.dao.IncidentDao
import com.rescuenet.app.data.local.dao.NetworkMessageDao
import com.rescuenet.app.data.local.dao.SyncQueueDao
import com.rescuenet.app.data.local.dao.UserProfileDao
import com.rescuenet.app.data.security.SecurePassphraseProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.sqlcipher.database.SupportFactory
import javax.inject.Singleton

/**
 * Phase 10: the local Room database is now SQLCipher-encrypted at rest (Part 11/19),
 * closing the gap flagged since Phase 3. The passphrase comes from
 * [SecurePassphraseProvider] — a random 256-bit value generated on first launch and stored
 * in Keystore-backed EncryptedSharedPreferences, never hard-coded and never transmitted.
 *
 * `clearPassphrase = true` tells SQLCipher's SupportFactory to zero the passphrase byte
 * array in memory immediately after it's consumed to open the database connection, so it
 * doesn't linger in the heap longer than necessary.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
        passphraseProvider: SecurePassphraseProvider,
    ): RescueNetDatabase {
        val passphrase = passphraseProvider.getOrCreatePassphrase()
        val factory = SupportFactory(passphrase, null, /* clearPassphrase = */ true)

        return Room.databaseBuilder(context, RescueNetDatabase::class.java, RescueNetDatabase.DATABASE_NAME)
            .openHelperFactory(factory)
            .fallbackToDestructiveMigration() // acceptable pre-1.0; replace with real migrations before release
            .build()
    }

    @Provides
    fun provideIncidentDao(db: RescueNetDatabase): IncidentDao = db.incidentDao()

    @Provides
    fun provideFamilyMemberDao(db: RescueNetDatabase): FamilyMemberDao = db.familyMemberDao()

    @Provides
    fun provideSyncQueueDao(db: RescueNetDatabase): SyncQueueDao = db.syncQueueDao()

    @Provides
    fun provideUserProfileDao(db: RescueNetDatabase): UserProfileDao = db.userProfileDao()

    @Provides
    fun provideNetworkMessageDao(db: RescueNetDatabase): NetworkMessageDao = db.networkMessageDao()
}

package com.rescuenet.app.data.remote

import com.rescuenet.app.data.repository.UserRepository
import com.rescuenet.app.data.settings.SettingsDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

data class BackendSession(val api: RescueNetApi, val bearerToken: String, val deviceUuid: String)

/**
 * Shared device-auth cache so SyncWorker and any AI/analysis call don't each independently
 * re-register the device. A token is reused for its lifetime (Part 19's backend issues
 * 24h-expiry JWTs by default — see rescuenet-backend/app/config.py) and only refreshed when
 * missing, expired-by-the-backend's-response, or the base URL changes.
 */
@Singleton
class BackendSessionManager @Inject constructor(
    private val backendApiClient: BackendApiClient,
    private val settingsDataStore: SettingsDataStore,
    private val userRepository: UserRepository,
) {
    private val mutex = Mutex()
    private var cached: BackendSession? = null
    private var cachedBaseUrl: String? = null

    /** Returns null if the backend can't be reached right now — callers must treat that as
     *  "AI/sync unavailable," never as a reason to block the emergency-report flow (Part 29). */
    suspend fun getSession(forceRefresh: Boolean = false): BackendSession? = mutex.withLock {
        val baseUrl = settingsDataStore.settings.first().backendBaseUrl
        val existing = cached
        if (!forceRefresh && existing != null && cachedBaseUrl == baseUrl) return@withLock existing

        return@withLock try {
            val api = backendApiClient.apiFor(baseUrl)
            val deviceUuid = userRepository.ensureProfileExists().deviceUuid
            val response = api.registerDevice(
                DeviceRegisterRequestDto(deviceUuid = deviceUuid, osVersion = android.os.Build.VERSION.RELEASE)
            )
            val token = response.body()?.accessToken ?: return@withLock null
            BackendSession(api, "Bearer $token", deviceUuid).also {
                cached = it
                cachedBaseUrl = baseUrl
            }
        } catch (e: Exception) {
            null
        }
    }

    fun invalidate() {
        cached = null
    }
}

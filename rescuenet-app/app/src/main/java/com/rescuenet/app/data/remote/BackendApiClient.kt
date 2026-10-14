package com.rescuenet.app.data.remote

import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.MediaType.Companion.toMediaType

/**
 * Builds (and caches by base URL) a Retrofit client for the RescueNet backend. The base URL
 * is user-configurable in Settings rather than hard-coded, because a hackathon demo commonly
 * runs the backend on a laptop on the same Wi-Fi network as the phones — there's no single
 * correct value to bake in. Defaults to the Android emulator's alias for the host machine's
 * localhost; a physical device needs the laptop's real LAN IP instead.
 */
@Singleton
class BackendApiClient @Inject constructor() {

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private val cache = ConcurrentHashMap<String, RescueNetApi>()

    fun apiFor(baseUrl: String): RescueNetApi = cache.getOrPut(normalize(baseUrl)) {
        val logging = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }
        // Nearby Help and Safe Route make the server consult public map services (and a free-tier
        // server may first need to wake up), so those two calls get a longer read timeout. Every
        // other call keeps the default so emergency reports still fail fast and fall back to the
        // offline queue instead of making someone wait.
        val patientForHelpLookups = Interceptor { chain ->
            val path = chain.request().url.encodedPath
            if (path.startsWith("/api/places/nearby") || path.startsWith("/api/routes/")) {
                chain.withReadTimeout(60, TimeUnit.SECONDS).proceed(chain.request())
            } else {
                chain.proceed(chain.request())
            }
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(patientForHelpLookups)
            .addInterceptor(logging)
            .build()
        Retrofit.Builder()
            .baseUrl(normalize(baseUrl))
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(RescueNetApi::class.java)
    }

    private fun normalize(baseUrl: String): String = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"

    companion object {
        /** 10.0.2.2 is the Android emulator's alias for the host machine's localhost. */
        const val DEFAULT_BASE_URL = "https://rescuenet-emergency-app1.onrender.com/"
    }
}

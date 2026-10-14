package com.rescuenet.app.data.repository

import com.rescuenet.app.data.location.DeviceLocation
import com.rescuenet.app.data.location.LocationProvider
import com.rescuenet.app.data.model.RouteDestination
import com.rescuenet.app.data.remote.BackendSession
import com.rescuenet.app.data.remote.BackendSessionManager
import com.rescuenet.app.data.remote.NearbyPlacesDto
import com.rescuenet.app.data.remote.SafeRouteDto
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import retrofit2.Response
import javax.inject.Inject
import javax.inject.Singleton

/** Result of a Nearby Help / Safe Route lookup, with a plain-language reason when it fails. */
sealed class HelpResult<out T> {
    data class Success<T>(val value: T) : HelpResult<T>()
    data class Failure(val kind: Kind, val message: String) : HelpResult<Nothing>()

    enum class Kind {
        /** The user hasn't granted location permission (or revoked it). */
        NO_PERMISSION,
        /** Permission is granted but the phone couldn't produce a location. */
        NO_LOCATION,
        /** The RescueNet server couldn't be reached (offline, asleep, wrong URL...). */
        UNREACHABLE,
        /** The server answered with an error (e.g. "no safe area found"). */
        SERVER,
    }
}

/**
 * Real, location-based help data from the RescueNet backend. Replaces the hardcoded lists
 * Nearby Help and Safe Route used to show.
 */
@Singleton
class HelpRepository @Inject constructor(
    private val sessionManager: BackendSessionManager,
    private val locationProvider: LocationProvider,
) {
    suspend fun currentLocation(): HelpResult<DeviceLocation> {
        if (!locationProvider.hasLocationPermission()) {
            return HelpResult.Failure(
                HelpResult.Kind.NO_PERMISSION,
                "RescueNet needs permission to use your location to find help near you.",
            )
        }
        val location = locationProvider.getBestLocation()
            ?: return HelpResult.Failure(
                HelpResult.Kind.NO_LOCATION,
                "Couldn't get your location. Make sure location is switched on, then try again.",
            )
        return HelpResult.Success(location)
    }

    suspend fun nearby(location: DeviceLocation, radiusKm: Double): HelpResult<NearbyPlacesDto> =
        authed { session ->
            session.api.nearbyPlaces(session.bearerToken, location.latitude, location.longitude, radiusKm, MAX_PLACES)
        }

    suspend fun safeRoute(
        location: DeviceLocation,
        destination: RouteDestination?,
        mode: String,
    ): HelpResult<SafeRouteDto> =
        authed { session ->
            session.api.safeRoute(
                session.bearerToken,
                location.latitude,
                location.longitude,
                destination?.latitude,
                destination?.longitude,
                destination?.name,
                destination?.category,
                mode,
            )
        }

    /** Runs a call with the device session, refreshing the session once if the server says the
     *  token has expired, and turning every failure into a message a person can act on. */
    private suspend fun <T> authed(call: suspend (BackendSession) -> Response<T>): HelpResult<T> {
        repeat(2) { attempt ->
            val session = sessionManager.getSession(forceRefresh = attempt == 1)
                ?: return HelpResult.Failure(HelpResult.Kind.UNREACHABLE, UNREACHABLE_MESSAGE)
            try {
                val response = call(session)
                val body = response.body()
                if (response.isSuccessful && body != null) return HelpResult.Success(body)
                if (response.code() == 401 && attempt == 0) return@repeat // token expired: retry with a fresh one
                return HelpResult.Failure(HelpResult.Kind.SERVER, serverMessage(response))
            } catch (e: Exception) {
                return HelpResult.Failure(HelpResult.Kind.UNREACHABLE, UNREACHABLE_MESSAGE)
            }
        }
        return HelpResult.Failure(HelpResult.Kind.SERVER, "Something went wrong. Please try again.")
    }

    private fun serverMessage(response: Response<*>): String {
        // The backend explains itself as {"detail": "..."} — show that rather than a bare code.
        val detail = try {
            val text = response.errorBody()?.string()
            if (text.isNullOrBlank()) null
            else Json.parseToJsonElement(text).jsonObject["detail"]?.jsonPrimitive?.contentOrNull
        } catch (e: Exception) {
            null
        }
        return detail ?: "The server couldn't complete that request (error ${response.code()})."
    }

    private companion object {
        const val MAX_PLACES = 40
        const val UNREACHABLE_MESSAGE =
            "Can't reach the RescueNet server. Check your internet connection and try again — if the server was idle it can take a minute to wake up."
    }
}

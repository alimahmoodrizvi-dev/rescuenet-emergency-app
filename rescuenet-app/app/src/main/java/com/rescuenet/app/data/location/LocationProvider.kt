package com.rescuenet.app.data.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

data class DeviceLocation(val latitude: Double, val longitude: Double, val accuracyMeters: Float)

/**
 * Thin wrapper over FusedLocationProviderClient. Per Part 8/19, location is only ever
 * requested at the moment a screen actually needs it (an emergency report, an "I'm Safe"
 * update, or the My Location screen) — nothing in this app polls location in the background.
 */
@Singleton
class LocationProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** Returns null if permission is missing or no fix could be obtained — callers must
     *  handle a null location gracefully (e.g. send the report anyway, per Part 29). */
    suspend fun getCurrentLocation(): DeviceLocation? {
        if (!hasLocationPermission()) return null

        val client = LocationServices.getFusedLocationProviderClient(context)
        val request = CurrentLocationRequest.Builder()
            .setPriority(Priority.PRIORITY_BALANCED_POWER_ACCURACY)
            .build()

        return suspendCancellableCoroutine { continuation ->
            try {
                client.getCurrentLocation(request, null)
                    .addOnSuccessListener { location ->
                        val result = location?.let {
                            DeviceLocation(it.latitude, it.longitude, it.accuracy)
                        }
                        if (continuation.isActive) continuation.resume(result)
                    }
                    .addOnFailureListener {
                        if (continuation.isActive) continuation.resume(null)
                    }
            } catch (e: SecurityException) {
                // Permission revoked between the check above and the call itself.
                if (continuation.isActive) continuation.resume(null)
            }
        }
    }

    /**
     * For screens that need "where is this phone right now" (Nearby Help, Safe Route). Asks for
     * a high-accuracy fix; if that doesn't arrive in time (typically indoors), falls back to the
     * last location the phone knows about instead of giving up. Returns null only if permission
     * is missing or the device has no location at all.
     */
    suspend fun getBestLocation(): DeviceLocation? {
        if (!hasLocationPermission()) return null

        val client = LocationServices.getFusedLocationProviderClient(context)
        val request = CurrentLocationRequest.Builder()
            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            .setDurationMillis(10_000L)
            .build()

        val fresh: DeviceLocation? = suspendCancellableCoroutine { continuation ->
            try {
                client.getCurrentLocation(request, null)
                    .addOnSuccessListener { location ->
                        val result = location?.let { DeviceLocation(it.latitude, it.longitude, it.accuracy) }
                        if (continuation.isActive) continuation.resume(result)
                    }
                    .addOnFailureListener {
                        if (continuation.isActive) continuation.resume(null)
                    }
            } catch (e: SecurityException) {
                if (continuation.isActive) continuation.resume(null)
            }
        }
        if (fresh != null) return fresh

        return suspendCancellableCoroutine { continuation ->
            try {
                client.lastLocation
                    .addOnSuccessListener { location ->
                        val result = location?.let { DeviceLocation(it.latitude, it.longitude, it.accuracy) }
                        if (continuation.isActive) continuation.resume(result)
                    }
                    .addOnFailureListener {
                        if (continuation.isActive) continuation.resume(null)
                    }
            } catch (e: SecurityException) {
                if (continuation.isActive) continuation.resume(null)
            }
        }
    }
}

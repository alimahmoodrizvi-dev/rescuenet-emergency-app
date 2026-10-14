package com.rescuenet.app.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Field names mirror rescuenet-backend/app/schemas.py ("Nearby Help + Safe Route" section).

@Serializable
data class NearbyPlaceDto(
    val id: String,
    val name: String,
    val category: String, // Hospital | Clinic | Police | Fire | Shelter | Assembly Point
    val latitude: Double,
    val longitude: Double,
    @SerialName("distance_km") val distanceKm: Double,
    val source: String, // "RescueNet" | "OpenStreetMap"
    val phone: String? = null,
    val detail: String? = null,
)

@Serializable
data class NearbyPlacesDto(
    @SerialName("origin_lat") val originLat: Double,
    @SerialName("origin_lng") val originLng: Double,
    @SerialName("radius_km") val radiusKm: Double,
    @SerialName("osm_available") val osmAvailable: Boolean,
    val places: List<NearbyPlaceDto>,
)

@Serializable
data class RouteStepDto(
    val instruction: String,
    @SerialName("distance_m") val distanceM: Double,
    @SerialName("duration_s") val durationS: Double,
)

@Serializable
data class RouteHazardRefDto(
    val id: String,
    val type: String,
    val message: String,
)

@Serializable
data class HazardZoneDto(
    val id: String,
    val type: String,
    val message: String,
    val latitude: Double,
    val longitude: Double,
    @SerialName("radius_m") val radiusM: Double,
    val color: String,
    val crossed: Boolean,
)

@Serializable
data class RouteOptionDto(
    @SerialName("distance_m") val distanceM: Double,
    @SerialName("duration_s") val durationS: Double,
    /** [[lat, lng], ...] from the start to the destination. */
    val geometry: List<List<Double>>,
    val steps: List<RouteStepDto>,
    @SerialName("exposure_m") val exposureM: Double,
    val risk: String, // "CLEAR" | "HAZARD"
    val label: String,
    @SerialName("crossed_hazards") val crossedHazards: List<RouteHazardRefDto>,
)

@Serializable
data class RouteDestinationDto(
    val name: String,
    val category: String,
    val latitude: Double,
    val longitude: Double,
    @SerialName("chosen_automatically") val chosenAutomatically: Boolean,
)

@Serializable
data class SafeRouteDto(
    val mode: String,
    @SerialName("origin_lat") val originLat: Double,
    @SerialName("origin_lng") val originLng: Double,
    val destination: RouteDestinationDto,
    val recommended: RouteOptionDto,
    val alternatives: List<RouteOptionDto> = emptyList(),
    @SerialName("hazard_zones") val hazardZones: List<HazardZoneDto> = emptyList(),
    val caveat: String = "",
)

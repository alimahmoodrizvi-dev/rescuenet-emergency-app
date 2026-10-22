package com.rescuenet.app.data.repository

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

// The JSON the phone sends to the server for the two things that matter most in an emergency.
// Kept as plain functions of plain values so they can be checked on their own, and so the
// rules below live in one place.

/**
 * A help request. Only what is actually known is included: a missing location is simply
 * left out — it used to be sent as the *text* "unknown", which the server rejected, so a
 * request from a phone with no GPS fix never reached the Command Center.
 */
fun buildIncidentPayloadJson(
    eventUuid: String,
    type: String,
    peopleCount: Int,
    injuryLevel: String,
    description: String,
    needs: List<String>,
    latitude: Double?,
    longitude: Double?,
    locationAccuracyM: Float?,
    reporterPhone: String?,
): String {
    val fields = mutableMapOf<String, JsonElement>(
        "event_uuid" to JsonPrimitive(eventUuid),
        "type" to JsonPrimitive(type),
        "people_count" to JsonPrimitive(peopleCount),
        "injury_level" to JsonPrimitive(injuryLevel),
        "description" to JsonPrimitive(description),
        "needs" to JsonArray(needs.map { JsonPrimitive(it) }),
    )
    latitude?.let { fields["latitude"] = JsonPrimitive(it) }
    longitude?.let { fields["longitude"] = JsonPrimitive(it) }
    locationAccuracyM?.let { fields["location_accuracy_m"] = JsonPrimitive(it) }
    reporterPhone?.let { fields["reporter_phone"] = JsonPrimitive(it) }
    return Json.encodeToString(JsonObject.serializer(), JsonObject(fields))
}

/**
 * "I'm safe". [phone] must already be a cleaned number (digits and an optional leading "+"),
 * and is placed into the JSON as a proper string either way.
 */
fun buildSafePayloadJson(
    atEpochMs: Long,
    latitude: Double?,
    longitude: Double?,
    phone: String?,
): String {
    val fields = mutableMapOf<String, JsonElement>(
        "status" to JsonPrimitive("SAFE"),
        "at" to JsonPrimitive(atEpochMs),
    )
    latitude?.let { fields["latitude"] = JsonPrimitive(it) }
    longitude?.let { fields["longitude"] = JsonPrimitive(it) }
    phone?.let { fields["phone"] = JsonPrimitive(it) }
    return Json.encodeToString(JsonObject.serializer(), JsonObject(fields))
}

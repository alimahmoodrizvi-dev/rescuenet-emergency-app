package com.rescuenet.app.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

/**
 * Retrofit client for the Phase 5 backend (`rescuenet-backend/`). Field names mirror
 * `app/schemas.py` exactly so no translation layer is needed between the two.
 */
interface RescueNetApi {

    @POST("api/auth/device-register")
    suspend fun registerDevice(@Body request: DeviceRegisterRequestDto): Response<TokenResponseDto>

    @POST("api/sync/batch")
    suspend fun syncBatch(
        @Header("Authorization") bearerToken: String,
        @Body request: SyncBatchRequestDto,
    ): Response<SyncBatchResultDto>

    @POST("api/ai/analyze-incident")
    suspend fun analyzeIncident(
        @Header("Authorization") bearerToken: String,
        @Body request: AIAnalyzeIncidentRequestDto,
    ): Response<AIAnalyzeIncidentResponseDto>
}

@Serializable
data class DeviceRegisterRequestDto(
    @SerialName("device_uuid") val deviceUuid: String,
    @SerialName("os_version") val osVersion: String? = null,
    @SerialName("app_version") val appVersion: String? = null,
)

@Serializable
data class TokenResponseDto(
    @SerialName("access_token") val accessToken: String,
    @SerialName("token_type") val tokenType: String,
    @SerialName("role") val role: String,
)

@Serializable
data class SyncBatchItemDto(
    @SerialName("entity_type") val entityType: String,
    // A raw JSON object, matching the backend's `payload: dict` — kept as a pass-through
    // string here and parsed with kotlinx.serialization.json.JsonObject at the call site,
    // since the shape differs per entity_type (incident vs safety_status).
    @SerialName("payload") val payload: kotlinx.serialization.json.JsonObject,
)

@Serializable
data class SyncBatchRequestDto(
    @SerialName("device_uuid") val deviceUuid: String,
    @SerialName("items") val items: List<SyncBatchItemDto>,
)

@Serializable
data class SyncBatchResultDto(
    @SerialName("accepted") val accepted: Int,
    @SerialName("duplicates") val duplicates: Int,
    @SerialName("rejected") val rejected: Int,
)

@Serializable
data class AIAnalyzeIncidentRequestDto(
    @SerialName("raw_text") val rawText: String,
    @SerialName("language_hint") val languageHint: String? = null,
)

/** Field names/values match app/schemas.py::AIAnalyzeIncidentResponse exactly — see
 *  AiRepository for the mapping into the app's own IncidentType/Severity/ResourceNeed enums. */
@Serializable
data class AIAnalyzeIncidentResponseDto(
    @SerialName("incident_type") val incidentType: String,
    @SerialName("severity") val severity: String,
    @SerialName("people_count") val peopleCount: Int? = null,
    @SerialName("injuries_present") val injuriesPresent: Boolean? = null,
    @SerialName("required_resources") val requiredResources: List<String> = emptyList(),
    @SerialName("confidence") val confidence: Int,
    @SerialName("model_provider") val modelProvider: String,
    @SerialName("is_simulated") val isSimulated: Boolean,
    @SerialName("caveat") val caveat: String = "",
)

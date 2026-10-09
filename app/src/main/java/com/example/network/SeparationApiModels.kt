package com.example.network

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class HealthResponse(
    val status: String,
    val service: String? = null,
    @Json(name = "demucs_available") val demucsAvailable: Boolean = false,
    @Json(name = "torch_available") val torchAvailable: Boolean = false,
    val device: String = "cpu",
    @Json(name = "supported_models") val supportedModels: List<String> = emptyList()
)

@JsonClass(generateAdapter = true)
data class StemsResponse(
    val soprano: String? = null,
    val contralto: String? = null,
    val tenor: String? = null,
    val baixo: String? = null,
    val instrumental: String? = null,
    val vocals: String? = null
)

@JsonClass(generateAdapter = true)
data class SeparationResponse(
    @Json(name = "task_id") val taskId: String,
    val status: String,
    val filename: String? = null,
    @Json(name = "duration_seconds") val durationSeconds: Float = 0f,
    @Json(name = "polyphony_detected") val polyphonyDetected: Boolean = true,
    val stems: StemsResponse
)

package com.caterktor.live.domain

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class FeedEvent(
    val id: String,
    val type: String,
    val payload: JsonObject,
    val timestamp: Instant,
    val sequenceNumber: Long,
    val topic: String,
)

@Serializable
data class TopicInfo(
    val name: String,
    val prefix: String,
    val supportsSnapshot: Boolean,
    val supportsReplay: Boolean,
    val supportsStream: Boolean,
    val requiresAuth: Boolean,
)

@Serializable
data class SnapshotEntry(
    val topic: String,
    val state: List<JsonObject>,
)

// Response envelopes

@Serializable
data class Meta(
    val requestId: String,
    val cursor: String? = null,
    val nextCursor: String? = null,
)

@Serializable
data class ApiResponse<T>(
    val data: T,
    val meta: Meta,
)

@Serializable
data class ApiError(
    val code: String,
    val message: String,
    val details: JsonObject = JsonObject(emptyMap()),
)

@Serializable
data class ApiErrorResponse(
    val error: ApiError,
    val meta: Meta,
)

@Serializable
data class EventPayload(
    val topic: String,
    val type: String,
    val payload: JsonObject,
)

@Serializable
data class HealthResponse(val status: String)

@Serializable
data class TopicResponse(
    val name: String,
    val supportsSnapshot: Boolean,
    val supportsReplay: Boolean,
    val supportsStream: Boolean,
    val requiresAuth: Boolean,
)

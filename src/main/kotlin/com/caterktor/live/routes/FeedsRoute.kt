package com.caterktor.live.routes

import com.caterktor.live.debug.DebugState
import com.caterktor.live.domain.*
import com.caterktor.live.plugins.getRequestId
import com.caterktor.live.registry.TopicRegistry
import com.caterktor.live.store.EventStore
import com.caterktor.live.store.SnapshotStore
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.sse.*
import io.ktor.sse.*
import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("FeedsRoute")

private val heartbeatIntervalMs: Long =
    (System.getenv("HEARTBEAT_INTERVAL_SECONDS")?.toLongOrNull() ?: 10L) * 1000L

fun Route.feedsRoute(eventStore: EventStore, snapshotStore: SnapshotStore) {

    get("/v1/feeds/{topic}/snapshot") {
        val topicName = call.parameters["topic"]!!
        val requestId = call.getRequestId()

        val topicInfo = TopicRegistry.find(topicName)
            ?: return@get respondError(call, HttpStatusCode.NotFound, "TOPIC_NOT_FOUND", "Topic '$topicName' not found", requestId)

        if (topicInfo.requiresAuth && !isBearerPresent(call)) {
            return@get respondError(call, HttpStatusCode.Unauthorized, "UNAUTHORIZED", "Bearer token required", requestId)
        }

        val snapshot = snapshotStore.get(topicName)
            ?: return@get respondError(call, HttpStatusCode.NotFound, "TOPIC_NOT_FOUND", "Snapshot not found for '$topicName'", requestId)

        call.respond(
            HttpStatusCode.OK,
            ApiResponse(
                data = SnapshotEntry(topic = topicName, state = snapshot.state),
                meta = Meta(requestId = requestId, cursor = snapshot.cursor),
            )
        )
    }

    get("/v1/feeds/{topic}/replay") {
        val topicName = call.parameters["topic"]!!
        val requestId = call.getRequestId()

        val topicInfo = TopicRegistry.find(topicName)
            ?: return@get respondError(call, HttpStatusCode.NotFound, "TOPIC_NOT_FOUND", "Topic '$topicName' not found", requestId)

        if (!topicInfo.supportsReplay) {
            return@get respondError(call, HttpStatusCode.BadRequest, "VALIDATION_ERROR", "Topic '$topicName' does not support replay", requestId)
        }

        if (topicInfo.requiresAuth && !isBearerPresent(call)) {
            return@get respondError(call, HttpStatusCode.Unauthorized, "UNAUTHORIZED", "Bearer token required", requestId)
        }

        val cursor = call.request.queryParameters["cursor"]
            ?: return@get respondError(call, HttpStatusCode.BadRequest, "VALIDATION_ERROR", "Missing required query param: cursor", requestId)

        val topicStore = eventStore[topicName]
            ?: return@get respondError(call, HttpStatusCode.NotFound, "TOPIC_NOT_FOUND", "Topic '$topicName' not found", requestId)

        if (topicStore.isCursorExpired(cursor)) {
            return@get respondError(
                call, HttpStatusCode.Conflict, "REPLAY_CURSOR_EXPIRED",
                "Replay window expired. Fetch a fresh snapshot.",
                requestId,
                details = buildJsonObject { put("topic", topicName) },
            )
        }

        val events = topicStore.eventsAfterCursor(cursor)
        val replayItems = events.map { event ->
            buildJsonObject {
                put("id", event.id)
                put("type", event.type)
                put("payload", event.payload)
            }
        }
        val nextCursor = events.lastOrNull()
            ?.let { "cur_${topicStore.prefix}_${it.sequenceNumber.toString().padStart(3, '0')}" }
            ?: cursor

        call.respond(
            HttpStatusCode.OK,
            buildJsonObject {
                put("data", Json.encodeToJsonElement(replayItems))
                put("meta", buildJsonObject {
                    put("nextCursor", nextCursor)
                    put("requestId", requestId)
                })
            }
        )
    }

    sse("/v1/feeds/{topic}/stream") {
        val topicName = call.parameters["topic"] ?: run { close(); return@sse }
        val requestId = call.getRequestId()

        val topicInfo = TopicRegistry.find(topicName)
        if (topicInfo == null) {
            call.response.status(HttpStatusCode.NotFound)
            close(); return@sse
        }

        if (topicInfo.requiresAuth && !isBearerPresent(call)) {
            call.response.status(HttpStatusCode.Unauthorized)
            close(); return@sse
        }

        if (DebugState.isStreamUnavailable(topicName)) {
            call.response.status(HttpStatusCode.ServiceUnavailable)
            close(); return@sse
        }

        val topicStore = eventStore[topicName]
        if (topicStore == null) {
            call.response.status(HttpStatusCode.NotFound)
            close(); return@sse
        }

        val lastEventId = call.request.headers["Last-Event-ID"]
        log.info("SSE connect: topic={} requestId={} lastEventId={}", topicName, requestId, lastEventId)

        // Retry hint
        send(ServerSentEvent(retry = 3000))

        // Replay missed events on reconnect
        if (lastEventId != null) {
            val cursorFromEventId = lastEventId.replace(Regex("^evt_"), "cur_")
            val missed = topicStore.eventsAfterCursor(cursorFromEventId)
            for (event in missed) {
                send(ServerSentEvent(data = Json.encodeToString(event.payload), event = event.type, id = event.id))
            }
        }

        // Heartbeat coroutine
        val heartbeatJob = launch {
            while (isActive) {
                delay(heartbeatIntervalMs)
                if (!DebugState.isHeartbeatPaused(topicName)) {
                    try {
                        send(ServerSentEvent(comments = "heartbeat"))
                    } catch (_: Exception) {
                        break
                    }
                }
            }
        }

        var eventsSent = 0
        try {
            if (DebugState.consumeMalformed(topicName)) {
                send(ServerSentEvent(data = "}{MALFORMED_JSON}{", event = "debug.malformed"))
            }

            topicStore.flow.collect { event ->
                send(ServerSentEvent(data = Json.encodeToString(event.payload), event = event.type, id = event.id))
                eventsSent++
                if (DebugState.consumeDisconnectToken(topicName)) {
                    log.info("Debug disconnect after {} events on topic={}", eventsSent, topicName)
                    heartbeatJob.cancel()
                    close()
                    return@collect
                }
            }
        } catch (e: Exception) {
            log.info("SSE stream closed: topic={} requestId={} reason={}", topicName, requestId, e.message)
        } finally {
            heartbeatJob.cancel()
            log.info("SSE disconnect: topic={} requestId={} eventsSent={}", topicName, requestId, eventsSent)
        }
    }
}

private fun isBearerPresent(call: ApplicationCall): Boolean {
    val auth = call.request.headers[HttpHeaders.Authorization] ?: return false
    return auth.startsWith("Bearer ") && auth.length > 7
}

private suspend fun respondError(
    call: ApplicationCall,
    status: HttpStatusCode,
    code: String,
    message: String,
    requestId: String,
    details: JsonObject = JsonObject(emptyMap()),
) {
    call.respond(
        status,
        ApiErrorResponse(
            error = ApiError(code = code, message = message, details = details),
            meta = Meta(requestId = requestId),
        )
    )
}

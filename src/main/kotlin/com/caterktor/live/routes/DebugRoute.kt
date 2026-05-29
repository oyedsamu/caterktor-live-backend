package com.caterktor.live.routes

import com.caterktor.live.debug.DebugState
import com.caterktor.live.plugins.getRequestId
import com.caterktor.live.registry.TopicRegistry
import com.caterktor.live.seed.seedAll
import com.caterktor.live.store.EventStore
import com.caterktor.live.store.SnapshotStore
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

fun Route.debugRoute(eventStore: EventStore, snapshotStore: SnapshotStore) {

    post("/v1/debug/pause-heartbeats/{topic}") {
        val topic = call.parameters["topic"]!!
        if (!TopicRegistry.exists(topic)) { call.respond(HttpStatusCode.NotFound); return@post }
        DebugState.pauseHeartbeats(topic)
        call.respond(HttpStatusCode.OK, ok(call.getRequestId(), "heartbeats paused for $topic"))
    }

    post("/v1/debug/resume-heartbeats/{topic}") {
        val topic = call.parameters["topic"]!!
        if (!TopicRegistry.exists(topic)) { call.respond(HttpStatusCode.NotFound); return@post }
        DebugState.resumeHeartbeats(topic)
        call.respond(HttpStatusCode.OK, ok(call.getRequestId(), "heartbeats resumed for $topic"))
    }

    post("/v1/debug/disconnect-after") {
        val topic = call.request.queryParameters["topic"] ?: return@post call.respond(HttpStatusCode.BadRequest)
        val n = call.request.queryParameters["n"]?.toIntOrNull() ?: return@post call.respond(HttpStatusCode.BadRequest)
        if (!TopicRegistry.exists(topic)) { call.respond(HttpStatusCode.NotFound); return@post }
        DebugState.setDisconnectAfterN(topic, n)
        call.respond(HttpStatusCode.OK, ok(call.getRequestId(), "will disconnect after $n events on $topic"))
    }

    post("/v1/debug/expire-cursor") {
        val topic = call.request.queryParameters["topic"] ?: return@post call.respond(HttpStatusCode.BadRequest)
        val cursor = call.request.queryParameters["cursor"] ?: return@post call.respond(HttpStatusCode.BadRequest)
        val store = eventStore[topic] ?: return@post call.respond(HttpStatusCode.NotFound)
        store.expireCursor(cursor)
        call.respond(HttpStatusCode.OK, ok(call.getRequestId(), "cursor $cursor expired for $topic"))
    }

    post("/v1/debug/inject-malformed") {
        val topic = call.request.queryParameters["topic"] ?: return@post call.respond(HttpStatusCode.BadRequest)
        if (!TopicRegistry.exists(topic)) { call.respond(HttpStatusCode.NotFound); return@post }
        DebugState.injectMalformed(topic)
        call.respond(HttpStatusCode.OK, ok(call.getRequestId(), "malformed event queued for $topic"))
    }

    post("/v1/debug/stream-unavailable") {
        val topic = call.request.queryParameters["topic"] ?: return@post call.respond(HttpStatusCode.BadRequest)
        if (!TopicRegistry.exists(topic)) { call.respond(HttpStatusCode.NotFound); return@post }
        DebugState.setStreamUnavailable(topic, true)
        call.respond(HttpStatusCode.OK, ok(call.getRequestId(), "stream marked unavailable for $topic"))
    }

    post("/v1/debug/stream-available") {
        val topic = call.request.queryParameters["topic"] ?: return@post call.respond(HttpStatusCode.BadRequest)
        if (!TopicRegistry.exists(topic)) { call.respond(HttpStatusCode.NotFound); return@post }
        DebugState.setStreamUnavailable(topic, false)
        call.respond(HttpStatusCode.OK, ok(call.getRequestId(), "stream marked available for $topic"))
    }

    post("/v1/debug/reset") {
        DebugState.reset()
        call.respond(HttpStatusCode.OK, ok(call.getRequestId(), "debug state reset"))
    }
}

private fun ok(requestId: String, message: String) = buildJsonObject {
    put("data", buildJsonObject { put("message", message) })
    put("meta", buildJsonObject { put("requestId", requestId) })
}

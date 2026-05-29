package com.caterktor.live.routes

import com.caterktor.live.domain.*
import com.caterktor.live.plugins.getRequestId
import com.caterktor.live.registry.TopicRegistry
import com.caterktor.live.store.EventStore
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*

fun Route.adminRoute(eventStore: EventStore) {
    post("/v1/admin/events") {
        val requestId = call.getRequestId()
        val body = call.receive<EventPayload>()

        if (!TopicRegistry.exists(body.topic)) {
            call.respond(
                HttpStatusCode.NotFound,
                ApiErrorResponse(
                    error = ApiError("TOPIC_NOT_FOUND", "Topic '${body.topic}' not found"),
                    meta = Meta(requestId = requestId),
                )
            )
            return@post
        }

        val topicStore = eventStore[body.topic]!!
        val event = topicStore.append(body.type, body.payload)

        call.respond(
            HttpStatusCode.Created,
            buildJsonObject {
                put("data", buildJsonObject {
                    put("id", event.id)
                    put("type", event.type)
                    put("topic", event.topic)
                    put("payload", event.payload)
                    put("timestamp", event.timestamp.toString())
                })
                put("meta", buildJsonObject { put("requestId", requestId) })
            }
        )
    }
}

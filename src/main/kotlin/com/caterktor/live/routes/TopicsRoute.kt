package com.caterktor.live.routes

import com.caterktor.live.domain.ApiResponse
import com.caterktor.live.domain.Meta
import com.caterktor.live.domain.TopicResponse
import com.caterktor.live.plugins.getRequestId
import com.caterktor.live.registry.TopicRegistry
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.topicsRoute() {
    get("/v1/topics") {
        val topics = TopicRegistry.all.map {
            TopicResponse(
                name = it.name,
                supportsSnapshot = it.supportsSnapshot,
                supportsReplay = it.supportsReplay,
                supportsStream = it.supportsStream,
                requiresAuth = it.requiresAuth,
            )
        }
        call.respond(
            HttpStatusCode.OK,
            ApiResponse(data = topics, meta = Meta(requestId = call.getRequestId())),
        )
    }
}

package com.caterktor.live.routes

import com.caterktor.live.domain.ApiResponse
import com.caterktor.live.domain.HealthResponse
import com.caterktor.live.domain.Meta
import com.caterktor.live.plugins.getRequestId
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.healthRoute() {
    get("/v1/health") {
        call.respond(
            HttpStatusCode.OK,
            ApiResponse(
                data = HealthResponse("ok"),
                meta = Meta(requestId = call.getRequestId()),
            )
        )
    }
}

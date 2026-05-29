package com.caterktor.live

import com.caterktor.live.plugins.RequestIdPlugin
import com.caterktor.live.registry.TopicRegistry
import com.caterktor.live.routes.*
import com.caterktor.live.seed.seedAll
import com.caterktor.live.store.EventStore
import com.caterktor.live.store.SnapshotStore
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sse.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.slf4j.LoggerFactory

private val appLog = LoggerFactory.getLogger("Application")

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    embeddedServer(CIO, port = port, host = "0.0.0.0", module = Application::module).start(wait = true)
}

fun Application.module() {
    val debugEnabled = System.getenv("DEBUG_ENDPOINTS")?.equals("true", ignoreCase = true) ?: false
    val corsPermissive = System.getenv("CORS_PERMISSIVE")?.equals("true", ignoreCase = true) ?: true

    val eventStore = EventStore()
    val snapshotStore = SnapshotStore()

    for (topic in TopicRegistry.all) {
        eventStore.register(topic.name, topic.prefix)
    }

    seedAll(eventStore, snapshotStore)
    log.info("Seed data loaded for topics: {}", TopicRegistry.all.map { it.name })

    install(SSE)

    install(ContentNegotiation) {
        json(Json {
            prettyPrint = false
            ignoreUnknownKeys = true
            encodeDefaults = true
        })
    }

    install(RequestIdPlugin)

    if (corsPermissive) {
        install(CORS) {
            anyHost()
            allowHeader(HttpHeaders.Authorization)
            allowHeader(HttpHeaders.ContentType)
            allowHeader("X-Request-ID")
            allowMethod(HttpMethod.Get)
            allowMethod(HttpMethod.Post)
        }
    }

    install(StatusPages) {
        exception<Throwable> { call, cause ->
            appLog.error("Unhandled exception", cause)
            call.respond(
                HttpStatusCode.InternalServerError,
                buildJsonObject {
                    put("error", buildJsonObject {
                        put("code", "INTERNAL_ERROR")
                        put("message", "An unexpected error occurred")
                        put("details", buildJsonObject {})
                    })
                    put("meta", buildJsonObject { put("requestId", "unknown") })
                }
            )
        }
    }

    // Background emitter: one event per topic every 30s
    val emitterJob = launch(Dispatchers.IO) {
        delay(30_000L)
        var tick = 0
        while (isActive) {
            tick++
            try {
                eventStore["orders"]?.append(
                    "order.updated",
                    buildJsonObject {
                        put("orderId", "ord_bg_$tick")
                        put("status", if (tick % 2 == 0) "processing" else "pending")
                        put("tick", tick)
                    }
                )
                eventStore["deliveries"]?.append(
                    "delivery.dispatched",
                    buildJsonObject { put("deliveryId", "del_bg_$tick"); put("tick", tick) }
                )
                eventStore["prices"]?.append(
                    "price.updated",
                    buildJsonObject { put("itemId", "item_A"); put("price", 10.00 + tick * 0.01); put("tick", tick) }
                )
                eventStore["incidents"]?.append(
                    "incident.updated",
                    buildJsonObject { put("incidentId", "inc_bg"); put("note", "Periodic check #$tick") }
                )
                log.debug("Background emitter tick={}", tick)
            } catch (e: Exception) {
                log.warn("Background emitter error: {}", e.message)
            }
            delay(30_000L)
        }
    }

    environment.monitor.subscribe(ApplicationStopped) {
        emitterJob.cancel()
    }

    routing {
        healthRoute()
        topicsRoute()
        feedsRoute(eventStore, snapshotStore)
        docsRoute()

        if (debugEnabled) {
            adminRoute(eventStore)
            debugRoute(eventStore, snapshotStore)
            log.warn("DEBUG_ENDPOINTS=true — admin and debug routes are active")
        }
    }

    log.info("CaterLive backend started on port ${System.getenv("PORT") ?: 8080}")
}

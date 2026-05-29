package com.caterktor.live.plugins

import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.util.*
import java.util.*

const val HEADER_REQUEST_ID = "X-Request-ID"
const val HEADER_TRACE_ID = "X-Trace-ID"

private val RequestIdKey = AttributeKey<String>("RequestId")
private val TraceIdKey = AttributeKey<String>("TraceId")

fun generateId(prefix: String = "req"): String =
    "${prefix}_${UUID.randomUUID().toString().replace("-", "").take(12)}"

fun ApplicationCall.getRequestId(): String =
    attributes.getOrNull(RequestIdKey) ?: generateId()

val RequestIdPlugin = createApplicationPlugin("RequestIdPlugin") {
    onCall { call ->
        val requestId = call.request.headers[HEADER_REQUEST_ID] ?: generateId()
        val traceId = generateId("trc")
        call.attributes.put(RequestIdKey, requestId)
        call.attributes.put(TraceIdKey, traceId)
        call.response.headers.append(HEADER_REQUEST_ID, requestId)
        call.response.headers.append(HEADER_TRACE_ID, traceId)
    }
}

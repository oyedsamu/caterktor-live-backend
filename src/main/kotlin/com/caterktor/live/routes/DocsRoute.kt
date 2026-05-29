package com.caterktor.live.routes

import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.docsRoute() {
    get("/docs") {
        call.respondText(swaggerUiHtml(), ContentType.Text.Html)
    }
    get("/v1/openapi.json") {
        call.respondText(openapiSpec(), ContentType.Application.Json)
    }
}

private fun swaggerUiHtml() = """
<!DOCTYPE html>
<html>
<head>
  <title>CaterLive API Docs</title>
  <meta charset="utf-8"/>
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <link rel="stylesheet" type="text/css" href="https://unpkg.com/swagger-ui-dist@5/swagger-ui.css">
</head>
<body>
<div id="swagger-ui"></div>
<script src="https://unpkg.com/swagger-ui-dist@5/swagger-ui-bundle.js"></script>
<script>
  window.onload = function() {
    SwaggerUIBundle({
      url: "/v1/openapi.json",
      dom_id: '#swagger-ui',
      presets: [SwaggerUIBundle.presets.apis, SwaggerUIBundle.SwaggerUIStandalonePreset],
      layout: "BaseLayout"
    })
  }
</script>
</body>
</html>
""".trimIndent()

private fun openapiSpec() = """
{
  "openapi": "3.0.3",
  "info": {
    "title": "CaterLive Events Backend",
    "version": "0.1.0",
    "description": "SSE-based realtime events backend for the CaterKtor KMP showcase.\n\n## SSE Streams\nThe `/v1/feeds/{topic}/stream` endpoint is an SSE stream (not representable in Swagger).\n\nEvent format:\n```\nid: evt_o_1021\nevent: order.updated\ndata: {\"orderId\":\"ord_22\",\"status\":\"shipped\"}\n```\n\nHeartbeats are emitted as SSE comments: `: heartbeat`\n\nUse `Last-Event-ID` header to resume after disconnect. Server sends `retry: 3000` on connect."
  },
  "servers": [{"url": "/", "description": "This server"}],
  "components": {
    "securitySchemes": {
      "bearerAuth": {
        "type": "http",
        "scheme": "bearer",
        "description": "Any non-empty token is accepted (simplified auth for showcase)"
      }
    },
    "schemas": {
      "Meta": {
        "type": "object",
        "properties": {
          "requestId": {"type": "string"},
          "cursor": {"type": "string", "nullable": true},
          "nextCursor": {"type": "string", "nullable": true}
        },
        "required": ["requestId"]
      },
      "ApiError": {
        "type": "object",
        "properties": {
          "error": {
            "type": "object",
            "properties": {
              "code": {"type": "string", "description": "SCREAMING_SNAKE_CASE stable error code"},
              "message": {"type": "string"},
              "details": {"type": "object"}
            },
            "required": ["code", "message", "details"]
          },
          "meta": {"${'$'}ref": "#/components/schemas/Meta"}
        }
      },
      "TopicInfo": {
        "type": "object",
        "properties": {
          "name": {"type": "string"},
          "supportsSnapshot": {"type": "boolean"},
          "supportsReplay": {"type": "boolean"},
          "supportsStream": {"type": "boolean"},
          "requiresAuth": {"type": "boolean"}
        }
      },
      "FeedEvent": {
        "type": "object",
        "properties": {
          "id": {"type": "string", "example": "evt_o_1021"},
          "type": {"type": "string", "example": "order.updated"},
          "payload": {"type": "object"}
        }
      }
    }
  },
  "paths": {
    "/v1/health": {
      "get": {
        "summary": "Health check",
        "operationId": "getHealth",
        "tags": ["Infrastructure"],
        "responses": {
          "200": {
            "description": "Service is healthy",
            "content": {
              "application/json": {
                "schema": {
                  "type": "object",
                  "properties": {
                    "data": {"type": "object", "properties": {"status": {"type": "string"}}},
                    "meta": {"${'$'}ref": "#/components/schemas/Meta"}
                  }
                }
              }
            }
          }
        }
      }
    },
    "/v1/topics": {
      "get": {
        "summary": "List all available topics",
        "operationId": "listTopics",
        "tags": ["Discovery"],
        "responses": {
          "200": {
            "description": "List of topics",
            "content": {
              "application/json": {
                "schema": {
                  "type": "object",
                  "properties": {
                    "data": {"type": "array", "items": {"${'$'}ref": "#/components/schemas/TopicInfo"}},
                    "meta": {"${'$'}ref": "#/components/schemas/Meta"}
                  }
                }
              }
            }
          }
        }
      }
    },
    "/v1/feeds/{topic}/snapshot": {
      "get": {
        "summary": "Get current snapshot for a topic",
        "operationId": "getSnapshot",
        "tags": ["Feeds"],
        "security": [{"bearerAuth": []}],
        "parameters": [
          {"name": "topic", "in": "path", "required": true, "schema": {"type": "string", "enum": ["orders","deliveries","prices","incidents"]}}
        ],
        "responses": {
          "200": {
            "description": "Snapshot with cursor watermark",
            "content": {
              "application/json": {
                "schema": {
                  "type": "object",
                  "properties": {
                    "data": {"type": "object", "properties": {"topic": {"type": "string"}, "state": {"type": "array", "items": {"type": "object"}}}},
                    "meta": {"${'$'}ref": "#/components/schemas/Meta"}
                  }
                }
              }
            }
          },
          "401": {"description": "UNAUTHORIZED", "content": {"application/json": {"schema": {"${'$'}ref": "#/components/schemas/ApiError"}}}},
          "404": {"description": "TOPIC_NOT_FOUND", "content": {"application/json": {"schema": {"${'$'}ref": "#/components/schemas/ApiError"}}}}
        }
      }
    },
    "/v1/feeds/{topic}/replay": {
      "get": {
        "summary": "Replay events after a cursor",
        "operationId": "replayEvents",
        "tags": ["Feeds"],
        "security": [{"bearerAuth": []}],
        "parameters": [
          {"name": "topic", "in": "path", "required": true, "schema": {"type": "string"}},
          {"name": "cursor", "in": "query", "required": true, "schema": {"type": "string"}, "description": "Cursor from snapshot or previous replay response"}
        ],
        "responses": {
          "200": {
            "description": "Events after cursor",
            "content": {
              "application/json": {
                "schema": {
                  "type": "object",
                  "properties": {
                    "data": {"type": "array", "items": {"${'$'}ref": "#/components/schemas/FeedEvent"}},
                    "meta": {"${'$'}ref": "#/components/schemas/Meta"}
                  }
                }
              }
            }
          },
          "400": {"description": "VALIDATION_ERROR — missing cursor or topic does not support replay"},
          "401": {"description": "UNAUTHORIZED"},
          "404": {"description": "TOPIC_NOT_FOUND"},
          "409": {"description": "REPLAY_CURSOR_EXPIRED — fetch a fresh snapshot and start over"}
        }
      }
    },
    "/v1/feeds/{topic}/stream": {
      "get": {
        "summary": "Subscribe to live SSE stream",
        "operationId": "streamEvents",
        "tags": ["Feeds"],
        "security": [{"bearerAuth": []}],
        "description": "Opens a Server-Sent Events stream. Not testable via Swagger UI — use curl:\n\n```\ncurl -N --no-buffer -H 'Authorization: Bearer demo' \\\n  http://localhost:8080/v1/feeds/orders/stream\n```\n\nEvents:\n- `id`: evt_{prefix}_{seq}\n- `event`: domain event type (e.g. order.updated)\n- `data`: JSON payload\n\nHeartbeat (every 10s idle): `: heartbeat`\n\nResume: include `Last-Event-ID` header with the last received event ID.\n\nServer sends `retry: 3000` on connect.",
        "parameters": [
          {"name": "topic", "in": "path", "required": true, "schema": {"type": "string"}},
          {"name": "Last-Event-ID", "in": "header", "required": false, "schema": {"type": "string"}, "description": "Resume from this event ID"}
        ],
        "responses": {
          "200": {"description": "text/event-stream (SSE)"},
          "401": {"description": "UNAUTHORIZED — evaluated before opening stream"},
          "503": {"description": "STREAM_UNAVAILABLE — retryable"}
        }
      }
    },
    "/v1/admin/events": {
      "post": {
        "summary": "Inject an event (DEBUG_ENDPOINTS=true only)",
        "operationId": "injectEvent",
        "tags": ["Admin"],
        "requestBody": {
          "required": true,
          "content": {
            "application/json": {
              "schema": {
                "type": "object",
                "properties": {
                  "topic": {"type": "string"},
                  "type": {"type": "string"},
                  "payload": {"type": "object"}
                },
                "required": ["topic", "type", "payload"]
              }
            }
          }
        },
        "responses": {
          "201": {"description": "Event created and fanned out to stream subscribers"},
          "404": {"description": "TOPIC_NOT_FOUND"}
        }
      }
    },
    "/v1/debug/pause-heartbeats/{topic}": {
      "post": {"summary": "Pause heartbeats for topic (debug)", "tags": ["Debug"], "parameters": [{"name": "topic", "in": "path", "required": true, "schema": {"type": "string"}}], "responses": {"200": {"description": "OK"}}}
    },
    "/v1/debug/resume-heartbeats/{topic}": {
      "post": {"summary": "Resume heartbeats for topic (debug)", "tags": ["Debug"], "parameters": [{"name": "topic", "in": "path", "required": true, "schema": {"type": "string"}}], "responses": {"200": {"description": "OK"}}}
    },
    "/v1/debug/disconnect-after": {
      "post": {"summary": "Close stream after N events (debug)", "tags": ["Debug"], "parameters": [{"name": "topic", "in": "query", "required": true, "schema": {"type": "string"}}, {"name": "n", "in": "query", "required": true, "schema": {"type": "integer"}}], "responses": {"200": {"description": "OK"}}}
    },
    "/v1/debug/expire-cursor": {
      "post": {"summary": "Force a cursor to expire (debug)", "tags": ["Debug"], "parameters": [{"name": "topic", "in": "query", "required": true, "schema": {"type": "string"}}, {"name": "cursor", "in": "query", "required": true, "schema": {"type": "string"}}], "responses": {"200": {"description": "OK"}}}
    },
    "/v1/debug/inject-malformed": {
      "post": {"summary": "Inject a malformed SSE line to active streams (debug)", "tags": ["Debug"], "parameters": [{"name": "topic", "in": "query", "required": true, "schema": {"type": "string"}}], "responses": {"200": {"description": "OK"}}}
    },
    "/v1/debug/reset": {
      "post": {"summary": "Reset all debug state (debug)", "tags": ["Debug"], "responses": {"200": {"description": "OK"}}}
    }
  }
}
""".trimIndent()

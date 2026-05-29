# CaterLive Events Backend — API Reference

## Overview

CaterLive is a Ktor/JVM backend that provides realtime SSE event streams for the CaterKtor KMP networking library showcase.

**Base URL:** `http://localhost:8080` (local), `https://your-service.railway.app` (Railway)

**Auth:** Bearer token. Any non-empty value is accepted (`Authorization: Bearer <any-token>`). Topics with `requiresAuth: true` enforce this; others are public.

---

## Response Envelopes

### Success

```json
{ "data": { ... }, "meta": { "requestId": "req_abc" } }
```

Paged/list:

```json
{ "data": [...], "meta": { "nextCursor": "cur_o_1020", "requestId": "req_abc" } }
```

### Error (all non-2xx)

```json
{
  "error": {
    "code": "REPLAY_CURSOR_EXPIRED",
    "message": "Replay window expired. Fetch a fresh snapshot.",
    "details": { "topic": "orders" }
  },
  "meta": { "requestId": "req_abc" }
}
```

### Required Response Headers

| Header | Description |
|--------|-------------|
| `X-Request-ID` | Echoed from client or generated UUID |
| `X-Trace-ID` | Generated UUID per request |

---

## Cursor and ID Scheme

| Format | Example | Meaning |
|--------|---------|---------|
| Event ID | `evt_o_1021` | `evt_{topic_prefix}_{seqNum}` |
| Cursor | `cur_o_1020` | `cur_{topic_prefix}_{seqNum}` |

Topic prefixes: `o` (orders), `d` (deliveries), `p` (prices), `i` (incidents).

Cursors are watermarks — the snapshot cursor (`cur_o_1000`) represents all events up to that sequence number materialized into the snapshot. Replay from this cursor returns all events after sequence 1000.

**Cursor → Event ID conversion:** Replace `cur_` prefix with `evt_` and vice versa. For `Last-Event-ID` reconnect, the server converts the event ID to a cursor internally.

---

## Endpoints

### `GET /v1/health`

Health check. No auth required.

**Response 200:**
```json
{ "data": { "status": "ok" }, "meta": { "requestId": "req_abc" } }
```

---

### `GET /v1/topics`

List all topics and their capabilities. No auth required.

**Response 200:**
```json
{
  "data": [
    { "name": "orders",     "supportsSnapshot": true, "supportsReplay": true,  "supportsStream": true, "requiresAuth": true },
    { "name": "deliveries", "supportsSnapshot": true, "supportsReplay": true,  "supportsStream": true, "requiresAuth": true },
    { "name": "prices",     "supportsSnapshot": true, "supportsReplay": false, "supportsStream": true, "requiresAuth": false },
    { "name": "incidents",  "supportsSnapshot": true, "supportsReplay": true,  "supportsStream": true, "requiresAuth": false }
  ],
  "meta": { "requestId": "req_abc" }
}
```

---

### `GET /v1/feeds/{topic}/snapshot`

Returns the current materialized state for a topic, with a cursor watermark.

**Auth:** Required for `orders`, `deliveries`. Not required for `prices`, `incidents`.

**Path params:** `topic` — one of `orders`, `deliveries`, `prices`, `incidents`

**Response 200:**
```json
{
  "data": {
    "topic": "orders",
    "state": [
      { "orderId": "ord_01", "status": "delivered", "item": "Pizza Margherita", "amount": 12.5 },
      ...
    ]
  },
  "meta": { "cursor": "cur_o_1000", "requestId": "req_abc" }
}
```

**Use this cursor** to start a replay or stream subscription. Pass it as the `cursor` query param to `/replay`, or use the last received event ID as `Last-Event-ID` for `/stream`.

**Error codes:**
- `401 UNAUTHORIZED` — missing or empty bearer token
- `404 TOPIC_NOT_FOUND`

---

### `GET /v1/feeds/{topic}/replay?cursor=cur_o_1000`

Returns events after the given cursor in order.

**Auth:** Same as snapshot for each topic.

**Query params:**
- `cursor` (required) — watermark cursor from snapshot or previous replay response

**Response 200:**
```json
{
  "data": [
    {
      "id": "evt_o_1001",
      "type": "order.created",
      "payload": { "orderId": "ord_01", "status": "pending", "item": "Pizza Margherita", "amount": 12.5 }
    }
  ],
  "meta": { "nextCursor": "cur_o_1020", "requestId": "req_abc" }
}
```

**Error codes:**
- `400 VALIDATION_ERROR` — missing cursor, or topic does not support replay
- `401 UNAUTHORIZED`
- `404 TOPIC_NOT_FOUND`
- `409 REPLAY_CURSOR_EXPIRED` — cursor older than retention window (default 24h); fetch fresh snapshot

**Replay retention:** configurable via `REPLAY_RETENTION_HOURS` env var (default `24`).

---

### `GET /v1/feeds/{topic}/stream`

Opens a Server-Sent Events stream. Test with curl:

```bash
curl -N --no-buffer \
  -H "Authorization: Bearer demo" \
  http://localhost:8080/v1/feeds/orders/stream
```

**Auth:** Evaluated **before** opening the stream. If unauthorized, returns HTTP error immediately — stream is never opened.

**Request headers:**
- `Authorization: Bearer <token>` — required for auth topics
- `Last-Event-ID` — optional, resume from this event ID after disconnect

**Response headers:**
- `Content-Type: text/event-stream`
- `Cache-Control: no-cache`
- `X-Request-ID`

**On connect:** server sends `retry: 3000` to suggest 3-second reconnect delay.

#### SSE Event Format

```
id: evt_o_1021
event: order.updated
data: {"orderId":"ord_22","status":"shipped","updatedAt":"2026-05-03T10:22:01Z"}

```

*(blank line after each event — required by SSE spec)*

#### Heartbeats

During idle periods (no domain events), the server emits heartbeat comments:

```
: heartbeat

```

Interval: configured via `HEARTBEAT_INTERVAL_SECONDS` env var (default `10`).

#### Reconnect Guidance

1. Receive `Last-Event-ID` from the last processed event.
2. On disconnect, wait for `retry` ms (server sends `3000`).
3. Re-connect with `Last-Event-ID` header — server will replay all missed events before resuming live delivery.
4. If you cannot reconnect (e.g., `409 REPLAY_CURSOR_EXPIRED`), fetch a fresh snapshot and restart.

**Error codes (before stream opens):**
- `401 UNAUTHORIZED`
- `503 STREAM_UNAVAILABLE` — retryable; include `Retry-After` when possible

---

## Event Catalogue

### `orders` topic

| Event type | Payload fields |
|-----------|----------------|
| `order.created` | `orderId`, `status: pending`, `item`, `amount` |
| `order.updated` | `orderId`, `status`, `updatedAt` |
| `order.delivered` | `orderId`, `status: delivered`, `deliveredAt` |
| `order.cancelled` | `orderId`, `status: cancelled`, `reason`, `cancelledAt` |

Example:
```json
{ "orderId": "ord_22", "status": "shipped", "updatedAt": "2026-05-03T10:22:01Z" }
```

### `deliveries` topic

| Event type | Payload fields |
|-----------|----------------|
| `delivery.scheduled` | `deliveryId`, `orderId`, `status: scheduled`, `eta` |
| `delivery.dispatched` | `deliveryId`, `orderId`, `status: dispatched`, `dispatchedAt` |
| `delivery.delivered` | `deliveryId`, `orderId`, `status: delivered`, `deliveredAt` |

### `prices` topic (no replay)

| Event type | Payload fields |
|-----------|----------------|
| `price.updated` | `itemId`, `price`, `updatedAt` |

Example:
```json
{ "itemId": "item_A", "price": 10.50, "updatedAt": "2026-05-01T08:00:00Z" }
```

### `incidents` topic

| Event type | Payload fields |
|-----------|----------------|
| `incident.opened` | `incidentId`, `status: open`, `title`, `severity`, `openedAt` |
| `incident.updated` | `incidentId`, `status`, `note`, `updatedAt` |
| `incident.resolved` | `incidentId`, `status: resolved`, `resolvedAt` |

---

## Admin Endpoints (DEBUG_ENDPOINTS=true only)

Enable with `DEBUG_ENDPOINTS=true` env var (never enable in production).

### `POST /v1/admin/events`

Inject an event and fan out to all active stream subscribers.

**Request:**
```json
{ "topic": "orders", "type": "order.updated", "payload": { "orderId": "ord_22", "status": "shipped" } }
```

**Response 201:**
```json
{
  "data": { "id": "evt_o_1021", "type": "order.updated", "topic": "orders", "payload": { ... }, "timestamp": "..." },
  "meta": { "requestId": "req_abc" }
}
```

---

## Debug Endpoints (DEBUG_ENDPOINTS=true only)

### `POST /v1/debug/pause-heartbeats/{topic}`

Stop heartbeats for the given topic. Active streams will go silent on idle — use to test client idle timeout behavior.

### `POST /v1/debug/resume-heartbeats/{topic}`

Resume heartbeats for the given topic.

### `POST /v1/debug/disconnect-after?topic=orders&n=5`

Close the stream connection after `n` events have been delivered. Use to test reconnect behavior.

### `POST /v1/debug/expire-cursor?topic=orders&cursor=cur_o_1000`

Force a cursor to be expired. Next replay attempt with this cursor returns `409 REPLAY_CURSOR_EXPIRED`.

### `POST /v1/debug/inject-malformed?topic=orders`

Queue a malformed SSE line to be sent to the next stream subscriber for that topic. Use to test client SSE parser robustness.

### `POST /v1/debug/stream-unavailable?topic=orders`

Mark stream as unavailable — next connection attempt returns `503` before opening stream.

### `POST /v1/debug/stream-available?topic=orders`

Mark stream as available again.

### `POST /v1/debug/reset`

Reset all debug state (un-expire all cursors, resume heartbeats, clear disconnect counters).

---

## Error Code Reference

| Code | HTTP | Retryable | Description |
|------|------|-----------|-------------|
| `TOPIC_NOT_FOUND` | 404 | No | Topic does not exist |
| `STREAM_UNAVAILABLE` | 503 | Yes | Temporary stream outage |
| `REPLAY_CURSOR_EXPIRED` | 409 | No | Cursor older than retention; fetch fresh snapshot |
| `RATE_LIMITED` | 429 | Yes | Too many requests; see `Retry-After` header |
| `UNAUTHORIZED` | 401 | No | Missing or empty bearer token |
| `VALIDATION_ERROR` | 400 | No | Missing or invalid request parameter |

---

## Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `PORT` | `8080` | Listening port (Railway injects this) |
| `DEBUG_ENDPOINTS` | `false` | Enable admin and debug routes |
| `CORS_PERMISSIVE` | `true` | Allow all origins |
| `REPLAY_RETENTION_HOURS` | `24` | How long cursors remain valid |
| `HEARTBEAT_INTERVAL_SECONDS` | `10` | SSE heartbeat cadence |

---

## Deployment (Railway)

1. Push to GitHub.
2. Create a new Railway project, connect repo.
3. Railway auto-detects the `Dockerfile`.
4. Set env vars: `DEBUG_ENDPOINTS=true` for staging, `false` for production.
5. Health check: `GET /v1/health` returns `200`.

> **Note:** The in-memory store resets on every Railway redeploy. This is intentional — the backend is a showcase, not a durable datastore.

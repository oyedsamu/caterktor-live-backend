package com.caterktor.live.seed

import com.caterktor.live.store.EventStore
import com.caterktor.live.store.SnapshotStore
import kotlinx.serialization.json.*

fun seedAll(eventStore: EventStore, snapshotStore: SnapshotStore) {
    seedOrders(eventStore, snapshotStore)
    seedDeliveries(eventStore, snapshotStore)
    seedPrices(eventStore, snapshotStore)
    seedIncidents(eventStore, snapshotStore)
}

private fun seedOrders(eventStore: EventStore, snapshotStore: SnapshotStore) {
    val store = eventStore["orders"] ?: return

    fun obj(vararg pairs: Pair<String, JsonElement>) = JsonObject(mapOf(*pairs))
    fun str(v: String) = JsonPrimitive(v)

    // 20 seed events: mix of created/updated/delivered/cancelled
    store.seedEvent("order.created",   obj("orderId" to str("ord_01"), "status" to str("pending"),    "item" to str("Pizza Margherita"),  "amount" to JsonPrimitive(12.50)))
    store.seedEvent("order.created",   obj("orderId" to str("ord_02"), "status" to str("pending"),    "item" to str("Burger Deluxe"),     "amount" to JsonPrimitive(15.00)))
    store.seedEvent("order.updated",   obj("orderId" to str("ord_01"), "status" to str("processing"), "updatedAt" to str("2026-05-01T08:10:00Z")))
    store.seedEvent("order.created",   obj("orderId" to str("ord_03"), "status" to str("pending"),    "item" to str("Caesar Salad"),      "amount" to JsonPrimitive(9.00)))
    store.seedEvent("order.updated",   obj("orderId" to str("ord_02"), "status" to str("processing"), "updatedAt" to str("2026-05-01T08:20:00Z")))
    store.seedEvent("order.created",   obj("orderId" to str("ord_04"), "status" to str("pending"),    "item" to str("Fish & Chips"),      "amount" to JsonPrimitive(13.50)))
    store.seedEvent("order.delivered", obj("orderId" to str("ord_01"), "status" to str("delivered"),  "deliveredAt" to str("2026-05-01T09:00:00Z")))
    store.seedEvent("order.updated",   obj("orderId" to str("ord_03"), "status" to str("processing"), "updatedAt" to str("2026-05-01T09:05:00Z")))
    store.seedEvent("order.created",   obj("orderId" to str("ord_05"), "status" to str("pending"),    "item" to str("Pasta Carbonara"),   "amount" to JsonPrimitive(11.00)))
    store.seedEvent("order.cancelled", obj("orderId" to str("ord_02"), "status" to str("cancelled"),  "reason" to str("customer_request"), "cancelledAt" to str("2026-05-01T09:10:00Z")))
    store.seedEvent("order.updated",   obj("orderId" to str("ord_04"), "status" to str("processing"), "updatedAt" to str("2026-05-01T09:15:00Z")))
    store.seedEvent("order.created",   obj("orderId" to str("ord_06"), "status" to str("pending"),    "item" to str("Sushi Platter"),     "amount" to JsonPrimitive(22.00)))
    store.seedEvent("order.delivered", obj("orderId" to str("ord_03"), "status" to str("delivered"),  "deliveredAt" to str("2026-05-01T10:00:00Z")))
    store.seedEvent("order.updated",   obj("orderId" to str("ord_05"), "status" to str("processing"), "updatedAt" to str("2026-05-01T10:05:00Z")))
    store.seedEvent("order.created",   obj("orderId" to str("ord_07"), "status" to str("pending"),    "item" to str("Tacos x3"),          "amount" to JsonPrimitive(10.50)))
    store.seedEvent("order.delivered", obj("orderId" to str("ord_04"), "status" to str("delivered"),  "deliveredAt" to str("2026-05-01T10:30:00Z")))
    store.seedEvent("order.created",   obj("orderId" to str("ord_08"), "status" to str("pending"),    "item" to str("Ramen Bowl"),        "amount" to JsonPrimitive(14.00)))
    store.seedEvent("order.updated",   obj("orderId" to str("ord_06"), "status" to str("processing"), "updatedAt" to str("2026-05-01T10:40:00Z")))
    store.seedEvent("order.created",   obj("orderId" to str("ord_09"), "status" to str("pending"),    "item" to str("Veggie Wrap"),       "amount" to JsonPrimitive(8.50)))
    store.seedEvent("order.updated",   obj("orderId" to str("ord_07"), "status" to str("processing"), "updatedAt" to str("2026-05-01T10:55:00Z")))

    // Snapshot: 10 orders in various states (derived from events above)
    snapshotStore.set(
        "orders",
        listOf(
            obj("orderId" to str("ord_01"), "status" to str("delivered"),  "item" to str("Pizza Margherita"),  "amount" to JsonPrimitive(12.50)),
            obj("orderId" to str("ord_02"), "status" to str("cancelled"),  "item" to str("Burger Deluxe"),     "amount" to JsonPrimitive(15.00)),
            obj("orderId" to str("ord_03"), "status" to str("delivered"),  "item" to str("Caesar Salad"),      "amount" to JsonPrimitive(9.00)),
            obj("orderId" to str("ord_04"), "status" to str("delivered"),  "item" to str("Fish & Chips"),      "amount" to JsonPrimitive(13.50)),
            obj("orderId" to str("ord_05"), "status" to str("processing"), "item" to str("Pasta Carbonara"),   "amount" to JsonPrimitive(11.00)),
            obj("orderId" to str("ord_06"), "status" to str("processing"), "item" to str("Sushi Platter"),     "amount" to JsonPrimitive(22.00)),
            obj("orderId" to str("ord_07"), "status" to str("processing"), "item" to str("Tacos x3"),          "amount" to JsonPrimitive(10.50)),
            obj("orderId" to str("ord_08"), "status" to str("pending"),    "item" to str("Ramen Bowl"),        "amount" to JsonPrimitive(14.00)),
            obj("orderId" to str("ord_09"), "status" to str("pending"),    "item" to str("Veggie Wrap"),       "amount" to JsonPrimitive(8.50)),
            obj("orderId" to str("ord_10"), "status" to str("pending"),    "item" to str("Chicken Tikka"),     "amount" to JsonPrimitive(16.00)),
        ),
        cursor = store.snapshotCursor(),
    )
}

private fun seedDeliveries(eventStore: EventStore, snapshotStore: SnapshotStore) {
    val store = eventStore["deliveries"] ?: return

    fun obj(vararg pairs: Pair<String, JsonElement>) = JsonObject(mapOf(*pairs))
    fun str(v: String) = JsonPrimitive(v)

    store.seedEvent("delivery.scheduled",  obj("deliveryId" to str("del_01"), "orderId" to str("ord_01"), "status" to str("scheduled"),  "eta" to str("2026-05-01T09:00:00Z")))
    store.seedEvent("delivery.scheduled",  obj("deliveryId" to str("del_02"), "orderId" to str("ord_03"), "status" to str("scheduled"),  "eta" to str("2026-05-01T10:00:00Z")))
    store.seedEvent("delivery.dispatched", obj("deliveryId" to str("del_01"), "orderId" to str("ord_01"), "status" to str("dispatched"), "dispatchedAt" to str("2026-05-01T08:30:00Z")))
    store.seedEvent("delivery.scheduled",  obj("deliveryId" to str("del_03"), "orderId" to str("ord_04"), "status" to str("scheduled"),  "eta" to str("2026-05-01T10:30:00Z")))
    store.seedEvent("delivery.delivered",  obj("deliveryId" to str("del_01"), "orderId" to str("ord_01"), "status" to str("delivered"),  "deliveredAt" to str("2026-05-01T09:00:00Z")))
    store.seedEvent("delivery.dispatched", obj("deliveryId" to str("del_02"), "orderId" to str("ord_03"), "status" to str("dispatched"), "dispatchedAt" to str("2026-05-01T09:30:00Z")))
    store.seedEvent("delivery.delivered",  obj("deliveryId" to str("del_02"), "orderId" to str("ord_03"), "status" to str("delivered"),  "deliveredAt" to str("2026-05-01T10:00:00Z")))
    store.seedEvent("delivery.dispatched", obj("deliveryId" to str("del_03"), "orderId" to str("ord_04"), "status" to str("dispatched"), "dispatchedAt" to str("2026-05-01T10:00:00Z")))
    store.seedEvent("delivery.delivered",  obj("deliveryId" to str("del_03"), "orderId" to str("ord_04"), "status" to str("delivered"),  "deliveredAt" to str("2026-05-01T10:30:00Z")))
    store.seedEvent("delivery.scheduled",  obj("deliveryId" to str("del_04"), "orderId" to str("ord_05"), "status" to str("scheduled"),  "eta" to str("2026-05-01T11:00:00Z")))

    snapshotStore.set(
        "deliveries",
        listOf(
            obj("deliveryId" to str("del_01"), "orderId" to str("ord_01"), "status" to str("delivered")),
            obj("deliveryId" to str("del_02"), "orderId" to str("ord_03"), "status" to str("delivered")),
            obj("deliveryId" to str("del_03"), "orderId" to str("ord_04"), "status" to str("delivered")),
            obj("deliveryId" to str("del_04"), "orderId" to str("ord_05"), "status" to str("scheduled")),
        ),
        cursor = store.snapshotCursor(),
    )
}

private fun seedPrices(eventStore: EventStore, snapshotStore: SnapshotStore) {
    val store = eventStore["prices"] ?: return

    fun obj(vararg pairs: Pair<String, JsonElement>) = JsonObject(mapOf(*pairs))
    fun str(v: String) = JsonPrimitive(v)

    val items = listOf("item_A", "item_B", "item_C")
    val prices = listOf(10.00, 14.50, 8.75)

    for (i in 1..15) {
        val item = items[(i - 1) % 3]
        val price = prices[(i - 1) % 3] + (i * 0.10)
        store.seedEvent("price.updated", obj("itemId" to str(item), "price" to JsonPrimitive(price.toBigDecimal().setScale(2).toDouble()), "updatedAt" to str("2026-05-01T0${(i % 10)}:00:00Z")))
    }

    snapshotStore.set(
        "prices",
        listOf(
            obj("itemId" to str("item_A"), "price" to JsonPrimitive(10.50), "currency" to str("USD")),
            obj("itemId" to str("item_B"), "price" to JsonPrimitive(14.50), "currency" to str("USD")),
            obj("itemId" to str("item_C"), "price" to JsonPrimitive(8.75),  "currency" to str("USD")),
        ),
        cursor = store.snapshotCursor(),
    )
}

private fun seedIncidents(eventStore: EventStore, snapshotStore: SnapshotStore) {
    val store = eventStore["incidents"] ?: return

    fun obj(vararg pairs: Pair<String, JsonElement>) = JsonObject(mapOf(*pairs))
    fun str(v: String) = JsonPrimitive(v)

    store.seedEvent("incident.opened",   obj("incidentId" to str("inc_01"), "status" to str("open"),     "title" to str("Payment gateway degraded"),    "severity" to str("high"),   "openedAt" to str("2026-05-01T06:00:00Z")))
    store.seedEvent("incident.updated",  obj("incidentId" to str("inc_01"), "status" to str("open"),     "note" to str("Engineers investigating"),       "updatedAt" to str("2026-05-01T06:30:00Z")))
    store.seedEvent("incident.resolved", obj("incidentId" to str("inc_01"), "status" to str("resolved"), "resolvedAt" to str("2026-05-01T07:00:00Z")))
    store.seedEvent("incident.opened",   obj("incidentId" to str("inc_02"), "status" to str("open"),     "title" to str("Delivery service slowdown"),    "severity" to str("medium"), "openedAt" to str("2026-05-01T08:00:00Z")))
    store.seedEvent("incident.updated",  obj("incidentId" to str("inc_02"), "status" to str("open"),     "note" to str("Monitoring — no action needed"), "updatedAt" to str("2026-05-01T08:45:00Z")))

    snapshotStore.set(
        "incidents",
        listOf(
            obj("incidentId" to str("inc_01"), "status" to str("resolved"), "title" to str("Payment gateway degraded"),  "severity" to str("high")),
            obj("incidentId" to str("inc_02"), "status" to str("open"),     "title" to str("Delivery service slowdown"), "severity" to str("medium")),
        ),
        cursor = store.snapshotCursor(),
    )
}

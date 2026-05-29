package com.caterktor.live.store

import com.caterktor.live.domain.FeedEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.datetime.Clock
import kotlinx.serialization.json.JsonObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

class TopicEventStore(val topicName: String, val prefix: String) {
    private val lock = ReentrantReadWriteLock()
    private val events = mutableListOf<FeedEvent>()
    private val _flow = MutableSharedFlow<FeedEvent>(replay = 0, extraBufferCapacity = 256)
    val flow: SharedFlow<FeedEvent> = _flow.asSharedFlow()

    // nextSeq starts after the implied historical baseline of 1000
    private var nextSeq: Long = 1001L

    // Expired cursors set via debug endpoint
    private val expiredCursors = ConcurrentHashMap.newKeySet<String>()

    fun seedEvent(type: String, payload: JsonObject, seqOverride: Long? = null): FeedEvent {
        return lock.write {
            val seq = seqOverride ?: nextSeq++
            if (seqOverride != null && seqOverride >= nextSeq) nextSeq = seqOverride + 1
            val event = FeedEvent(
                id = eventId(seq),
                type = type,
                payload = payload,
                timestamp = Clock.System.now(),
                sequenceNumber = seq,
                topic = topicName,
            )
            events.add(event)
            event
        }
    }

    suspend fun append(type: String, payload: JsonObject): FeedEvent {
        val event = lock.write {
            val seq = nextSeq++
            FeedEvent(
                id = eventId(seq),
                type = type,
                payload = payload,
                timestamp = Clock.System.now(),
                sequenceNumber = seq,
                topic = topicName,
            ).also { events.add(it) }
        }
        _flow.emit(event)
        return event
    }

    fun eventsAfterCursor(cursor: String): List<FeedEvent> = lock.read {
        val seq = parseCursorSeq(cursor) ?: return@read events.toList()
        events.filter { it.sequenceNumber > seq }
    }

    fun isCursorExpired(cursor: String): Boolean = expiredCursors.contains(cursor)

    fun expireCursor(cursor: String) { expiredCursors.add(cursor) }

    fun currentCursor(): String = lock.read { cursorId(maxOf(nextSeq - 1, 1000L)) }

    fun snapshotCursor(): String = "cur_${prefix}_1000"

    fun allEvents(): List<FeedEvent> = lock.read { events.toList() }

    fun lastEventId(): String? = lock.read { events.lastOrNull()?.id }

    private fun eventId(seq: Long) = "evt_${prefix}_${seq.toString().padStart(3, '0')}"
    private fun cursorId(seq: Long) = "cur_${prefix}_${seq.toString().padStart(3, '0')}"

    private fun parseCursorSeq(cursor: String): Long? {
        val parts = cursor.split("_")
        return parts.lastOrNull()?.toLongOrNull()
    }
}

class EventStore {
    private val stores = ConcurrentHashMap<String, TopicEventStore>()

    fun register(topicName: String, prefix: String): TopicEventStore {
        val store = TopicEventStore(topicName, prefix)
        stores[topicName] = store
        return store
    }

    operator fun get(topic: String): TopicEventStore? = stores[topic]

    fun topics(): Set<String> = stores.keys
}

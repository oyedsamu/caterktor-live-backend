package com.caterktor.live.store

import kotlinx.serialization.json.JsonObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

data class Snapshot(
    val topic: String,
    val state: List<JsonObject>,
    val cursor: String,
)

class SnapshotStore {
    private val snapshots = ConcurrentHashMap<String, Snapshot>()
    private val locks = ConcurrentHashMap<String, ReentrantReadWriteLock>()

    fun set(topic: String, state: List<JsonObject>, cursor: String) {
        lockFor(topic).write {
            snapshots[topic] = Snapshot(topic, state, cursor)
        }
    }

    fun get(topic: String): Snapshot? = lockFor(topic).read { snapshots[topic] }

    private fun lockFor(topic: String) = locks.getOrPut(topic) { ReentrantReadWriteLock() }
}

package com.caterktor.live.debug

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

object DebugState {
    private val heartbeatPaused = ConcurrentHashMap<String, AtomicBoolean>()
    private val disconnectAfterN = ConcurrentHashMap<String, AtomicInteger>()
    private val streamUnavailable = ConcurrentHashMap<String, AtomicBoolean>()
    private val malformedPending = ConcurrentHashMap<String, AtomicBoolean>()

    fun pauseHeartbeats(topic: String) {
        heartbeatPaused.getOrPut(topic) { AtomicBoolean(false) }.set(true)
    }

    fun resumeHeartbeats(topic: String) {
        heartbeatPaused.getOrPut(topic) { AtomicBoolean(false) }.set(false)
    }

    fun isHeartbeatPaused(topic: String): Boolean =
        heartbeatPaused[topic]?.get() ?: false

    fun setDisconnectAfterN(topic: String, n: Int) {
        disconnectAfterN[topic] = AtomicInteger(n)
    }

    fun consumeDisconnectToken(topic: String): Boolean {
        val counter = disconnectAfterN[topic] ?: return false
        val remaining = counter.decrementAndGet()
        if (remaining < 0) {
            disconnectAfterN.remove(topic)
            return false
        }
        return remaining == 0
    }

    fun setStreamUnavailable(topic: String, unavailable: Boolean) {
        streamUnavailable.getOrPut(topic) { AtomicBoolean(false) }.set(unavailable)
    }

    fun isStreamUnavailable(topic: String): Boolean =
        streamUnavailable[topic]?.get() ?: false

    fun injectMalformed(topic: String) {
        malformedPending.getOrPut(topic) { AtomicBoolean(false) }.set(true)
    }

    fun consumeMalformed(topic: String): Boolean {
        return malformedPending[topic]?.getAndSet(false) ?: false
    }

    fun reset() {
        heartbeatPaused.clear()
        disconnectAfterN.clear()
        streamUnavailable.clear()
        malformedPending.clear()
    }
}

package com.opencode2api.android.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.ArrayDeque

/** One captured line from the gateway's stdout/stderr. */
data class LogLine(
    val timestamp: Long,
    val stream: Stream,
    val text: String,
) {
    enum class Stream { STDOUT, STDERR }
}

/**
 * Bounded, in-memory buffer of the gateway's own output.
 *
 * This is deliberately separate from the gateway's `/api/logs` surface: it
 * exists to explain failures that happen *before* the HTTP server is listening,
 * when there is no API to ask. It is capped so a chatty run cannot grow without
 * bound over a multi-day uptime.
 */
class LogBus(private val capacity: Int = 2000) {

    private val lock = Any()
    private val buffer = ArrayDeque<LogLine>(capacity)
    private val _revision = MutableStateFlow(0L)

    /** Bumped on every append, so Compose can cheaply observe "something new". */
    val revision: StateFlow<Long> = _revision.asStateFlow()

    fun append(stream: LogLine.Stream, text: String) {
        val line = LogLine(System.currentTimeMillis(), stream, text)
        synchronized(lock) {
            if (buffer.size >= capacity) buffer.removeFirst()
            buffer.addLast(line)
        }
        _revision.value += 1
    }

    fun snapshot(): List<LogLine> = synchronized(lock) { buffer.toList() }

    fun clear() {
        synchronized(lock) { buffer.clear() }
        _revision.value += 1
    }
}

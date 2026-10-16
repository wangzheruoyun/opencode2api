package com.opencode2api.android.ui.util

import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Turning the gateway's numbers into something readable.
 *
 * The API reports every count as an exact integer and every time as RFC 3339.
 * Screens want neither: a token counter is 12345678 on one line and 12.3M on
 * the next, and nobody reads a column of nanosecond timestamps. Everything here
 * is total — a malformed value renders as `-` rather than throwing, because a
 * dashboard that survives a bad field is worth more than one that is precise
 * about it.
 */

/** `12345` -> `12.3K`. Counters here run to millions and never need precision. */
fun formatCount(value: Long): String = when {
    value < 1_000 -> value.toString()
    value < 1_000_000 -> "%.1fK".format(value / 1_000.0)
    else -> "%.1fM".format(value / 1_000_000.0)
}

/** `0.87` -> `87.0%`; a non-finite or absent rate renders as `-`. */
fun formatPercent(fraction: Double?): String =
    if (fraction == null || fraction.isNaN() || fraction.isInfinite()) {
        "-"
    } else {
        "%.1f%%".format(fraction * 100)
    }

/** Milliseconds, in the unit a human would say it in. */
fun formatMillis(ms: Long): String = when {
    ms <= 0 -> "-"
    ms < 1_000 -> "${ms}ms"
    ms < 60_000 -> "%.1fs".format(ms / 1_000.0)
    else -> "%.1fm".format(ms / 60_000.0)
}

/** Durations up to a day, as `2h 04m` / `3m 12s` / `8s`. */
fun formatDuration(millis: Long): String {
    val seconds = (millis / 1_000).coerceAtLeast(0)
    val hours = seconds / 3_600
    val minutes = (seconds % 3_600) / 60
    val rest = seconds % 60
    return when {
        hours > 0 -> "%dh %02dm".format(hours, minutes)
        minutes > 0 -> "%dm %02ds".format(minutes, rest)
        else -> "${rest}s"
    }
}

private val LocalTimestamp = DateTimeFormatter.ofPattern("MM-dd HH:mm")

/**
 * An RFC 3339 instant in the device's own zone, to the minute.
 *
 * The gateway writes UTC and the phone is very likely not in it, so the offset
 * is honoured rather than the string being sliced: "resets at 09:00" has to
 * mean 09:00 where the person is standing.
 */
fun formatTimestamp(iso: String?): String {
    if (iso.isNullOrBlank()) return "-"
    return runCatching {
        OffsetDateTime.parse(iso).atZoneSameInstant(ZoneId.systemDefault()).format(LocalTimestamp)
    }.getOrElse {
        // A value that is not RFC 3339 is still worth showing verbatim.
        iso.substringBefore('.').replace('T', ' ')
    }
}

/**
 * [formatTimestamp] for the times the gateway marks optional.
 *
 * Go writes an unset `time.Time` as year 1 rather than omitting the field, so a
 * model that has never been probed arrives as `0001-01-01T00:00:00Z`. Rendering
 * that as a date would be worse than rendering nothing.
 */
fun formatPendingTimestamp(iso: String?, fallback: String): String =
    if (iso.isNullOrBlank() || iso.startsWith("0001-")) fallback else formatTimestamp(iso)

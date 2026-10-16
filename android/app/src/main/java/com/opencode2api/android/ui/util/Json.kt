package com.opencode2api.android.ui.util

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

/**
 * Dotted-path readers for the admin API's JSON.
 *
 * The gateway's responses are nested a few levels deep in places —
 * `usage.last_hour.tokens.total_tokens` — and a screen that spells that out
 * with `jsonObject["usage"]?.jsonObject[...]` at every use site is mostly
 * punctuation. These keep the call sites reading like the field paths the Go
 * handlers define.
 *
 * Every reader is total: a missing or mistyped field yields the fallback rather
 * than throwing, because a screen rendering a `-` where a number should be is a
 * better outcome than a crash on a field upstream renamed.
 */

/** Resolves `a.b.c`, objects only. Never needs array indexing. */
private fun JsonObject.element(path: String): JsonElement? {
    var current: JsonElement = this
    for (segment in path.split('.')) {
        current = (current as? JsonObject)?.get(segment) ?: return null
    }
    return current
}

fun JsonObject.obj(path: String): JsonObject? = element(path) as? JsonObject

fun JsonObject.arr(path: String): JsonArray? = element(path) as? JsonArray

/** The raw text of any primitive — numbers included, for display. */
fun JsonObject.raw(path: String): String? =
    (element(path) as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull

/** A string, and only a string: numbers do not silently become text. */
fun JsonObject.str(path: String, fallback: String = ""): String =
    (element(path) as? JsonPrimitive)?.takeIf { it.isString }?.content ?: fallback

fun JsonObject.num(path: String): Double? =
    (element(path) as? JsonPrimitive)?.doubleOrNull

fun JsonObject.long(path: String): Long =
    (element(path) as? JsonPrimitive)?.longOrNull ?: 0L

fun JsonObject.int(path: String): Int =
    (element(path) as? JsonPrimitive)?.intOrNull ?: 0

fun JsonObject.bool(path: String, fallback: Boolean = false): Boolean =
    (element(path) as? JsonPrimitive)?.booleanOrNull ?: fallback

/** Fields written as JSON `null` and fields that are absent both count as unset. */
fun JsonObject.has(path: String): Boolean = when (val value = element(path)) {
    null, is JsonNull -> false
    else -> value !is JsonPrimitive || value.contentOrNull != null
}

fun JsonObject.list(path: String): List<JsonObject> =
    arr(path)?.mapNotNull { it as? JsonObject } ?: emptyList()

fun JsonObject.stringList(path: String): List<String> =
    arr(path)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull } ?: emptyList()

/** Renders a subtree back out for the monospaced blocks. */
private val PrettyJson = Json { prettyPrint = true }

fun JsonElement.pretty(): String =
    runCatching { PrettyJson.encodeToString(JsonElement.serializer(), this) }.getOrDefault(toString())

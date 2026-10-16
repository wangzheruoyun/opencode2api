package com.opencode2api.android.data

import android.content.Context
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.security.SecureRandom

/**
 * Reads and writes the gateway's `config.json`.
 *
 * The document is kept as a [JsonObject] rather than being deserialized into a
 * Kotlin data class. Upstream's schema grows often — the release notes for the
 * last few versions have each added a block — and a typed mirror would silently
 * drop anything this app did not know about the first time it saved. Editing
 * the tree in place means fields added upstream survive a round-trip through
 * the Android UI untouched.
 */
class GatewayConfigStore(private val context: Context) {

    private val json = Json {
        prettyPrint = true
        prettyPrintIndent = "  "
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    val file: File get() = File(context.filesDir, FILE_NAME)

    /** `true` once a config exists on disk (i.e. this is not a first run). */
    fun exists(): Boolean = file.isFile

    /**
     * Loads the config, writing a generated default first if there is none.
     * Normalizes nothing else: what upstream would write is what this returns.
     */
    fun loadOrCreate(): JsonObject {
        if (!file.isFile) {
            val created = defaultConfig()
            save(created)
            return created
        }
        return runCatching { json.parseToJsonElement(file.readText()) as JsonObject }
            .getOrElse { defaultConfig() }
    }

    fun load(): JsonObject? =
        runCatching { json.parseToJsonElement(file.readText()) as JsonObject }.getOrNull()

    /**
     * Writes atomically, mirroring upstream's own `SaveAtomic`: the temporary
     * file is created beside the target so the final rename stays on one
     * filesystem, and the previous file is kept as `config.json.bak`.
     */
    fun save(config: JsonObject) {
        val target = file
        target.parentFile?.mkdirs()
        val temp = File.createTempFile(".config-", ".tmp", target.parentFile)
        try {
            temp.writeText(json.encodeToString(JsonObject.serializer(), config))
            if (target.isFile) {
                target.copyTo(File(target.parentFile, "$FILE_NAME.bak"), overwrite = true)
            }
            if (!temp.renameTo(target)) {
                temp.copyTo(target, overwrite = true)
                temp.delete()
            }
        } finally {
            if (temp.exists()) temp.delete()
        }
    }

    // ---- typed accessors -------------------------------------------------
    // Kept here so callers never have to think about JsonElement unwrapping and
    // so a missing or mistyped field degrades to a sane default.

    fun string(config: JsonObject, path: String, fallback: String = ""): String =
        config.stringOrNull(path) ?: fallback

    fun stringList(config: JsonObject, path: String): List<String> =
        (config.element(path) as? JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.content }
            ?: emptyList()

    fun bool(config: JsonObject, path: String, fallback: Boolean = false): Boolean =
        (config.element(path) as? JsonPrimitive)?.booleanOrNull ?: fallback

    fun int(config: JsonObject, path: String, fallback: Int = 0): Int =
        (config.element(path) as? JsonPrimitive)?.intOrNull ?: fallback

    /**
     * Resolves a dotted path, e.g. `webui.listen`. Objects only — array
     * indexing is never needed by the screens that use this.
     */
    private fun JsonObject.element(path: String): JsonElement? {
        var current: JsonElement = this
        for (segment in path.split('.')) {
            current = (current as? JsonObject)?.get(segment) ?: return null
        }
        return current
    }

    private fun JsonObject.stringOrNull(path: String): String? =
        (element(path) as? JsonPrimitive)?.takeIf { it.isString }?.content

    // ---- derived values --------------------------------------------------

    /** Port the OpenAI/Anthropic-compatible API is served on. */
    fun apiPort(config: JsonObject): Int =
        portOf(string(config, "listen"), DEFAULT_API_PORT)

    /** Port the bundled web admin UI is served on, or `null` when disabled. */
    fun webUiPort(config: JsonObject): Int? =
        if (!bool(config, "webui.enabled", true)) {
            null
        } else {
            portOf(string(config, "webui.listen"), DEFAULT_WEBUI_PORT)
        }

    /**
     * Extracts the port from a `host:port` listen address. Upstream validates
     * these on load, so a malformed value here means the file was edited by
     * hand; falling back to the default keeps the UI usable rather than
     * crashing on someone's typo.
     */
    private fun portOf(address: String, fallback: Int): Int {
        if (address.isBlank()) return fallback
        val separator = address.lastIndexOf(':')
        if (separator < 0) return fallback
        return address.substring(separator + 1).trim().toIntOrNull()?.takeIf { it in 1..65535 }
            ?: fallback
    }

    // ---- default document ------------------------------------------------

    /**
     * The shipped example config with two changes that matter on a phone:
     * credentials are random rather than the literal `change-this-*` strings,
     * and the admin UI binds loopback instead of `0.0.0.0` so installing the
     * app does not publish an admin console to whatever Wi-Fi it joins.
     */
    private fun defaultConfig(): JsonObject {
        val text = DEFAULT_TEMPLATE
            .replace(SERVER_KEY_TOKEN, randomToken(24))
            .replace(ADMIN_PASSWORD_TOKEN, randomToken(18))
        return json.parseToJsonElement(text) as JsonObject
    }

    private fun randomToken(bytes: Int): String {
        val buffer = ByteArray(bytes)
        SecureRandom().nextBytes(buffer)
        return buffer.joinToString("") { "%02x".format(it) }
    }

    companion object {
        const val FILE_NAME = "config.json"

        const val DEFAULT_API_PORT = 8080
        const val DEFAULT_WEBUI_PORT = 8081

        // Placeholders rather than $name so the raw string below contains no
        // Kotlin template expressions.
        private const val SERVER_KEY_TOKEN = "@@SERVER_KEY@@"
        private const val ADMIN_PASSWORD_TOKEN = "@@ADMIN_PASSWORD@@"

        private val DEFAULT_TEMPLATE = """
            {
              "listen": "127.0.0.1:8080",
              "server_keys": ["@@SERVER_KEY@@"],
              "zen_keys": [],
              "go_keys": [],
              "anonymous": false,
              "proxies": ["direct"],
              "proxyfile": "",
              "upstream": {
                "zen": "https://opencode.ai/zen",
                "go": "https://opencode.ai/zen/go",
                "anonymous": "https://opencode.ai/inference"
              },
              "retry": {
                "max_attempts": 3,
                "timeout_seconds": 300
              },
              "models": {
                "refresh_seconds": 300,
                "protocols": {},
                "free_only": false
              },
              "performance": {
                "max_idle_conns": 64,
                "max_idle_conns_per_host": 16,
                "max_conns_per_host": 0,
                "idle_conn_timeout_seconds": 120,
                "connect_timeout_seconds": 5,
                "failure_cooldown_seconds": 15,
                "attempt_timeout_seconds": 0,
                "first_event_timeout_seconds": 0
              },
              "logging": {
                "level": "info",
                "ring_size": 2000,
                "dump_request_bodies": false
              },
              "webui": {
                "enabled": true,
                "listen": "127.0.0.1:8081",
                "username": "admin",
                "password": "@@ADMIN_PASSWORD@@",
                "session_ttl_minutes": 720
              },
              "prefer": "go",
              "reasoning": {
                "effort": "high",
                "effort_by_model": {}
              },
              "rotation": {
                "sota": {
                  "alias": "sota",
                  "order": []
                },
                "sweet": {
                  "alias": "sweet",
                  "order": []
                },
                "first_output_seconds": 30
              }
            }
        """.trimIndent()
    }
}

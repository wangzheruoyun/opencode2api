package com.opencode2api.android.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.util.concurrent.TimeUnit

/**
 * Client for the gateway's own admin API.
 *
 * Two things about that API shape everything here:
 *
 *  - Auth is a session cookie plus a CSRF header. The cookie is `HttpOnly` and
 *    `SameSite=Strict`, so it has to be carried by a real cookie jar rather than
 *    read back out of a response body. Login hands back the CSRF token in the
 *    body, and every non-GET needs it in `X-CSRF-Token`.
 *  - The server also rejects a mutating request whose `Origin` header disagrees
 *    with its own host. OkHttp sends no `Origin`, which is why this works at all
 *    from a native client; adding one would break every write.
 *
 * Everything is loopback, so no TLS and no certificate pinning.
 */
class AdminClient(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private val client = OkHttpClient.Builder()
        .cookieJar(PersistentCookieJar(prefs))
        .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    /**
     * The CSRF token for the current session. Held in memory only: it is
     * meaningless once the process restarts, because the session it belongs to
     * lives in the gateway's memory too.
     */
    @Volatile
    var csrfToken: String? = null
        private set

    @Volatile
    var username: String? = null
        private set

    @Volatile
    var authenticated: Boolean = false
        private set

    fun baseUrl(port: Int): String = "http://127.0.0.1:$port"

    suspend fun login(baseUrl: String, user: String, password: String): Result<Unit> =
        execute(
            Request.Builder()
                .url("$baseUrl/api/auth/login")
                .post(
                    jsonObjectOf("username" to user, "password" to password)
                        .toString()
                        .toRequestBody(JSON_MEDIA_TYPE),
                )
                .build(),
            method = "POST",
        ).map { body ->
            csrfToken = body["csrf_token"]?.jsonPrimitive?.contentOrNull()
            username = body["username"]?.jsonPrimitive?.contentOrNull()
            authenticated = true
        }

    /** Reads the live session, which also re-syncs [csrfToken] after a restart. */
    suspend fun session(baseUrl: String): Result<Unit> =
        execute(Request.Builder().url("$baseUrl/api/auth/session").get().build(), method = "GET")
            .map { body ->
                username = body["username"]?.jsonPrimitive?.contentOrNull()
                csrfToken = body["csrf_token"]?.jsonPrimitive?.contentOrNull()
                authenticated = true
            }

    suspend fun logout(baseUrl: String): Result<Unit> {
        val result = execute(
            Request.Builder().url("$baseUrl/api/auth/logout").post(EMPTY_BODY).build(),
            method = "POST",
        )
        csrfToken = null
        username = null
        authenticated = false
        return result.map { }
    }

    suspend fun get(baseUrl: String, path: String): Result<JsonObject> =
        execute(Request.Builder().url(baseUrl + path).get().build(), method = "GET")

    suspend fun post(baseUrl: String, path: String, body: JsonObject? = null): Result<JsonObject> =
        execute(
            Request.Builder().url(baseUrl + path).post((body ?: JsonObject(emptyMap())).toString().toRequestBody(JSON_MEDIA_TYPE)).build(),
            method = "POST",
        )

    suspend fun put(baseUrl: String, path: String, body: JsonObject): Result<JsonObject> =
        execute(
            Request.Builder().url(baseUrl + path).put(body.toString().toRequestBody(JSON_MEDIA_TYPE)).build(),
            method = "PUT",
        )

    suspend fun patch(baseUrl: String, path: String, body: JsonObject): Result<JsonObject> =
        execute(
            Request.Builder().url(baseUrl + path).patch(body.toString().toRequestBody(JSON_MEDIA_TYPE)).build(),
            method = "PATCH",
        )

    suspend fun delete(baseUrl: String, path: String): Result<JsonObject> =
        execute(
            Request.Builder().url(baseUrl + path).delete().build(),
            method = "DELETE",
        )

    private suspend fun execute(request: Request, method: String): Result<JsonObject> =
        withContext(Dispatchers.IO) {
            runCatching {
                val builder = request.newBuilder().header("Accept", "application/json")
                if (method != "GET" && method != "HEAD") {
                    csrfToken?.let { builder.header("X-CSRF-Token", it) }
                }
                client.newCall(builder.build()).execute().use { response -> parse(response) }
            }
        }

    private fun parse(response: Response): JsonObject {
        val text = response.body?.string().orEmpty()
        val parsed = if (text.isBlank()) {
            JsonObject(emptyMap())
        } else {
            runCatching { json.parseToJsonElement(text).jsonObject }.getOrElse { JsonObject(emptyMap()) }
        }

        if (!response.isSuccessful) {
            // Every admin endpoint reports failures as {"error":{code,message}};
            // surfacing the server's own message beats a bare status code.
            val message = parsed["error"]?.jsonObject
                ?.get("message")?.jsonPrimitive?.contentOrNull()
                ?: "HTTP ${response.code}"
            if (response.code == 401) {
                authenticated = false
                csrfToken = null
            }
            throw AdminApiException(response.code, message)
        }
        return parsed
    }

    private companion object {
        const val PREFS = "admin_session"
        const val CONNECT_TIMEOUT_SECONDS = 5L
        const val READ_TIMEOUT_SECONDS = 30L
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        val EMPTY_BODY = ByteArray(0).toRequestBody(JSON_MEDIA_TYPE)
    }
}

/** A non-2xx answer from the admin API, already unpacked from its envelope. */
class AdminApiException(val statusCode: Int, message: String) : Exception(message)

internal fun jsonObjectOf(vararg pairs: Pair<String, String>): JsonObject =
    JsonObject(pairs.associate { (key, value) -> key to JsonPrimitive(value) })

/**
 * Session cookies, kept in SharedPreferences.
 *
 * The gateway's session is in-memory, so the cookie outliving the app only
 * helps across an app restart — which is common enough (a config change that
 * restarts the process) to be worth the twenty lines.
 */
private class PersistentCookieJar(private val prefs: android.content.SharedPreferences) : CookieJar {

    private val lock = Any()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (cookies.isEmpty()) return
        synchronized(lock) {
            val stored = prefs.getStringSet(KEY, emptySet())!!.toMutableSet()
            // Cookies are keyed by host+name so a stale session for another
            // port cannot shadow the current one.
            cookies.forEach { cookie ->
                if (cookie.expiresAt < System.currentTimeMillis()) {
                    stored.removeAll { it.startsWith("${url.host}|${cookie.name}|") }
                } else {
                    stored.removeAll { it.startsWith("${url.host}|${cookie.name}|") }
                    stored.add("${url.host}|${cookie.name}|${cookie.expiresAt}|${cookie.value}")
                }
            }
            prefs.edit().putStringSet(KEY, stored).apply()
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        synchronized(lock) {
            val stored = prefs.getStringSet(KEY, emptySet()).orEmpty()
            val valid = mutableListOf<String>()
            val result = mutableListOf<Cookie>()
            stored.forEach { entry ->
                val parts = entry.split('|', limit = 4)
                if (parts.size != 4 || parts[0] != url.host) return@forEach
                val expires = parts[2].toLongOrNull() ?: return@forEach
                if (expires < now) return@forEach
                valid.add(entry)
                result.add(
                    Cookie.Builder()
                        .name(parts[1])
                        .value(parts[3])
                        .hostOnlyDomain(url.host)
                        .expiresAt(expires)
                        .build(),
                )
            }
            if (valid.size != stored.size) {
                prefs.edit().putStringSet(KEY, valid.toSet()).apply()
            }
            return result
        }
    }

    private companion object {
        const val KEY = "cookies"
    }
}

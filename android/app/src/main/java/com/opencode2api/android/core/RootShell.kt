package com.opencode2api.android.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Minimal `su` bridge.
 *
 * A full root library (libsu) would give a bound service and richer plumbing,
 * but the gateway only needs three things from root: prove it exists, run one
 * command to spawn, and run one command to kill. Shelling out to `su` covers
 * that without adding a dependency whose AIDL service has to stay alive.
 *
 * Everything here is best-effort. A device without root is a normal, fully
 * supported configuration, so detection failures must resolve to "no", never
 * to an exception that reaches the UI.
 */
object RootShell {

    private const val PROBE_TIMEOUT_MS = 4_000L
    private const val COMMAND_TIMEOUT_MS = 10_000L

    /** Cached so the UI can ask on every recomposition without forking `su`. */
    @Volatile
    private var cached: Boolean? = null

    suspend fun isAvailable(): Boolean {
        cached?.let { return it }
        val result = probe()
        cached = result
        return result
    }

    /** Forces the next [isAvailable] to re-probe. */
    fun invalidate() {
        cached = null
    }

    private suspend fun probe(): Boolean = withContext(Dispatchers.IO) {
        val su = findSuBinary() ?: return@withContext false
        val output = withTimeoutOrNull(PROBE_TIMEOUT_MS) {
            runCatching {
                val process = ProcessBuilder(su, "-c", "id")
                    .redirectErrorStream(true)
                    .start()
                val text = process.inputStream.bufferedReader().use { it.readText() }
                if (!process.waitFor(PROBE_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                    process.destroyForcibly()
                    return@runCatching null
                }
                text
            }.getOrNull()
        }
        output?.contains("uid=0") == true
    }

    /**
     * Runs [command] as root and returns its combined output, or `null` if the
     * command could not be run at all.
     */
    suspend fun run(command: String): String? = withContext(Dispatchers.IO) {
        val su = findSuBinary() ?: return@withContext null
        withTimeoutOrNull(COMMAND_TIMEOUT_MS) {
            runCatching {
                val process = ProcessBuilder(su, "-c", command)
                    .redirectErrorStream(true)
                    .start()
                val text = process.inputStream.bufferedReader().use { it.readText() }
                if (!process.waitFor(COMMAND_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                    process.destroyForcibly()
                    return@runCatching null
                }
                text
            }.getOrNull()
        }
    }

    /**
     * Best-effort path to `su`. Android's `PATH` for app processes is short and
     * does not always include the module's bind-mount, so the well-known
     * locations are checked explicitly.
     */
    private fun findSuBinary(): String? {
        val candidates = listOf(
            "/system/bin/su",
            "/system/xbin/su",
            "/sbin/su",
            "/su/bin/su",
            "/debug_ramdisk/su",
            "/apex/com.android.virt/bin/su",
        )
        candidates.firstOrNull { File(it).let { f -> f.exists() && f.canExecute() } }
            ?.let { return it }

        // Managed/module-based roots (KernelSU, Magisk) place `su` somewhere on
        // PATH and not always in a fixed directory.
        return runCatching {
            val process = ProcessBuilder("sh", "-c", "command -v su")
                .redirectErrorStream(true)
                .start()
            val text = process.inputStream.bufferedReader().use { it.readText() }.trim()
            if (process.waitFor(2, TimeUnit.SECONDS) && text.isNotEmpty()) {
                text.lineSequence().firstOrNull { it.startsWith("/") }
            } else {
                null
            }
        }.getOrNull()
    }
}

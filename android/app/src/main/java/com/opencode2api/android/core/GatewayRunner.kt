package com.opencode2api.android.core

import android.content.Context
import com.opencode2api.android.data.GatewayConfigStore
import com.opencode2api.android.data.apiPort
import com.opencode2api.android.data.webUiPort
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.TimeUnit

/**
 * Owns the lifecycle of the bundled gateway process.
 *
 * There is exactly one instance, held by [GatewayRuntime]. The UI never spawns
 * anything itself: it observes [state] and calls [start]/[stop], so the
 * foreground service and the Compose screens can never disagree about whether
 * the gateway is up.
 */
class GatewayRunner(
    private val context: Context,
    private val configStore: GatewayConfigStore,
    private val logs: LogBus,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow<GatewayState>(GatewayState.Stopped)
    val state: StateFlow<GatewayState> = _state.asStateFlow()

    private val _resolvedMode = MutableStateFlow<ResolvedMode?>(null)

    /** Which mode the last start actually used, and any fallback note. */
    val resolvedMode: StateFlow<ResolvedMode?> = _resolvedMode.asStateFlow()

    private var process: Process? = null
    private var pumpJobs = mutableListOf<Job>()

    /**
     * Serializes start/stop/restart. Without it, a stop racing a start can
     * orphan a process holding the API port, which then looks like a
     * "port already in use" failure on the next start.
     */
    private val controlMutex = Mutex()

    private val pidFile: File get() = File(context.filesDir, "gateway.pid")

    val isActive: Boolean
        get() = _state.value.let { it is GatewayState.Running || it is GatewayState.Starting }

    // ---- public control --------------------------------------------------

    /**
     * Starts the gateway and waits for it to answer on its API port.
     *
     * Resolves [RunMode.AUTO] to root-or-user, spawns, pumps output into the
     * log buffer, then TCP-probes the API port. Returns only once the outcome
     * is known, so callers can report a real failure instead of a hopeful
     * "starting" that never resolves.
     */
    suspend fun start(mode: RunMode = RunMode.AUTO): Result<Unit> =
        controlMutex.withLock { withContext(Dispatchers.IO) { doStart(mode) } }

    private suspend fun doStart(mode: RunMode): Result<Unit> {
        // A start over a live gateway is a restart, which is what the UI means
        // when the config changed underneath it.
        if (isActive) doStop()

        val binary = GatewayBinary.path(context)
        if (!binary.isFile) {
            return fail(
                "未找到内置网关二进制(${GatewayBinary.FILE_NAME})。" +
                    "请确认安装的是 arm64-v8a 或 x86_64 版本。",
            )
        }
        if (!binary.canExecute()) {
            return fail("内置网关二进制没有执行权限,安装可能不完整。")
        }

        val config = configStore.loadOrCreate()
        val resolved = resolveMode(mode)
        _resolvedMode.value = resolved
        resolved.note?.let { logs.append(LogLine.Stream.STDERR, "[app] $it") }

        val since = System.currentTimeMillis()
        _state.value = GatewayState.Starting(since, resolved.isRoot)

        val spawned = runCatching { spawn(resolved, binary, configStore.file) }
            .getOrElse { return fail(it.message ?: "无法启动网关进程") }

        process = spawned
        pumpOutput(spawned)

        val apiPort = configStore.apiPort(config)
        val webUiPort = configStore.webUiPort(config)

        if (!awaitReady(spawned, apiPort, READY_TIMEOUT_MS)) {
            val tail = logs.snapshot().takeLast(6).joinToString("\n") { it.text }
            stopProcess(spawned, resolved)
            process = null
            _state.value = GatewayState.Failed("网关未在 ${READY_TIMEOUT_MS / 1000} 秒内就绪")
            return Result.failure(
                IllegalStateException(
                    "网关启动超时。最近输出:\n$tail",
                ),
            )
        }

        _state.value = GatewayState.Running(since, resolved.isRoot, apiPort, webUiPort)
        return Result.success(Unit)
    }

    /** Stops the gateway, escalating from SIGTERM to SIGKILL. */
    suspend fun stop() = controlMutex.withLock { withContext(Dispatchers.IO) { doStop() } }

    /** Caller must already hold [controlMutex]. */
    private suspend fun doStop() {
        val current = process
        if (current == null) {
            _state.value = GatewayState.Stopped
            return
        }
        val mode = _resolvedMode.value ?: ResolvedMode(RunMode.USER, isRoot = false)
        stopProcess(current, mode)
        process = null
        _state.value = GatewayState.Stopped
    }

    suspend fun restart(mode: RunMode = RunMode.AUTO): Result<Unit> =
        controlMutex.withLock {
            withContext(Dispatchers.IO) {
                doStop()
                doStart(mode)
            }
        }

    /**
     * The gateway has no SIGHUP reload, so the config is re-read by restarting.
     * (`/api/config/reload` exists, but it needs an authenticated admin session
     * and only re-reads what is already on disk.)
     */
    suspend fun reload(mode: RunMode = RunMode.AUTO): Result<Unit> = restart(mode)

    // ---- spawning --------------------------------------------------------

    private suspend fun resolveMode(requested: RunMode): ResolvedMode = when (requested) {
        RunMode.USER -> ResolvedMode(RunMode.USER, isRoot = false)
        RunMode.ROOT -> if (RootShell.isAvailable()) {
            ResolvedMode(RunMode.ROOT, isRoot = true)
        } else {
            ResolvedMode(RunMode.USER, isRoot = false, note = "未检测到 root,已回退到普通应用模式")
        }

        RunMode.AUTO -> if (RootShell.isAvailable()) {
            ResolvedMode(RunMode.ROOT, isRoot = true, note = "检测到 root,使用 Root 模式启动")
        } else {
            ResolvedMode(RunMode.USER, isRoot = false)
        }
    }

    /**
     * Spawns the process.
     *
     * Under root the binary is started by a shell that first records its own
     * PID — after `exec` the binary inherits it — because the PID the JVM can
     * see belongs to `su`, not to the gateway, and killing `su` would leave the
     * gateway orphaned and still holding the port.
     */
    private fun spawn(
        resolved: ResolvedMode,
        binary: File,
        configFile: File,
    ): Process {
        val workingDir = context.filesDir
        return if (resolved.isRoot) {
            pidFile.delete()
            val command = buildString {
                append("cd ").append(quote(workingDir.absolutePath)).append(" && ")
                append("echo \$\$ > ").append(quote(pidFile.absolutePath)).append(" && ")
                append("exec ").append(quote(binary.absolutePath))
                append(" -config ").append(quote(configFile.absolutePath))
            }
            ProcessBuilder("su", "-c", command)
                .directory(workingDir)
                .apply { environment().applyDefaults(workingDir) }
                .redirectErrorStream(false)
                .start()
        } else {
            ProcessBuilder(binary.absolutePath, "-config", configFile.absolutePath)
                .directory(workingDir)
                .apply { environment().applyDefaults(workingDir) }
                .start()
        }
    }

    private fun MutableMap<String, String>.applyDefaults(workingDir: File) {
        put("HOME", workingDir.absolutePath)
        put("TMPDIR", context.cacheDir.absolutePath)
        // Go's pure resolver reads /etc/resolv.conf; keep the platform roots
        // reachable so any helper it shells out to can still be found.
        putIfAbsent("PATH", "/system/bin:/system/xbin:/vendor/bin")
    }

    private fun quote(value: String): String = "'" + value.replace("'", "'\\''") + "'"

    // ---- output ----------------------------------------------------------

    private fun pumpOutput(process: Process) {
        pumpJobs.forEach { it.cancel() }
        pumpJobs = mutableListOf(
            pump(process.inputStream.bufferedReader(), LogLine.Stream.STDOUT),
            pump(process.errorStream.bufferedReader(), LogLine.Stream.STDERR),
        )
    }

    private fun pump(reader: java.io.BufferedReader, stream: LogLine.Stream): Job =
        scope.launch {
            try {
                while (isActive) {
                    val line = reader.readLine() ?: break
                    logs.append(stream, line)
                }
            } catch (_: Throwable) {
                // Stream closed because the process died; the watcher reports it.
            }
        }

    // ---- readiness -------------------------------------------------------

    /**
     * Polls the API port until it accepts a connection, or the process exits,
     * or the deadline passes. Watching for exit as well as for the port means a
     * binary that dies instantly (missing config, bad ELF) fails in
     * milliseconds instead of burning the whole timeout.
     */
    private suspend fun awaitReady(process: Process, port: Int, timeoutMs: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (!process.isAlive) return false
            if (canConnect(port)) return true
            delay(PROBE_INTERVAL_MS)
        }
        return canConnect(port)
    }

    private fun canConnect(port: Int): Boolean = runCatching {
        Socket().use { socket ->
            socket.connect(InetSocketAddress("127.0.0.1", port), PROBE_TIMEOUT_MS.toInt())
        }
        true
    }.getOrDefault(false)

    // ---- teardown --------------------------------------------------------

    private suspend fun stopProcess(process: Process, resolved: ResolvedMode) {
        if (resolved.isRoot) {
            // Under root the JVM only holds `su`; signal the gateway itself.
            val pid = pidFile.takeIf { it.isFile }?.readText()?.trim()?.toIntOrNull()
            if (pid != null) {
                RootShell.run("kill -TERM $pid")
            } else {
                RootShell.run("pkill -TERM -f ${quote(GatewayBinary.FILE_NAME)}")
            }
            awaitExit(process, ROOT_EXIT_TIMEOUT_MS)
            if (process.isAlive) {
                if (pid != null) RootShell.run("kill -KILL $pid")
                process.destroyForcibly()
            }
        } else {
            process.destroy() // SIGTERM: the gateway shuts its listeners down.
            if (!awaitExit(process, GRACEFUL_EXIT_TIMEOUT_MS)) {
                process.destroyForcibly()
                awaitExit(process, 2_000)
            }
        }
        pidFile.delete()
        pumpJobs.forEach { it.cancel() }
        pumpJobs = mutableListOf()
    }

    private suspend fun awaitExit(process: Process, timeoutMs: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (!process.isAlive) return true
            delay(50)
        }
        return !process.isAlive
    }

    private fun fail(reason: String): Result<Unit> {
        logs.append(LogLine.Stream.STDERR, "[app] $reason")
        _state.value = GatewayState.Failed(reason)
        return Result.failure(IllegalStateException(reason))
    }

    private companion object {
        const val READY_TIMEOUT_MS = 25_000L
        const val PROBE_INTERVAL_MS = 200L
        const val PROBE_TIMEOUT_MS = 400L
        const val GRACEFUL_EXIT_TIMEOUT_MS = 8_000L
        const val ROOT_EXIT_TIMEOUT_MS = 4_000L
    }
}

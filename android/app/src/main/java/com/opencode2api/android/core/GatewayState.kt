package com.opencode2api.android.core

/** Lifecycle of the bundled gateway process, as the UI observes it. */
sealed interface GatewayState {
    /** No process is running and none is being started. */
    data object Stopped : GatewayState

    /** A process exists but has not answered a TCP probe yet. */
    data class Starting(val since: Long, val root: Boolean) : GatewayState

    /** The port answered. [since] is when the process was spawned. */
    data class Running(
        val since: Long,
        val root: Boolean,
        val apiPort: Int,
        val webUiPort: Int?,
    ) : GatewayState

    /** The process exited or never came up. */
    data class Failed(val reason: String) : GatewayState
}

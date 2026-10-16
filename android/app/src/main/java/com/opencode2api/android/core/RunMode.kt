package com.opencode2api.android.core

/**
 * How the gateway process is spawned.
 *
 * [AUTO] is what the UI stores; [ROOT] and [USER] are what the runner resolves
 * it to at start time. Running under root is strictly optional here: the
 * gateway is a userspace proxy that binds loopback, so nothing it does
 * actually requires the privilege. Root is offered because it lets the process
 * outlive the app's own process group and survive aggressive OEM task killers.
 */
enum class RunMode(val value: String) {
    AUTO("auto"),
    ROOT("root"),
    USER("user"),
    ;

    companion object {
        val DEFAULT = AUTO

        fun fromValue(value: String?): RunMode =
            entries.firstOrNull { it.value == value } ?: DEFAULT
    }
}

/** Result of [RunMode.AUTO] resolution: what actually got used, and why. */
data class ResolvedMode(
    val mode: RunMode,
    val isRoot: Boolean,
    /** Non-null when auto-detection had to fall back. Surfaced in the UI. */
    val note: String? = null,
)

package com.opencode2api.android.data

import android.content.Context
import androidx.core.content.edit
import com.opencode2api.android.core.RunMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * App-level preferences, as opposed to the gateway's own `config.json`.
 *
 * These are things only the Android shell cares about: how to spawn the
 * process, whether to come back after a reboot, and how to skin the UI. They
 * are stored in `SharedPreferences` because they are a handful of scalars that
 * must be readable synchronously during `Application.onCreate` and from a
 * `BroadcastReceiver`, where suspending to a DataStore would be awkward.
 */
class SettingsStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(read())
    val state: StateFlow<Settings> = _state.asStateFlow()

    val current: Settings get() = _state.value

    private fun read() = Settings(
        runMode = RunMode.fromValue(prefs.getString(KEY_RUN_MODE, null)),
        startOnBoot = prefs.getBoolean(KEY_START_ON_BOOT, false),
        keepAlive = prefs.getBoolean(KEY_KEEP_ALIVE, true),
        uiMode = prefs.getString(KEY_UI_MODE, UI_MODE_MIUIX) ?: UI_MODE_MIUIX,
        themeMode = prefs.getInt(KEY_THEME_MODE, 0),
        paletteStyle = prefs.getString(KEY_PALETTE_STYLE, DEFAULT_PALETTE) ?: DEFAULT_PALETTE,
        colorSpec = prefs.getString(KEY_COLOR_SPEC, DEFAULT_COLOR_SPEC) ?: DEFAULT_COLOR_SPEC,
        keyColor = prefs.getInt(KEY_KEY_COLOR, 0),
        monet = prefs.getBoolean(KEY_MONET, true),
    )

    private fun update(block: (Settings) -> Settings) {
        val next = block(_state.value)
        prefs.edit {
            putString(KEY_RUN_MODE, next.runMode.value)
            putBoolean(KEY_START_ON_BOOT, next.startOnBoot)
            putBoolean(KEY_KEEP_ALIVE, next.keepAlive)
            putString(KEY_UI_MODE, next.uiMode)
            putInt(KEY_THEME_MODE, next.themeMode)
            putString(KEY_PALETTE_STYLE, next.paletteStyle)
            putString(KEY_COLOR_SPEC, next.colorSpec)
            putInt(KEY_KEY_COLOR, next.keyColor)
            putBoolean(KEY_MONET, next.monet)
        }
        _state.value = next
    }

    fun setRunMode(value: RunMode) = update { it.copy(runMode = value) }
    fun setStartOnBoot(value: Boolean) = update { it.copy(startOnBoot = value) }
    fun setKeepAlive(value: Boolean) = update { it.copy(keepAlive = value) }
    fun setUiMode(value: String) = update { it.copy(uiMode = value) }
    fun setThemeMode(value: Int) = update { it.copy(themeMode = value) }
    fun setPaletteStyle(value: String) = update { it.copy(paletteStyle = value) }
    fun setColorSpec(value: String) = update { it.copy(colorSpec = value) }
    fun setKeyColor(value: Int) = update { it.copy(keyColor = value) }
    fun setMonet(value: Boolean) = update { it.copy(monet = value) }

    companion object {
        const val UI_MODE_MIUIX = "miuix"
        const val UI_MODE_MATERIAL = "material"
        const val DEFAULT_PALETTE = "TonalSpot"
        const val DEFAULT_COLOR_SPEC = "SPEC_2025"

        private const val KEY_RUN_MODE = "run_mode"
        private const val KEY_START_ON_BOOT = "start_on_boot"
        private const val KEY_KEEP_ALIVE = "keep_alive"
        private const val KEY_UI_MODE = "ui_mode"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_PALETTE_STYLE = "palette_style"
        private const val KEY_COLOR_SPEC = "color_spec"
        private const val KEY_KEY_COLOR = "key_color"
        private const val KEY_MONET = "monet"
    }
}

data class Settings(
    val runMode: RunMode,
    val startOnBoot: Boolean,
    val keepAlive: Boolean,
    val uiMode: String,
    val themeMode: Int,
    val paletteStyle: String,
    val colorSpec: String,
    /** 0 means "no explicit key colour"; the theme then derives one. */
    val keyColor: Int,
    val monet: Boolean,
)

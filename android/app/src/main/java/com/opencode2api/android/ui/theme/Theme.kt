package com.opencode2api.android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import com.opencode2api.android.data.Settings
import com.opencode2api.android.ui.LocalUiMode
import com.opencode2api.android.ui.UiMode

/** Theme mode, mirroring the numbering SukiSU-Ultra persists. */
enum class ColorMode(val value: Int) {
    SYSTEM(0),
    LIGHT(1),
    DARK(2),
    MONET_SYSTEM(3),
    MONET_LIGHT(4),
    MONET_DARK(5),
    DARK_AMOLED(6),
    ;

    companion object {
        fun fromValue(value: Int): ColorMode = entries.firstOrNull { it.value == value } ?: SYSTEM
    }

    val isSystem: Boolean get() = this == SYSTEM || this == MONET_SYSTEM
    val isDark: Boolean get() = this == DARK || this == MONET_DARK || this == DARK_AMOLED
    val isAmoled: Boolean get() = this == DARK_AMOLED
    val isMonet: Boolean get() = value >= MONET_SYSTEM.value

    /** Drops the "use system colours" half, keeping light/dark. */
    fun toNonMonet(): ColorMode = when (this) {
        MONET_SYSTEM -> SYSTEM
        MONET_LIGHT -> LIGHT
        MONET_DARK, DARK_AMOLED -> DARK
        else -> this
    }

    /** Adds the "use system colours" half, keeping light/dark. */
    fun toMonet(): ColorMode = when (this) {
        SYSTEM -> MONET_SYSTEM
        LIGHT -> MONET_LIGHT
        DARK -> MONET_DARK
        else -> this
    }
}

/**
 * Everything the theme needs, resolved once from [Settings] so the composables
 * below stay free of storage concerns.
 */
data class AppThemeSettings(
    val colorMode: ColorMode,
    val paletteStyle: PaletteStyle,
    val colorSpec: ColorSpec.SpecVersion,
    /** Explicit ARGB seed, or 0 to derive one. */
    val keyColor: Int,
)

fun Settings.toThemeSettings(): AppThemeSettings {
    val mode = ColorMode.fromValue(themeMode)
    // Only the miuix skin consults the "monet" switch; Material always follows
    // the system when it can, so the two skins cannot silently disagree.
    val resolvedMode = when {
        isMiuix && !monet && mode.isMonet -> mode.toNonMonet()
        isMiuix && monet && !mode.isMonet -> mode.toMonet()
        else -> mode
    }
    return AppThemeSettings(
        colorMode = resolvedMode,
        paletteStyle = runCatching { PaletteStyle.valueOf(paletteStyle) }
            .getOrDefault(PaletteStyle.TonalSpot),
        colorSpec = runCatching { ColorSpec.SpecVersion.valueOf(colorSpec) }
            .getOrDefault(ColorSpec.SpecVersion.SPEC_2025),
        keyColor = keyColor,
    )
}

private val Settings.isMiuix: Boolean
    get() = uiMode != "material"

/**
 * Resolves the seed colour the palette is generated from.
 *
 * Order matters: an explicit pick wins, then the system wallpaper, then a fixed
 * fallback for pre-Android-12 devices that have no wallpaper API to ask.
 */
@Composable
fun resolveSeedColor(settings: AppThemeSettings, dark: Boolean): Color? {
    val context = LocalContext.current
    return when {
        settings.keyColor != 0 -> Color(settings.keyColor)
        settings.colorMode.isMonet && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context).primary else dynamicLightColorScheme(context).primary

        settings.colorMode.isMonet -> FallbackSeedColor
        else -> null
    }
}

@Composable
fun isDarkTheme(settings: AppThemeSettings): Boolean = when {
    settings.colorMode.isDark -> true
    settings.colorMode.isSystem -> isSystemInDarkTheme()
    else -> false
}

/** Root theme. Picks a skin, then delegates. */
@Composable
fun O2ATheme(
    settings: AppThemeSettings,
    uiMode: UiMode = LocalUiMode.current,
    content: @Composable () -> Unit,
) {
    when (uiMode) {
        UiMode.Miuix -> MiuixO2ATheme(settings, content)
        UiMode.Material -> MaterialO2ATheme(settings, content)
    }
}

/**
 * Ported from SukiSU-Ultra's `AppSettings.isInDarkTheme`. Reads the raw theme
 * mode rather than [AppThemeSettings] so it also works inside the settings
 * screen, where the palette may not have been applied yet.
 */
@Composable
@ReadOnlyComposable
fun isInDarkTheme(): Boolean = when (LocalThemeMode.current) {
    ColorMode.LIGHT.value, ColorMode.MONET_LIGHT.value -> false
    ColorMode.DARK.value, ColorMode.MONET_DARK.value, ColorMode.DARK_AMOLED.value -> true
    else -> isSystemInDarkTheme()
}

val LocalThemeMode = staticCompositionLocalOf { ColorMode.SYSTEM.value }

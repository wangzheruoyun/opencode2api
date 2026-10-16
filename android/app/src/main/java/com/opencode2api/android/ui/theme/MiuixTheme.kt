package com.opencode2api.android.ui.theme

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowInsetsControllerCompat
import com.materialkolor.dynamiccolor.ColorSpec
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeColorSpec
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.ThemePaletteStyle

/**
 * The MIUI/HyperOS skin, ported from SukiSU-Ultra's `MiuixKernelSUTheme`.
 *
 * miuix derives the whole palette from a single seed, so all this has to do is
 * decide the seed and hand over the light/dark mode.
 */
@Composable
fun MiuixO2ATheme(
    settings: AppThemeSettings,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val dark = isDarkTheme(settings)
    val seed = resolveSeedColor(settings, dark)

    val paletteStyle = runCatching { ThemePaletteStyle.valueOf(settings.paletteStyle.name) }
        .getOrDefault(ThemePaletteStyle.TonalSpot)

    val colorSpec = if (settings.colorSpec == ColorSpec.SpecVersion.SPEC_2025) {
        ThemeColorSpec.Spec2025
    } else {
        ThemeColorSpec.Spec2021
    }

    val schemeMode = when (settings.colorMode) {
        ColorMode.SYSTEM -> ColorSchemeMode.System
        ColorMode.LIGHT -> ColorSchemeMode.Light
        ColorMode.DARK, ColorMode.DARK_AMOLED -> ColorSchemeMode.Dark
        ColorMode.MONET_SYSTEM -> ColorSchemeMode.MonetSystem
        ColorMode.MONET_LIGHT -> ColorSchemeMode.MonetLight
        ColorMode.MONET_DARK -> ColorSchemeMode.MonetDark
    }

    val controller = ThemeController(
        colorSchemeMode = schemeMode,
        keyColor = seed,
        colorSpec = colorSpec,
        paletteStyle = paletteStyle,
        isDark = dark,
    )

    MiuixTheme(controller = controller) {
        SystemBarAppearance(dark)
        content()
    }
}

/** Keeps the status/navigation bar icons legible against the current skai. */
@Composable
internal fun SystemBarAppearance(dark: Boolean) {
    val context = LocalContext.current
    LaunchedEffect(dark) {
        val window = (context as? Activity)?.window ?: return@LaunchedEffect
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }
}

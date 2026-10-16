package com.opencode2api.android.ui.theme

import androidx.compose.runtime.Composable
import com.materialkolor.DynamicMaterialTheme

/**
 * The Material 3 skin — the same palette engine as the miuix skin, rendered
 * through Material components for users who prefer stock Android.
 */
@Composable
fun MaterialO2ATheme(
    settings: AppThemeSettings,
    content: @Composable () -> Unit,
) {
    val dark = isDarkTheme(settings)
    val seed = resolveSeedColor(settings, dark) ?: FallbackSeedColor

    DynamicMaterialTheme(
        seedColor = seed,
        isDark = dark,
        isAmoled = settings.colorMode.isAmoled,
        style = settings.paletteStyle,
        specVersion = settings.colorSpec,
    ) {
        SystemBarAppearance(dark)
        content()
    }
}

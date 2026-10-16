package com.opencode2api.android.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

/**
 * Seed used to derive a palette on devices without system dynamic colour
 * (Android 11 and below). Chosen to match the app icon's slate blue.
 */
val FallbackSeedColor = Color(0xFF3D5A80)

/** Swatches offered by the "theme colour" picker, plus "follow the system". */
val KeyColorOptions = listOf(
    Color(0xFF3D5A80).toArgb(),
    Color(0xFF2196F3).toArgb(),
    Color(0xFF009688).toArgb(),
    Color(0xFF4FAF50).toArgb(),
    Color(0xFFFFC107).toArgb(),
    Color(0xFFFF9800).toArgb(),
    Color(0xFFF44336).toArgb(),
    Color(0xFFE91E63).toArgb(),
    Color(0xFF9C27B0).toArgb(),
    Color(0xFF673AB7).toArgb(),
    Color(0xFF607D8F).toArgb(),
    Color(0xFF795548).toArgb(),
)

package com.opencode2api.android.ui

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Which component library the UI is drawn with.
 *
 * [Miuix] is the MIUI/HyperOS-flavoured look that SukiSU-Ultra uses and is the
 * default here; [Material] is the Material 3 fallback for anyone who prefers
 * stock Android styling. Every screen has a variant for each, mirroring how
 * upstream SukiSU-Ultra structures its own screens.
 */
enum class UiMode(val value: String) {
    Miuix("miuix"),
    Material("material"),
    ;

    companion object {
        fun fromValue(value: String?): UiMode = when (value) {
            Material.value -> Material
            else -> Miuix
        }

        val DEFAULT = Miuix
    }
}

val LocalUiMode = staticCompositionLocalOf { UiMode.DEFAULT }

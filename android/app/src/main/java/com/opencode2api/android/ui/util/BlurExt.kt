package com.opencode2api.android.ui.util

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * The backdrop a blurred bar samples from, or null when blur is off or the platform
 * cannot do it at all.
 *
 * Returning null rather than a no-op backdrop is the point: a null result means the
 * bar's call site keeps its own opaque colour, so one value decides whether blur is on.
 *
 * isRuntimeShaderSupported() is API 33+, and it gates the whole blur pipeline: the
 * backdrop modifier computes `enabled && isRuntimeShaderSupported()`. miuix's blur
 * module re-exports it, so there is nothing extra to declare for it.
 */
@Composable
fun rememberBlurBackdrop(enableBlur: Boolean): LayerBackdrop? {
    if (!enableBlur || !isRuntimeShaderSupported()) return null
    val surfaceColor = MiuixTheme.colorScheme.surface
    return rememberLayerBackdrop {
        drawRect(surfaceColor)
        drawContent()
    }
}

/**
 * Hosts [content] over a blurred copy of whatever [backdrop] captured. With a null
 * backdrop, or with [blurActive] off, this is a plain Box: it draws [content] through
 * untouched and adds nothing, so the bar keeps whatever colour its call site gave it.
 */
@Composable
fun BlurredBar(
    backdrop: LayerBackdrop?,
    blurActive: Boolean = true,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = if (blurActive && backdrop != null) {
            Modifier.textureBlur(
                backdrop = backdrop,
                shape = RectangleShape,
                blurRadius = 25f,
                colors = BlurColors(
                    blendColors = listOf(
                        BlendColorEntry(color = MiuixTheme.colorScheme.surface.copy(0.87f)),
                    ),
                ),
            )
        } else {
            Modifier
        },
    ) {
        content()
    }
}

package com.opencode2api.android.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.opencode2api.android.ui.LocalUiMode
import com.opencode2api.android.ui.UiMode

/**
 * The component vocabulary the screens are written against.
 *
 * SukiSU-Ultra carries two copies of every screen, one per skin, and picks
 * between them at the call site. This module takes the other route: the
 * screens name a neutral component and the dispatch happens once, down here.
 * It costs one extra layer of indirection and buys every screen for free in
 * both skins — and it keeps "which skin am I on" out of screen code entirely,
 * which is where SukiSU's copy-paste approach tends to go wrong.
 *
 * Anything that genuinely cannot be expressed on both skins lives in
 * `ui/platform/` instead, so this file stays skin-shaped rather than
 * component-shaped.
 */
enum class O2ATextStyle { Title, Subtitle, Body, Caption, Monospace }

/** One entry in [O2ANavigationBar]. */
data class O2ANavItem(val label: String, val icon: ImageVector)

/** Which skin the component set is drawing with, read once per component. */
private val isMiuix: Boolean
    @Composable @ReadOnlyComposable get() = LocalUiMode.current == UiMode.Miuix

@Composable
fun O2AScaffold(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    if (isMiuix) MiuixScaffold(modifier, topBar, bottomBar, content)
    else MaterialScaffold(modifier, topBar, bottomBar, content)
}

/**
 * The screen's app bar. [subtitle] is shown under the title on the miuix skin
 * and dropped on Material, which has no room for it in a standard bar.
 */
@Composable
fun O2ATopBar(
    title: String,
    subtitle: String = "",
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    if (isMiuix) MiuixTopBar(title, subtitle, navigationIcon, actions)
    else MaterialTopBar(title, navigationIcon, actions)
}

/** Bottom tab bar. [selectedIndex] is an index into [items]. */
@Composable
fun O2ANavigationBar(
    items: List<O2ANavItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    if (isMiuix) MiuixNavigationBar(items, selectedIndex, onSelect)
    else MaterialNavigationBar(items, selectedIndex, onSelect)
}

/** A rounded container. Pass [onClick] to make the whole card pressable. */
@Composable
fun O2ACard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (isMiuix) MiuixCard(modifier, onClick, content)
    else MaterialCard(modifier, onClick, content)
}

/**
 * The small grey heading that separates cards in a settings list, matching the
 * `title` blocks upstream's WebUI uses between config sections.
 */
@Composable
fun O2ASectionTitle(text: String, modifier: Modifier = Modifier) {
    if (isMiuix) MiuixSectionTitle(text, modifier) else MaterialSectionTitle(text, modifier)
}

/**
 * A list row: title, optional summary, optional leading and trailing content.
 * This is the workhorse of the settings-style screens.
 */
@Composable
fun O2ARow(
    title: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    if (isMiuix) MiuixRow(title, modifier, summary, leading, trailing, onClick)
    else MaterialRow(title, modifier, summary, leading, trailing, onClick)
}

/** A label plus a switch, on one pressable row. */
@Composable
fun O2ASwitchRow(
    title: String,
    checked: Boolean,
    modifier: Modifier = Modifier,
    summary: String? = null,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    if (isMiuix) MiuixSwitchRow(title, checked, modifier, summary, enabled, onCheckedChange)
    else MaterialSwitchRow(title, checked, modifier, summary, enabled, onCheckedChange)
}

@Composable
fun O2AButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    primary: Boolean = true,
) {
    if (isMiuix) MiuixButton(text, onClick, modifier, enabled, primary)
    else MaterialButton(text, onClick, modifier, enabled, primary)
}

/** A button with no fill, for secondary and destructive-adjacent actions. */
@Composable
fun O2ATextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    if (isMiuix) MiuixTextButton(text, onClick, modifier, enabled)
    else MaterialTextButton(text, onClick, modifier, enabled)
}

/** A bare icon that acts as a button — app bar actions, refresh, copy. */
@Composable
fun O2AIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    if (isMiuix) MiuixIconButton(icon, contentDescription, onClick, modifier, enabled)
    else MaterialIconButton(icon, contentDescription, onClick, modifier, enabled)
}

/** A non-interactive icon, for leading slots and decoration. */
@Composable
fun O2AIcon(
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified,
) {
    if (isMiuix) {
        top.yukonga.miuix.kmp.basic.Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = modifier,
            tint = if (tint == Color.Unspecified) {
                top.yukonga.miuix.kmp.theme.LocalContentColor.current
            } else {
                tint
            },
        )
    } else {
        androidx.compose.material3.Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = modifier,
            tint = tint,
        )
    }
}

@Composable
fun O2ATextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    enabled: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    val options = KeyboardOptions(keyboardType = keyboardType)
    if (isMiuix) {
        MiuixTextField(value, onValueChange, label, modifier, singleLine, enabled, options)
    } else {
        MaterialTextField(value, onValueChange, label, modifier, singleLine, enabled, options)
    }
}

@Composable
fun O2ADivider(modifier: Modifier = Modifier) {
    if (isMiuix) MiuixDivider(modifier) else MaterialDivider(modifier)
}

@Composable
fun O2AText(
    text: String,
    modifier: Modifier = Modifier,
    style: O2ATextStyle = O2ATextStyle.Body,
    color: Color = Color.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
) {
    if (isMiuix) MiuixText(text, modifier, style, color, maxLines)
    else MaterialText(text, modifier, style, color, maxLines)
}

/** [progress] of null renders an indeterminate bar. */
@Composable
fun O2AProgressBar(progress: Float?, modifier: Modifier = Modifier) {
    if (isMiuix) MiuixProgressBar(progress, modifier) else MaterialProgressBar(progress, modifier)
}

/** A block of monospaced text — log lines, JSON bodies, keys. */
@Composable
fun O2AMonoBlock(
    text: String,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
) {
    O2AText(text, modifier, O2ATextStyle.Monospace, Color.Unspecified, maxLines)
}

/** The centred grey line shown when a list has nothing in it. */
@Composable
fun O2AEmptyState(text: String, modifier: Modifier = Modifier) {
    if (isMiuix) MiuixEmptyState(text, modifier) else MaterialEmptyState(text, modifier)
}

/** A short label standing next to a row's title — a port, a status, a count. */
@Composable
fun O2ATag(text: String, modifier: Modifier = Modifier, highlight: Boolean = false) {
    if (isMiuix) MiuixTag(text, modifier, highlight) else MaterialTag(text, modifier, highlight)
}

/**
 * One option in a horizontal set — theme mode, run mode, log level. Picking one
 * is a single-choice action, so the set is built from these rather than a
 * dropdown: at three or four options, showing them all beats a menu.
 */
@Composable
fun O2ASelectableChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (isMiuix) MiuixSelectableChip(text, selected, onClick, modifier)
    else MaterialSelectableChip(text, selected, onClick, modifier)
}

/** A horizontal row of chips that wraps. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun O2AChipRow(
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.FlowRowScope.() -> Unit,
) {
    androidx.compose.foundation.layout.FlowRow(
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

/** Lays a couple of small controls out side by side, wrapping when narrow. */
@Composable
fun O2AActionRow(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        content = content,
    )
}

/** A column with the standard gap between cards. */
@Composable
fun O2AColumn(
    modifier: Modifier = Modifier,
    spacing: Int = 12,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(spacing.dp),
        content = content,
    )
}

/**
 * The accent colour, for the few places that paint rather than lay out — chart
 * bars, tier splits, the filled half of a progress bar.
 *
 * Screens need a colour here and there, and the alternative to this accessor is
 * every one of them reaching for `MiuixTheme` and `MaterialTheme` by name,
 * which is exactly the coupling this layer exists to prevent.
 */
@Composable
fun O2AAccentColor(): Color = if (isMiuix) MiuixAccentColor() else MaterialAccentColor()

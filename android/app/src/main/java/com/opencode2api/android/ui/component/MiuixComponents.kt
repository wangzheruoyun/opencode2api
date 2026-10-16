package com.opencode2api.android.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * The miuix half of the component set. Each function here is a thin translation
 * of the neutral API onto `top.yukonga.miuix.kmp`, with no policy of its own.
 */

@Composable
internal fun MiuixScaffold(
    modifier: Modifier,
    topBar: @Composable () -> Unit,
    bottomBar: @Composable () -> Unit,
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(modifier = modifier, topBar = topBar, bottomBar = bottomBar, content = content)
}

@Composable
internal fun MiuixTopBar(
    title: String,
    subtitle: String,
    navigationIcon: (@Composable () -> Unit)?,
    actions: @Composable RowScope.() -> Unit,
) {
    // SmallTopAppBar rather than TopAppBar: the large collapsing title is
    // charming on a phone home screen but wrong on dense tool screens.
    SmallTopAppBar(
        title = title,
        subtitle = subtitle,
        navigationIcon = { navigationIcon?.invoke() },
        actions = actions,
    )
}

@Composable
internal fun MiuixNavigationBar(
    items: List<O2ANavItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    NavigationBar {
        items.forEachIndexed { index, item ->
            NavigationBarItem(
                selected = index == selectedIndex,
                onClick = { onSelect(index) },
                icon = item.icon,
                label = item.label,
            )
        }
    }
}

@Composable
internal fun MiuixCard(
    modifier: Modifier,
    onClick: (() -> Unit)?,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(modifier = modifier, onClick = onClick, content = content)
}

@Composable
internal fun MiuixSectionTitle(text: String, modifier: Modifier) {
    SmallTitle(text = text, modifier = modifier)
}

@Composable
internal fun MiuixRow(
    title: String,
    modifier: Modifier,
    summary: String?,
    leading: (@Composable () -> Unit)?,
    trailing: (@Composable () -> Unit)?,
    onClick: (() -> Unit)?,
) {
    BasicComponent(
        modifier = modifier,
        title = title,
        summary = summary,
        startAction = leading,
        endActions = { trailing?.invoke() },
        onClick = onClick,
    )
}

@Composable
internal fun MiuixSwitchRow(
    title: String,
    checked: Boolean,
    modifier: Modifier,
    summary: String?,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    BasicComponent(
        modifier = modifier,
        title = title,
        summary = summary,
        endActions = {
            Switch(
                checked = checked,
                onCheckedChange = { onCheckedChange(it) },
                enabled = enabled,
            )
        },
        // The whole row is the target, as MIUI settings rows are; the switch
        // consumes its own drags, so the two never fight.
        onClick = if (enabled) ({ onCheckedChange(!checked) }) else null,
    )
}

@Composable
internal fun MiuixButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    primary: Boolean,
) {
    if (primary) {
        Button(onClick = onClick, modifier = modifier, enabled = enabled) { Text(text) }
    } else {
        TextButton(text = text, onClick = onClick, modifier = modifier, enabled = enabled)
    }
}

@Composable
internal fun MiuixTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
) {
    TextButton(text = text, onClick = onClick, modifier = modifier, enabled = enabled)
}

@Composable
internal fun MiuixTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier,
    singleLine: Boolean,
    enabled: Boolean,
    keyboardOptions: KeyboardOptions,
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = label,
        useLabelAsPlaceholder = true,
        enabled = enabled,
        keyboardOptions = keyboardOptions,
        singleLine = singleLine,
        maxLines = if (singleLine) 1 else Int.MAX_VALUE,
    )
}

@Composable
internal fun MiuixIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
) {
    IconButton(onClick = onClick, modifier = modifier, enabled = enabled) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = MiuixTheme.colorScheme.onSurface,
        )
    }
}

@Composable
internal fun MiuixDivider(modifier: Modifier) {
    HorizontalDivider(modifier = modifier)
}

@Composable
internal fun MiuixText(
    text: String,
    modifier: Modifier,
    style: O2ATextStyle,
    color: Color,
    maxLines: Int,
) {
    val textStyles = MiuixTheme.textStyles
    Text(
        text = text,
        modifier = modifier,
        color = color,
        maxLines = maxLines,
        style = when (style) {
            O2ATextStyle.Title -> textStyles.title3
            O2ATextStyle.Subtitle -> textStyles.title4
            O2ATextStyle.Body -> textStyles.main
            O2ATextStyle.Caption -> textStyles.footnote1
            O2ATextStyle.Monospace -> textStyles.footnote1.copy(fontFamily = FontFamily.Monospace)
        },
    )
}

@Composable
internal fun MiuixProgressBar(progress: Float?, modifier: Modifier) {
    LinearProgressIndicator(modifier = modifier, progress = progress)
}

@Composable
internal fun MiuixEmptyState(text: String, modifier: Modifier) {
    Text(
        text = text,
        modifier = modifier.fillMaxWidth().padding(vertical = 32.dp, horizontal = 24.dp),
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        textAlign = TextAlign.Center,
        style = MiuixTheme.textStyles.footnote1,
    )
}

@Composable
internal fun MiuixSelectableChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val scheme = MiuixTheme.colorScheme
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) scheme.primary else scheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        Text(
            text = text,
            color = if (selected) scheme.onPrimary else scheme.onSurfaceContainerHigh,
            style = MiuixTheme.textStyles.footnote1,
        )
    }
}

@Composable
internal fun MiuixTag(text: String, modifier: Modifier, highlight: Boolean) {
    val scheme = MiuixTheme.colorScheme
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (highlight) scheme.primaryContainer else scheme.surfaceContainerHigh)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(
            text = text,
            color = if (highlight) scheme.onPrimaryContainer else scheme.onSurfaceContainerHigh,
            style = MiuixTheme.textStyles.footnote2,
        )
    }
}

/**
 * The accent for painting, on the miuix skin. See [O2AAccentColor].
 *
 * `primary` rather than `primaryContainer`: the charts this colours are thin
 * marks on a card, and the container tone is close enough to the card's own
 * background at these sizes to disappear.
 */
@Composable
internal fun MiuixAccentColor(): Color = MiuixTheme.colorScheme.primary

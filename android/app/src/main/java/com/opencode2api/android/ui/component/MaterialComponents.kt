package com.opencode2api.android.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * The Material 3 half of the component set.
 *
 * Everything here is a stable, non-experimental Material 3 API. The app bar in
 * particular is hand-built rather than using `TopAppBar`, which is still behind
 * `@ExperimentalMaterial3Api` and would spread opt-ins across every screen.
 */

@Composable
internal fun MaterialScaffold(
    modifier: Modifier,
    topBar: @Composable () -> Unit,
    bottomBar: @Composable () -> Unit,
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(modifier = modifier, topBar = topBar, bottomBar = bottomBar, content = content)
}

@Composable
internal fun MaterialTopBar(
    title: String,
    navigationIcon: (@Composable () -> Unit)?,
    actions: @Composable RowScope.() -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .height(56.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (navigationIcon != null) {
                Box(modifier = Modifier.padding(start = 4.dp)) { navigationIcon() }
            }
            Text(
                text = title,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = if (navigationIcon != null) 4.dp else 16.dp),
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
            )
            actions()
            Box(modifier = Modifier.width(4.dp))
        }
    }
}

@Composable
internal fun MaterialNavigationBar(
    items: List<O2ANavItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    NavigationBar {
        items.forEachIndexed { index, item ->
            NavigationBarItem(
                selected = index == selectedIndex,
                onClick = { onSelect(index) },
                icon = { Icon(item.icon, contentDescription = item.label) },
                label = { Text(item.label, style = MaterialTheme.typography.labelSmall, maxLines = 1) },
            )
        }
    }
}

@Composable
internal fun MaterialCard(
    modifier: Modifier,
    onClick: (() -> Unit)?,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (onClick == null) {
        Card(modifier = modifier) { content() }
    } else {
        Card(onClick = onClick, modifier = modifier) { content() }
    }
}

@Composable
internal fun MaterialSectionTitle(text: String, modifier: Modifier) {
    Text(
        text = text,
        modifier = modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
        color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.labelLarge,
    )
}

@Composable
internal fun MaterialRow(
    title: String,
    modifier: Modifier,
    summary: String?,
    leading: (@Composable () -> Unit)?,
    trailing: (@Composable () -> Unit)?,
    onClick: (() -> Unit)?,
) {
    val rowModifier = if (onClick != null) {
        modifier.fillMaxWidth().clickable(onClick = onClick)
    } else {
        modifier.fillMaxWidth()
    }
    Row(
        modifier = rowModifier.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        leading?.invoke()
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (!summary.isNullOrBlank()) {
                Text(
                    summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        trailing?.invoke()
    }
}

@Composable
internal fun MaterialSwitchRow(
    title: String,
    checked: Boolean,
    modifier: Modifier,
    summary: String?,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { if (enabled) onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            if (!summary.isNullOrBlank()) {
                Text(
                    summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

/** Shared by the rows above: a whole-row click target with a ripple. */
@Composable
internal fun MaterialButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    primary: Boolean,
) {
    if (primary) {
        Button(onClick = onClick, modifier = modifier, enabled = enabled) { Text(text) }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier, enabled = enabled) { Text(text) }
    }
}

@Composable
internal fun MaterialTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
) {
    TextButton(onClick = onClick, modifier = modifier, enabled = enabled) { Text(text) }
}

@Composable
internal fun MaterialTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier,
    singleLine: Boolean,
    enabled: Boolean,
    keyboardOptions: KeyboardOptions,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        enabled = enabled,
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 3,
        keyboardOptions = keyboardOptions,
        textStyle = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
internal fun MaterialDivider(modifier: Modifier) {
    HorizontalDivider(modifier = modifier)
}

@Composable
internal fun MaterialText(
    text: String,
    modifier: Modifier,
    style: O2ATextStyle,
    color: Color,
    maxLines: Int,
) {
    val typography = MaterialTheme.typography
    Text(
        text = text,
        modifier = modifier,
        color = color,
        maxLines = maxLines,
        style = when (style) {
            O2ATextStyle.Title -> typography.titleLarge
            O2ATextStyle.Subtitle -> typography.titleMedium
            O2ATextStyle.Body -> typography.bodyMedium
            O2ATextStyle.Caption -> typography.bodySmall
            O2ATextStyle.Monospace -> typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Normal,
            )
        },
    )
}

@Composable
internal fun MaterialProgressBar(progress: Float?, modifier: Modifier) {
    if (progress == null) {
        LinearProgressIndicator(modifier = modifier)
    } else {
        // Material 3 takes the value as a lambda so the bar can be animated
        // without recomposing the caller.
        LinearProgressIndicator(progress = { progress }, modifier = modifier)
    }
}

@Composable
internal fun MaterialEmptyState(text: String, modifier: Modifier) {
    Text(
        text = text,
        modifier = modifier.fillMaxWidth().padding(vertical = 32.dp, horizontal = 24.dp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.bodySmall,
    )
}

@Composable
internal fun MaterialSelectableChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) scheme.primary else scheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        Text(
            text = text,
            color = if (selected) scheme.onPrimary else scheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
internal fun MaterialTag(text: String, modifier: Modifier, highlight: Boolean) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(
                if (highlight) scheme.primaryContainer else scheme.surfaceVariant,
            )
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(
            text = text,
            color = if (highlight) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

/** An icon button on Material. */
@Composable
internal fun MaterialIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
) {
    IconButton(onClick = onClick, modifier = modifier, enabled = enabled) {
        Icon(icon, contentDescription = contentDescription)
    }
}

/** The accent for painting, on the Material skin. See [O2AAccentColor]. */
@Composable
internal fun MaterialAccentColor(): Color = MaterialTheme.colorScheme.primary

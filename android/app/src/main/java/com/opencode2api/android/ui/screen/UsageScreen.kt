package com.opencode2api.android.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.opencode2api.android.ui.Route
import com.opencode2api.android.ui.Tab
import com.opencode2api.android.ui.component.O2AAccentColor
import com.opencode2api.android.ui.component.O2AActionRow
import com.opencode2api.android.ui.component.O2ACard
import com.opencode2api.android.ui.component.O2AEmptyState
import com.opencode2api.android.ui.component.O2AProgressBar
import com.opencode2api.android.ui.component.O2ARow
import com.opencode2api.android.ui.component.O2ASectionTitle
import com.opencode2api.android.ui.component.O2AText
import com.opencode2api.android.ui.component.O2ATextButton
import com.opencode2api.android.ui.component.O2ATextStyle
import com.opencode2api.android.ui.util.formatCount
import com.opencode2api.android.ui.util.formatPercent
import com.opencode2api.android.ui.util.list
import com.opencode2api.android.ui.util.long
import com.opencode2api.android.ui.util.num
import com.opencode2api.android.ui.util.obj
import kotlinx.serialization.json.JsonObject

/**
 * Token 用量 — where the tokens went.
 *
 * Everything here comes from a single `/api/monitor` snapshot, so the four
 * headline numbers and the trend behind them are guaranteed to agree with each
 * other. The two windows are shown side by side rather than one at a time
 * because "12k this hour, 4.1M lifetime" is a more useful sentence than either
 * half of it.
 */
@Composable
fun UsageScreen(onNavigate: (Route) -> Unit) {
    val base = rememberAdminBaseUrl()

    AdminGate(baseUrl = base) { url ->
        val monitor = rememberAdminJson(url, "/api/monitor")

        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            val data = monitor.data
            val window = data?.obj("usage.last_hour")
            val lifetime = data?.obj("usage.lifetime")
            val tokens = window?.obj("tokens")
            val accent = O2AAccentColor()

            O2ACard(modifier = Modifier.padding(horizontal = 12.dp)) {
                if (data == null) {
                    if (monitor.loading) {
                        O2AProgressBar(progress = null)
                    } else {
                        O2AText(monitor.error ?: "暂无数据", style = O2ATextStyle.Caption)
                    }
                } else {
                    O2AText("最近 60 分钟", style = O2ATextStyle.Subtitle)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Metric("Usage coverage", formatPercent(window?.num("coverage")), "有 Token 回报 / 推理请求")
                        Metric("Total tokens", formatCount(tokens?.long("total_tokens") ?: 0), "输入 + 输出总量")
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Metric("Cached tokens", formatCount(tokens?.long("cached_tokens") ?: 0), "上游回报的缓存命中")
                        Metric("Reasoning tokens", formatCount(tokens?.long("reasoning_tokens") ?: 0), "上游回报的推理用量")
                    }
                    O2AActionRow { O2ATextButton(text = "刷新", onClick = monitor.refresh) }
                }
            }

            if (lifetime != null) {
                O2ASectionTitle("累计")
                O2ACard(modifier = Modifier.padding(horizontal = 12.dp)) {
                    val lifeTokens = lifetime.obj("tokens")
                    O2ARow(title = "推理请求", summary = formatCount(lifetime.long("requests")))
                    O2ARow(title = "有 Token 回报", summary = formatCount(lifetime.long("reported")))
                    O2ARow(
                        title = "覆盖率",
                        summary = formatPercent(lifetime.num("coverage")),
                    )
                    O2ARow(title = "输入 Token", summary = formatCount(lifeTokens?.long("input_tokens") ?: 0))
                    O2ARow(title = "输出 Token", summary = formatCount(lifeTokens?.long("output_tokens") ?: 0))
                    O2ARow(title = "总量", summary = formatCount(lifeTokens?.long("total_tokens") ?: 0))
                }
            }

            val series = data?.list("metrics.series").orEmpty()
            O2ASectionTitle("每分钟 Token 趋势")
            O2ACard(modifier = Modifier.padding(horizontal = 12.dp)) {
                if (series.isEmpty()) {
                    O2AEmptyState("这一小时内还没有请求")
                } else {
                    O2AText(
                        "最近 60 分钟，每根柱子是一分钟内的 total_tokens。",
                        style = O2ATextStyle.Caption,
                    )
                    TrendChart(series = series, accent = accent)
                }
            }

            val models = data?.obj("usage.last_hour.models")
            O2ASectionTitle("模型排行")
            O2ACard(modifier = Modifier.padding(horizontal = 12.dp)) {
                if (models.isNullOrEmpty()) {
                    O2AEmptyState("这一小时内还没有模型用量")
                } else {
                    val ranked = models.entries
                        .map { (name, counts) -> name to (counts as? JsonObject) }
                        .sortedByDescending { it.second?.long("total_tokens") ?: 0L }
                        .take(15)
                    ranked.forEach { (name, counts) ->
                        O2ARow(
                            title = name,
                            summary = "输入 ${formatCount(counts?.long("input_tokens") ?: 0)}" +
                                " · 输出 ${formatCount(counts?.long("output_tokens") ?: 0)}" +
                                " · 总量 ${formatCount(counts?.long("total_tokens") ?: 0)}",
                        )
                    }
                    if (models.size > ranked.size) {
                        O2AText(
                            "只显示前 ${ranked.size} 个，共 ${models.size} 个模型。",
                            style = O2ATextStyle.Caption,
                        )
                    }
                }
            }

            val tiers = data?.obj("usage.last_hour.tiers")
            O2ASectionTitle("Tier 分布")
            O2ACard(modifier = Modifier.padding(horizontal = 12.dp)) {
                if (tiers.isNullOrEmpty()) {
                    O2AEmptyState("这一小时内还没有分流记录")
                } else {
                    val total = tiers.values.sumOf { (it as? JsonObject)?.long("total_tokens") ?: 0L }
                    tiers.forEach { (tier, counts) ->
                        val value = (counts as? JsonObject)?.long("total_tokens") ?: 0L
                        O2ARow(
                            title = tier,
                            summary = "${formatCount(value)} · " +
                                formatPercent(if (total > 0) value.toDouble() / total else null),
                        )
                        SplitBar(fraction = if (total > 0) value.toFloat() / total else 0f, accent = accent)
                    }
                }
            }

            O2ASectionTitle("相关页面")
            O2ACard(modifier = Modifier.padding(horizontal = 12.dp)) {
                O2ARow(
                    title = "Playground",
                    summary = "发一条真实请求，看看走的是哪条路由",
                    onClick = { onNavigate(Route.Playground) },
                )
                O2ARow(
                    title = "路由诊断",
                    summary = "模型路由表与最近的上游尝试",
                    onClick = { onNavigate(Route.Diagnostics) },
                )
                O2ARow(
                    title = "模型与额度",
                    summary = "目录状态与 Go 套餐窗口",
                    onClick = { onNavigate(Route.TabRoute(Tab.MODELS)) },
                )
            }
        }
    }
}

/** One of the four headline numbers. */
@Composable
private fun RowScope.Metric(label: String, value: String, note: String) {
    Column(modifier = Modifier.weight(1f)) {
        O2AText(label, style = O2ATextStyle.Caption)
        O2AText(value, style = O2ATextStyle.Title)
        O2AText(note, style = O2ATextStyle.Caption)
    }
}

/**
 * The per-minute bars.
 *
 * Height is relative to the busiest minute in the window rather than to an
 * absolute ceiling: an empty hour and a quiet hour should not look the same,
 * and a fixed scale would make a lightly used gateway a flat line.
 */
@Composable
private fun TrendChart(series: List<JsonObject>, accent: Color) {
    val maximum = series.maxOfOrNull { it.long("total_tokens") }?.coerceAtLeast(1L) ?: 1L
    Row(
        modifier = Modifier.fillMaxWidth().height(96.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        series.forEach { point ->
            val value = point.long("total_tokens")
            // A minute with no traffic keeps a hairline so the axis reads as
            // continuous, which is what makes a gap in the middle of an hour
            // visible at a glance.
            val fraction = value.toFloat() / maximum
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height((4 + fraction * 88).dp)
                    .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                    .background(if (value > 0L) accent else accent.copy(alpha = 0.15f)),
            )
        }
    }
    O2AText(
        "峰值 ${formatCount(maximum)} tokens/分钟",
        style = O2ATextStyle.Caption,
    )
}

/** A one-bar split, for the tier breakdown. */
@Composable
private fun SplitBar(fraction: Float, accent: Color) {
    Row(modifier = Modifier.fillMaxWidth().height(6.dp)) {
        Box(
            modifier = Modifier
                .weight(fraction.coerceIn(0.001f, 1f))
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(accent),
        )
        if (fraction < 1f) {
            Box(
                modifier = Modifier
                    .weight((1f - fraction).coerceAtLeast(0.001f))
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(accent.copy(alpha = 0.15f)),
            )
        }
    }
}

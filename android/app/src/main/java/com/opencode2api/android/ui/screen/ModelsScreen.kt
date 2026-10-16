package com.opencode2api.android.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.opencode2api.android.ui.Route
import com.opencode2api.android.ui.component.O2AActionRow
import com.opencode2api.android.ui.component.O2ACard
import com.opencode2api.android.ui.component.O2AColumn
import com.opencode2api.android.ui.component.O2AEmptyState
import com.opencode2api.android.ui.component.O2AProgressBar
import com.opencode2api.android.ui.component.O2ARow
import com.opencode2api.android.ui.component.O2ASectionTitle
import com.opencode2api.android.ui.component.O2ATag
import com.opencode2api.android.ui.component.O2AText
import com.opencode2api.android.ui.component.O2ATextButton
import com.opencode2api.android.ui.component.O2ATextStyle
import com.opencode2api.android.ui.util.bool
import com.opencode2api.android.ui.util.formatPercent
import com.opencode2api.android.ui.util.formatTimestamp
import com.opencode2api.android.ui.util.list
import com.opencode2api.android.ui.util.num
import com.opencode2api.android.ui.util.obj
import com.opencode2api.android.ui.util.raw
import com.opencode2api.android.ui.util.str
import kotlinx.serialization.json.JsonObject

/**
 * 模型与额度 — what the gateway can serve, what it is currently willing to
 * serve, and how much of the Go subscription is left.
 *
 * Three sources, deliberately separate. The catalogue counts come from the
 * monitor snapshot and are free. The per-model availability list comes from the
 * background probe that disables models upstream has stopped honouring. The
 * quota windows come from OpenCode's own usage endpoint, which is rate limited
 * and costs a round trip per Go key — so that one is fetched once and refreshed
 * only when the user asks, never on a timer.
 */
@Composable
fun ModelsScreen(onNavigate: (Route) -> Unit) {
    val base = rememberAdminBaseUrl()

    AdminGate(baseUrl = base) { url ->
        val monitor = rememberAdminJson(url, "/api/monitor")
        val availability = rememberAdminJson(url, "/api/models/availability")
        val quotas = rememberAdminJson(url, "/api/model-quotas")

        val models = availability.data
            ?.list("models")
            .orEmpty()
            .sortedWith(compareBy({ it.bool("disabled") }, { it.str("model") }))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                CatalogueCard(
                    catalog = monitor.data?.obj("resources.models"),
                    loading = monitor.loading,
                    error = monitor.error,
                    onRefresh = monitor.refresh,
                )
            }

            item {
                QuotaCard(
                    data = quotas.data,
                    loading = quotas.loading,
                    error = quotas.error,
                    onRefresh = quotas.refresh,
                )
            }

            item {
                Column(modifier = Modifier.padding(horizontal = 12.dp)) {
                    O2ASectionTitle("模型可用性")
                    O2AActionRow {
                        O2ATextButton(text = "刷新", onClick = availability.refresh)
                        O2ATextButton(
                            text = "完整列表",
                            onClick = { onNavigate(Route.Availability) },
                        )
                        O2ATextButton(
                            text = "路由诊断",
                            onClick = { onNavigate(Route.Diagnostics) },
                        )
                    }
                    O2AText(
                        "后台探针每小时检查一次免费模型，被上游拒绝的会被自动停用，" +
                            "24 小时后再试；也可以手动恢复或停用。",
                        style = O2ATextStyle.Caption,
                    )
                }
            }

            when {
                availability.loading && models.isEmpty() -> item {
                    PaddedSection {
                        O2AProgressBar(progress = null)
                    }
                }

                availability.error != null && models.isEmpty() -> item {
                    PaddedSection { O2AText(availability.error, style = O2ATextStyle.Caption) }
                }

                models.isEmpty() -> item {
                    PaddedSection { O2AEmptyState("暂无可用模型，等待上游目录首次刷新") }
                }

                else -> items(models, key = { it.str("model") }) { model ->
                    ModelCard(model)
                }
            }
        }
    }
}


@Composable
private fun CatalogueCard(
    catalog: JsonObject?,
    loading: Boolean,
    error: String?,
    onRefresh: () -> Unit,
) {
    O2ACard(modifier = Modifier.padding(horizontal = 12.dp)) {
        O2AText("模型目录", style = O2ATextStyle.Subtitle)
        if (catalog == null) {
            if (loading) O2AProgressBar(progress = null) else O2AText(error ?: "暂无数据", style = O2ATextStyle.Caption)
        } else {
            O2ARow(title = "可暴露模型", summary = catalog.raw("exposed") ?: "0")
            O2ARow(title = "上游总数", summary = catalog.raw("total") ?: "0")
            O2ARow(
                title = "Zen / Go 分布",
                summary = "${catalog.raw("zen") ?: "0"} / ${catalog.raw("go") ?: "0"}",
            )
            if (catalog.bool("stale")) {
                O2ATag(text = "目录为快取", highlight = false)
                O2AText(
                    "上游目录尚未刷新成功，当前用的是 ${catalog.str("cache_source", "磁盘")} 快取。",
                    style = O2ATextStyle.Caption,
                )
            }
            O2ARow(title = "最近更新", summary = formatTimestamp(catalog.raw("updated_at")))
        }
        O2AActionRow {
            O2ATextButton(text = "刷新", onClick = onRefresh)
        }
    }
}

/**
 * The Go subscription's own quota windows.
 *
 * The gateway is explicit that these percentages are subscription-wide and that
 * several Go keys may share one subscription, so the numbers are shown per key
 * without any summing — adding two keys of the same plan would report double
 * the usage that actually exists.
 */
@Composable
private fun QuotaCard(data: JsonObject?, loading: Boolean, error: String?, onRefresh: () -> Unit) {
    val accounts = data?.list("accounts").orEmpty()

    O2ACard(modifier = Modifier.padding(horizontal = 12.dp)) {
        O2AText("Go 套餐额度", style = O2ATextStyle.Subtitle)

        if (loading && accounts.isEmpty()) {
            O2AProgressBar(progress = null)
        } else if (accounts.isEmpty()) {
            O2AText(
                error ?: "没有配置 Go 密钥，或尚未查询过额度。",
                style = O2ATextStyle.Caption,
            )
        } else {
            accounts.forEach { account ->
                val display = account.str("display", "未命名")
                val status = account.str("status")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    O2AText(display, style = O2ATextStyle.Body)
                    O2ATag(
                        text = when (status) {
                            "ok" -> "正常"
                            "stale" -> "数据过期"
                            else -> "不可用"
                        },
                        highlight = status == "ok",
                    )
                }
                account.raw("error")?.let {
                    O2AText(it, style = O2ATextStyle.Caption)
                }
                val windows = account.obj("windows")
                if (windows == null) {
                    O2AText("暂无窗口数据", style = O2ATextStyle.Caption)
                } else {
                    QUOTA_WINDOWS.forEach { (key, label) ->
                        val window = windows.obj(key) ?: return@forEach
                        val used = window.num("used_percent") ?: 0.0
                        O2AText(
                            "$label · 剩余 ${formatPercent(1 - used / 100)} · 重置 ${formatTimestamp(window.raw("resets_at"))}",
                            style = O2ATextStyle.Caption,
                        )
                        O2AProgressBar(progress = (used / 100).toFloat().coerceIn(0f, 1f))
                    }
                }
                O2AText(
                    "查询于 ${formatTimestamp(account.raw("checked_at"))}",
                    style = O2ATextStyle.Caption,
                )
            }
            data?.str("note")?.takeIf { it.isNotBlank() }?.let {
                O2AText(it, style = O2ATextStyle.Caption)
            }
        }

        O2AActionRow {
            O2ATextButton(text = "查询额度", onClick = onRefresh)
        }
    }
}

private val QUOTA_WINDOWS = listOf(
    "rolling" to "滚动窗口",
    "weekly" to "本周",
    "monthly" to "本月",
)

@Composable
private fun ModelCard(model: JsonObject) {
    val disabled = model.bool("disabled")
    val channel = model.str("channel")

    O2ACard(modifier = Modifier.padding(horizontal = 12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            O2AText(model.str("model"), style = O2ATextStyle.Body)
            if (disabled) O2ATag(text = "已停用") else O2ATag(text = "可用", highlight = true)
        }
        val details = buildList {
            channel.takeIf { it.isNotBlank() }?.let { add("通道 $it") }
            if (model.bool("manual")) add("手动设置")
            model.raw("next_check")?.let { add("下次检查 ${formatTimestamp(it)}") }
        }
        if (details.isNotEmpty()) {
            O2AText(details.joinToString(" · "), style = O2ATextStyle.Caption)
        }
        model.str("reason").takeIf { it.isNotBlank() }?.let {
            O2AText(it, style = O2ATextStyle.Caption)
        }
    }
}

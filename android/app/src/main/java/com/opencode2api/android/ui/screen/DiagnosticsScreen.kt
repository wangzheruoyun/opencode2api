package com.opencode2api.android.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.opencode2api.android.ui.component.O2AActionRow
import com.opencode2api.android.ui.component.O2ACard
import com.opencode2api.android.ui.component.O2AEmptyState
import com.opencode2api.android.ui.component.O2AMonoBlock
import com.opencode2api.android.ui.component.O2AProgressBar
import com.opencode2api.android.ui.component.O2ARow
import com.opencode2api.android.ui.component.O2ASectionTitle
import com.opencode2api.android.ui.component.O2ATag
import com.opencode2api.android.ui.component.O2AText
import com.opencode2api.android.ui.component.O2ATextField
import com.opencode2api.android.ui.component.O2ATextStyle
import com.opencode2api.android.ui.component.O2ATextButton
import com.opencode2api.android.ui.util.bool
import com.opencode2api.android.ui.util.formatMillis
import com.opencode2api.android.ui.util.formatTimestamp
import com.opencode2api.android.ui.util.int
import com.opencode2api.android.ui.util.list
import com.opencode2api.android.ui.util.obj
import com.opencode2api.android.ui.util.pretty
import com.opencode2api.android.ui.util.raw
import com.opencode2api.android.ui.util.str
import kotlinx.serialization.json.JsonObject

/**
 * 路由诊断 — why a given request went where it went.
 *
 * The model table is the interesting half: for every model the gateway knows
 * about it shows the protocol it speaks natively, whether it may take the
 * anonymous lane and on what grounds, and what the two channels cost. The two
 * tables under it are the raw trail — which credential each request finished
 * on, and every key the gateway tried before it got there.
 */
@Composable
fun DiagnosticsScreen() {
    val base = rememberAdminBaseUrl()

    AdminGate(baseUrl = base) { url ->
        val debug = rememberAdminJson(url, "/api/debug/models")
        val monitor = rememberAdminJson(url, "/api/monitor")
        var query by remember { mutableStateOf("") }

        val allModels = debug.data?.list("models").orEmpty()
        val models = if (query.isBlank()) {
            allModels
        } else {
            val needle = query.trim().lowercase()
            allModels.filter { it.str("model").lowercase().contains(needle) }
        }
        val requests = monitor.data?.list("upstream.requests").orEmpty().takeLast(100).reversed()
        val attempts = monitor.data?.list("upstream.recent").orEmpty().takeLast(100).reversed()

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                O2ACard(modifier = Modifier.padding(horizontal = 12.dp)) {
                    O2AText("模型 Metadata", style = O2ATextStyle.Subtitle)
                    val metadata = debug.data?.obj("metadata")
                    if (metadata == null) {
                        if (debug.loading) {
                            O2AProgressBar(progress = null)
                        } else {
                            O2AText(debug.error ?: "暂无数据", style = O2ATextStyle.Caption)
                        }
                    } else {
                        O2AText(
                            "models.dev 的 OpenCode 价格缓存；零成本或名称含 free 任一条件即可进入匿名通道。",
                            style = O2ATextStyle.Caption,
                        )
                        O2ARow(
                            title = "缓存状态",
                            summary = when {
                                metadata.bool("stale") -> "已过期，等待刷新"
                                metadata.bool("ready") -> "就绪"
                                else -> "尚未就绪"
                            },
                        )
                        O2ARow(title = "已缓存模型", summary = metadata.int("models").toString())
                        O2ARow(title = "最近更新", summary = formatTimestamp(metadata.raw("updated_at")))
                        O2ARow(title = "下次刷新", summary = formatTimestamp(metadata.raw("next_refresh")))
                        metadata.raw("last_error")?.let {
                            O2AText(it, style = O2ATextStyle.Caption)
                        }
                    }
                    O2AActionRow {
                        O2ATextButton(text = "刷新", onClick = {
                            debug.refresh()
                            monitor.refresh()
                        })
                    }
                }
            }

            item {
                O2ACard(modifier = Modifier.padding(horizontal = 12.dp)) {
                    O2AText("最近 Playground", style = O2ATextStyle.Subtitle)
                    O2AText(
                        "只保留当前管理进程内的最后一次追踪。",
                        style = O2ATextStyle.Caption,
                    )
                    val last = debug.data?.obj("last_inference")
                    if (last == null) {
                        O2AEmptyState("本次进程内还没有 Playground 请求")
                    } else {
                        O2AMonoBlock(last.pretty(), maxLines = 20)
                    }
                }
            }

            item {
                Column(modifier = Modifier.padding(horizontal = 12.dp)) {
                    O2ASectionTitle("模型路由表")
                    O2ATextField(
                        value = query,
                        onValueChange = { query = it },
                        label = "筛选模型（共 ${allModels.size} 个）",
                    )
                }
            }

            when {
                models.isEmpty() && debug.loading -> item {
                    PaddedSection { O2AProgressBar(progress = null) }
                }

                models.isEmpty() -> item {
                    PaddedSection { O2AEmptyState("没有匹配的模型") }
                }

                else -> items(models, key = { it.str("model") }) { model ->
                    ModelRouteCard(model)
                }
            }

            item {
                Column(modifier = Modifier.padding(horizontal = 12.dp)) {
                    O2ASectionTitle("请求路由（最近 ${requests.size} 条）")
                    O2AText(
                        "每个请求最终实际使用的 Key 尾码或 anonymous。",
                        style = O2ATextStyle.Caption,
                    )
                }
            }
            if (requests.isEmpty()) {
                item { PaddedSection { O2AEmptyState("还没有经过网关的请求") } }
            } else {
                items(requests) { record -> RequestRow(record) }
            }

            item {
                Column(modifier = Modifier.padding(horizontal = 12.dp)) {
                    O2ASectionTitle("上游尝试（最近 ${attempts.size} 条）")
                    O2AText(
                        "一个请求可能试过多个 Key；这里列的是每一次尝试。",
                        style = O2ATextStyle.Caption,
                    )
                }
            }
            if (attempts.isEmpty()) {
                item { PaddedSection { O2AEmptyState("还没有上游尝试记录") } }
            } else {
                items(attempts) { record -> AttemptRow(record) }
            }
        }
    }
}


@Composable
private fun ModelRouteCard(model: JsonObject) {
    val eligibility = model.obj("anonymous_eligibility")

    O2ACard(modifier = Modifier.padding(horizontal = 12.dp)) {
        O2AText(model.str("model"), style = O2ATextStyle.Body)
        O2ARow(title = "原生协议", summary = model.str("native_protocol", "-"))
        O2ARow(title = "协议来源", summary = model.str("protocol_source", "-"))
        O2ARow(
            title = "可用通道",
            summary = buildList {
                if (model.bool("available_zen")) add("Zen")
                if (model.bool("available_go")) add("Go")
            }.joinToString(" · ").ifEmpty { "仅匿名" },
        )
        if (eligibility != null) {
            O2ATag(
                text = if (eligibility.bool("allowed")) "可走匿名" else "不可匿名",
                highlight = eligibility.bool("allowed"),
            )
            O2ARow(title = "判断来源", summary = eligibility.str("source", "-"))
            val costs = buildList {
                eligibility.raw("input_cost")?.let { add("输入 $it") }
                eligibility.raw("output_cost")?.let { add("输出 $it") }
            }
            if (costs.isNotEmpty()) {
                O2ARow(title = "成本", summary = costs.joinToString(" · "))
            }
        }
        model.raw("route_error")?.let { O2AText(it, style = O2ATextStyle.Caption) }
    }
}

@Composable
private fun RequestRow(record: JsonObject) {
    val ok = record.bool("success")
    O2ACard(modifier = Modifier.padding(horizontal = 12.dp)) {
        O2AText(
            "${record.str("model", "-")} · ${record.str("channel", "-")}",
            style = O2ATextStyle.Body,
        )
        O2ATag(text = if (ok) "成功" else "失败", highlight = ok)
        O2ARow(title = "时间", summary = formatTimestamp(record.raw("time")))
        record.raw("request_id")?.let { O2ARow(title = "Request ID", summary = it) }
        record.raw("tier")?.let { O2ARow(title = "Tier", summary = it) }
        O2ARow(
            title = "耗时 / 状态 / 尝试",
            summary = "${formatMillis(record.raw("duration_ms")?.toLongOrNull() ?: 0L)} · " +
                "${record.raw("status") ?: "-"} · ${record.raw("attempts") ?: "-"} 次",
        )
        val credential = buildList {
            record.raw("key_id")?.let { add("Key $it") }
            if (record.bool("anonymous")) add("anonymous")
            record.raw("proxy_node")?.let { add("Proxy $it") }
        }
        if (credential.isNotEmpty()) {
            O2ARow(title = "出口", summary = credential.joinToString(" · "))
        }
        record.raw("outcome")?.let { O2ARow(title = "结果", summary = it) }
    }
}

@Composable
private fun AttemptRow(record: JsonObject) {
    val ok = record.bool("success")
    O2ACard(modifier = Modifier.padding(horizontal = 12.dp)) {
        O2AText(
            "${record.str("model", "-")} · 第 ${record.raw("attempt") ?: "-"} 次",
            style = O2ATextStyle.Body,
        )
        O2ATag(text = if (ok) "成功" else "失败", highlight = ok)
        O2ARow(
            title = "时间 / 耗时",
            summary = "${formatTimestamp(record.raw("time"))} · " +
                formatMillis(record.raw("duration_ms")?.toLongOrNull() ?: 0L),
        )
        val credential = buildList {
            record.raw("key_id")?.let { add("Key $it") }
            record.raw("channel")?.let { add(it) }
            if (record.bool("anonymous")) add("anonymous")
            record.raw("proxy_node")?.let { add("Proxy $it") }
        }
        if (credential.isNotEmpty()) {
            O2ARow(title = "出口", summary = credential.joinToString(" · "))
        }
        record.raw("outcome")?.let { O2ARow(title = "结果", summary = it) }
    }
}

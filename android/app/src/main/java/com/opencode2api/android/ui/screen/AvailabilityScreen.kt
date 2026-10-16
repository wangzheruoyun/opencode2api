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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.opencode2api.android.core.GatewayRuntime
import com.opencode2api.android.ui.component.O2AActionRow
import com.opencode2api.android.ui.component.O2ACard
import com.opencode2api.android.ui.component.O2AEmptyState
import com.opencode2api.android.ui.component.O2AProgressBar
import com.opencode2api.android.ui.component.O2ARow
import com.opencode2api.android.ui.component.O2ATag
import com.opencode2api.android.ui.component.O2AText
import com.opencode2api.android.ui.component.O2ATextButton
import com.opencode2api.android.ui.component.O2ATextStyle
import com.opencode2api.android.ui.util.bool
import com.opencode2api.android.ui.util.formatPendingTimestamp
import com.opencode2api.android.ui.util.list
import com.opencode2api.android.ui.util.obj
import com.opencode2api.android.ui.util.raw
import com.opencode2api.android.ui.util.str
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * 免费模型探测 — the full availability list, including the models that have been
 * switched off.
 *
 * The probe is the gateway's own: it checks the models whose names contain
 * `free` or whose metadata prices them at zero, prefers the anonymous lane and
 * falls back to a Zen key, and only disables a model when the upstream says the
 * model itself is gone. Everything here is a view onto that decision, plus the
 * two overrides a person can apply — restart probing now, or take the model out
 * of the probe's hands entirely.
 */
@Composable
fun AvailabilityScreen() {
    val base = rememberAdminBaseUrl()

    AdminGate(baseUrl = base) { url ->
        val scope = rememberCoroutineScope()
        val availability = rememberAdminJson(url, "/api/models/availability")

        var busy by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf<String?>(null) }

        val models = availability.data?.list("models").orEmpty()

        // Both overrides answer with the whole list, so the screen just re-fetches.
        val send: (String, JsonObject) -> Unit = { path, body ->
            if (!busy) {
                busy = true
                error = null
                scope.launch {
                    GatewayRuntime.admin.post(url, path, body)
                        .onSuccess { availability.refresh() }
                        .onFailure { error = it.message ?: "操作失败" }
                    busy = false
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                O2ACard(modifier = Modifier.padding(horizontal = 12.dp)) {
                    O2AText("免费模型探测", style = O2ATextStyle.Subtitle)
                    O2AText(
                        "探测名称含 free 或元数据零价格、且未弃用的模型：优先匿名通道，再试 Zen Key；" +
                            "只有确认模型失效才禁用，并在 24 小时后复测。",
                        style = O2ATextStyle.Caption,
                    )
                    O2AText(
                        "临时错误不会新增禁用：已启用的模型 1 小时后重试，已禁用的保留原因并在 24 小时后重试。" +
                            "恢复启用或切为手动后，该模型保持启用且不再自动探测。",
                        style = O2ATextStyle.Caption,
                    )
                    error?.let { O2AText(it, style = O2ATextStyle.Caption) }
                    O2AActionRow {
                        O2ATextButton(
                            text = "刷新状态",
                            enabled = !busy,
                            onClick = availability.refresh,
                        )
                    }
                }
            }

            item {
                Column(modifier = Modifier.padding(horizontal = 12.dp)) {
                    O2AText(
                        "共 ${models.size} 个模型 · 其中已禁用 ${models.count { it.bool("disabled") }} 个",
                        style = O2ATextStyle.Caption,
                    )
                }
            }

            when {
                models.isEmpty() && availability.loading -> item {
                    PaddedSection { O2AProgressBar(progress = null) }
                }

                models.isEmpty() -> item {
                    PaddedSection {
                        O2AEmptyState(
                            availability.error
                                ?: "当前模型目录中没有符合免费资格的模型",
                        )
                    }
                }

                else -> items(models, key = { it.str("model") }) { model ->
                    AvailabilityCard(
                        model = model,
                        busy = busy,
                        onRestore = {
                            send(
                                "/api/models/restore",
                                buildJsonObject { put("model", model.str("model")) },
                            )
                        },
                        onToggleManual = {
                            send(
                                "/api/models/manual",
                                buildJsonObject {
                                    put("model", model.str("model"))
                                    put("manual", !model.bool("manual"))
                                },
                            )
                        },
                    )
                }
            }
        }
    }
}


@Composable
private fun AvailabilityCard(
    model: JsonObject,
    busy: Boolean,
    onRestore: () -> Unit,
    onToggleManual: () -> Unit,
) {
    val disabled = model.bool("disabled")
    val manual = model.bool("manual")
    val channel = model.str("channel")
    val reason = model.str("reason")
    val effort = model.obj("auto_effort")?.str("effort").orEmpty()

    O2ACard(modifier = Modifier.padding(horizontal = 12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            O2AText(model.str("model"), style = O2ATextStyle.Body)
            if (disabled) O2ATag(text = "已禁用") else O2ATag(text = "已启用", highlight = true)
            O2ATag(text = if (manual) "手动" else "自动")
        }
        O2ARow(
            title = "通道 / 结果",
            summary = listOf(channel, reason).filter { it.isNotBlank() }
                .joinToString(" / ")
                .ifEmpty { "待探测" },
        )
        O2ARow(
            title = "已验证思考档位",
            summary = if (effort.isBlank()) {
                "未验证"
            } else {
                "$effort（${formatPendingTimestamp(model.raw("effort_checked_at"), "未验证")}）"
            },
        )
        O2ARow(
            title = "上次探测",
            summary = formatPendingTimestamp(model.raw("checked_at"), "待探测"),
        )
        O2ARow(
            title = "下次探测",
            summary = if (manual) {
                "手动控制，不自动探测"
            } else {
                formatPendingTimestamp(model.raw("next_check"), "待探测")
            },
        )
        model.raw("probe_version")?.let { O2ARow(title = "探测版本", summary = it) }
        O2AActionRow {
            O2ATextButton(text = "恢复启用", enabled = !busy && disabled, onClick = onRestore)
            O2ATextButton(
                text = if (manual) "切回自动" else "切为手动",
                enabled = !busy,
                onClick = onToggleManual,
            )
        }
    }
}

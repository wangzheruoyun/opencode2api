package com.opencode2api.android.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.opencode2api.android.core.GatewayRuntime
import com.opencode2api.android.ui.component.O2AActionRow
import com.opencode2api.android.ui.component.O2AButton
import com.opencode2api.android.ui.component.O2ACard
import com.opencode2api.android.ui.component.O2AEmptyState
import com.opencode2api.android.ui.component.O2AProgressBar
import com.opencode2api.android.ui.component.O2ARow
import com.opencode2api.android.ui.component.O2ASectionTitle
import com.opencode2api.android.ui.component.O2ATag
import com.opencode2api.android.ui.component.O2AText
import com.opencode2api.android.ui.component.O2ATextButton
import com.opencode2api.android.ui.component.O2ATextField
import com.opencode2api.android.ui.component.O2ATextStyle
import com.opencode2api.android.ui.util.formatTimestamp
import com.opencode2api.android.ui.util.list
import com.opencode2api.android.ui.util.obj
import com.opencode2api.android.ui.util.raw
import com.opencode2api.android.ui.util.str
import com.opencode2api.android.ui.util.stringList
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * 模型轮转 — the SOTA / 甜点 aliases, their fallback order, and where each group
 * currently sits.
 *
 * The gateway serves a group by keeping its position and only moving when the
 * current model runs out of quota or errors, so the order in this screen is a
 * fallback list rather than a rotation schedule. Only the order and the alias are
 * editable: the membership of the SOTA group is fixed by the server, which also
 * rejects an order that drops or adds a model.
 */
@Composable
fun RotationScreen() {
    val base = rememberAdminBaseUrl()

    AdminGate(baseUrl = base) { url ->
        val scope = rememberCoroutineScope()
        val rotation = rememberAdminJson(url, "/api/rotation")

        var draft by remember { mutableStateOf<RotationDraft?>(null) }
        var dirty by remember { mutableStateOf(false) }
        var busy by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf<String?>(null) }

        // Seed from the server whenever we are not holding unsaved edits, so a
        // refresh after a failed save (or a manual switch made elsewhere) wins
        // over a stale local copy — but never discards work in progress.
        LaunchedEffect(rotation.data) {
            val snapshot = rotation.data
            if (snapshot != null && !dirty) draft = RotationDraft.from(snapshot)
        }

        val mutate: (suspend () -> Result<JsonObject>) -> Unit = { block ->
            if (!busy) {
                busy = true
                error = null
                scope.launch {
                    block()
                        .onSuccess {
                            dirty = false
                            rotation.refresh()
                        }
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
                    O2AText("SOTA / 甜点模型轮转", style = O2ATextStyle.Subtitle)
                    O2AText(
                        "成功后继续使用当前模型；额度不足或错误时按顺序切换，仅使用 OpenCode Go。",
                        style = O2ATextStyle.Caption,
                    )
                    O2AText(
                        "沿用现有 Base URL 和 API Key，把模型名设为下面的别名即可。每个模型普通错误最多尝试 3 次，" +
                            "一次请求最多重试 3 个模型；额度不足直接跳过。",
                        style = O2ATextStyle.Caption,
                    )
                    O2AText(
                        "输出开始后本次回答固定使用同一模型，中途故障会结束本次输出，下一次请求才换模型。" +
                            "模型共享套餐额度，切换不保证解决套餐耗尽。",
                        style = O2ATextStyle.Caption,
                    )
                    O2ARow(
                        title = "状态",
                        summary = rotation.data?.raw("persistence_error")
                            ?: "已同步 · 两组独立轮转，所有调用方共享当前位置",
                    )
                    O2AActionRow {
                        O2ATextButton(
                            text = "刷新状态",
                            enabled = !busy,
                            onClick = {
                                dirty = false
                                rotation.refresh()
                            },
                        )
                    }
                }
            }

            val current = draft
            if (current == null) {
                item {
                    PaddedSection {
                        if (rotation.loading) {
                            O2AProgressBar(progress = null)
                        } else {
                            O2AEmptyState(rotation.error ?: "暂无轮转状态")
                        }
                    }
                }
            } else {
                item {
                    GroupCard(
                        group = current.sota,
                        busy = busy,
                        onAlias = { value ->
                            dirty = true
                            draft = current.copy(sota = current.sota.copy(alias = value))
                        },
                        onMove = { index, delta ->
                            dirty = true
                            draft = current.copy(sota = current.sota.moved(index, delta))
                        },
                        onSetCurrent = { model ->
                            mutate {
                                GatewayRuntime.admin.post(
                                    url,
                                    "/api/rotation/current",
                                    buildJsonObject {
                                        put("group", current.sota.id)
                                        put("model", model)
                                    },
                                )
                            }
                        },
                    )
                }

                item {
                    GroupCard(
                        group = current.sweet,
                        busy = busy,
                        onAlias = { value ->
                            dirty = true
                            draft = current.copy(sweet = current.sweet.copy(alias = value))
                        },
                        onMove = { index, delta ->
                            dirty = true
                            draft = current.copy(sweet = current.sweet.moved(index, delta))
                        },
                        onSetCurrent = { model ->
                            mutate {
                                GatewayRuntime.admin.post(
                                    url,
                                    "/api/rotation/current",
                                    buildJsonObject {
                                        put("group", current.sweet.id)
                                        put("model", model)
                                    },
                                )
                            }
                        },
                    )
                }

                item {
                    O2ACard(modifier = Modifier.padding(horizontal = 12.dp)) {
                        NumberField(
                            label = "首个有效输出等待上限（秒）",
                            value = current.firstOutputSeconds,
                            onChange = {
                                dirty = true
                                draft = current.copy(firstOutputSeconds = it)
                            },
                        )
                        O2AText(
                            "1–300 秒。超过这个时间还没有第一个有效输出，就按顺序换下一个模型。",
                            style = O2ATextStyle.Caption,
                        )
                        error?.let { O2AText(it, style = O2ATextStyle.Caption) }
                        O2AActionRow {
                            O2AButton(
                                text = if (busy) "保存中…" else "保存别名与顺序",
                                enabled = !busy && dirty,
                                onClick = {
                                    val body = buildJsonObject {
                                        put("sota", current.sota.toJson())
                                        put("sweet", current.sweet.toJson())
                                        put("first_output_seconds", current.firstOutputSeconds)
                                    }
                                    mutate {
                                        GatewayRuntime.admin.put(url, "/api/rotation", body)
                                    }
                                },
                            )
                        }
                        O2AText(
                            "保存立即生效，调整顺序不会打断正在输出的回答；手动选择当前模型会立即生效，" +
                                "并解除该模型的临时暂停。SOTA 组必须包含全部五个指定模型。",
                            style = O2ATextStyle.Caption,
                        )
                    }
                }
            }

            val attempts = rotation.data?.list("attempts").orEmpty().takeLast(50).reversed()
            item {
                Column(modifier = Modifier.padding(horizontal = 12.dp)) {
                    O2ASectionTitle("最近请求尝试（${attempts.size}）")
                    O2AText(
                        "每次请求选中的别名与实际模型，以及是第几次尝试。",
                        style = O2ATextStyle.Caption,
                    )
                }
            }
            if (attempts.isEmpty()) {
                item { PaddedSection { O2AEmptyState("还没有轮转请求") } }
            } else {
                items(attempts) { attempt -> AttemptCard(attempt) }
            }
        }
    }
}


@Composable
private fun GroupCard(
    group: GroupDraft,
    busy: Boolean,
    onAlias: (String) -> Unit,
    onMove: (Int, Int) -> Unit,
    onSetCurrent: (String) -> Unit,
) {
    O2ACard(modifier = Modifier.padding(horizontal = 12.dp)) {
        O2AText(group.title, style = O2ATextStyle.Subtitle)
        O2ATextField(
            value = group.alias,
            onValueChange = onAlias,
            label = "调用别名",
            enabled = !busy,
        )
        O2ARow(
            title = "当前模型",
            summary = group.current.ifBlank { "等待可用模型" },
        )
        group.order.forEachIndexed { index, model ->
            val pausedUntil = group.paused[model]
            val status = when {
                group.available[model] == false -> "已下架 / 暂不可用"
                pausedUntil != null -> "暂停至 ${formatTimestamp(pausedUntil)}"
                model == group.current -> "当前使用"
                else -> "可用"
            }
            O2ARow(title = "${index + 1}. $model", summary = status)
            O2AActionRow {
                O2ATextButton(
                    text = "上移",
                    enabled = !busy && index > 0,
                    onClick = { onMove(index, -1) },
                )
                O2ATextButton(
                    text = "下移",
                    enabled = !busy && index < group.order.lastIndex,
                    onClick = { onMove(index, 1) },
                )
                O2ATextButton(
                    text = "设为当前",
                    enabled = !busy && group.available[model] != false && model != group.current,
                    onClick = { onSetCurrent(model) },
                )
            }
        }
        if (group.order.isEmpty()) {
            O2AEmptyState("该组还没有模型")
        }
    }
}

@Composable
private fun AttemptCard(attempt: JsonObject) {
    O2ACard(modifier = Modifier.padding(horizontal = 12.dp)) {
        O2AText(
            "${attempt.str("alias", "-")} → ${attempt.str("model", "-")}",
            style = O2ATextStyle.Body,
        )
        O2ATag(text = attempt.str("outcome", "-"))
        O2ARow(
            title = "时间 / 第几次",
            summary = "${formatTimestamp(attempt.raw("time"))} · 第 ${attempt.raw("number") ?: "-"} 次",
        )
        attempt.raw("request")?.let { O2ARow(title = "Request ID", summary = it) }
    }
}

/** The editable half of one rotation group. */
private data class GroupDraft(
    val id: String,
    val title: String,
    val alias: String,
    val order: List<String>,
    val current: String,
    val available: Map<String, Boolean>,
    val paused: Map<String, String>,
) {
    /** Swaps the model at [index] with its neighbour [delta] places away. */
    fun moved(index: Int, delta: Int): GroupDraft {
        val target = index + delta
        if (index !in order.indices || target !in order.indices) return this
        val next = order.toMutableList()
        next[index] = order[target]
        next[target] = order[index]
        return copy(order = next)
    }

    /**
     * The server takes the whole group back, and rejects an order whose
     * membership it does not recognise — so this sends the list it was given,
     * reordered, and nothing else.
     */
    fun toJson(): JsonObject = buildJsonObject {
        put("alias", alias.trim())
        put("order", buildJsonArray { order.forEach { add(JsonPrimitive(it)) } })
    }
}

private data class RotationDraft(
    val sota: GroupDraft,
    val sweet: GroupDraft,
    val firstOutputSeconds: Int,
) {
    companion object {
        fun from(snapshot: JsonObject): RotationDraft {
            val groups = snapshot.list("groups")
            return RotationDraft(
                sota = groupOf(groups, "sota", "SOTA"),
                sweet = groupOf(groups, "sweet", "甜点"),
                firstOutputSeconds = snapshot.raw("first_output_seconds")?.toIntOrNull() ?: 30,
            )
        }

        private fun groupOf(groups: List<JsonObject>, id: String, title: String): GroupDraft {
            val group = groups.firstOrNull { it.str("id") == id }
            return GroupDraft(
                id = id,
                title = title,
                alias = group?.str("alias").orEmpty(),
                order = group?.stringList("order").orEmpty(),
                current = group?.str("current").orEmpty(),
                available = group?.obj("available").orEmpty()
                    .mapValues { (_, value) -> (value as? JsonPrimitive)?.booleanOrNull == true },
                paused = group?.obj("paused").orEmpty()
                    .mapValues { (_, value) -> (value as? JsonPrimitive)?.content.orEmpty() },
            )
        }
    }
}

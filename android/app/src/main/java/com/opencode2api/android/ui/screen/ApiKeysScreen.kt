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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.opencode2api.android.core.GatewayRuntime
import com.opencode2api.android.ui.component.O2AActionRow
import com.opencode2api.android.ui.component.O2AButton
import com.opencode2api.android.ui.component.O2ACard
import com.opencode2api.android.ui.component.O2AEmptyState
import com.opencode2api.android.ui.component.O2AProgressBar
import com.opencode2api.android.ui.component.O2ARow
import com.opencode2api.android.ui.component.O2ASectionTitle
import com.opencode2api.android.ui.component.O2ASwitchRow
import com.opencode2api.android.ui.component.O2ATag
import com.opencode2api.android.ui.component.O2AText
import com.opencode2api.android.ui.component.O2ATextButton
import com.opencode2api.android.ui.component.O2ATextField
import com.opencode2api.android.ui.component.O2ATextStyle
import com.opencode2api.android.ui.util.bool
import com.opencode2api.android.ui.util.copyToClipboard
import com.opencode2api.android.ui.util.formatCount
import com.opencode2api.android.ui.util.formatTimestamp
import com.opencode2api.android.ui.util.list
import com.opencode2api.android.ui.util.long
import com.opencode2api.android.ui.util.obj
import com.opencode2api.android.ui.util.raw
import com.opencode2api.android.ui.util.str
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * 客户端 API Key — the `sk-local-…` keys this gateway hands out to the tools that
 * call *it*, as opposed to the upstream keys it calls out with.
 *
 * A generated key is shown exactly once, in the response to the create call, and
 * can never be read back afterwards: the server keeps a fingerprint and a masked
 * display, not the secret. So the created key gets its own card, pinned above the
 * list, until the user says they have it.
 */
@Composable
fun ApiKeysScreen() {
    val base = rememberAdminBaseUrl()

    AdminGate(baseUrl = base) { url ->
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val keys = rememberAdminJson(url, "/api/api-keys")

        var name by remember { mutableStateOf("") }
        var custom by remember { mutableStateOf("") }
        var busy by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf<String?>(null) }
        var created by remember { mutableStateOf<String?>(null) }

        var renamingId by remember { mutableStateOf<String?>(null) }
        var renameText by remember { mutableStateOf("") }
        var deletingId by remember { mutableStateOf<String?>(null) }

        val keyList = keys.data?.list("keys").orEmpty()

        val mutate: (suspend () -> Result<JsonObject>) -> Unit = { block ->
            if (!busy) {
                busy = true
                error = null
                scope.launch {
                    block()
                        .onSuccess {
                            // Only the create call carries a "key"; for the rest
                            // this keeps whatever was already pinned.
                            it.raw("key")?.let { secret -> created = secret }
                            renamingId = null
                            deletingId = null
                            name = ""
                            custom = ""
                            keys.refresh()
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
                    O2AText("新建 API Key", style = O2ATextStyle.Subtitle)
                    O2AText(
                        "客户端用这个 Key 访问本网关，与上游 Zen / Go 密钥无关。留空自定义值即随机生成。",
                        style = O2ATextStyle.Caption,
                    )
                    O2ATextField(
                        value = name,
                        onValueChange = { name = it },
                        label = "名称（1–80 字）",
                        enabled = !busy,
                    )
                    O2ATextField(
                        value = custom,
                        onValueChange = { custom = it },
                        label = "自定义 Key（可选，16–256 位）",
                        enabled = !busy,
                    )
                    O2AText(
                        "自定义 Key 仅支持英文字母、数字、短横线、下划线和点；最多 200 个。",
                        style = O2ATextStyle.Caption,
                    )
                    error?.let { O2AText(it, style = O2ATextStyle.Caption) }
                    O2AActionRow {
                        O2AButton(
                            text = if (busy) "处理中…" else "创建",
                            enabled = !busy && name.isNotBlank(),
                            onClick = {
                                val payload = buildJsonObject {
                                    put("name", name.trim())
                                    if (custom.isNotBlank()) put("value", custom.trim())
                                }
                                mutate {
                                    GatewayRuntime.admin.post(url, "/api/api-keys", payload)
                                }
                            },
                        )
                    }
                }
            }

            created?.let { secret ->
                item {
                    O2ACard(modifier = Modifier.padding(horizontal = 12.dp)) {
                        O2AText("新 Key（只显示这一次）", style = O2ATextStyle.Subtitle)
                        O2AText(
                            "服务端只保存指纹与掩码，关掉这张卡片后就再也读不回来了。先复制，再继续。",
                            style = O2ATextStyle.Caption,
                        )
                        O2ARow(
                            title = "完整 Key",
                            summary = secret,
                            onClick = { copyToClipboard(context, "API Key", secret) },
                        )
                        O2AActionRow {
                            O2ATextButton(
                                text = "复制",
                                onClick = { copyToClipboard(context, "API Key", secret) },
                            )
                            O2ATextButton(text = "我已保存", onClick = { created = null })
                        }
                    }
                }
            }

            item {
                Column(modifier = Modifier.padding(horizontal = 12.dp)) {
                    O2ASectionTitle("已有 Key（${keyList.size}）")
                    O2AActionRow {
                        O2ATextButton(text = "刷新", onClick = keys.refresh)
                    }
                }
            }

            when {
                keyList.isEmpty() && keys.loading -> item {
                    PaddedSection { O2AProgressBar(progress = null) }
                }

                keyList.isEmpty() -> item {
                    PaddedSection { O2AEmptyState(keys.error ?: "还没有客户端 Key") }
                }

                else -> items(keyList, key = { it.str("id") }) { key ->
                    val id = key.str("id")
                    ApiKeyCard(
                        key = key,
                        busy = busy,
                        renaming = renamingId == id,
                        renameText = renameText,
                        onRenameText = { renameText = it },
                        confirmingDelete = deletingId == id,
                        onStartRename = {
                            renamingId = id
                            renameText = key.str("name")
                            deletingId = null
                        },
                        onCancel = {
                            renamingId = null
                            deletingId = null
                        },
                        onConfirmRename = {
                            val payload = buildJsonObject { put("name", renameText.trim()) }
                            mutate {
                                GatewayRuntime.admin.patch(url, "/api/api-keys/$id", payload)
                            }
                        },
                        onToggle = { enabled ->
                            val payload = buildJsonObject { put("enabled", enabled) }
                            mutate {
                                GatewayRuntime.admin.patch(url, "/api/api-keys/$id", payload)
                            }
                        },
                        onDelete = { deletingId = id },
                        onConfirmDelete = {
                            mutate { GatewayRuntime.admin.delete(url, "/api/api-keys/$id") }
                        },
                    )
                }
            }

            item {
                O2ACard(modifier = Modifier.padding(horizontal = 12.dp)) {
                    O2AText(
                        "服务端要求至少保留一个启用的 Key；停用或删除最后一个会被拒绝。" +
                            "用量统计随进程重启归零，与网关自身的监控口径一致。",
                        style = O2ATextStyle.Caption,
                    )
                }
            }
        }
    }
}


@Composable
private fun ApiKeyCard(
    key: JsonObject,
    busy: Boolean,
    renaming: Boolean,
    renameText: String,
    onRenameText: (String) -> Unit,
    confirmingDelete: Boolean,
    onStartRename: () -> Unit,
    onCancel: () -> Unit,
    onConfirmRename: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onConfirmDelete: () -> Unit,
) {
    val enabled = key.bool("enabled")
    val usage = key.obj("usage")

    O2ACard(modifier = Modifier.padding(horizontal = 12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            O2AText(key.str("name", "未命名"), style = O2ATextStyle.Body)
            if (enabled) O2ATag(text = "已启用", highlight = true) else O2ATag(text = "已停用")
        }
        O2ARow(title = "Key 尾码", summary = key.str("display", "-"))
        O2ARow(title = "创建时间", summary = formatTimestamp(key.raw("created_at")))
        usage?.obj("lifetime")?.let { life ->
            O2ARow(
                title = "累计请求",
                summary = "${formatCount(life.long("requests"))} · " +
                    "Token ${formatCount(life.long("total_tokens"))}",
            )
        }
        usage?.obj("last_hour")?.let { hour ->
            O2ARow(
                title = "最近一小时",
                summary = "${formatCount(hour.long("requests"))} · " +
                    "Token ${formatCount(hour.long("total_tokens"))}",
            )
        }

        if (renaming) {
            O2ATextField(
                value = renameText,
                onValueChange = onRenameText,
                label = "新名称",
                enabled = !busy,
            )
            O2AActionRow {
                O2AButton(
                    text = "保存名称",
                    enabled = !busy && renameText.isNotBlank(),
                    onClick = onConfirmRename,
                )
                O2ATextButton(text = "取消", onClick = onCancel, enabled = !busy)
            }
        } else if (confirmingDelete) {
            O2AText(
                "删除后使用该 Key 的客户端会立刻收到 401，且无法恢复。",
                style = O2ATextStyle.Caption,
            )
            O2AActionRow {
                O2AButton(text = "确认删除", enabled = !busy, onClick = onConfirmDelete)
                O2ATextButton(text = "取消", onClick = onCancel, enabled = !busy)
            }
        } else {
            O2ASwitchRow(
                title = "启用",
                checked = enabled,
                summary = "停用不会删除 Key，只是拒绝用它发起的请求",
                enabled = !busy,
                onCheckedChange = onToggle,
            )
            O2AActionRow {
                O2ATextButton(text = "重命名", onClick = onStartRename, enabled = !busy)
                O2ATextButton(text = "删除", onClick = onDelete, enabled = !busy)
            }
        }
    }
}

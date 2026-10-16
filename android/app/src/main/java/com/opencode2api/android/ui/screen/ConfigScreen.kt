package com.opencode2api.android.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.opencode2api.android.core.GatewayRuntime
import com.opencode2api.android.core.GatewayState
import com.opencode2api.android.service.GatewayService
import com.opencode2api.android.ui.Route
import com.opencode2api.android.ui.component.O2AActionRow
import com.opencode2api.android.ui.component.O2AButton
import com.opencode2api.android.ui.component.O2ACard
import com.opencode2api.android.ui.component.O2AChipRow
import com.opencode2api.android.ui.component.O2AEmptyState
import com.opencode2api.android.ui.component.O2AMonoBlock
import com.opencode2api.android.ui.component.O2ARow
import com.opencode2api.android.ui.component.O2ASectionTitle
import com.opencode2api.android.ui.component.O2ASelectableChip
import com.opencode2api.android.ui.component.O2ASwitchRow
import com.opencode2api.android.ui.component.O2AText
import com.opencode2api.android.ui.component.O2ATextField
import com.opencode2api.android.ui.component.O2ATextStyle
import com.opencode2api.android.ui.component.O2ATextButton
import com.opencode2api.android.ui.util.bool
import com.opencode2api.android.ui.util.copyToClipboard
import com.opencode2api.android.ui.util.int
import com.opencode2api.android.ui.util.pretty
import com.opencode2api.android.ui.util.str
import com.opencode2api.android.ui.util.stringList
import com.opencode2api.android.ui.util.toast
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * 配置中心 — the gateway's own `config.json`, edited in place.
 *
 * Two decisions shape this screen. First, it edits the file directly through
 * [com.opencode2api.android.data.GatewayConfigStore] rather than through the
 * admin API's `PUT /api/config`: the file is on this device and this app owns
 * it, and the admin route exists for a browser that has no filesystem. Going
 * through the API would mean holding a session and re-sending every field the
 * gateway knows about — including ones added upstream after this app shipped —
 * just to change a port number.
 *
 * Second, the tree is edited as JSON rather than through a typed mirror of the
 * schema. Upstream adds configuration blocks regularly, and a typed model would
 * quietly drop the ones it had never heard of the first time the user pressed
 * save. See the store's own note on the same subject.
 *
 * The cost of editing the file is that the running process keeps its old
 * values until it is told to re-read them, which is what 重载 is for.
 */
@Composable
fun ConfigScreen(onNavigate: (Route) -> Unit) {
    val context = LocalContext.current
    val store = GatewayRuntime.configStore
    val runnerState by GatewayRuntime.runner.state.collectAsStateWithLifecycle()

    var doc by remember { mutableStateOf(store.loadOrCreate()) }
    var dirty by remember { mutableStateOf(false) }
    var showRaw by remember { mutableStateOf(false) }

    val edit: ((JsonObject) -> JsonObject) -> Unit = { transform ->
        doc = transform(doc)
        dirty = true
    }

    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        O2ASectionTitle("服务与管理端")
        O2ACard {
            O2ATextField(
                value = doc.str("listen"),
                onValueChange = { value -> edit { it.withString("listen", value) } },
                label = "API 监听地址",
            )
            O2AText(
                "OpenAI / Anthropic 兼容接口的地址。手机自用保持 127.0.0.1 即可，" +
                    "填 0.0.0.0 才会对同一网络下的其它设备开放。",
                style = O2ATextStyle.Caption,
            )
            O2ASwitchRow(
                title = "启用管理界面",
                checked = doc.bool("webui.enabled", true),
                summary = "关闭后「事件日志」以外的管理页面都无法访问",
                onCheckedChange = { value -> edit { it.withBool("webui.enabled", value) } },
            )
            O2ATextField(
                value = doc.str("webui.listen"),
                onValueChange = { value -> edit { it.withString("webui.listen", value) } },
                label = "管理界面监听地址",
                enabled = doc.bool("webui.enabled", true),
            )
            O2ATextField(
                value = doc.str("webui.username"),
                onValueChange = { value -> edit { it.withString("webui.username", value) } },
                label = "管理端账号",
            )
            O2ATextField(
                value = doc.str("webui.password"),
                onValueChange = { value -> edit { it.withString("webui.password", value) } },
                label = "管理端密码",
                keyboardType = KeyboardType.Password,
            )
            O2AText(
                "首次启动时随机生成，明文保存在 config.json 里。改完需要保存并重载才生效。",
                style = O2ATextStyle.Caption,
            )
            O2ARow(
                title = "复制密码",
                onClick = {
                    copyToClipboard(context, "管理端密码", doc.str("webui.password"))
                },
            )
        }

        O2ASectionTitle("密钥与匿名通道")
        O2ACard {
            O2ASwitchRow(
                title = "启用匿名通道",
                checked = doc.bool("anonymous", true),
                summary = "无密钥时走 OpenCode 的匿名推理入口，额度与出口 IP 共享",
                onCheckedChange = { value -> edit { it.withBool("anonymous", value) } },
            )
            O2AText(
                "匿名通道之下，所有请求共用一个上游 IP 额度桶；配置了 zen / go 密钥时优先走密钥。",
                style = O2ATextStyle.Caption,
            )
        }
        KeyList(
            title = "OpenCode Zen 密钥",
            hint = "Zen 通道的上游密钥，按顺序轮换。",
            values = doc.stringList("zen_keys"),
            onChange = { values -> edit { it.withStringList("zen_keys", values) } },
        )
        KeyList(
            title = "OpenCode Go 密钥",
            hint = "Go 通道的上游密钥，按顺序轮换。",
            values = doc.stringList("go_keys"),
            onChange = { values -> edit { it.withStringList("go_keys", values) } },
        )
        KeyList(
            title = "本机访问密钥",
            hint = "客户端连本网关时使用的 sk- 密钥；「API Key 管理」管的是同一份数据。",
            values = doc.stringList("server_keys"),
            onChange = { values -> edit { it.withStringList("server_keys", values) } },
        )

        O2ASectionTitle("上游与协议路由")
        O2ACard {
            O2ATextField(
                value = doc.str("upstream.zen"),
                onValueChange = { value -> edit { it.withString("upstream.zen", value) } },
                label = "Zen 上游地址",
            )
            O2ATextField(
                value = doc.str("upstream.go"),
                onValueChange = { value -> edit { it.withString("upstream.go", value) } },
                label = "Go 上游地址",
            )
            O2ATextField(
                value = doc.str("upstream.anonymous"),
                onValueChange = { value -> edit { it.withString("upstream.anonymous", value) } },
                label = "匿名上游地址",
            )
            O2AText("首选通道", style = O2ATextStyle.Subtitle)
            O2AChipRow {
                listOf("zen" to "Zen", "go" to "Go").forEach { (value, label) ->
                    O2ASelectableChip(
                        text = label,
                        selected = doc.str("prefer", "go") == value,
                        onClick = { edit { it.withString("prefer", value) } },
                    )
                }
            }
            O2ATextField(
                value = doc.str("proxyfile"),
                onValueChange = { value -> edit { it.withString("proxyfile", value) } },
                label = "Proxy 文件路径",
            )
            O2AText(
                "Proxy 节点列表在「Proxies」字段里维护；本机直连时保持 direct 即可。",
                style = O2ATextStyle.Caption,
            )
            O2ARow(
                title = "模型轮转与别名",
                summary = "sota / sweet 两组的顺序、别名与首个输出超时",
                onClick = { onNavigate(Route.Rotation) },
            )
        }

        O2ASectionTitle("Thinking 强度")
        O2ACard {
            O2AText(
                "覆盖请求里的 reasoning 强度。auto 跟随上游默认，none 表示不下发该字段。",
                style = O2ATextStyle.Caption,
            )
            O2AChipRow {
                EFFORTS.forEach { effort ->
                    O2ASelectableChip(
                        text = effort,
                        selected = doc.str("reasoning.effort").ifEmpty { "auto" } == effort,
                        onClick = { edit { it.withString("reasoning.effort", effort) } },
                    )
                }
            }
        }

        O2ASectionTitle("性能与日志")
        O2ACard {
            O2AText("日志级别", style = O2ATextStyle.Subtitle)
            O2AChipRow {
                LOG_LEVELS.forEach { level ->
                    O2ASelectableChip(
                        text = level,
                        selected = doc.str("logging.level", "info") == level,
                        onClick = { edit { it.withString("logging.level", level) } },
                    )
                }
            }
            O2ASwitchRow(
                title = "记录请求体",
                checked = doc.bool("logging.dump_request_bodies"),
                summary = "排查问题时打开，会把请求正文写进日志",
                onCheckedChange = { value ->
                    edit { it.withBool("logging.dump_request_bodies", value) }
                },
            )
            O2ASwitchRow(
                title = "只用免费模型",
                checked = doc.bool("models.free_only"),
                summary = "模型列表只保留免费额度可用的条目",
                onCheckedChange = { value -> edit { it.withBool("models.free_only", value) } },
            )
            NumberField(
                label = "最大尝试次数",
                value = doc.int("retry.max_attempts", 3),
                onChange = { value -> edit { it.withInt("retry.max_attempts", value) } },
            )
            NumberField(
                label = "单请求超时（秒）",
                value = doc.int("retry.timeout_seconds", 300),
                onChange = { value -> edit { it.withInt("retry.timeout_seconds", value) } },
            )
            NumberField(
                label = "模型列表刷新（秒）",
                value = doc.int("models.refresh_seconds", 300),
                onChange = { value -> edit { it.withInt("models.refresh_seconds", value) } },
            )
            NumberField(
                label = "失败冷却（秒）",
                value = doc.int("performance.failure_cooldown_seconds", 15),
                onChange = { value ->
                    edit { it.withInt("performance.failure_cooldown_seconds", value) }
                },
            )
        }

        O2ASectionTitle("保存")
        O2ACard {
            if (dirty) {
                O2AText("有未保存的修改。", style = O2ATextStyle.Caption)
            } else {
                O2AText("当前没有待保存的修改。", style = O2ATextStyle.Caption)
            }
            O2AActionRow {
                O2AButton(
                    text = "保存",
                    enabled = dirty,
                    onClick = {
                        runCatching { store.save(doc) }
                            .onSuccess {
                                dirty = false
                                toast(context, "配置已保存")
                            }
                            .onFailure { toast(context, "保存失败：${it.message}") }
                    },
                )
                O2ATextButton(
                    text = "重载网关",
                    enabled = runnerState is GatewayState.Running,
                    onClick = { GatewayService.restart(context) },
                )
                O2ATextButton(
                    text = "从磁盘重读",
                    enabled = !dirty,
                    onClick = { doc = store.loadOrCreate() },
                )
            }
            O2AText(
                "「重载网关」会重启网关进程，新配置在进程重启后生效；" +
                    "网关未运行时保存的配置会在下次启动时读到。",
                style = O2ATextStyle.Caption,
            )
            if (dirty) {
                O2AText(
                    "从磁盘重读会丢弃尚未保存的修改，因此有改动时不可用。",
                    style = O2ATextStyle.Caption,
                )
            }
        }

        O2ACard {
            O2AText("配置文件", style = O2ATextStyle.Subtitle)
            O2AText(store.file.absolutePath, style = O2ATextStyle.Monospace)
            O2ARow(
                title = "复制路径",
                onClick = { copyToClipboard(context, "路径", store.file.absolutePath) },
            )
            O2ARow(
                title = if (showRaw) "收起原始 JSON" else "查看原始 JSON",
                summary = "包含上游新增、这个界面还认不出的字段",
                onClick = { showRaw = !showRaw },
            )
            if (showRaw) {
                O2AMonoBlock(doc.pretty(), maxLines = 200)
            }
        }
    }
}

private val EFFORTS = listOf("auto", "minimal", "low", "medium", "high", "xhigh", "max", "none")
private val LOG_LEVELS = listOf("debug", "info", "warn", "error")

/**
 * A list of secrets — upstream keys, local access keys.
 *
 * Values are shown masked because this screen is routinely held up next to
 * another person; the copy action on each row is how a key leaves the app.
 */
@Composable
private fun KeyList(
    title: String,
    hint: String,
    values: List<String>,
    onChange: (List<String>) -> Unit,
) {
    val context = LocalContext.current
    var draft by remember { mutableStateOf("") }

    O2ASectionTitle(title)
    O2ACard {
        O2AText(hint, style = O2ATextStyle.Caption)
        if (values.isEmpty()) {
            O2AEmptyState("尚未配置")
        } else {
            values.forEachIndexed { index, value ->
                O2ARow(
                    title = maskSecret(value),
                    summary = "#${index + 1}",
                    onClick = { copyToClipboard(context, title, value) },
                    trailing = {
                        O2ATextButton(
                            text = "删除",
                            onClick = { onChange(values.filterIndexed { i, _ -> i != index }) },
                        )
                    },
                )
            }
        }
        O2ATextField(
            value = draft,
            onValueChange = { draft = it },
            label = "添加密钥",
        )
        O2AActionRow {
            O2AButton(
                text = "添加",
                enabled = draft.isNotBlank(),
                onClick = {
                    onChange(values + draft.trim())
                    draft = ""
                },
            )
        }
    }
}

/** A number that is edited as text but stored as a number. */
@Composable
private fun NumberField(label: String, value: Int, onChange: (Int) -> Unit) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    O2ATextField(
        value = text,
        onValueChange = { input ->
            // Reject anything that is not a run of digits rather than accepting
            // it and silently dropping the edit, so the field cannot drift from
            // what is actually stored.
            if (input.isEmpty() || input.all { it.isDigit() }) {
                text = input
                input.toIntOrNull()?.let(onChange)
            }
        },
        label = label,
        keyboardType = KeyboardType.Number,
    )
}

/** `sk-abcdef…1234`: enough to tell two keys apart, not enough to be one. */
internal fun maskSecret(value: String): String = when {
    value.length <= 10 -> value
    else -> "${value.take(6)}…${value.takeLast(4)}"
}

// ---- JSON tree editing ---------------------------------------------------
// Small enough to keep beside the screen that needs it, rather than a general
// JSON-patch layer the app has no other use for.

private fun JsonObject.withString(path: String, value: String): JsonObject =
    with(path, JsonPrimitive(value))

private fun JsonObject.withInt(path: String, value: Int): JsonObject =
    with(path, JsonPrimitive(value))

private fun JsonObject.withBool(path: String, value: Boolean): JsonObject =
    with(path, JsonPrimitive(value))

private fun JsonObject.withStringList(path: String, values: List<String>): JsonObject =
    with(path, JsonArray(values.map { JsonPrimitive(it) }))

/** Replaces the value at `a.b.c`, creating intermediate objects as needed. */
private fun JsonObject.with(path: String, value: JsonElement): JsonObject {
    val segments = path.split('.')
    val head = segments.first()
    if (segments.size == 1) return JsonObject(this + (head to value))
    val child = this[head] as? JsonObject ?: JsonObject(emptyMap())
    val tail = segments.drop(1).joinToString(".")
    return JsonObject(this + (head to child.with(tail, value)))
}

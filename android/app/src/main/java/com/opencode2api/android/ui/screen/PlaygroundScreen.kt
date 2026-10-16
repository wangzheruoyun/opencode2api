package com.opencode2api.android.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.opencode2api.android.core.GatewayRuntime
import com.opencode2api.android.ui.component.O2AActionRow
import com.opencode2api.android.ui.component.O2AButton
import com.opencode2api.android.ui.component.O2ACard
import com.opencode2api.android.ui.component.O2AChipRow
import com.opencode2api.android.ui.component.O2AMonoBlock
import com.opencode2api.android.ui.component.O2AProgressBar
import com.opencode2api.android.ui.component.O2ARow
import com.opencode2api.android.ui.component.O2ASectionTitle
import com.opencode2api.android.ui.component.O2ASelectableChip
import com.opencode2api.android.ui.component.O2AText
import com.opencode2api.android.ui.component.O2ATextField
import com.opencode2api.android.ui.component.O2ATextStyle
import com.opencode2api.android.ui.component.O2ATextButton
import com.opencode2api.android.ui.util.copyToClipboard
import com.opencode2api.android.ui.util.formatMillis
import com.opencode2api.android.ui.util.list
import com.opencode2api.android.ui.util.obj
import com.opencode2api.android.ui.util.pretty
import com.opencode2api.android.ui.util.raw
import com.opencode2api.android.ui.util.str
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Playground — send one real request through the gateway and see the route it
 * took.
 *
 * This is not a chat client. It exists to answer "why did that call fail" for
 * someone who cannot attach a debugger to a phone: the same request goes
 * through the real routing code, and the response carries the route decision,
 * the selected upstream key and the raw upstream body. The server forces
 * `stream: false` on whatever is sent, because a diagnostic that returns an SSE
 * stream is not a diagnostic.
 */
@Composable
fun PlaygroundScreen() {
    val context = LocalContext.current
    val base = rememberAdminBaseUrl()

    AdminGate(baseUrl = base) { url ->
        val catalog = rememberAdminJson(url, "/api/debug/models")
        val scope = rememberCoroutineScope()

        var protocol by remember { mutableStateOf("chat") }
        var model by remember { mutableStateOf("") }
        var keyMode by remember { mutableStateOf("auto") }
        var selectedKeyId by remember { mutableStateOf("") }
        var requestText by remember { mutableStateOf(templateFor("chat", "")) }
        var running by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf<String?>(null) }
        var result by remember { mutableStateOf<JsonObject?>(null) }

        val keys = catalog.data?.obj("keys")

        val send: () -> Unit = {
            if (!running) {
                running = true
                error = null
                scope.launch {
                    val parsed = runCatching {
                        LenientJson.parseToJsonElement(requestText).jsonObject
                    }.getOrNull()
                    if (parsed == null) {
                        error = "请求 JSON 无法解析，请检查括号与引号。"
                        running = false
                        return@launch
                    }
                    val body = buildJsonObject {
                        put("protocol", protocol)
                        if (keyMode != "auto" && selectedKeyId.isNotBlank()) {
                            put(
                                "key",
                                buildJsonObject {
                                    put("mode", "selected")
                                    put("tier", keyMode)
                                    put("id", selectedKeyId)
                                },
                            )
                        }
                        put("request", parsed)
                    }
                    GatewayRuntime.admin.post(url, "/api/debug/inference", body)
                        .onSuccess {
                            result = it
                            error = null
                        }
                        .onFailure { error = it.message ?: "请求失败" }
                    running = false
                    // The server keeps the last trace itself, so re-reading the
                    // catalogue is how "最近 Playground" on 路由诊断 stays in
                    // step with what was just sent.
                    catalog.refresh()
                }
            }
        }

        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            O2ASectionTitle("三协议请求台")
            O2ACard(modifier = Modifier.padding(horizontal = 12.dp)) {
                O2AText(
                    "服务端强制非流式，并经由真实 Gateway 路由。",
                    style = O2ATextStyle.Caption,
                )
                O2AText("入口协议", style = O2ATextStyle.Subtitle)
                O2AChipRow {
                    PROTOCOLS.forEach { (value, label) ->
                        O2ASelectableChip(text = label, selected = protocol == value) {
                            protocol = value
                            requestText = templateFor(value, model)
                        }
                    }
                }
                O2ATextField(
                    value = model,
                    onValueChange = { input ->
                        // Follow the template while it is still untouched, so
                        // the common case is "type a model, press send"; once
                        // the user edits the body, leave it alone.
                        val previous = templateFor(protocol, model)
                        if (requestText == previous) requestText = templateFor(protocol, input)
                        model = input
                    },
                    label = "模型",
                )
                val models = catalog.data?.list("models").orEmpty()
                if (models.isNotEmpty()) {
                    O2AText("可用模型", style = O2ATextStyle.Subtitle)
                    O2AChipRow {
                        models.take(12).forEach { item ->
                            val id = item.str("model")
                            O2ASelectableChip(text = id, selected = model == id) {
                                model = id
                                requestText = templateFor(protocol, id)
                            }
                        }
                    }
                    if (models.size > 12) {
                        O2AText(
                            "只列了前 12 个，共 ${models.size} 个，其余请直接手填。",
                            style = O2ATextStyle.Caption,
                        )
                    }
                }

                O2AText("上游 Key", style = O2ATextStyle.Subtitle)
                O2AChipRow {
                    O2ASelectableChip(text = "自动路由", selected = keyMode == "auto") {
                        keyMode = "auto"
                    }
                    listOf("zen" to "Zen", "go" to "Go").forEach { (tier, label) ->
                        O2ASelectableChip(text = label, selected = keyMode == tier) {
                            keyMode = tier
                            selectedKeyId = ""
                        }
                    }
                }
                if (keyMode != "auto") {
                    val tierKeys = keys?.let { it.list(keyMode) }.orEmpty()
                    if (tierKeys.isEmpty()) {
                        O2AText("$keyMode 通道还没有配置密钥。", style = O2ATextStyle.Caption)
                    } else {
                        O2AChipRow {
                            tierKeys.forEach { key ->
                                val id = key.str("id")
                                O2ASelectableChip(
                                    text = key.str("display", id.take(8)),
                                    selected = selectedKeyId == id,
                                ) { selectedKeyId = id }
                            }
                        }
                    }
                    O2AText(
                        "选定具体 Key 后只测试该 Key：不会切换到其它 Key、其它 Tier 或匿名通道，" +
                            "也不会改变 Key 冷却或代理健康状态。",
                        style = O2ATextStyle.Caption,
                    )
                }
            }

            O2ASectionTitle("请求 JSON")
            O2ACard(modifier = Modifier.padding(horizontal = 12.dp)) {
                O2ATextField(
                    value = requestText,
                    onValueChange = { requestText = it },
                    label = "请求体",
                    singleLine = false,
                )
                O2AActionRow {
                    O2ATextButton(text = "重置模板") {
                        requestText = templateFor(protocol, model)
                    }
                    O2AButton(
                        text = if (running) "发送中…" else "发送诊断请求",
                        enabled = !running,
                        onClick = send,
                    )
                }
                if (running) O2AProgressBar(progress = null)
            }

            O2ASectionTitle("诊断结果")
            O2ACard(modifier = Modifier.padding(horizontal = 12.dp)) {
                val outcome = result
                when {
                    outcome != null -> {
                        val ok = outcome["ok"]?.jsonPrimitive?.booleanOrNull ?: false
                        O2ARow(title = "结果", summary = if (ok) "成功" else "失败")
                        O2ARow(title = "HTTP 状态", summary = outcome.raw("http_status") ?: "-")
                        O2ARow(
                            title = "耗时",
                            summary = formatMillis(outcome.raw("duration_ms")?.toLongOrNull() ?: 0L),
                        )
                        outcome.raw("request_id")?.let { O2ARow(title = "Request ID", summary = it) }
                        outcome.raw("key_test")?.let { O2ARow(title = "Key 测试", summary = it) }
                        outcome.obj("route")?.let { route ->
                            O2AText("路由", style = O2ATextStyle.Subtitle)
                            O2ARow(title = "原生协议", summary = route.str("native_protocol"))
                            O2ARow(title = "协议来源", summary = route.str("protocol_source"))
                            O2ARow(
                                title = "可用通道",
                                summary = buildList {
                                    if (route["available_zen"]?.jsonPrimitive?.booleanOrNull == true) {
                                        add("Zen")
                                    }
                                    if (route["available_go"]?.jsonPrimitive?.booleanOrNull == true) {
                                        add("Go")
                                    }
                                    add("匿名")
                                }.joinToString(" · "),
                            )
                            route.raw("key_id")?.let { O2ARow(title = "Key 尾码", summary = it) }
                            route.raw("route_error")?.let {
                                O2AText(it, style = O2ATextStyle.Caption)
                            }
                        }
                        outcome["response"]?.let { response ->
                            O2AText("上游原始响应", style = O2ATextStyle.Subtitle)
                            O2AMonoBlock(response.pretty(), maxLines = 40)
                            O2ARow(
                                title = "复制响应",
                                onClick = { copyToClipboard(context, "响应", response.pretty()) },
                            )
                        }
                    }

                    catalog.data?.obj("last_inference") != null -> {
                        O2AText(
                            "本次进程内最后一次追踪，来自之前的 Playground 请求。",
                            style = O2ATextStyle.Caption,
                        )
                        O2AMonoBlock(catalog.data?.obj("last_inference")?.pretty().orEmpty(), maxLines = 30)
                    }

                    else -> {
                        if (catalog.loading) {
                            O2AProgressBar(progress = null)
                        } else {
                            O2AText(
                                error ?: "尚未发送请求。",
                                style = O2ATextStyle.Caption,
                            )
                        }
                    }
                }
                error?.takeIf { outcome != null }?.let {
                    O2AText(it, style = O2ATextStyle.Caption)
                }
            }
        }
    }
}

private val PROTOCOLS = listOf(
    "chat" to "Chat Completions",
    "responses" to "Responses",
    "anthropic" to "Anthropic Messages",
)

/** The same three bodies the WebUI's template button fills in. */
private fun templateFor(protocol: String, model: String): String {
    val name = model.ifBlank { "YOUR_MODEL" }
    return when (protocol) {
        "responses" -> """{"model":"$name","input":"Hello"}"""
        "anthropic" ->
            """{"model":"$name","max_tokens":512,"messages":[{"role":"user","content":"Hello"}]}"""

        else ->
            """{"model":"$name","messages":[{"role":"user","content":"Hello"}]}"""
    }
}

private val LenientJson = Json { ignoreUnknownKeys = true; isLenient = true; explicitNulls = false }

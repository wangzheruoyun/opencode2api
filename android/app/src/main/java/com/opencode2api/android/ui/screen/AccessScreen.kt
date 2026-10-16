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
import com.opencode2api.android.core.GatewayRuntime
import com.opencode2api.android.ui.component.O2ACard
import com.opencode2api.android.ui.component.O2AChipRow
import com.opencode2api.android.ui.component.O2AMonoBlock
import com.opencode2api.android.ui.component.O2ARow
import com.opencode2api.android.ui.component.O2ASectionTitle
import com.opencode2api.android.ui.component.O2ASelectableChip
import com.opencode2api.android.ui.component.O2AText
import com.opencode2api.android.ui.component.O2ATextField
import com.opencode2api.android.ui.component.O2ATextStyle
import com.opencode2api.android.ui.util.copyToClipboard
import com.opencode2api.android.ui.util.str
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections

/**
 * 接入手册 — what to put in a client to talk to this gateway.
 *
 * The base address is derived from the config file rather than from where the
 * app happens to be running: the WebUI can say "the host the browser used",
 * but a phone has no such thing, so a config that binds a routable address also
 * offers the device's LAN address as a second choice. Everything else is the
 * same three protocol examples and two SDK snippets the WebUI shows, with the
 * model name filled in from the field at the top.
 */
@Composable
fun AccessScreen() {
    val context = LocalContext.current
    val store = GatewayRuntime.configStore
    val doc = remember { store.loadOrCreate() }

    val port = store.apiPort(doc)
    val listen = doc.str("listen")
    val exposesLan = !listen.startsWith("127.") && !listen.startsWith("[::1]") && !listen.startsWith("localhost")

    val loopback = "http://127.0.0.1:$port"
    val lan = remember { if (exposesLan) localIpv4()?.let { "http://$it:$port" } else null }
    var base by remember { mutableStateOf(loopback) }
    var model by remember { mutableStateOf("YOUR_MODEL") }

    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        O2ASectionTitle("基础地址")
        O2ACard {
            O2AText("推导的 API Base", style = O2ATextStyle.Subtitle)
            O2AMonoBlock(base)
            if (lan != null) {
                O2AText(
                    "配置监听在 $listen，同网络的其它设备可以用下面这个地址。",
                    style = O2ATextStyle.Caption,
                )
                O2AChipRow {
                    O2ASelectableChip("本机 $loopback", base == loopback) { base = loopback }
                    O2ASelectableChip("局域网 $lan", base == lan) { base = lan }
                }
            } else {
                O2AText(
                    "当前监听在 $listen，只有本机能访问。要让其它设备接入，" +
                        "把「配置中心」的 API 监听地址改成 0.0.0.0:$port。",
                    style = O2ATextStyle.Caption,
                )
            }
            O2ATextField(
                value = model,
                onValueChange = { model = it },
                label = "示例模型",
            )
            O2AText(
                "模型名以「模型与额度」里列出的为准；sota 与 sweet 是两个别名，会按轮转顺序挑选真实模型。",
                style = O2ATextStyle.Caption,
            )
            O2ARow(
                title = "复制 Base URL",
                onClick = { copyToClipboard(context, "API Base", base) },
            )
        }

        O2ASectionTitle("三种协议入口")
        CodeCard(
            label = "Chat Completions",
            code = """
                curl $base/v1/chat/completions \
                  -H "Authorization: Bearer YOUR_API_KEY" \
                  -H "Content-Type: application/json" \
                  -d '{"model":"$model","messages":[{"role":"user","content":"Hello"}]}'
            """.trimIndent(),
        )
        CodeCard(
            label = "Responses",
            code = """
                curl $base/v1/responses \
                  -H "Authorization: Bearer YOUR_API_KEY" \
                  -H "Content-Type: application/json" \
                  -d '{"model":"$model","input":"Hello"}'
            """.trimIndent(),
        )
        CodeCard(
            label = "Anthropic Messages",
            code = """
                curl $base/v1/messages \
                  -H "x-api-key: YOUR_API_KEY" \
                  -H "anthropic-version: 2023-06-01" \
                  -H "Content-Type: application/json" \
                  -d '{"model":"$model","max_tokens":512,"messages":[{"role":"user","content":"Hello"}]}'
            """.trimIndent(),
        )

        O2ASectionTitle("Python 与 JavaScript")
        CodeCard(
            label = "Python · OpenAI SDK",
            code = """
                from openai import OpenAI

                client = OpenAI(base_url="$base/v1", api_key="YOUR_API_KEY")
                response = client.chat.completions.create(
                    model="$model",
                    messages=[{"role": "user", "content": "Hello"}],
                )
                print(response.choices[0].message.content)
            """.trimIndent(),
        )
        CodeCard(
            label = "JavaScript · OpenAI SDK",
            code = """
                import OpenAI from "openai";

                const client = new OpenAI({
                  baseURL: "$base/v1",
                  apiKey: "YOUR_API_KEY",
                });
                const response = await client.responses.create({
                  model: "$model",
                  input: "Hello",
                });
                console.log(response.output_text);
            """.trimIndent(),
        )

        O2ASectionTitle("接入注意")
        O2ACard {
            listOf(
                "OpenAI 兼容客户端使用 Authorization: Bearer YOUR_API_KEY。",
                "Anthropic 客户端可使用 x-api-key，路径为 /v1/messages。",
                "流式请求会保持原协议事件；跨协议时由 Gateway 转换。",
                "YOUR_API_KEY 换成「本机访问密钥」里的任意一个，别把上游 Zen / Go 密钥发给客户端。",
                "监听在 127.0.0.1 时只有本机能连；Android 上其它 App 也走这个地址。",
            ).forEach { note ->
                O2AText("· $note", style = O2ATextStyle.Caption)
            }
        }
    }
}

@Composable
private fun CodeCard(label: String, code: String) {
    val context = LocalContext.current
    O2ACard {
        O2AText(label, style = O2ATextStyle.Subtitle)
        O2AMonoBlock(code)
        O2ARow(title = "复制", onClick = { copyToClipboard(context, label, code) })
    }
}

/**
 * The device's private IPv4 address, for the case where the gateway is bound to
 * something routable. Returns null on a device with no Wi-Fi or Ethernet, which
 * is the normal state of a phone in airplane mode.
 */
private fun localIpv4(): String? = runCatching {
    Collections.list(NetworkInterface.getNetworkInterfaces())
        .filter { it.isUp && !it.isLoopback }
        .flatMap { Collections.list(it.inetAddresses) }
        .filterIsInstance<Inet4Address>()
        .firstOrNull { it.isSiteLocalAddress }
        ?.hostAddress
}.getOrNull()

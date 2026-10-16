package com.opencode2api.android.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.opencode2api.android.ui.Route
import com.opencode2api.android.ui.Tab
import com.opencode2api.android.ui.component.O2AActionRow
import com.opencode2api.android.ui.component.O2ACard
import com.opencode2api.android.ui.component.O2AColumn
import com.opencode2api.android.ui.component.O2AProgressBar
import com.opencode2api.android.ui.component.O2ARow
import com.opencode2api.android.ui.component.O2ATag
import com.opencode2api.android.ui.component.O2AText
import com.opencode2api.android.ui.component.O2ATextButton
import com.opencode2api.android.ui.component.O2ATextStyle
import com.opencode2api.android.ui.util.bool
import com.opencode2api.android.ui.util.int
import com.opencode2api.android.ui.util.list
import com.opencode2api.android.ui.util.obj
import com.opencode2api.android.ui.util.raw
import com.opencode2api.android.ui.util.str

/**
 * 首次运行 — the six checks the WebUI calls 六步上线检查.
 *
 * The list is the same six upstream draws, in the same order, with the same
 * predicates, because the point of the screen is to answer "is this thing
 * actually working" for someone who has just installed it. They are computed
 * from the live config and the live resource snapshot rather than from anything
 * this app remembers, so a check goes green the moment the gateway says so and
 * not a moment earlier.
 */
@Composable
fun GuideScreen(onNavigate: (Route) -> Unit) {
    val base = rememberAdminBaseUrl()

    AdminGate(baseUrl = base) { url ->
        val config = rememberAdminJson(url, "/api/config")
        val monitor = rememberAdminJson(url, "/api/monitor")

        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            O2AColumn {
                O2ACard {
                    O2AText("状态来自当前配置与实时资源，不写入任何本地存储。", style = O2ATextStyle.Caption)
                    if (config.loading || monitor.loading) {
                        O2AProgressBar(progress = null)
                    } else {
                        (config.error ?: monitor.error)?.let {
                            O2AText(it, style = O2ATextStyle.Caption)
                        }
                    }
                    O2AActionRow {
                        O2ATextButton(text = "刷新", onClick = {
                            config.refresh()
                            monitor.refresh()
                        })
                    }
                }

                val checks = buildChecks(config.data, monitor.data)
                checks.forEach { check ->
                    O2ACard {
                        O2AText(check.title, style = O2ATextStyle.Subtitle)
                        O2AText(check.description, style = O2ATextStyle.Caption)
                        O2ATag(
                            text = if (check.done) "✓ 已就绪" else "○ 待处理",
                            highlight = check.done,
                        )
                    }
                }

                O2ACard {
                    O2AText("下一步", style = O2ATextStyle.Subtitle)
                    O2AText(
                        "六步都绿了就可以接入了：把某台设备/某个客户端的 base_url 指到本机网关，" +
                            "密钥填「本机访问密钥」里的任意一个。",
                        style = O2ATextStyle.Caption,
                    )
                    O2ARow(
                        title = "接入手册",
                        summary = "三种协议的 curl 与 SDK 示例",
                        onClick = { onNavigate(Route.Access) },
                    )
                    O2ARow(
                        title = "配置中心",
                        summary = "补上缺失的密钥或通道",
                        onClick = { onNavigate(Route.TabRoute(Tab.CONFIG)) },
                    )
                }
            }
        }
    }
}

private data class GuideCheck(val title: String, val description: String, val done: Boolean)

/**
 * The six checks. Every predicate is written to degrade to "not done" when the
 * snapshot it reads has not arrived yet, so a slow first refresh shows an amber
 * list rather than an empty one.
 */
private fun buildChecks(
    config: kotlinx.serialization.json.JsonObject?,
    monitor: kotlinx.serialization.json.JsonObject?,
): List<GuideCheck> {
    val catalog = monitor?.obj("resources.models")
    val proxies = monitor?.list("resources.proxies").orEmpty()

    val description = when {
        catalog == null -> "上游目录尚未就绪，等待首次刷新。"
        catalog.bool("stale") ->
            "目录来自 ${catalog.str("cache_source", "磁盘")} 快取，等待背景刷新。"

        else -> "上游目录已完成首次刷新且有可暴露模型。"
    }

    return listOf(
        GuideCheck("登录管理端", "当前 Session 已通过服务端验证。", done = true),
        GuideCheck(
            title = "配置本地 Key",
            description = "客户端使用 Server Key 访问 Gateway。",
            done = config !== null && config.list("server_keys").isNotEmpty(),
        ),
        GuideCheck(
            title = "选择上游通道",
            description = "至少有 Zen / Go Key，或启用匿名通道。",
            done = config != null && (
                config.list("zen_keys").size + config.list("go_keys").size > 0 ||
                    config.bool("anonymous")
                ),
        ),
        GuideCheck(
            title = "检查 Proxy",
            description = "至少一个代理节点健康。",
            done = proxies.any { it.bool("healthy") },
        ),
        GuideCheck(
            title = "等待模型目录",
            description = description,
            done = catalog != null && catalog.int("exposed") > 0 && catalog.raw("updated_at") != null,
        ),
        GuideCheck("复制接入示例", "进入接入手册，替换模型与 YOUR_API_KEY。", done = true),
    )
}

package com.opencode2api.android.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.opencode2api.android.core.GatewayRuntime
import com.opencode2api.android.core.RunMode
import com.opencode2api.android.ui.Route
import com.opencode2api.android.ui.UiMode
import com.opencode2api.android.ui.component.O2ACard
import com.opencode2api.android.ui.component.O2AChipRow
import com.opencode2api.android.ui.component.O2ASectionTitle
import com.opencode2api.android.ui.component.O2ASelectableChip
import com.opencode2api.android.ui.component.O2ARow
import com.opencode2api.android.ui.component.O2ASwitchRow
import com.opencode2api.android.ui.theme.ColorMode

/**
 * 更多 — the overflow tab.
 *
 * Upstream's WebUI carries the same split: five things you touch constantly on
 * the main pages, and a long tail of one-off tools behind "更多". The skin and
 * theme pickers live here too, since this is the app's only settings surface.
 */
@Composable
fun MoreScreen(onNavigate: (Route) -> Unit) {
    val settings by GatewayRuntime.settings.state.collectAsStateWithLifecycle()
    val mode = ColorMode.fromValue(settings.themeMode)

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
    ) {
        O2ASectionTitle("功能")
        O2ACard {
            O2ARow(title = "首次运行", summary = "从零开始的三步引导", onClick = { onNavigate(Route.Guide) })
            O2ARow(title = "接入手册", summary = "OpenAI / Anthropic 客户端配置", onClick = { onNavigate(Route.Access) })
            O2ARow(title = "Token 用量", summary = "请求与 Token 统计", onClick = { onNavigate(Route.Usage) })
            O2ARow(title = "Playground", summary = "在应用内试一条请求", onClick = { onNavigate(Route.Playground) })
            O2ARow(title = "路由诊断", summary = "每次请求走了哪条上游", onClick = { onNavigate(Route.Diagnostics) })
            O2ARow(title = "模型与额度", summary = "查看可用模型", onClick = { onNavigate(Route.TabRoute(com.opencode2api.android.ui.Tab.MODELS)) })
            O2ARow(title = "模型轮转", summary = "轮转策略与别名", onClick = { onNavigate(Route.Rotation) })
            O2ARow(title = "免费模型可用性", summary = "当前能答话的免费模型", onClick = { onNavigate(Route.Availability) })
        }

        O2ASectionTitle("账号安全")
        O2ACard {
            O2ARow(title = "管理端登录", summary = "修改管理员密码", onClick = { onNavigate(Route.Account) })
            O2ARow(title = "API Key 管理", summary = "对外发放的访问密钥", onClick = { onNavigate(Route.ApiKeys) })
        }

        O2ASectionTitle("外观")
        O2ACard {
            O2ARow(title = "界面风格", summary = if (settings.uiMode == UiMode.Material.value) "Material" else "MIUIX")
            O2AChipRow {
                O2ASelectableChip("MIUIX", settings.uiMode != UiMode.Material.value) {
                    GatewayRuntime.settings.setUiMode(UiMode.Miuix.value)
                }
                O2ASelectableChip("Material", settings.uiMode == UiMode.Material.value) {
                    GatewayRuntime.settings.setUiMode(UiMode.Material.value)
                }
            }
            O2ARow(
                title = "主题模式",
                summary = mode.label(),
                modifier = Modifier.padding(top = 8.dp),
            )
            O2AChipRow {
                ColorMode.entries.forEach { candidate ->
                    O2ASelectableChip(candidate.label(), candidate == mode) {
                        GatewayRuntime.settings.setThemeMode(candidate.value)
                    }
                }
            }
            // Only the miuix skin has a sense of "the wallpaper chooses"; on
            // Material the palette always follows the system when it can.
            if (settings.uiMode != UiMode.Material.value) {
                O2ASwitchRow(
                    title = "使用系统取色",
                    summary = "从壁纸派生配色，而非固定主题色",
                    checked = settings.monet,
                    onCheckedChange = { GatewayRuntime.settings.setMonet(it) },
                )
            }
        }

        O2ASectionTitle("行为")
        O2ACard {
            O2ASwitchRow(
                title = "开机自启",
                summary = "设备启动后自动拉起网关",
                checked = settings.startOnBoot,
                onCheckedChange = { GatewayRuntime.settings.setStartOnBoot(it) },
            )
            O2ASwitchRow(
                title = "保持后台运行",
                summary = "以常驻前台服务运行，降低被系统回收的概率",
                checked = settings.keepAlive,
                onCheckedChange = { GatewayRuntime.settings.setKeepAlive(it) },
            )
            O2ARow(title = "运行模式", summary = settings.runMode.label())
            O2AChipRow {
                RunMode.entries.forEach { candidate ->
                    O2ASelectableChip(candidate.label(), candidate == settings.runMode) {
                        GatewayRuntime.settings.setRunMode(candidate)
                    }
                }
            }
        }

        O2ASectionTitle("关于")
        O2ACard {
            O2ARow(title = "关于 OpenCode2API", onClick = { onNavigate(Route.About) })
        }
    }
}

private fun ColorMode.label(): String = when (this) {
    ColorMode.SYSTEM -> "跟随系统"
    ColorMode.LIGHT -> "浅色"
    ColorMode.DARK -> "深色"
    ColorMode.MONET_SYSTEM -> "系统取色 · 跟随系统"
    ColorMode.MONET_LIGHT -> "系统取色 · 浅色"
    ColorMode.MONET_DARK -> "系统取色 · 深色"
    ColorMode.DARK_AMOLED -> "深色 · AMOLED 纯黑"
}

private fun RunMode.label(): String = when (this) {
    RunMode.AUTO -> "自动"
    RunMode.ROOT -> "Root"
    RunMode.USER -> "普通应用"
}

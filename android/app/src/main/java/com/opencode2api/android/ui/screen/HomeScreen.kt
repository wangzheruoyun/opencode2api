package com.opencode2api.android.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.opencode2api.android.R
import com.opencode2api.android.core.GatewayRuntime
import com.opencode2api.android.core.GatewayState
import com.opencode2api.android.core.RunMode
import com.opencode2api.android.service.GatewayService
import com.opencode2api.android.ui.Route
import com.opencode2api.android.ui.component.O2AActionRow
import com.opencode2api.android.ui.component.O2AButton
import com.opencode2api.android.ui.component.O2ACard
import com.opencode2api.android.ui.component.O2AChipRow
import com.opencode2api.android.ui.component.O2AColumn
import com.opencode2api.android.ui.component.O2AEmptyState
import com.opencode2api.android.ui.component.O2AMonoBlock
import com.opencode2api.android.ui.component.O2ARow
import com.opencode2api.android.ui.component.O2ASectionTitle
import com.opencode2api.android.ui.component.O2ASelectableChip
import com.opencode2api.android.ui.component.O2ATag
import com.opencode2api.android.ui.component.O2AText
import com.opencode2api.android.ui.component.O2ATextButton
import com.opencode2api.android.ui.component.O2ATextStyle
import com.opencode2api.android.ui.util.copyToClipboard
import kotlinx.coroutines.delay

/**
 * 运行桌面 — the home tab.
 *
 * Everything here is local state: whether the process is up, on which port, in
 * which mode, and what it printed on the way up. The token counters live on the
 * usage screen, which talks to the gateway's own admin API.
 */
@Composable
fun HomeScreen(onNavigate: (Route) -> Unit) {
    val context = LocalContext.current
    val state by GatewayRuntime.runner.state.collectAsStateWithLifecycle()
    val resolved by GatewayRuntime.runner.resolvedMode.collectAsStateWithLifecycle()
    val settings by GatewayRuntime.settings.state.collectAsStateWithLifecycle()
    val revision by GatewayRuntime.logs.revision.collectAsStateWithLifecycle()

    val recentLogs = remember(revision) { GatewayRuntime.logs.snapshot().takeLast(8) }
    val running = state as? GatewayState.Running

    val uptimeMs by produceState(0L, running?.since) {
        val since = running?.since
        if (since == null) {
            value = 0L
        } else {
            while (true) {
                value = System.currentTimeMillis() - since
                delay(1_000)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        StatusCard(
            state = state,
            isRoot = resolved?.isRoot == true,
            note = resolved?.note,
            uptimeMs = uptimeMs,
            apiPort = running?.apiPort,
        )

        ControlCard(
            state = state,
            runMode = settings.runMode,
            onStart = { GatewayService.start(context) },
            onStop = { GatewayService.stop(context) },
            onRestart = { GatewayService.restart(context) },
            onRunModeChange = { GatewayRuntime.settings.setRunMode(it) },
        )

        O2ASectionTitle("接入地址")
        O2ACard {
            val apiPort = running?.apiPort
            val baseUrl = apiPort?.let { "http://127.0.0.1:$it/v1" }
            O2ARow(
                title = "OpenAI 兼容",
                summary = baseUrl ?: "服务未运行",
                trailing = if (baseUrl != null) {
                    {
                        O2ATextButton("复制", onClick = {
                            copyToClipboard(context, "基础地址", baseUrl)
                        })
                    }
                } else {
                    null
                },
            )
            if (baseUrl != null) {
                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    O2AMonoBlock(text = baseUrl)
                }
            }
            running?.webUiPort?.let { port ->
                O2ARow(
                    title = "管理界面",
                    summary = "http://127.0.0.1:$port",
                    trailing = {
                        O2ATextButton("复制", onClick = {
                            copyToClipboard(context, "管理界面地址", "http://127.0.0.1:$port")
                        })
                    },
                )
            }
            O2ARow(
                title = "接入手册",
                summary = "客户端如何连接到本机网关",
                onClick = { onNavigate(Route.Access) },
            )
        }

        O2ASectionTitle("快捷入口")
        O2ACard {
            O2ARow(title = "首次运行", summary = "从零开始的三步引导", onClick = { onNavigate(Route.Guide) })
            O2ARow(title = "Token 用量", summary = "请求与 Token 统计", onClick = { onNavigate(Route.Usage) })
            O2ARow(title = "Playground", summary = "在应用内直接试一条请求", onClick = { onNavigate(Route.Playground) })
            O2ARow(title = "路由诊断", summary = "查看每次请求走了哪条上游", onClick = { onNavigate(Route.Diagnostics) })
        }

        O2ASectionTitle("最近的网关输出")
        O2ACard {
            if (recentLogs.isEmpty()) {
                O2AEmptyState("暂无日志")
            } else {
                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    O2AMonoBlock(
                        text = recentLogs.joinToString("\n") { it.text },
                        maxLines = 12,
                    )
                }
            }
            O2ARow(
                title = "查看全部日志",
                onClick = {
                    // The logs tab is a peer of this one, so the shell cannot be
                    // asked to push it — selecting the tab is the honest move.
                    onNavigate(Route.TabRoute(com.opencode2api.android.ui.Tab.LOGS))
                },
            )
        }
    }
}

@Composable
private fun StatusCard(
    state: GatewayState,
    isRoot: Boolean,
    note: String?,
    uptimeMs: Long,
    apiPort: Int?,
) {
    O2ACard {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                O2AText(statusLabel(state), style = O2ATextStyle.Title)
                O2AText(
                    text = statusDetail(state, apiPort),
                    style = O2ATextStyle.Caption,
                )
            }
            if (state is GatewayState.Running) {
                O2ATag(if (isRoot) "Root" else "普通", highlight = true)
            }
        }

        if (state is GatewayState.Running) {
            O2ARow(
                title = stringResource(R.string.home_uptime),
                summary = formatDuration(uptimeMs),
            )
        }
        if (state is GatewayState.Failed) {
            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                O2AMonoBlock(text = state.reason, maxLines = 6)
            }
        }
        if (note != null) {
            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                O2AText(note, style = O2ATextStyle.Caption)
            }
        }
        if (state is GatewayState.Starting) {
            Box(modifier = Modifier.padding(16.dp)) {
                com.opencode2api.android.ui.component.O2AProgressBar(null, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun ControlCard(
    state: GatewayState,
    runMode: RunMode,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onRestart: () -> Unit,
    onRunModeChange: (RunMode) -> Unit,
) {
    O2ACard {
        O2AColumn(spacing = 8) {
            O2AActionRow {
                when (state) {
                    is GatewayState.Running, is GatewayState.Starting -> {
                        O2AButton("停止", onClick = onStop, modifier = Modifier.weight(1f), primary = false)
                        O2AButton("重启", onClick = onRestart, modifier = Modifier.weight(1f), primary = false)
                    }

                    else -> {
                        O2AButton("启动", onClick = onStart, modifier = Modifier.weight(1f))
                        O2AButton("重启", onClick = onRestart, modifier = Modifier.weight(1f), primary = false)
                    }
                }
            }
            O2AText("运行模式", style = O2ATextStyle.Caption)
            O2AChipRow {
                O2ASelectableChip("自动", runMode == RunMode.AUTO) { onRunModeChange(RunMode.AUTO) }
                O2ASelectableChip("Root", runMode == RunMode.ROOT) { onRunModeChange(RunMode.ROOT) }
                O2ASelectableChip("普通应用", runMode == RunMode.USER) { onRunModeChange(RunMode.USER) }
            }
        }
    }
}

private fun statusLabel(state: GatewayState): String = when (state) {
    is GatewayState.Running -> "服务运行中"
    is GatewayState.Starting -> "正在启动…"
    is GatewayState.Failed -> "启动失败"
    GatewayState.Stopped -> "服务已停止"
}

private fun statusDetail(state: GatewayState, apiPort: Int?): String = when (state) {
    is GatewayState.Running -> "监听 127.0.0.1:${apiPort ?: state.apiPort}"
    is GatewayState.Starting -> "等待端口就绪"
    is GatewayState.Failed -> "请查看下方日志"
    GatewayState.Stopped -> "点击下方按钮启动"
}

/** Compact `1h 02m` / `3m 12s` / `8s` rendering of an uptime. */
private fun formatDuration(millis: Long): String {
    val seconds = millis / 1000
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60
    return when {
        hours > 0 -> "%dh %02dm".format(hours, minutes)
        minutes > 0 -> "%dm %02ds".format(minutes, secs)
        else -> "${secs}s"
    }
}

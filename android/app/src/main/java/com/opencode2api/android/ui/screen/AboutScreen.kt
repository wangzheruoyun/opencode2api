package com.opencode2api.android.ui.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.opencode2api.android.BuildConfig
import com.opencode2api.android.core.GatewayBinary
import com.opencode2api.android.core.GatewayRuntime
import com.opencode2api.android.ui.component.O2ACard
import com.opencode2api.android.ui.component.O2AMonoBlock
import com.opencode2api.android.ui.component.O2ARow
import com.opencode2api.android.ui.component.O2ASectionTitle
import com.opencode2api.android.ui.util.copyToClipboard

private const val UPSTREAM_REPO = "https://github.com/jasonxu114514/opencode2api"
private const val SUKISU_REPO = "https://github.com/SukiSU-Ultra/SukiSU-Ultra"

/**
 * 关于 — version, provenance, and the two addresses that matter when filing a
 * bug: which build this is, and where the gateway binary actually lives.
 */
@Composable
fun AboutScreen() {
    val context = LocalContext.current
    val binary = GatewayBinary.path(context)

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        O2ASectionTitle("版本")
        O2ACard {
            O2ARow(title = "应用版本", summary = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            O2ARow(title = "应用 ID", summary = BuildConfig.APPLICATION_ID)
        }

        O2ASectionTitle("网关")
        O2ACard {
            O2ARow(
                title = "网关状态",
                summary = if (GatewayBinary.exists(context)) "已随应用安装" else "未找到二进制",
            )
            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                O2AMonoBlock(text = binary.absolutePath, maxLines = 3)
            }
            O2ARow(
                title = "复制路径",
                onClick = { copyToClipboard(context, "路径", binary.absolutePath) },
            )
            O2ARow(
                title = "网关配置",
                summary = GatewayRuntime.configStore.file.absolutePath,
                onClick = {
                    copyToClipboard(context, "路径", GatewayRuntime.configStore.file.absolutePath)
                },
            )
        }

        O2ASectionTitle("开源")
        O2ACard {
            O2ARow(
                title = "opencode2api",
                summary = UPSTREAM_REPO,
                onClick = { copyToClipboard(context, "仓库地址", UPSTREAM_REPO) },
            )
            O2ARow(
                title = "SukiSU-Ultra",
                summary = SUKISU_REPO,
                onClick = { copyToClipboard(context, "仓库地址", SUKISU_REPO) },
            )
            O2ARow(title = "许可证", summary = "GPL-3.0")
        }
    }
}

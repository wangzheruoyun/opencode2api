package com.opencode2api.android.ui.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.opencode2api.android.core.GatewayRuntime
import com.opencode2api.android.core.LogLine
import com.opencode2api.android.ui.component.O2AActionRow
import com.opencode2api.android.ui.component.O2ACard
import com.opencode2api.android.ui.component.O2AColumn
import com.opencode2api.android.ui.component.O2AEmptyState
import com.opencode2api.android.ui.component.O2AMonoBlock
import com.opencode2api.android.ui.component.O2ASectionTitle
import com.opencode2api.android.ui.component.O2AText
import com.opencode2api.android.ui.component.O2ATextButton
import com.opencode2api.android.ui.component.O2ATextField
import com.opencode2api.android.ui.component.O2ATextStyle

/**
 * 事件日志 — the gateway's own stdout/stderr, as captured by [GatewayRuntime.logs].
 *
 * This is not the gateway's `/api/logs`: that stream only exists once the HTTP
 * server is up, which is exactly the case where you do not need it. What people
 * actually come here for is a start-up that failed, so the buffer behind this
 * screen is filled from the moment the process is spawned.
 */
@Composable
fun LogsScreen() {
    val revision by GatewayRuntime.logs.revision.collectAsStateWithLifecycle()

    var paused by remember { mutableStateOf(false) }
    var pausedSnapshot by remember { mutableStateOf<List<LogLine>>(emptyList()) }
    var query by remember { mutableStateOf("") }

    val live = remember(revision) { GatewayRuntime.logs.snapshot() }
    LaunchedEffect(paused) { if (paused) pausedSnapshot = GatewayRuntime.logs.snapshot() }

    val filtered = remember(if (paused) pausedSnapshot else live, query) {
        val source = if (paused) pausedSnapshot else live
        if (query.isBlank()) source else source.filter { it.text.contains(query, ignoreCase = true) }
    }

    val listState = rememberLazyListState()
    // Follow the tail, but only while live: a paused view that jumped as new
    // lines arrived would defeat the point of pausing.
    LaunchedEffect(filtered.size, paused) {
        if (!paused && filtered.isNotEmpty()) {
            listState.scrollToItem(filtered.lastIndex)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        O2AColumn(spacing = 8) {
            O2AActionRow {
                O2ATextButton(
                    text = if (paused) "继续" else "暂停",
                    onClick = { paused = !paused },
                )
                O2ATextButton(
                    text = "清空",
                    onClick = {
                        GatewayRuntime.logs.clear()
                        pausedSnapshot = emptyList()
                    },
                )
                O2AText(
                    text = "${filtered.size} 行",
                    style = O2ATextStyle.Caption,
                    modifier = Modifier.weight(1f),
                )
            }
            O2ATextField(
                value = query,
                onValueChange = { query = it },
                label = "筛选关键字",
            )
        }

        O2ASectionTitle("输出")

        if (filtered.isEmpty()) {
            O2AEmptyState(if (query.isBlank()) "暂无日志" else "没有匹配的日志行")
        } else {
            O2ACard {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 240.dp),
                ) {
                    items(filtered) { line ->
                        Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp)) {
                            O2AMonoBlock(text = line.text)
                        }
                    }
                }
            }
        }
    }
}

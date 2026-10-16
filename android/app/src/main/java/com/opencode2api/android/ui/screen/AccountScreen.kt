package com.opencode2api.android.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import com.opencode2api.android.core.GatewayRuntime
import com.opencode2api.android.ui.component.O2AActionRow
import com.opencode2api.android.ui.component.O2AButton
import com.opencode2api.android.ui.component.O2ACard
import com.opencode2api.android.ui.component.O2AColumn
import com.opencode2api.android.ui.component.O2ASectionTitle
import com.opencode2api.android.ui.component.O2AText
import com.opencode2api.android.ui.component.O2ATextButton
import com.opencode2api.android.ui.component.O2ATextField
import com.opencode2api.android.ui.component.O2ATextStyle
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * 账号安全 — the admin username and password, which are also the gateway's WebUI
 * credentials.
 *
 * Changing either one revokes every session the server is holding, including
 * this app's, so the screen deliberately ends on a "sign in again" button rather
 * than silently invalidating itself: the user should see that the change landed
 * before the login panel replaces it.
 */
@Composable
fun AccountScreen() {
    val base = rememberAdminBaseUrl()

    AdminGate(baseUrl = base) { url ->
        val admin = GatewayRuntime.admin
        val scope = rememberCoroutineScope()

        var current by rememberSaveable { mutableStateOf("") }
        var username by rememberSaveable { mutableStateOf(admin.username ?: "admin") }
        var password by rememberSaveable { mutableStateOf("") }
        var confirm by rememberSaveable { mutableStateOf("") }
        var busy by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf<String?>(null) }
        var updated by remember { mutableStateOf(false) }

        // The server enforces these two; catching them here turns a round trip
        // and a generic 400 into an inline explanation.
        val mismatch = password.isNotEmpty() && password != confirm
        val tooShort = password.isNotEmpty() && password.length < 10
        val canSubmit = current.isNotBlank() && !busy && !mismatch && !tooShort

        val submit: () -> Unit = {
            if (!busy) {
                busy = true
                error = null
                scope.launch {
                    val body = buildJsonObject {
                        put("current_password", current)
                        put("username", username.trim())
                        put("new_password", password)
                    }
                    admin.put(url, "/api/account", body)
                        .onSuccess {
                            updated = true
                            current = ""
                            password = ""
                            confirm = ""
                        }
                        .onFailure { error = it.message ?: "修改失败" }
                    busy = false
                }
            }
        }

        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            O2ASectionTitle("管理端账号")
            O2AColumn {
                O2ACard {
                    O2AText(
                        "原版 WebUI 与 App 共用这套账号密码。保存后服务端会注销全部会话，" +
                            "包括当前这一个，需要用新密码重新登录。",
                        style = O2ATextStyle.Caption,
                    )
                    O2ATextField(
                        value = username,
                        onValueChange = { username = it },
                        label = "用户名",
                        enabled = !busy,
                    )
                    O2ATextField(
                        value = current,
                        onValueChange = { current = it },
                        label = "当前密码",
                        enabled = !busy,
                        keyboardType = KeyboardType.Password,
                    )
                    O2ATextField(
                        value = password,
                        onValueChange = { password = it },
                        label = "新密码（留空表示不修改）",
                        enabled = !busy,
                        keyboardType = KeyboardType.Password,
                    )
                    O2ATextField(
                        value = confirm,
                        onValueChange = { confirm = it },
                        label = "确认新密码",
                        enabled = !busy,
                        keyboardType = KeyboardType.Password,
                    )
                    O2AText(
                        "新密码至少 10 位，可以含空格。",
                        style = O2ATextStyle.Caption,
                    )
                    if (tooShort) O2AText("新密码不足 10 位。", style = O2ATextStyle.Caption)
                    if (mismatch) O2AText("两次输入的新密码不一致。", style = O2ATextStyle.Caption)
                    error?.let { O2AText(it, style = O2ATextStyle.Caption) }
                    O2AActionRow {
                        O2AButton(
                            text = if (busy) "保存中…" else "保存",
                            onClick = submit,
                            enabled = canSubmit,
                        )
                    }
                }

                if (updated) {
                    O2ACard {
                        O2AText("账号已更新", style = O2ATextStyle.Subtitle)
                        O2AText(
                            "服务端已注销全部会话。点击下面的按钮清掉本机保存的登录态。",
                            style = O2ATextStyle.Caption,
                        )
                        O2AActionRow {
                            O2ATextButton(text = "去重新登录") {
                                scope.launch {
                                    // The session is already gone server-side, so a
                                    // failure here is expected and not worth showing:
                                    // what matters is dropping the local cookie.
                                    admin.logout(url)
                                    invalidateAdminSession()
                                }
                            }
                        }
                    }
                }

                O2ACard {
                    O2AText(
                        "当前登录：" + (admin.username ?: "未知"),
                        style = O2ATextStyle.Caption,
                    )
                }
            }
        }
    }
}

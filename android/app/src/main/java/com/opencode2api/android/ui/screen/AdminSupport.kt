package com.opencode2api.android.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.opencode2api.android.core.GatewayRuntime
import com.opencode2api.android.core.GatewayState
import com.opencode2api.android.ui.component.O2AButton
import com.opencode2api.android.ui.component.O2ACard
import com.opencode2api.android.ui.component.O2AColumn
import com.opencode2api.android.ui.component.O2AEmptyState
import com.opencode2api.android.ui.component.O2AProgressBar
import com.opencode2api.android.ui.component.O2AText
import com.opencode2api.android.ui.component.O2ATextField
import com.opencode2api.android.ui.component.O2ATextStyle
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject

/**
 * Shared plumbing for the screens that talk to the gateway's admin API.
 *
 * Every one of them has the same three preconditions — the process is up, we
 * hold a session, and the CSRF token is in hand — and the same three states to
 * render while they are not met. Factoring that here keeps each screen to the
 * part that is actually about its own data.
 */

/** The admin (WebUI) port, or null when the gateway is not listening. */
@Composable
fun rememberAdminPort(): Int? {
    val state by GatewayRuntime.runner.state.collectAsStateWithLifecycle()
    return (state as? GatewayState.Running)?.webUiPort
}

/** The loopback base URL for the admin API, or null when it is not up. */
@Composable
fun rememberAdminBaseUrl(): String? {
    val port = rememberAdminPort() ?: return null
    return GatewayRuntime.admin.baseUrl(port)
}

/** Where the gateway renders its admin UI, for the "open in browser" affordance. */
@Composable
fun rememberAdminUrl(): String? = rememberAdminBaseUrl()

/**
 * The placeholder every admin-backed screen shows while the gateway is down.
 *
 * It is deliberately not an error state: a stopped gateway is the normal state
 * of a freshly installed app, and the fix is one tap away on the home tab.
 */
@Composable
fun AdminOfflineNotice(modifier: Modifier = Modifier) {
    O2ACard(modifier = modifier) {
        O2AEmptyState("网关未运行，先到「运行桌面」启动服务")
    }
}

/** Unpacks the gateway's `{"error":{"code","message"}}` envelope. */
fun Result<JsonObject>.errorText(): String = exceptionOrNull()?.message ?: "请求失败"

/**
 * The card gutter, for a block that is not a card.
 *
 * Screens that lay themselves out as a list use `contentPadding` for this, but
 * a screen that scrolls a plain column has to add it by hand — and getting it
 * wrong is the one mistake that looks like a layout bug rather than a typo.
 */
@Composable
fun PaddedSection(content: @Composable () -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 12.dp)) { content() }
}

/**
 * A number that is edited as text but stored as a number.
 *
 * The text is held locally so the field can be emptied while it is being
 * retyped; committing on every keystroke would fight the user at the exact
 * moment they are clearing it. Non-digits are refused rather than accepted and
 * silently dropped, so what is on screen always matches what was stored.
 */
@Composable
fun NumberField(label: String, value: Int, onChange: (Int) -> Unit) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    O2ATextField(
        value = text,
        onValueChange = { input ->
            if (input.isEmpty() || input.all { it.isDigit() }) {
                text = input
                input.toIntOrNull()?.let(onChange)
            }
        },
        label = label,
        keyboardType = KeyboardType.Number,
    )
}

/**
 * One GET against the admin API, re-issued whenever [key] changes.
 *
 * The screens that poll do it by bumping [refresh] rather than by running their
 * own timer: the gateway's monitor is a snapshot of a ring buffer it maintains
 * anyway, so pulling on demand keeps the app from asking for data nobody is
 * looking at.
 */
class AdminJson(
    val data: JsonObject?,
    val error: String?,
    val loading: Boolean,
    val refresh: () -> Unit,
)

@Composable
fun rememberAdminJson(baseUrl: String, path: String, key: Any? = null): AdminJson {
    var data by remember(baseUrl, path) { mutableStateOf<JsonObject?>(null) }
    var error by remember(baseUrl, path) { mutableStateOf<String?>(null) }
    var loading by remember(baseUrl, path) { mutableStateOf(true) }
    var tick by remember(baseUrl, path) { mutableIntStateOf(0) }

    LaunchedEffect(baseUrl, path, tick, key) {
        loading = true
        GatewayRuntime.admin.get(baseUrl, path)
            .onSuccess {
                data = it
                error = null
            }
            .onFailure { error = it.message ?: "请求失败" }
        loading = false
    }

    return AdminJson(data, error, loading) { tick++ }
}

private enum class Phase { Checking, Login, Ready }

/**
 * Bumped when the session behind [AdminGate] is known to be gone — the account
 * was just changed, which revokes every session including this one.
 *
 * A plain field on the client would not do: nothing would recompose, and every
 * gated screen would keep rendering with a session that no longer exists.
 */
private val adminSessionEpoch = mutableIntStateOf(0)

/** Sends every gate back through the session check, landing on the login panel. */
fun invalidateAdminSession() {
    adminSessionEpoch.intValue++
}

/**
 * Renders [content] once the app holds an admin session, and a login panel
 * until then.
 *
 * The gateway's own WebUI authenticates with a session cookie and requires the
 * CSRF token from the login response on every later write, so "am I signed in"
 * is a property of the process-wide [AdminClient] rather than of any one
 * screen. That is why this gate can live above all of them: signing in here
 * counts as signed in on the next screen, and the session survives navigation
 * because nothing tears the client down.
 */
@Composable
fun AdminGate(
    baseUrl: String?,
    modifier: Modifier = Modifier,
    content: @Composable (String) -> Unit,
) {
    if (baseUrl == null) {
        AdminOfflineNotice(modifier)
        return
    }

    val admin = GatewayRuntime.admin
    val epoch = adminSessionEpoch.intValue
    var phase by remember(baseUrl, epoch) {
        mutableStateOf(if (admin.authenticated) Phase.Ready else Phase.Checking)
    }

    // A cookie outlives the app process but the session behind it does not, so
    // the remembered state is not trusted: ask the server what it thinks. The
    // ask is a loopback GET, which is cheaper than being wrong about it.
    LaunchedEffect(baseUrl, epoch) {
        phase = if (admin.session(baseUrl).isSuccess) Phase.Ready else Phase.Login
    }

    when (phase) {
        Phase.Checking -> O2AColumn(modifier) {
            O2ACard {
                O2AProgressBar(progress = null)
                O2AText("正在校验管理端会话…", style = O2ATextStyle.Caption)
            }
        }

        Phase.Ready -> content(baseUrl)

        Phase.Login -> LoginPanel(baseUrl = baseUrl, modifier = modifier) {
            phase = Phase.Ready
        }
    }
}

/** Username and password, exactly as the WebUI's own login form asks for them. */
@Composable
private fun LoginPanel(baseUrl: String, modifier: Modifier = Modifier, onSignedIn: () -> Unit) {
    val admin = GatewayRuntime.admin
    var username by rememberSaveable { mutableStateOf(admin.username ?: "admin") }
    var password by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val submit: () -> Unit = {
        if (!busy && username.isNotBlank()) {
            busy = true
            error = null
            scope.launch {
                admin.login(baseUrl, username.trim(), password)
                    .onSuccess { onSignedIn() }
                    .onFailure { error = it.message ?: "登录失败" }
                busy = false
            }
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        O2AColumn {
            O2ACard {
                O2AText("需要管理端登录", style = O2ATextStyle.Subtitle)
                O2AText(
                    "该页面读写的是网关的管理接口，与原版 WebUI 共用同一套账号密码。",
                    style = O2ATextStyle.Caption,
                )
                O2ATextField(
                    value = username,
                    onValueChange = { username = it },
                    label = "用户名",
                    enabled = !busy,
                )
                O2ATextField(
                    value = password,
                    onValueChange = { password = it },
                    label = "密码",
                    enabled = !busy,
                    keyboardType = KeyboardType.Password,
                )
                error?.let { O2AText(it, style = O2ATextStyle.Caption) }
                O2AButton(
                    text = if (busy) "登录中…" else "登录",
                    onClick = submit,
                    enabled = !busy && username.isNotBlank(),
                )
            }
            O2ACard {
                O2AText(
                    "初始账号为 admin，密码是本机首次启动时随机生成的，写在 config.json 的 webui 段落里，" +
                        "可以在「配置中心」查看，或在「账号安全」里改成自己记得住的。",
                    style = O2ATextStyle.Caption,
                )
            }
        }
    }
}

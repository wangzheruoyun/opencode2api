package com.opencode2api.android.ui

import androidx.compose.ui.graphics.vector.ImageVector
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.icon.extended.Notes
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.icon.extended.Store

/**
 * A primary tab in the bottom bar. The five here are the screens that carry
 * live state; everything else is a leaf reached from "更多".
 */
enum class Tab(val title: String) {
    HOME("运行桌面"),
    CONFIG("配置中心"),
    LOGS("事件日志"),
    MODELS("模型与额度"),
    MORE("更多"),
    ;

    val icon: ImageVector
        get() = when (this) {
            HOME -> MiuixIcons.Regular.Home
            CONFIG -> MiuixIcons.Regular.Settings
            LOGS -> MiuixIcons.Regular.Notes
            MODELS -> MiuixIcons.Regular.Store
            MORE -> MiuixIcons.Regular.More
        }
}

/**
 * Every screen the app can show. [Tab] entries are reachable from the bottom
 * bar and never stack; the rest are pushed on top with a back arrow.
 */
sealed interface Route {
    val title: String

    data class TabRoute(val tab: Tab) : Route {
        override val title: String get() = tab.title
    }

    /** 首次运行 — the post-install walkthrough. */
    data object Guide : Route {
        override val title: String get() = "首次运行"
    }

    /** 接入手册 — how to point an OpenAI/Anthropic client at this gateway. */
    data object Access : Route {
        override val title: String get() = "接入手册"
    }

    /** Token 用量 — request and token counters over time. */
    data object Usage : Route {
        override val title: String get() = "Token 用量"
    }

    /** Playground — send a prompt through the gateway from the app. */
    data object Playground : Route {
        override val title: String get() = "Playground"
    }

    /** 路由诊断 — which upstream each request took, and why. */
    data object Diagnostics : Route {
        override val title: String get() = "路由诊断"
    }

    /** 账号安全 — admin password and server key. */
    data object Account : Route {
        override val title: String get() = "账号安全"
    }

    /** API Key 管理 — keys this gateway hands out to clients. */
    data object ApiKeys : Route {
        override val title: String get() = "API Key 管理"
    }

    /** 模型轮转 — the rotation policy across upstream keys. */
    data object Rotation : Route {
        override val title: String get() = "模型轮转"
    }

    /** 免费模型可用性 — which free models currently answer. */
    data object Availability : Route {
        override val title: String get() = "免费模型可用性"
    }

    data object About : Route {
        override val title: String get() = "关于"
    }
}

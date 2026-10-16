package com.opencode2api.android.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.opencode2api.android.ui.component.O2AIconButton
import com.opencode2api.android.ui.component.O2ANavItem
import com.opencode2api.android.ui.component.O2ANavigationBar
import com.opencode2api.android.ui.component.O2AScaffold
import com.opencode2api.android.ui.component.O2ATopBar
import com.opencode2api.android.ui.screen.AboutScreen
import com.opencode2api.android.ui.screen.AccessScreen
import com.opencode2api.android.ui.screen.AccountScreen
import com.opencode2api.android.ui.screen.ApiKeysScreen
import com.opencode2api.android.ui.screen.AvailabilityScreen
import com.opencode2api.android.ui.screen.ConfigScreen
import com.opencode2api.android.ui.screen.DiagnosticsScreen
import com.opencode2api.android.ui.screen.GuideScreen
import com.opencode2api.android.ui.screen.HomeScreen
import com.opencode2api.android.ui.screen.LogsScreen
import com.opencode2api.android.ui.screen.ModelsScreen
import com.opencode2api.android.ui.screen.MoreScreen
import com.opencode2api.android.ui.screen.PlaygroundScreen
import com.opencode2api.android.ui.screen.RotationScreen
import com.opencode2api.android.ui.screen.UsageScreen
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back

/**
 * Navigation shell.
 *
 * The five tabs are peers and never stack — selecting a tab replaces the other.
 * The remaining screens are pushed on top and pop with back. A `List` used as a
 * stack is enough for a tree this shallow; a nav library would be more moving
 * parts than the shape justifies.
 */
@Composable
fun MainScreen() {
    var tabIndex by rememberSaveable { mutableIntStateOf(0) }
    val stack = remember { mutableStateListOf<Route>() }

    val tab = Tab.entries[tabIndex.coerceIn(0, Tab.entries.lastIndex)]
    val current: Route = stack.lastOrNull() ?: Route.TabRoute(tab)
    val onBack: () -> Unit = { if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex) }
    val navigate: (Route) -> Unit = { stack.add(it) }

    BackHandler(enabled = stack.isNotEmpty(), onBack = onBack)

    O2AScaffold(
        topBar = {
            O2ATopBar(
                title = current.title,
                navigationIcon = if (stack.isEmpty()) {
                    null
                } else {
                    { O2AIconButton(MiuixIcons.Regular.Back, "返回", onBack) }
                },
            )
        },
        bottomBar = {
            if (stack.isEmpty()) {
                O2ANavigationBar(
                    items = Tab.entries.map { O2ANavItem(it.title, it.icon) },
                    selectedIndex = tabIndex,
                    onSelect = { tabIndex = it },
                )
            }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (current) {
                is Route.TabRoute -> when (current.tab) {
                    Tab.HOME -> HomeScreen(onNavigate = navigate)
                    Tab.CONFIG -> ConfigScreen(onNavigate = navigate)
                    Tab.LOGS -> LogsScreen()
                    Tab.MODELS -> ModelsScreen(onNavigate = navigate)
                    Tab.MORE -> MoreScreen(onNavigate = navigate)
                }

                Route.Guide -> GuideScreen(onNavigate = navigate)
                Route.Access -> AccessScreen()
                Route.Usage -> UsageScreen(onNavigate = navigate)
                Route.Playground -> PlaygroundScreen()
                Route.Diagnostics -> DiagnosticsScreen()
                Route.Account -> AccountScreen()
                Route.ApiKeys -> ApiKeysScreen()
                Route.Rotation -> RotationScreen()
                Route.Availability -> AvailabilityScreen()
                Route.About -> AboutScreen()
            }
        }
    }
}

package com.opencode2api.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.opencode2api.android.core.GatewayRuntime
import com.opencode2api.android.ui.LocalUiMode
import com.opencode2api.android.ui.MainScreen
import com.opencode2api.android.ui.UiMode
import com.opencode2api.android.ui.theme.O2ATheme
import com.opencode2api.android.ui.theme.toThemeSettings

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // installSplashScreen must run before super.onCreate to take over the
        // window the splash theme put up.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        GatewayRuntime.attach(applicationContext)

        setContent {
            val settings by GatewayRuntime.settings.state.collectAsStateWithLifecycle()
            // The skin is a Compose local rather than a theme parameter so that
            // screens deep in the tree can read it without threading it down.
            CompositionLocalProvider(LocalUiMode provides UiMode.fromValue(settings.uiMode)) {
                O2ATheme(settings.toThemeSettings()) {
                    MainScreen()
                }
            }
        }
    }
}

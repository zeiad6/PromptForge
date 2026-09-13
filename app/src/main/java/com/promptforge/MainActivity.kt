package com.promptforge

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.promptforge.data.AppSettings
import com.promptforge.ui.nav.ForgeRootApp
import com.promptforge.ui.nav.LocalAppContainer
import com.promptforge.ui.theme.ForgeTheme
import com.promptforge.util.LangPrefs

class MainActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        // Explicit language choice wins; "system" follows the device language natively.
        super.attachBaseContext(LangPrefs.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as PromptForgeApp).container
        setContent {
            val settings by container.settings.settings
                .collectAsStateWithLifecycle(initialValue = AppSettings())
            val dark = when (settings.theme) {
                "dark" -> true
                "light" -> false
                else -> isSystemInDarkTheme()
            }
            // Status / nav bar icon contrast follows the active theme
            WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
            ForgeTheme(darkTheme = dark) {
                CompositionLocalProvider(LocalAppContainer provides container) {
                    ForgeRootApp()
                }
            }
        }
    }
}

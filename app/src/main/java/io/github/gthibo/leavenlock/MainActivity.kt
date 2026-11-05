package io.github.gthibo.leavenlock

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.gthibo.leavenlock.data.settings.ThemeMode
import io.github.gthibo.leavenlock.ui.AppNavigation
import io.github.gthibo.leavenlock.ui.permissions.PermissionGate
import io.github.gthibo.leavenlock.ui.theme.ThemeViewModel
import io.github.gthibo.leavenlock.ui.theme.porcelain.PorcelainTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val themeViewModel: ThemeViewModel = hiltViewModel()
            val prefs by themeViewModel.prefs.collectAsStateWithLifecycle()
            val dark = when (prefs.mode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            // Transparent system bars over `surface`; dark icons on Porcelain, light on Graphite,
            // following the user's theme choice rather than only the system setting.
            DisposableEffect(dark) {
                val style = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose {}
            }
            // Dynamic colour is intentionally off: the palette stays LEAVE/LOCK's own.
            PorcelainTheme(darkTheme = dark) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    PermissionGate {
                        AppNavigation()
                    }
                }
            }
        }
    }
}

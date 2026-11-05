package io.github.gthibo.leavenlock.ui.theme.porcelain

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable

/**
 * LEAVE/LOCK app theme. Pass the user's choice (System default / Light / Dark) as [darkTheme].
 * Dynamic colour is intentionally not used.
 */
@Composable
fun PorcelainTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) PorcelainDark else PorcelainLight
    CompositionLocalProvider(LocalPorcelainColors provides colors) {
        MaterialTheme(
            colorScheme = colors.toColorScheme(darkTheme),
            typography = PorcelainTypography,
            shapes = PorcelainShapes,
            content = content,
        )
    }
}

/** Access tokens that Material 3 has no slot for: `Porcelain.colors.signal`, `Porcelain.colors.tealDeepRaised`… */
object Porcelain {
    val colors: PorcelainColors
        @Composable @ReadOnlyComposable get() = LocalPorcelainColors.current
}

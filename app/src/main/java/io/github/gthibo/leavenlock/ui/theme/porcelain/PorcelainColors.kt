package io.github.gthibo.leavenlock.ui.theme.porcelain

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Porcelain Grid colour tokens for LEAVE/LOCK. Generated from design/tokens/tokens.json.
 * Every token, in both themes. Names match the design system (kebab-case → camelCase).
 * Do not edit values here: change the design system, then regenerate.
 */
@Immutable
data class PorcelainColors(
    val surface: Color,
    val surfaceRaised: Color,
    val surfaceSunken: Color,
    val silver: Color,
    val silverSoft: Color,
    val line: Color,
    val lineStrong: Color,
    val ink: Color,
    val inkMuted: Color,
    val graphite: Color,
    val onGraphite: Color,
    val teal: Color,
    val tealSoft: Color,
    val onTeal: Color,
    val tealDeep: Color,
    val tealDeepEdge: Color,
    val tealDeepRaised: Color,
    val onTealDeep: Color,
    val onTealDeepMuted: Color,
    val mint: Color,
    val onMint: Color,
    val signal: Color,
    val focus: Color,
    val link: Color,
    val warn: Color,
    val danger: Color,
    val onDanger: Color,
    val tonal: Color,
    val markHi: Color,
    val markLo: Color,
    val markRune: Color,
)

/** Porcelain (light) theme. */
val PorcelainLight = PorcelainColors(
    surface = Color(0xFFF3F4F1),
    surfaceRaised = Color(0xFFFBFBFA),
    surfaceSunken = Color(0xFFE9EBE8),
    silver = Color(0xFFC9CED0),
    silverSoft = Color(0xFFDEE2E3),
    line = Color(0xFFD3D8D9),
    lineStrong = Color(0xFF80888C),
    ink = Color(0xFF23272A),
    inkMuted = Color(0xFF565E62),
    graphite = Color(0xFF2E3337),
    onGraphite = Color(0xFFF3F4F1),
    teal = Color(0xFF2E6E74),
    tealSoft = Color(0xFFDCE9E8),
    onTeal = Color(0xFFFFFFFF),
    tealDeep = Color(0xFF1F5257),
    tealDeepEdge = Color(0xFF1F5257),
    tealDeepRaised = Color(0xFF2A6267),
    onTealDeep = Color(0xFFEEF4F3),
    onTealDeepMuted = Color(0xFFA9CBC7),
    mint = Color(0xFFB9E8DC),
    onMint = Color(0xFF23272A),
    signal = Color(0xFFE39A2D),
    focus = Color(0xFF2E6E74),
    link = Color(0xFF2E6E74),
    warn = Color(0xFF8A5A00),
    danger = Color(0xFFA33A2B),
    onDanger = Color(0xFFFFFFFF),
    tonal = Color(0xFFDEE2E3),
    markHi = Color(0xFF5B686E),
    markLo = Color(0xFF2E3337),
    markRune = Color(0xFFF3F4F1),
)

/** Graphite (dark) theme. */
val PorcelainDark = PorcelainColors(
    surface = Color(0xFF141719),
    surfaceRaised = Color(0xFF1C2023),
    surfaceSunken = Color(0xFF0F1113),
    silver = Color(0xFF6B7377),
    silverSoft = Color(0xFF2A3034),
    line = Color(0xFF2C3236),
    lineStrong = Color(0xFF687075),
    ink = Color(0xFFE6E9E8),
    inkMuted = Color(0xFFA1A9AC),
    graphite = Color(0xFF262B2F),
    onGraphite = Color(0xFFE6E9E8),
    teal = Color(0xFF6DB2B6),
    tealSoft = Color(0xFF1B3335),
    onTeal = Color(0xFF0E1416),
    tealDeep = Color(0xFF173B3E),
    tealDeepEdge = Color(0xFF2C5F63),
    tealDeepRaised = Color(0xFF20494C),
    onTealDeep = Color(0xFFE6EFEE),
    onTealDeepMuted = Color(0xFFA3C4C1),
    mint = Color(0xFF9EDFCF),
    onMint = Color(0xFF0E1416),
    signal = Color(0xFFF0A640),
    focus = Color(0xFF6DB2B6),
    link = Color(0xFF6DB2B6),
    warn = Color(0xFFE0B04F),
    danger = Color(0xFFF08A78),
    onDanger = Color(0xFF1A0D0B),
    tonal = Color(0xFF343C41),
    markHi = Color(0xFFD7E4E3),
    markLo = Color(0xFF8FA7AB),
    markRune = Color(0xFF1C2023),
)

val LocalPorcelainColors = staticCompositionLocalOf { PorcelainLight }

/** Material 3 roles mapped from the tokens (see design/README.md). Dynamic colour stays off. */
fun PorcelainColors.toColorScheme(dark: Boolean): ColorScheme {
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = teal, onPrimary = onTeal,
        primaryContainer = tealDeep, onPrimaryContainer = onTealDeep,
        secondary = graphite, onSecondary = onGraphite,
        secondaryContainer = tonal, onSecondaryContainer = ink,
        tertiary = mint, onTertiary = onMint,
        background = surface, onBackground = ink,
        surface = surface, onSurface = ink,
        surfaceVariant = surfaceSunken, onSurfaceVariant = inkMuted,
        surfaceContainerLowest = surfaceRaised, surfaceContainerLow = surfaceRaised,
        surfaceContainer = surfaceRaised, surfaceContainerHigh = surfaceRaised,
        surfaceContainerHighest = surfaceSunken,
        outline = lineStrong, outlineVariant = line,
        error = danger, onError = onDanger,
        inverseSurface = ink, inverseOnSurface = surface, inversePrimary = tealSoft,
        surfaceTint = Color.Transparent,
    )
}

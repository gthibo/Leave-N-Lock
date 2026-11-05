package io.github.gthibo.leavenlock.ui.theme.porcelain

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.gthibo.leavenlock.R

/**
 * Porcelain Grid type for LEAVE/LOCK. Needs the font files from design/android/res/font
 * copied into app/src/main/res/font. Exo 2 and Instrument Sans are variable fonts (minSdk 26 is enough).
 */
@OptIn(ExperimentalTextApi::class)
private fun variable(res: Int, weight: Int) =
    Font(res, FontWeight(weight), variationSettings = FontVariation.Settings(FontVariation.weight(weight)))

val ExoTwo = FontFamily(listOf(300, 400, 500, 600, 700).map { variable(R.font.exo2_variable, it) })
val InstrumentSans = FontFamily(listOf(400, 500, 600).map { variable(R.font.instrument_sans_variable, it) })
val PlexMono = FontFamily(
    Font(R.font.ibm_plex_mono_regular, FontWeight.W400),
    Font(R.font.ibm_plex_mono_medium, FontWeight.W500),
)

/** Every named style from the design system. `label` text is set uppercase by the caller. */
object PorcelainText {
    val wordmark = TextStyle(fontFamily = ExoTwo, fontWeight = FontWeight.W700, fontSize = 40.sp, lineHeight = 38.sp, letterSpacing = (0.02).em)
    val displayXl = TextStyle(fontFamily = ExoTwo, fontWeight = FontWeight.W600, fontSize = 64.sp, lineHeight = 68.sp, letterSpacing = (-0.02).em)
    val display = TextStyle(fontFamily = ExoTwo, fontWeight = FontWeight.W600, fontSize = 40.sp, lineHeight = 44.sp, letterSpacing = (-0.015).em)
    val readout = TextStyle(fontFamily = ExoTwo, fontWeight = FontWeight.W300, fontSize = 48.sp, lineHeight = 52.sp, letterSpacing = (-0.01).em)
    val status = TextStyle(fontFamily = ExoTwo, fontWeight = FontWeight.W600, fontSize = 32.sp, lineHeight = 38.sp, letterSpacing = (-0.01).em)
    val heading1 = TextStyle(fontFamily = ExoTwo, fontWeight = FontWeight.W500, fontSize = 24.sp, lineHeight = 30.sp, letterSpacing = 0.sp)
    val heading2 = TextStyle(fontFamily = ExoTwo, fontWeight = FontWeight.W500, fontSize = 20.sp, lineHeight = 26.sp, letterSpacing = 0.sp)
    val heading3 = TextStyle(fontFamily = ExoTwo, fontWeight = FontWeight.W500, fontSize = 17.sp, lineHeight = 24.sp, letterSpacing = 0.sp)
    val bodyLg = TextStyle(fontFamily = InstrumentSans, fontWeight = FontWeight.W400, fontSize = 17.sp, lineHeight = 26.sp, letterSpacing = 0.sp)
    val body = TextStyle(fontFamily = InstrumentSans, fontWeight = FontWeight.W400, fontSize = 15.sp, lineHeight = 22.sp, letterSpacing = 0.sp)
    val bodyStrong = TextStyle(fontFamily = InstrumentSans, fontWeight = FontWeight.W500, fontSize = 16.sp, lineHeight = 22.sp, letterSpacing = 0.sp)
    val bodySm = TextStyle(fontFamily = InstrumentSans, fontWeight = FontWeight.W400, fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = 0.sp)
    val label = TextStyle(fontFamily = PlexMono, fontWeight = FontWeight.W500, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = (0.12).em)
    val data = TextStyle(fontFamily = PlexMono, fontWeight = FontWeight.W400, fontSize = 13.sp, lineHeight = 20.sp, letterSpacing = 0.sp)
}

/** Material 3 slots mapped to the design system styles. */
val PorcelainTypography = Typography(
    displayLarge = PorcelainText.displayXl,
    displayMedium = PorcelainText.display,
    displaySmall = PorcelainText.readout,
    headlineSmall = PorcelainText.status,
    titleLarge = PorcelainText.heading1,
    titleMedium = PorcelainText.heading2,
    titleSmall = PorcelainText.heading3,
    bodyLarge = PorcelainText.bodyLg,
    bodyMedium = PorcelainText.body,
    bodySmall = PorcelainText.bodySm,
    labelLarge = PorcelainText.bodyStrong,
    labelMedium = PorcelainText.label,
    labelSmall = PorcelainText.data,
)

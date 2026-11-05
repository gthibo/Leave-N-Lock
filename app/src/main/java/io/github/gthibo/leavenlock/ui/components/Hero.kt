package io.github.gthibo.leavenlock.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.gthibo.leavenlock.ui.theme.porcelain.PorcelainSpace
import io.github.gthibo.leavenlock.ui.theme.porcelain.PorcelainText

/**
 * Header for secondary screens: the flat teal-deep hero card (chamfer-lg) with an icon well, the
 * screen title and a one-line purpose. Pass [icon] = null to show the LEAVE/LOCK mark instead.
 */
@Composable
fun HeroBanner(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    @DrawableRes icon: Int? = null,
    eyebrow: String? = null,
) {
    PgCard(
        modifier = modifier,
        tone = CardTone.Hero,
        cut = 24.dp,
        eyebrow = eyebrow,
        pixels = listOf("w.w", "ddw", "w.m"),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PorcelainSpace.s4)) {
            if (icon != null) {
                IconWell(icon, size = 48.dp, iconSize = 24.dp)
            } else {
                PorcelainMark(Modifier.height(48.dp))
            }
            Text(title, style = PorcelainText.status.copy(fontSize = 28.sp, lineHeight = 34.sp))
        }
        subtitle?.let { Text(it, style = PorcelainText.body, color = mutedText) }
    }
}

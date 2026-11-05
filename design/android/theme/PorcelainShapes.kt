package io.github.gthibo.leavenlock.ui.theme.porcelain

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/** One 45° cut on the top-right corner: the Porcelain Grid card shape. */
class ChamferShape(private val cut: Dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val c = with(density) { cut.toPx() }.coerceAtMost(minOf(size.width, size.height) / 2f)
        return Outline.Generic(Path().apply {
            moveTo(0f, 0f)
            lineTo(size.width - c, 0f)
            lineTo(size.width, c)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        })
    }
}

object PorcelainShape {
    val chamferSm = ChamferShape(8.dp)   // stat tiles, icon wells
    val chamferMd = ChamferShape(16.dp)  // cards, settings groups
    val chamferLg = ChamferShape(24.dp)  // hero cards
    val radiusXs = RoundedCornerShape(2.dp) // labels, switch thumbs
    val radiusSm = RoundedCornerShape(4.dp) // buttons, inputs, switch tracks, dialogs (the ceiling)
}

/** Nothing rounder than 4dp. Large and extra-large surfaces are chamfered. */
val PorcelainShapes = Shapes(
    extraSmall = PorcelainShape.radiusXs,
    small = PorcelainShape.radiusSm,
    medium = PorcelainShape.radiusSm,
    large = PorcelainShape.chamferMd,
    extraLarge = PorcelainShape.chamferLg,
)

/** Spacing and layout tokens, in dp. */
object PorcelainSpace {
    val s1 = 4.dp; val s2 = 8.dp; val s3 = 12.dp; val s4 = 16.dp; val s5 = 24.dp
    val s6 = 32.dp; val s7 = 48.dp; val s8 = 64.dp; val s9 = 96.dp
    val pixel = 8.dp; val pixelGap = 2.dp
    val screenMargin = 16.dp; val touchTarget = 48.dp
    val hairline = 1.dp; val rule = 2.dp
}

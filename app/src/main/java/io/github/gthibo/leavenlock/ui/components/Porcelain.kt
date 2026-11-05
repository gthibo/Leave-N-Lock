package io.github.gthibo.leavenlock.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.layout
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.gthibo.leavenlock.R
import io.github.gthibo.leavenlock.ui.theme.porcelain.ChamferShape
import io.github.gthibo.leavenlock.ui.theme.porcelain.Porcelain
import io.github.gthibo.leavenlock.ui.theme.porcelain.PorcelainShape
import io.github.gthibo.leavenlock.ui.theme.porcelain.PorcelainSpace
import io.github.gthibo.leavenlock.ui.theme.porcelain.PorcelainText

/*
 * Porcelain Grid building blocks for LEAVE/LOCK, following design/README.md and the design
 * system page: tokens only (no raw hex), one 45° chamfer per card, 4dp corners on controls,
 * no gradients / glows / shadows, 48dp touch targets.
 */

/** True inside a hero (teal-deep) card, so controls invert to the on-teal-deep colours. */
val LocalOnHero = staticCompositionLocalOf { false }

/** Secondary text colour that follows the surface (ink-muted, or on-teal-deep-muted on a hero card). */
val mutedText: Color
    @Composable @ReadOnlyComposable get() =
        if (LocalOnHero.current) Porcelain.colors.onTealDeepMuted else Porcelain.colors.inkMuted

// ── Icons ───────────────────────────────────────────────────────────

/** A Porcelain Grid line icon (`ic_pg_*`). Tinted with the current content colour by default. */
@Composable
fun PgIcon(
    @DrawableRes res: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
    tint: Color = LocalContentColor.current,
) {
    Icon(painterResource(res), contentDescription, modifier.size(size), tint = tint)
}

/** 48dp icon button for top bars and card headers. */
@Composable
fun PgIconButton(
    @DrawableRes res: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: Dp = 24.dp,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = IconButtonDefaults.iconButtonColors(
            contentColor = LocalContentColor.current,
            disabledContentColor = mutedText,
        ),
    ) {
        PgIcon(res, contentDescription, size = size, tint = LocalContentColor.current)
    }
}

/**
 * Pulls a 48dp control into a card's top-right padding (the design's `margin: -12px -12px 0 0`),
 * so a header action lines up with the eyebrow without making the header taller.
 */
fun Modifier.cornerBleed(bleed: Dp = 12.dp): Modifier = layout { measurable, constraints ->
    val p = measurable.measure(constraints)
    val b = bleed.roundToPx()
    val w = (p.width - b).coerceAtLeast(0)
    val h = (p.height - 2 * b).coerceAtLeast(0)
    layout(w, h) { p.place(0, -b) }
}

// ── Text bits ───────────────────────────────────────────────────────

/** Small uppercase mono label above a title ("System status", "Vehicle"). */
@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier, color: Color = mutedText) {
    Text(text.uppercase(), modifier = modifier, style = PorcelainText.label, color = color)
}

/** A 1dp `line` hairline between rows. */
@Composable
fun Hairline(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier, thickness = PorcelainSpace.hairline, color = Porcelain.colors.line)
}

// ── Card ────────────────────────────────────────────────────────────

enum class CardTone { Raised, Hero }

/**
 * The container: flat fill, 1dp edge, one 45° chamfer on the top-right corner.
 * [CardTone.Hero] is the flat teal-deep status card (one or two per screen) and may carry a
 * crisp [pixels] cluster under the chamfer, or bottom-right with [pixelsAtBottom].
 */
@Composable
fun PgCard(
    modifier: Modifier = Modifier,
    tone: CardTone = CardTone.Raised,
    cut: Dp = 16.dp,
    eyebrow: String? = null,
    title: String? = null,
    meta: (@Composable () -> Unit)? = null,
    pixels: List<String>? = null,
    pixelsAtBottom: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(PorcelainSpace.s5),
    spacing: Dp = PorcelainSpace.s4,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Porcelain.colors
    val hero = tone == CardTone.Hero
    val shape = remember(cut) { ChamferShape(cut) }
    Box(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (hero) c.tealDeep else c.surfaceRaised)
            .border(PorcelainSpace.hairline, if (hero) c.tealDeepEdge else c.line, shape),
    ) {
        CompositionLocalProvider(
            LocalContentColor provides if (hero) c.onTealDeep else c.ink,
            LocalOnHero provides hero,
        ) {
            Column(
                Modifier.fillMaxWidth().padding(contentPadding),
                verticalArrangement = Arrangement.spacedBy(spacing),
            ) {
                if (eyebrow != null || title != null || meta != null) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(PorcelainSpace.s4),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Column(Modifier.weight(1f)) {
                            eyebrow?.let { Eyebrow(it) }
                            title?.let {
                                Text(it, style = PorcelainText.heading2, modifier = Modifier.padding(top = if (eyebrow != null) 4.dp else 0.dp))
                            }
                        }
                        meta?.invoke()
                    }
                }
                content()
            }
            pixels?.let {
                PixelCluster(
                    it,
                    Modifier
                        .align(if (pixelsAtBottom) Alignment.BottomEnd else Alignment.TopEnd)
                        .padding(
                            top = if (pixelsAtBottom) 0.dp else cut + PorcelainSpace.s2,
                            bottom = if (pixelsAtBottom) PorcelainSpace.s5 else 0.dp,
                            end = PorcelainSpace.s5,
                        ),
                )
            }
        }
    }
}

/**
 * Square pixels on a strict grid: the one ornament. Rows use `.` empty, `t` teal, `m` mint,
 * `s` silver, `l` line, `1` ink, `a` signal amber, `w` on-teal-deep, `d` teal-deep-raised.
 * Crisp and static: never blurred, never animated.
 */
@Composable
fun PixelCluster(pattern: List<String>, modifier: Modifier = Modifier, cell: Dp = 6.dp, gap: Dp = PorcelainSpace.pixelGap) {
    val c = Porcelain.colors
    val cols = pattern.maxOfOrNull { it.length } ?: 0
    val w = cell * cols + gap * (cols - 1).coerceAtLeast(0)
    val h = cell * pattern.size + gap * (pattern.size - 1).coerceAtLeast(0)
    Canvas(modifier.size(w, h)) {
        val cs = cell.toPx()
        val gs = gap.toPx()
        pattern.forEachIndexed { r, row ->
            row.forEachIndexed { col, ch ->
                val color = when (ch) {
                    't' -> c.teal
                    'm' -> c.mint
                    's' -> c.silver
                    'l' -> c.line
                    '1' -> c.ink
                    'a' -> c.signal
                    'w' -> c.onTealDeep
                    'd' -> c.tealDeepRaised
                    else -> null
                }
                if (color != null) {
                    drawRect(color, Offset(col * (cs + gs), r * (cs + gs)), Size(cs, cs))
                }
            }
        }
    }
}

// ── Buttons ─────────────────────────────────────────────────────────

enum class ButtonVariant { Primary, Tonal, Secondary, Ghost, Danger }

/**
 * Sentence-case action, 48dp tall, 4dp corners, no shadow. Primary once per screen; Tonal for
 * routine actions; Secondary outlined; Ghost text-only; Danger for risky actions on the real car.
 * Disabled buttons sit on surface-sunken; say why in the label ("Already locked").
 */
@Composable
fun PgButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: ButtonVariant = ButtonVariant.Primary,
    @DrawableRes icon: Int? = null,
    enabled: Boolean = true,
) {
    val c = Porcelain.colors
    val (container, content) = when (variant) {
        ButtonVariant.Primary -> c.teal to c.onTeal
        ButtonVariant.Tonal -> c.tonal to c.ink
        ButtonVariant.Secondary, ButtonVariant.Ghost -> Color.Transparent to LocalContentColor.current
        ButtonVariant.Danger -> c.danger to c.onDanger
    }
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = PorcelainSpace.touchTarget),
        enabled = enabled,
        shape = PorcelainShape.radiusSm,
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = content,
            disabledContainerColor = c.surfaceSunken,
            disabledContentColor = c.inkMuted,
        ),
        elevation = null,
        border = if (variant == ButtonVariant.Secondary && enabled) {
            androidx.compose.foundation.BorderStroke(PorcelainSpace.hairline, c.lineStrong)
        } else null,
        contentPadding = PaddingValues(
            horizontal = if (variant == ButtonVariant.Ghost) PorcelainSpace.s3 else PorcelainSpace.s4,
        ),
    ) {
        if (icon != null) {
            PgIcon(icon, contentDescription = null, size = 18.dp, tint = LocalContentColor.current)
            Spacer(Modifier.width(PorcelainSpace.s2))
        }
        Text(text, style = PorcelainText.bodyStrong.copy(fontSize = 15.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

// ── Switch / radio / choice ─────────────────────────────────────────

/** 48×28dp rectangular track with a square thumb. Inverts on a hero card. */
@Composable
fun PgSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val c = Porcelain.colors
    val hero = LocalOnHero.current
    val track = when {
        hero && checked -> c.onTealDeep
        hero -> c.tealDeepRaised
        checked -> c.teal
        else -> c.surfaceSunken
    }
    val edge = when {
        hero && checked -> c.onTealDeep
        hero -> c.onTealDeepMuted
        checked -> c.teal
        else -> c.lineStrong
    }
    val thumb = when {
        hero && checked -> c.tealDeep
        hero -> c.onTealDeepMuted
        checked -> c.onTeal
        else -> c.inkMuted
    }
    val x by animateDpAsState(if (checked) 25.dp else 5.dp, tween(180), label = "thumb")
    Box(
        modifier
            .minimumInteractiveComponentSize()
            .semantics { if (contentDescription != null) this.contentDescription = contentDescription }
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
            .wrapContentSize(Alignment.Center)
            .requiredSize(48.dp, 28.dp)
            .clip(PorcelainShape.radiusSm)
            .background(track)
            .border(PorcelainSpace.hairline, edge, PorcelainShape.radiusSm),
    ) {
        Box(
            Modifier
                .offset { IntOffset(x.roundToPx(), 5.dp.roundToPx()) }
                .size(18.dp)
                .clip(PorcelainShape.radiusXs)
                .background(if (enabled) thumb else thumb.copy(alpha = 0.5f)),
        )
    }
}

/** One setting: title, one-line description and a trailing control. At least 56dp. */
@Composable
fun SettingRow(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    divider: Boolean = false,
    control: (@Composable () -> Unit)? = null,
) {
    Column(modifier.fillMaxWidth()) {
        if (divider) Hairline()
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .padding(vertical = if (divider) PorcelainSpace.s3 else 0.dp),
            horizontalArrangement = Arrangement.spacedBy(PorcelainSpace.s4),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = PorcelainText.bodyStrong)
                description?.let {
                    Text(it, style = PorcelainText.body.copy(fontSize = 14.sp, lineHeight = 20.sp), color = mutedText, modifier = Modifier.padding(top = 2.dp))
                }
            }
            control?.invoke()
        }
    }
}

/** A switch setting row (the common case). */
@Composable
fun SwitchRow(
    title: String,
    description: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    divider: Boolean = true,
) {
    SettingRow(title, description = description, divider = divider) {
        PgSwitch(checked, onCheckedChange, contentDescription = title)
    }
}

/** A square radio option, 48dp tall, with an optional hint that states the consequence. */
@Composable
fun PgRadio(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    hint: String? = null,
) {
    val c = Porcelain.colors
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = PorcelainSpace.touchTarget)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = PorcelainSpace.s1),
        horizontalArrangement = Arrangement.spacedBy(PorcelainSpace.s4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(20.dp)
                .border(1.5.dp, if (selected) c.teal else c.lineStrong, PorcelainShape.radiusXs),
        ) {
            if (selected) Box(Modifier.padding(4.dp).size(12.dp).background(c.teal))
        }
        Column(Modifier.weight(1f)) {
            Text(label, style = PorcelainText.body.copy(fontSize = 16.sp))
            hint?.let { Text(it, style = PorcelainText.body.copy(fontSize = 14.sp, lineHeight = 20.sp), color = mutedText) }
        }
    }
}

/** A row of square choice buttons (2–4 short options), for intervals and similar. */
@Composable
fun <T> PgChoiceRow(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Porcelain.colors
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(PorcelainSpace.s2)) {
        options.forEach { (value, label) ->
            val on = value == selected
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = PorcelainSpace.touchTarget)
                    .clip(PorcelainShape.radiusSm)
                    .background(if (on) c.tealSoft else Color.Transparent)
                    .border(PorcelainSpace.hairline, if (on) c.teal else c.lineStrong, PorcelainShape.radiusSm)
                    .selectable(selected = on, role = Role.RadioButton, onClick = { onSelect(value) }),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = PorcelainText.bodyStrong.copy(fontSize = 15.sp), color = if (on) c.teal else c.ink)
            }
        }
    }
}

/** A continuous value: 6dp track, teal fill, narrow rectangular thumb; the value in mono on the right. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PgSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    valueText: String? = null,
    description: String? = null,
    steps: Int = 0,
) {
    val c = Porcelain.colors
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Text(label, style = PorcelainText.bodyStrong, modifier = Modifier.weight(1f))
            valueText?.let { Text(it, style = PorcelainText.data.copy(fontSize = 15.sp)) }
        }
        description?.let {
            Text(it, style = PorcelainText.body.copy(fontSize = 14.sp, lineHeight = 20.sp), color = mutedText, modifier = Modifier.padding(top = 2.dp))
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            colors = SliderDefaults.colors(thumbColor = c.teal, activeTrackColor = c.teal, inactiveTrackColor = c.surfaceSunken),
            thumb = {
                Box(
                    Modifier
                        .size(14.dp, 28.dp)
                        .clip(PorcelainShape.radiusXs)
                        .background(c.teal)
                        .border(2.dp, c.surfaceRaised, PorcelainShape.radiusXs),
                )
            },
            track = { state ->
                val span = state.valueRange.endInclusive - state.valueRange.start
                val fraction = if (span <= 0f) 0f else ((state.value - state.valueRange.start) / span).coerceIn(0f, 1f)
                Canvas(Modifier.fillMaxWidth().height(6.dp)) {
                    drawRect(c.surfaceSunken)
                    drawRect(c.line, style = Stroke(1.dp.toPx()))
                    drawRect(c.teal, size = Size(size.width * fraction, size.height))
                }
            },
        )
    }
}

// ── Status pieces ───────────────────────────────────────────────────

enum class LabelTone { Neutral, Teal, Live, Signal, Warn, Outline }

/** A mono uppercase tag with one square pixel stating a condition in one to three words. */
@Composable
fun PgLabel(text: String, modifier: Modifier = Modifier, tone: LabelTone = LabelTone.Neutral, dot: Boolean = true) {
    val c = Porcelain.colors
    val hero = LocalOnHero.current
    val bg: Color
    val fg: Color
    var edge: Color? = null
    when {
        hero -> { bg = c.tealDeepRaised; fg = c.onTealDeep }
        tone == LabelTone.Neutral -> { bg = c.silverSoft; fg = c.ink }
        tone == LabelTone.Teal -> { bg = c.tealSoft; fg = c.teal }
        tone == LabelTone.Live || tone == LabelTone.Signal -> { bg = c.graphite; fg = c.onGraphite; edge = c.lineStrong }
        tone == LabelTone.Warn -> { bg = Color.Transparent; fg = c.warn; edge = c.warn }
        else -> { bg = Color.Transparent; fg = c.inkMuted; edge = c.lineStrong }
    }
    val pixel = when (tone) {
        LabelTone.Live -> c.mint
        LabelTone.Signal -> c.signal
        else -> fg
    }
    Row(
        modifier
            .height(24.dp)
            .clip(PorcelainShape.radiusXs)
            .background(bg)
            .then(if (edge != null) Modifier.border(PorcelainSpace.hairline, edge, PorcelainShape.radiusXs) else Modifier)
            .padding(horizontal = PorcelainSpace.s2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PorcelainSpace.s2),
    ) {
        if (dot) Box(Modifier.size(6.dp).background(pixel))
        Text(text.uppercase(), style = PorcelainText.label.copy(lineHeight = 11.sp), color = fg, maxLines = 1)
    }
}

/**
 * The pixel lamp: a row of square segments. Teal for levels with a mint leading segment; [signal]
 * (amber) for a countdown or pending action. Always pair it with the exact number in text.
 */
@Composable
fun SegmentMeter(
    value: Int,
    contentDescription: String,
    modifier: Modifier = Modifier,
    segments: Int = 20,
    signal: Boolean = false,
) {
    val c = Porcelain.colors
    val hero = LocalOnHero.current
    val lit = ((value.coerceIn(0, 100) / 100f) * segments + 0.0001f).toInt().coerceIn(0, segments)
    Row(
        modifier
            .fillMaxWidth()
            .height(10.dp)
            .semantics { this.contentDescription = contentDescription },
        horizontalArrangement = Arrangement.spacedBy(PorcelainSpace.pixelGap),
    ) {
        repeat(segments) { i ->
            val on = i < lit
            val lead = on && i == lit - 1
            val fill = when {
                signal && on -> c.signal
                lead -> c.mint
                on -> if (hero) c.onTealDeep else c.teal
                hero -> c.tealDeepRaised
                else -> c.surfaceSunken
            }
            val edge = when {
                signal && on -> null
                lead -> if (hero) null else c.teal
                on -> null
                hero -> null
                else -> c.line
            }
            Box(
                Modifier
                    .weight(1f)
                    .height(10.dp)
                    .background(fill)
                    .then(if (edge != null) Modifier.border(PorcelainSpace.hairline, edge) else Modifier),
            )
        }
    }
}

/** A compact value with an icon and a mono caption, chamfered small. */
@Composable
fun StatTile(@DrawableRes icon: Int, value: String, label: String, modifier: Modifier = Modifier) {
    val c = Porcelain.colors
    val hero = LocalOnHero.current
    Row(
        modifier
            .heightIn(min = PorcelainSpace.touchTarget)
            .clip(PorcelainShape.chamferSm)
            .background(if (hero) c.tealDeepRaised else c.tonal)
            .padding(horizontal = PorcelainSpace.s4, vertical = PorcelainSpace.s3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PorcelainSpace.s3),
    ) {
        val fg = if (hero) c.onTealDeep else c.ink
        PgIcon(icon, contentDescription = null, size = 20.dp, tint = fg)
        Column {
            Text(value, style = PorcelainText.data.copy(fontSize = 17.sp, lineHeight = 20.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.W500), color = fg, maxLines = 1)
            Text(label.uppercase(), style = PorcelainText.label.copy(fontSize = 10.sp, lineHeight = 14.sp), color = fg, maxLines = 1)
        }
    }
}

/** The chamfered square that holds a large glyph (the lock state, a screen's icon). */
@Composable
fun IconWell(@DrawableRes icon: Int, modifier: Modifier = Modifier, size: Dp = 64.dp, iconSize: Dp = 28.dp) {
    val c = Porcelain.colors
    val hero = LocalOnHero.current
    Box(
        modifier
            .size(size)
            .clip(PorcelainShape.chamferSm)
            .background(if (hero) c.tealDeepRaised else c.tonal),
        contentAlignment = Alignment.Center,
    ) {
        PgIcon(icon, contentDescription = null, size = iconSize, tint = if (hero) c.onTealDeep else c.ink)
    }
}

/** Square-ended progress line (no spinners). */
@Composable
fun PgProgress(modifier: Modifier = Modifier) {
    LinearProgressIndicator(
        modifier = modifier.fillMaxWidth().height(4.dp),
        color = Porcelain.colors.teal,
        trackColor = Porcelain.colors.surfaceSunken,
        strokeCap = StrokeCap.Butt,
        gapSize = 0.dp,
    )
}

// ── Field ───────────────────────────────────────────────────────────

/** A labelled single-line input: mono label above, 48dp box, 4dp corners, hint or error below. */
@Composable
fun PgField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    hint: String? = null,
    error: String? = null,
    enabled: Boolean = true,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    val c = Porcelain.colors
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(PorcelainSpace.s2)) {
        Eyebrow(label)
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            singleLine = true,
            isError = error != null,
            textStyle = PorcelainText.body.copy(fontSize = 16.sp, lineHeight = 24.sp, color = c.ink),
            shape = PorcelainShape.radiusSm,
            visualTransformation = visualTransformation,
            keyboardOptions = keyboardOptions,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = c.teal,
                unfocusedBorderColor = c.lineStrong,
                errorBorderColor = c.danger,
                focusedContainerColor = c.surfaceRaised,
                unfocusedContainerColor = c.surfaceRaised,
                cursorColor = c.teal,
            ),
        )
        when {
            error != null -> Text(error, style = PorcelainText.bodySm, color = c.danger)
            hint != null -> Text(hint, style = PorcelainText.bodySm, color = mutedText)
        }
    }
}

// ── Top bar + logo ──────────────────────────────────────────────────

/** 64dp top app bar, flat on `surface`. Inner screens pass [onBack]; Home passes a [brand]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PgTopBar(
    title: String? = null,
    onBack: (() -> Unit)? = null,
    brand: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val c = Porcelain.colors
    TopAppBar(
        title = {
            when {
                brand != null -> brand()
                title != null -> Text(title, style = PorcelainText.heading1.copy(fontSize = 22.sp, lineHeight = 28.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        },
        navigationIcon = {
            if (onBack != null) {
                PgIconButton(R.drawable.ic_pg_arrow_left, stringResource(R.string.cd_back), onBack)
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = c.surface,
            scrolledContainerColor = c.surface,
            titleContentColor = c.ink,
            navigationIconContentColor = c.ink,
            actionIconContentColor = c.ink,
        ),
    )
}

/** The LEAVE/LOCK wordmark on one line, next to the compact mark (top bar). */
@Composable
fun BrandLockup(modifier: Modifier = Modifier, markHeight: Dp = 22.dp, style: TextStyle = PorcelainText.wordmark.copy(fontSize = 20.sp, lineHeight = 20.sp)) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PorcelainSpace.s3)) {
        PorcelainMark(Modifier.height(markHeight))
        Text(stringResource(R.string.app_name), style = style, color = LocalContentColor.current, maxLines = 1)
    }
}

private val markHiPath by lazy { PathParser().parsePathString("M133.415 212.887V442.891L373.68 212.887Z").toPath() }
private val markLoPath by lazy { PathParser().parsePathString("M375.34 443.943V213.939L135.075 443.943Z").toPath() }
private val shackleRightPath by lazy { PathParser().parsePathString("M254.5 78h50l41 41v52.5h-33V135l-24-24h-34Z").toPath() }
private val shackleLeftPath by lazy { PathParser().parsePathString("M254.5 78h-50l-41 41v85.5h33V135l24-24h34Z").toPath() }
private val runePath by lazy {
    PathParser().parsePathString(
        "M789 1064V0l1281 1012-702 643 702 661L778 3334l12-1123-544 532L0 2509l789-737v-263L5 778l246-251 538 538zm344-352l363 305-363 292V712zm0 1304l363 305-363 292v-597z",
    ).toPath().apply { fillType = PathFillType.EvenOdd }
}

/** Dissolve cells (x, y, shade): 0 = mark-lo, 1 = line-strong, 2 = line, 3 = signal. */
private val dissolve = listOf(
    Triple(312.5f, 171.5f, 0), Triple(320.75f, 171.5f, 0), Triple(329.0f, 171.5f, 0), Triple(337.25f, 171.5f, 0),
    Triple(312.5f, 179.75f, 0), Triple(320.75f, 179.75f, 1), Triple(329.0f, 179.75f, 0), Triple(337.25f, 179.75f, 1),
    Triple(312.5f, 188.0f, 1), Triple(329.0f, 188.0f, 1), Triple(337.25f, 188.0f, 3),
    Triple(312.5f, 196.25f, 2), Triple(329.0f, 196.25f, 2),
)

/**
 * The compact LEAVE/LOCK mark (lock + pixel dissolve, no arcs), drawn from the design's
 * `mark-compact-on-*` artwork with theme tokens, so it is gunmetal on Porcelain and satin
 * silver on Graphite. [light] is for teal-deep and graphite grounds. Size it by height.
 */
@Composable
fun PorcelainMark(modifier: Modifier = Modifier, light: Boolean = LocalOnHero.current) {
    val c = Porcelain.colors
    val hi = if (light) c.onTealDeep else c.markHi
    val lo = if (light) c.onTealDeepMuted else c.markLo
    val mid = if (light) c.teal else c.lineStrong
    val dim = if (light) c.tealDeepRaised else c.line
    val rune = if (light) c.tealDeep else c.markRune
    BoxWithConstraints(modifier) {
        val h = maxHeight.takeIf { it != Dp.Infinity } ?: 22.dp
        Canvas(Modifier.size(h * (252f / 374f), h)) {
            val s = size.height / 374f
            withTransform({
                scale(s, s, pivot = Offset.Zero)
                translate(-128f, -74f)
            }) {
                drawPath(markHiPath, hi)
                drawPath(markLoPath, lo)
                drawPath(shackleRightPath, lo)
                drawPath(shackleLeftPath, hi)
                dissolve.forEach { (x, y, shade) ->
                    val col = when (shade) { 0 -> lo; 1 -> mid; 2 -> dim; else -> c.signal }
                    drawRect(col, Offset(x, y), Size(8.25f, 8.25f))
                }
                withTransform({
                    translate(188.75f, 234.47f)
                    scale(0.05737f, 0.05737f, pivot = Offset.Zero)
                }) {
                    drawPath(runePath, rune)
                }
            }
        }
    }
}

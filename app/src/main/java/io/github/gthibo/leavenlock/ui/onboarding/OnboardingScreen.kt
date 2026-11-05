package io.github.gthibo.leavenlock.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.gthibo.leavenlock.R
import io.github.gthibo.leavenlock.ui.components.BrandLockup
import io.github.gthibo.leavenlock.ui.components.ButtonVariant
import io.github.gthibo.leavenlock.ui.components.CardTone
import io.github.gthibo.leavenlock.ui.components.IconWell
import io.github.gthibo.leavenlock.ui.components.PgButton
import io.github.gthibo.leavenlock.ui.components.PgCard
import io.github.gthibo.leavenlock.ui.components.PorcelainMark
import io.github.gthibo.leavenlock.ui.components.mutedText
import io.github.gthibo.leavenlock.ui.theme.porcelain.Porcelain
import io.github.gthibo.leavenlock.ui.theme.porcelain.PorcelainSpace
import io.github.gthibo.leavenlock.ui.theme.porcelain.PorcelainText
import kotlinx.coroutines.launch

/** [icon] null = show the LEAVE/LOCK mark (welcome page). */
private data class OnboardStep(val icon: Int?, val titleRes: Int, val bodyRes: Int)

private val steps = listOf(
    OnboardStep(null, R.string.onboarding_welcome_title, R.string.onboarding_welcome_body),
    OnboardStep(R.drawable.ic_pg_bluetooth, R.string.onboarding_how_title, R.string.onboarding_how_body),
    OnboardStep(R.drawable.ic_pg_lock, R.string.onboarding_safety_title, R.string.onboarding_safety_body),
    OnboardStep(R.drawable.ic_pg_sliders, R.string.onboarding_setup_title, R.string.onboarding_setup_body),
)

@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    val pager = rememberPagerState(pageCount = { steps.size })
    val scope = rememberCoroutineScope()
    val c = Porcelain.colors

    Box(Modifier.fillMaxSize().background(c.surface)) {
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = PorcelainSpace.screenMargin, vertical = PorcelainSpace.s4),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                BrandLockup(Modifier.weight(1f))
                PgButton(stringResource(R.string.onboarding_skip), onFinish, variant = ButtonVariant.Ghost)
            }

            HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { page ->
                OnboardPage(steps[page])
            }

            // Page position as square lamp pixels.
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = PorcelainSpace.s4)
                    .semantics { contentDescription = "${pager.currentPage + 1} / ${steps.size}" },
                horizontalArrangement = Arrangement.spacedBy(PorcelainSpace.pixelGap, Alignment.CenterHorizontally),
            ) {
                repeat(steps.size) { i ->
                    Box(
                        Modifier
                            .size(PorcelainSpace.pixel)
                            .background(if (i == pager.currentPage) c.teal else c.line),
                    )
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(PorcelainSpace.s2), verticalAlignment = Alignment.CenterVertically) {
                if (pager.currentPage > 0) {
                    PgButton(
                        stringResource(R.string.onboarding_back),
                        { scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } },
                        Modifier.weight(1f),
                        ButtonVariant.Secondary,
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
                val last = pager.currentPage == steps.lastIndex
                PgButton(
                    text = stringResource(if (last) R.string.onboarding_get_started else R.string.onboarding_next),
                    onClick = { if (last) onFinish() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
                    modifier = Modifier.weight(1f),
                    icon = if (last) null else R.drawable.ic_pg_arrow_right,
                )
            }
        }
    }
}

@Composable
private fun OnboardPage(step: OnboardStep) {
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
    ) {
        PgCard(
            tone = CardTone.Hero,
            cut = 24.dp,
            pixels = listOf("w.w", ".d.", "w.m"),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(PorcelainSpace.s6),
        ) {
            if (step.icon == null) {
                PorcelainMark(Modifier.height(96.dp))
            } else {
                IconWell(step.icon, size = 96.dp, iconSize = 48.dp)
            }
        }
        Spacer(Modifier.height(PorcelainSpace.s6))
        Text(stringResource(step.titleRes), style = PorcelainText.status)
        Spacer(Modifier.height(PorcelainSpace.s3))
        Text(stringResource(step.bodyRes), style = PorcelainText.bodyLg, color = mutedText)
        Spacer(Modifier.width(1.dp))
    }
}

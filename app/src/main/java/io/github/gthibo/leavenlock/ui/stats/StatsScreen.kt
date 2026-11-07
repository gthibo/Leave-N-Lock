package io.github.gthibo.leavenlock.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.gthibo.leavenlock.R
import io.github.gthibo.leavenlock.data.metrics.ApiCall
import io.github.gthibo.leavenlock.data.metrics.ApiOutcome
import io.github.gthibo.leavenlock.domain.LogEntry
import io.github.gthibo.leavenlock.domain.LogLevel
import io.github.gthibo.leavenlock.ui.components.ButtonVariant
import io.github.gthibo.leavenlock.ui.components.Hairline
import io.github.gthibo.leavenlock.ui.components.PgButton
import io.github.gthibo.leavenlock.ui.components.PgCard
import io.github.gthibo.leavenlock.ui.components.PgTopBar
import io.github.gthibo.leavenlock.ui.components.SegmentMeter
import io.github.gthibo.leavenlock.ui.components.mutedText
import io.github.gthibo.leavenlock.ui.theme.porcelain.Porcelain
import io.github.gthibo.leavenlock.ui.theme.porcelain.PorcelainShape
import io.github.gthibo.leavenlock.ui.theme.porcelain.PorcelainSpace
import io.github.gthibo.leavenlock.ui.theme.porcelain.PorcelainText
import io.github.gthibo.leavenlock.ui.util.TimeFormat
import io.github.gthibo.leavenlock.ui.util.copyToClipboard
import io.github.gthibo.leavenlock.ui.util.toClipboardText
import kotlin.math.roundToInt

private data class ConfirmAction(val title: String, val message: String, val action: () -> Unit)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    onBack: () -> Unit,
    viewModel: StatsViewModel = hiltViewModel(),
) {
    val snapshot by viewModel.snapshot.collectAsStateWithLifecycle()
    val log by viewModel.log.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val copyActionLabel = stringResource(R.string.action_copy)

    // Pending confirmation for destructive "Clear" actions.
    var confirm by remember { mutableStateOf<ConfirmAction?>(null) }
    // Captured in composable scope so they can be used inside click lambdas.
    val clearCallsTitle = stringResource(R.string.stats_clear_calls_title)
    val clearCallsMsg = stringResource(R.string.stats_clear_calls_msg)
    val clearLogTitle = stringResource(R.string.stats_clear_log_title)
    val clearLogMsg = stringResource(R.string.stats_clear_log_msg)

    confirm?.let { pending ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            shape = PorcelainShape.radiusSm,
            containerColor = Porcelain.colors.surfaceRaised,
            title = { Text(pending.title, style = PorcelainText.heading2) },
            text = { Text(pending.message, style = PorcelainText.body) },
            confirmButton = {
                PgButton(stringResource(R.string.stats_clear), { pending.action(); confirm = null }, variant = ButtonVariant.Danger)
            },
            dismissButton = {
                PgButton(stringResource(R.string.action_cancel), { confirm = null }, variant = ButtonVariant.Ghost)
            },
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Porcelain.colors.surface,
        topBar = { PgTopBar(title = stringResource(R.string.stats_title), onBack = onBack) },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(start = PorcelainSpace.screenMargin, end = PorcelainSpace.screenMargin, top = PorcelainSpace.s1, bottom = PorcelainSpace.s6),
            verticalArrangement = Arrangement.spacedBy(PorcelainSpace.s4),
        ) {
            // Session / connection.
            Section(stringResource(R.string.stats_sec_session)) {
                KeyValue(stringResource(R.string.stats_mode), if (settings.demoMode) stringResource(R.string.stats_mode_demo) else stringResource(R.string.stats_mode_live))
                KeyValue(stringResource(R.string.stats_command_mode), settings.runMode.name)
                KeyValue(stringResource(R.string.stats_account), settings.accountEmail ?: stringResource(R.string.set_not_signed_in))
                KeyValue(stringResource(R.string.stats_vehicle), settings.vehicleNickname ?: stringResource(R.string.stats_none_selected))
            }

            // Rate limiting.
            Section(stringResource(R.string.stats_sec_rate)) {
                if (snapshot.isRateLimited()) {
                    val secs = ((snapshot.rateLimitedUntilEpochMs!! - System.currentTimeMillis()) / 1000)
                        .coerceAtLeast(0)
                    Text(
                        stringResource(R.string.stats_rate_limited, secs.toInt()),
                        style = PorcelainText.bodyStrong,
                        color = Porcelain.colors.warn,
                    )
                } else {
                    Text(stringResource(R.string.stats_not_rate_limited), style = PorcelainText.body, color = Porcelain.colors.teal)
                }
                KeyValue(stringResource(R.string.stats_rate_hits), snapshot.rateLimitedCount.toString())
            }

            // Aggregate metrics.
            Section(stringResource(R.string.stats_sec_totals)) {
                KeyValue(stringResource(R.string.stats_total_calls), snapshot.totalCalls.toString())
                val pct = (snapshot.successRate * 100).roundToInt()
                KeyValue(stringResource(R.string.stats_success_rate), "$pct%")
                SegmentMeter(value = pct, contentDescription = "${stringResource(R.string.stats_success_rate)} $pct%")
                KeyValue(stringResource(R.string.stats_successes), snapshot.successCount.toString())
                KeyValue(stringResource(R.string.stats_failures), snapshot.failureCount.toString())
                KeyValue(stringResource(R.string.stats_auth_failures), snapshot.unauthenticatedCount.toString())
                KeyValue(stringResource(R.string.stats_avg_duration), "${snapshot.avgDurationMs} ms")
                snapshot.lastCall?.let {
                    KeyValue(stringResource(R.string.stats_last_call), "${it.operation} · ${it.durationMs} ms · ${it.outcome}")
                }
            }

            // Recent API calls.
            SectionWithAction(
                title = stringResource(R.string.stats_sec_recent),
                actionLabel = stringResource(R.string.stats_clear),
                onAction = {
                    confirm = ConfirmAction(clearCallsTitle, clearCallsMsg, viewModel::clearMetrics)
                },
                content = {
                    if (snapshot.calls.isEmpty()) {
                        Text(stringResource(R.string.stats_no_calls), style = PorcelainText.body, color = mutedText)
                    } else {
                        snapshot.calls.take(50).forEach { ApiCallRow(it) }
                    }
                },
                secondaryLabel = copyActionLabel.takeIf { snapshot.calls.isNotEmpty() },
                onSecondary = if (snapshot.calls.isNotEmpty()) {
                    { context.copyToClipboard("LEAVE/LOCK API calls", snapshot.calls.toClipboardText()) }
                } else null,
            )

            // Activity log.
            SectionWithAction(
                title = stringResource(R.string.stats_sec_log),
                actionLabel = stringResource(R.string.stats_clear),
                onAction = {
                    confirm = ConfirmAction(clearLogTitle, clearLogMsg, viewModel::clearLog)
                },
                content = {
                    if (log.isEmpty()) {
                        Text(stringResource(R.string.stats_no_activity), style = PorcelainText.body, color = mutedText)
                    } else {
                        log.take(50).forEach { LogRow(it) }
                    }
                },
                secondaryLabel = copyActionLabel.takeIf { log.isNotEmpty() },
                onSecondary = if (log.isNotEmpty()) {
                    { context.copyToClipboard("LEAVE/LOCK activity log", log.toClipboardText()) }
                } else null,
            )
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    PgCard(title = title, spacing = PorcelainSpace.s2) { content() }
}

@Composable
private fun SectionWithAction(
    title: String,
    actionLabel: String,
    onAction: () -> Unit,
    content: @Composable () -> Unit,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
) {
    PgCard(
        title = title,
        spacing = PorcelainSpace.s2,
        meta = {
            Row(horizontalArrangement = Arrangement.spacedBy(PorcelainSpace.s1)) {
                if (secondaryLabel != null && onSecondary != null) {
                    PgButton(secondaryLabel, onSecondary, variant = ButtonVariant.Ghost)
                }
                PgButton(actionLabel, onAction, variant = ButtonVariant.Ghost)
            }
        },
    ) { content() }
}

@Composable
private fun KeyValue(key: String, value: String) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(PorcelainSpace.s3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(key, style = PorcelainText.body, color = mutedText)
        Text(
            value,
            style = PorcelainText.data.copy(fontSize = 14.sp),
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

/** A single square lamp pixel that marks an outcome (the words still say it). */
@Composable
private fun OutcomePixel(color: Color) {
    Box(Modifier.padding(top = 6.dp).size(6.dp).background(color))
}

@Composable
private fun ApiCallRow(call: ApiCall) {
    val time = TimeFormat.clock(LocalContext.current, call.timestamp, seconds = true)
    val c = Porcelain.colors
    val color = when (call.outcome) {
        ApiOutcome.SUCCESS -> c.teal
        ApiOutcome.RATE_LIMITED -> c.signal
        ApiOutcome.UNAUTHENTICATED -> c.signal
        ApiOutcome.FAILURE -> c.danger
    }
    Column {
        Hairline()
        Row(Modifier.fillMaxWidth().padding(vertical = PorcelainSpace.s2), horizontalArrangement = Arrangement.spacedBy(PorcelainSpace.s2)) {
            OutcomePixel(color)
            Text(time, style = PorcelainText.data, color = mutedText)
            Text(call.operation, style = PorcelainText.bodySm, modifier = Modifier.weight(1f))
            Text("${call.durationMs}ms", style = PorcelainText.data, color = mutedText)
            Text(call.outcome.name, style = PorcelainText.label)
        }
    }
}

@Composable
private fun LogRow(entry: LogEntry) {
    val time = TimeFormat.clock(LocalContext.current, entry.timestamp, seconds = true)
    val c = Porcelain.colors
    val color = when (entry.level) {
        LogLevel.SUCCESS -> c.teal
        LogLevel.WARN -> c.signal
        LogLevel.ERROR -> c.danger
        LogLevel.INFO -> c.lineStrong
    }
    Column {
        Hairline()
        Row(Modifier.fillMaxWidth().padding(vertical = PorcelainSpace.s2), horizontalArrangement = Arrangement.spacedBy(PorcelainSpace.s2)) {
            OutcomePixel(color)
            Text(time, style = PorcelainText.data, color = mutedText)
            Text(entry.message, style = PorcelainText.bodySm, modifier = Modifier.weight(1f))
        }
    }
}

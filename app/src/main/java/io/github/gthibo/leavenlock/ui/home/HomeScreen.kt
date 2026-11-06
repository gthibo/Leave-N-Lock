package io.github.gthibo.leavenlock.ui.home

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.gthibo.leavenlock.R
import io.github.gthibo.leavenlock.data.bluelink.model.LockState
import io.github.gthibo.leavenlock.data.settings.RunMode
import io.github.gthibo.leavenlock.domain.LogEntry
import io.github.gthibo.leavenlock.domain.LogLevel
import io.github.gthibo.leavenlock.domain.detection.DetectionState
import io.github.gthibo.leavenlock.ui.components.BrandLockup
import io.github.gthibo.leavenlock.ui.components.ButtonVariant
import io.github.gthibo.leavenlock.ui.components.CardTone
import io.github.gthibo.leavenlock.ui.components.Eyebrow
import io.github.gthibo.leavenlock.ui.components.Hairline
import io.github.gthibo.leavenlock.ui.components.IconWell
import io.github.gthibo.leavenlock.ui.components.LabelTone
import io.github.gthibo.leavenlock.ui.components.PgButton
import io.github.gthibo.leavenlock.ui.components.PgCard
import io.github.gthibo.leavenlock.ui.components.PgField
import io.github.gthibo.leavenlock.ui.components.PgIcon
import io.github.gthibo.leavenlock.ui.components.PgIconButton
import io.github.gthibo.leavenlock.ui.components.PgLabel
import io.github.gthibo.leavenlock.ui.components.PgProgress
import io.github.gthibo.leavenlock.ui.components.PgRadio
import io.github.gthibo.leavenlock.ui.components.PgSwitch
import io.github.gthibo.leavenlock.ui.components.PgTopBar
import io.github.gthibo.leavenlock.ui.components.SegmentMeter
import io.github.gthibo.leavenlock.ui.components.StatTile
import io.github.gthibo.leavenlock.ui.components.cornerBleed
import io.github.gthibo.leavenlock.ui.components.mutedText
import io.github.gthibo.leavenlock.ui.theme.porcelain.Porcelain
import io.github.gthibo.leavenlock.ui.theme.porcelain.PorcelainShape
import io.github.gthibo.leavenlock.ui.theme.porcelain.PorcelainSpace
import io.github.gthibo.leavenlock.ui.theme.porcelain.PorcelainText
import io.github.gthibo.leavenlock.ui.util.copyToClipboard
import io.github.gthibo.leavenlock.ui.util.TimeFormat
import io.github.gthibo.leavenlock.ui.util.toClipboardText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenSettings: () -> Unit,
    onOpenHelp: () -> Unit,
    onOpenLogin: () -> Unit = {},
    unlockRequested: Boolean = false,
    onUnlockRequestHandled: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val autoLock by viewModel.autoLock.collectAsStateWithLifecycle()
    val log by viewModel.log.collectAsStateWithLifecycle()
    val vehicleStatus by viewModel.vehicleStatus.collectAsStateWithLifecycle()
    val lockResult by viewModel.lockResult.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()

    val context = LocalContext.current
    // null = no dialog; true = lock; false = unlock.
    var pendingCommand by remember { mutableStateOf<Boolean?>(null) }
    // The widget's "Unlock…" lands here: show the same confirmation as "Unlock now". The unlock
    // itself still goes through manualUnlock (PIN, Armed mode, sign-in).
    LaunchedEffect(unlockRequested) {
        if (unlockRequested) {
            pendingCommand = false
            onUnlockRequestHandled()
        }
    }
    LaunchedEffect(lockResult) {
        lockResult?.let {
            android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_SHORT).show()
            viewModel.clearLockResult()
        }
    }
    LaunchedEffect(notice) {
        notice?.let {
            android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_SHORT).show()
            viewModel.clearNotice()
        }
    }
    pendingCommand?.let { isLock ->
        LockCommandDialog(
            lock = isLock,
            requirePin = viewModel.hasPin(),
            onDismiss = { pendingCommand = null },
            onConfirm = { pin ->
                pendingCommand = null
                if (isLock) viewModel.manualLock(pin) else viewModel.manualUnlock(pin)
            },
        )
    }

    Scaffold(
        containerColor = Porcelain.colors.surface,
        topBar = {
            PgTopBar(
                brand = { BrandLockup() },
                actions = {
                    PgIconButton(R.drawable.ic_pg_help, stringResource(R.string.cd_help), onOpenHelp)
                    PgIconButton(R.drawable.ic_pg_sliders, stringResource(R.string.cd_settings), onOpenSettings)
                },
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = vehicleStatus.loading,
            onRefresh = { viewModel.refreshStatus(force = true) },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = PorcelainSpace.screenMargin),
                verticalArrangement = Arrangement.spacedBy(PorcelainSpace.s4),
                contentPadding = PaddingValues(top = PorcelainSpace.s1, bottom = PorcelainSpace.s6),
            ) {
                item {
                    StatusCard(
                        enabled = settings.enabled,
                        demoMode = settings.demoMode,
                        dryRun = settings.runMode == RunMode.DRY_RUN,
                        detection = autoLock.detection,
                        graceRemaining = autoLock.graceRemaining,
                        graceTotal = settings.graceSeconds,
                        vehicleName = settings.vehicleNickname,
                        onToggle = viewModel::setEnabled,
                        onCancel = viewModel::cancel,
                    )
                }
                if (vehicleStatus.needsReauth) {
                    item { ReauthBanner(onSignIn = onOpenLogin) }
                }
                if (vehicleStatus.lowVoltage) {
                    item { LowVoltageBanner(percent = vehicleStatus.status?.twelveVoltPercent) }
                }
                if (settings.knownVehicles.size > 1) {
                    item {
                        VehicleSwitcher(
                            vehicles = settings.knownVehicles,
                            selectedId = settings.vehicleId,
                            onSelect = viewModel::selectVehicle,
                        )
                    }
                }
                item {
                    VehicleStatusCard(
                        ui = vehicleStatus,
                        parkedLabel = settings.parkedLabel.takeIf { settings.rememberParkedLocation },
                        parkedLat = settings.parkedLat.takeIf { settings.rememberParkedLocation },
                        parkedLng = settings.parkedLng.takeIf { settings.rememberParkedLocation },
                        onRefresh = { viewModel.refreshStatus(force = true) },
                    )
                }
                item {
                    // Simulating in Armed mode on a real account would send a real lock, so the
                    // button only exists in Demo or Dry run.
                    val armedLive = settings.runMode == RunMode.ARMED && !settings.demoMode
                    Column(verticalArrangement = Arrangement.spacedBy(PorcelainSpace.s2)) {
                        if (!armedLive && (settings.isConfigured || settings.demoMode)) {
                            PgButton(
                                text = stringResource(R.string.home_simulate),
                                onClick = viewModel::runNow,
                                modifier = Modifier.fillMaxWidth(),
                                variant = ButtonVariant.Tonal,
                                icon = R.drawable.ic_pg_car,
                                enabled = !autoLock.detection.isInFlight(),
                            )
                        }
                        if (settings.isConfigured || settings.demoMode) {
                            val alreadyLocked = vehicleStatus.status?.lockState == LockState.LOCKED
                            val alreadyUnlocked = vehicleStatus.status?.lockState == LockState.UNLOCKED
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(PorcelainSpace.s2),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                PgButton(
                                    text = if (alreadyLocked) stringResource(R.string.action_already_locked) else stringResource(R.string.action_lock_now),
                                    onClick = { pendingCommand = true },
                                    modifier = Modifier.weight(1f),
                                    icon = R.drawable.ic_pg_lock,
                                    enabled = !alreadyLocked,
                                )
                                PgButton(
                                    text = if (alreadyUnlocked) stringResource(R.string.action_already_unlocked) else stringResource(R.string.action_unlock_now),
                                    onClick = { pendingCommand = false },
                                    modifier = Modifier.weight(1f),
                                    variant = ButtonVariant.Danger,
                                    icon = R.drawable.ic_pg_unlock,
                                    enabled = !alreadyUnlocked,
                                )
                            }
                        }
                    }
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = PorcelainSpace.s2),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Eyebrow(stringResource(R.string.home_recent_activity))
                        if (log.isNotEmpty()) {
                            PgButton(
                                text = stringResource(R.string.action_copy),
                                onClick = { context.copyToClipboard("LEAVE/LOCK activity log", log.toClipboardText()) },
                                variant = ButtonVariant.Ghost,
                            )
                        }
                    }
                }
                item { ActivityLogCard(log) }
            }
        }
    }
}

@Composable
private fun LockCommandDialog(lock: Boolean, requirePin: Boolean, onDismiss: () -> Unit, onConfirm: (String?) -> Unit) {
    var pin by remember { mutableStateOf("") }
    val titleRes = if (lock) R.string.home_lock_dialog_title else R.string.home_unlock_dialog_title
    val pinBodyRes = if (lock) R.string.home_lock_dialog_pin else R.string.home_unlock_dialog_pin
    val plainBodyRes = if (lock) R.string.home_lock_dialog_plain else R.string.home_unlock_dialog_plain
    val actionRes = if (lock) R.string.action_lock_now else R.string.action_unlock_now
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = PorcelainShape.radiusSm,
        containerColor = Porcelain.colors.surfaceRaised,
        icon = { PgIcon(if (lock) R.drawable.ic_pg_lock else R.drawable.ic_pg_unlock, contentDescription = null, size = 24.dp) },
        title = { Text(stringResource(titleRes), style = PorcelainText.heading2) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(PorcelainSpace.s3)) {
                Text(
                    if (requirePin) stringResource(pinBodyRes) else stringResource(plainBodyRes),
                    style = PorcelainText.body,
                )
                if (requirePin) {
                    PgField(
                        label = stringResource(R.string.home_pin_label),
                        value = pin,
                        onValueChange = { if (it.length <= 8) pin = it.filter(Char::isDigit) },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    )
                }
            }
        },
        confirmButton = {
            PgButton(
                text = stringResource(actionRes),
                onClick = { onConfirm(pin.ifBlank { null }) },
                variant = if (lock) ButtonVariant.Primary else ButtonVariant.Danger,
                enabled = !requirePin || pin.length >= 4,
            )
        },
        dismissButton = {
            PgButton(text = stringResource(R.string.action_cancel), onClick = onDismiss, variant = ButtonVariant.Ghost)
        },
    )
}

@Composable
private fun ReauthBanner(onSignIn: () -> Unit) {
    PgCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PorcelainSpace.s3)) {
            PgIcon(R.drawable.ic_pg_lock, contentDescription = null, tint = Porcelain.colors.warn)
            Text(stringResource(R.string.home_reauth), style = PorcelainText.body, modifier = Modifier.weight(1f))
        }
        PgButton(text = stringResource(R.string.action_sign_in), onClick = onSignIn, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun LowVoltageBanner(percent: Int?) {
    PgCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PorcelainSpace.s3)) {
            PgIcon(R.drawable.ic_pg_battery, contentDescription = null, tint = Porcelain.colors.warn)
            Text(stringResource(R.string.home_low_volt_title), style = PorcelainText.heading3, color = Porcelain.colors.warn)
        }
        Text(
            if (percent != null) stringResource(R.string.home_low_volt_body, percent)
            else stringResource(R.string.home_low_volt_body_generic),
            style = PorcelainText.body,
            color = mutedText,
        )
    }
}

@Composable
private fun VehicleSwitcher(
    vehicles: List<io.github.gthibo.leavenlock.data.settings.KnownVehicle>,
    selectedId: String?,
    onSelect: (io.github.gthibo.leavenlock.data.settings.KnownVehicle) -> Unit,
) {
    PgCard(eyebrow = stringResource(R.string.home_vehicles), spacing = 0.dp) {
        vehicles.forEach { v ->
            PgRadio(label = v.nickname, selected = v.id == selectedId, onClick = { onSelect(v) })
        }
    }
}

// ── Hero status card ────────────────────────────────────────────────
@Composable
private fun StatusCard(
    enabled: Boolean,
    demoMode: Boolean,
    dryRun: Boolean,
    detection: DetectionState,
    graceRemaining: Int,
    graceTotal: Int,
    vehicleName: String?,
    onToggle: (Boolean) -> Unit,
    onCancel: () -> Unit,
) {
    // An evaluation is actively in flight vs. a settled outcome vs. resting.
    val active = detection.isInFlight()
    PgCard(
        modifier = Modifier.animateContentSize(),
        tone = CardTone.Hero,
        cut = 24.dp,
        eyebrow = stringResource(R.string.home_system_status),
        pixels = listOf("w.w", ".d.", "w.m"),
        pixelsAtBottom = true,
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (enabled) stringResource(R.string.home_watching) else stringResource(R.string.home_off),
                style = PorcelainText.status,
            )
            PgSwitch(
                checked = enabled,
                onCheckedChange = onToggle,
                contentDescription = stringResource(R.string.home_switch_cd),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PorcelainSpace.s2)) {
            PgIcon(R.drawable.ic_pg_car, contentDescription = null)
            Text(vehicleName ?: stringResource(R.string.home_no_vehicle), style = PorcelainText.heading3)
        }

        AnimatedContent(targetState = detection, label = "detection") { state ->
            val label = when (state) {
                DetectionState.IDLE -> if (enabled) stringResource(R.string.home_state_idle) else stringResource(R.string.home_state_disabled)
                DetectionState.ARMED -> stringResource(R.string.home_state_armed)
                DetectionState.CONFIRMING -> stringResource(R.string.home_state_confirming)
                DetectionState.GRACE -> stringResource(R.string.home_state_grace, graceRemaining)
                DetectionState.VERIFYING -> stringResource(R.string.home_state_verifying)
                DetectionState.AWAITING_CONFIRM -> stringResource(R.string.home_state_awaiting)
                DetectionState.LOCKING -> stringResource(R.string.home_state_locking)
                DetectionState.LOCKED -> stringResource(R.string.home_state_locked)
                DetectionState.SKIPPED -> stringResource(R.string.home_state_skipped)
                DetectionState.ABORTED -> stringResource(R.string.home_state_aborted)
                DetectionState.ERROR -> stringResource(R.string.home_state_error)
                DetectionState.SENT_UNVERIFIED -> stringResource(R.string.home_state_unverified)
                DetectionState.NOT_LOCKED_OPEN -> stringResource(R.string.home_state_not_locked_open)
            }
            Text(label, style = PorcelainText.body, color = mutedText)
        }

        // The meter means something: an amber countdown during the grace period, amber while a
        // check or lock is in flight, and a full teal lamp while watching.
        when {
            detection == DetectionState.GRACE && graceTotal > 0 -> SegmentMeter(
                value = (graceRemaining * 100) / graceTotal,
                contentDescription = stringResource(R.string.home_state_grace, graceRemaining),
                segments = 16,
                signal = true,
            )
            active -> SegmentMeter(value = 100, contentDescription = stringResource(R.string.home_meter_busy), segments = 16, signal = true)
            enabled -> SegmentMeter(value = 100, contentDescription = stringResource(R.string.home_watching), segments = 16)
        }

        // Stop a countdown or lock that's running; nothing to cancel otherwise.
        if (active) {
            PgButton(
                text = stringResource(R.string.action_cancel),
                onClick = onCancel,
                modifier = Modifier.fillMaxWidth(),
                variant = ButtonVariant.Secondary,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(PorcelainSpace.s2)) {
            if (demoMode) PgLabel(stringResource(R.string.badge_demo))
            PgLabel(
                if (dryRun) stringResource(R.string.badge_dry_run) else stringResource(R.string.badge_armed),
                tone = if (dryRun) LabelTone.Teal else LabelTone.Live,
            )
        }
    }
}

// ── Vehicle status card ─────────────────────────────────────────────
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VehicleStatusCard(
    ui: VehicleStatusUi,
    parkedLabel: String?,
    parkedLat: Double?,
    parkedLng: Double?,
    onRefresh: () -> Unit,
) {
    val status = ui.status
    val lockState = status?.lockState ?: LockState.UNKNOWN
    val now = rememberNow()
    val context = LocalContext.current

    PgCard(
        modifier = Modifier.animateContentSize(),
        eyebrow = stringResource(R.string.home_vehicle),
        meta = {
            PgIconButton(
                R.drawable.ic_pg_refresh,
                if (ui.loading) stringResource(R.string.home_refreshing_cd) else stringResource(R.string.home_refresh_status),
                onRefresh,
                enabled = !ui.loading,
                size = 22.dp,
                modifier = Modifier.cornerBleed(),
            )
        },
    ) {
        AnimatedContent(targetState = lockState, label = "lock") { state ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PorcelainSpace.s4)) {
                IconWell(if (state == LockState.LOCKED) R.drawable.ic_pg_lock else R.drawable.ic_pg_unlock)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PorcelainSpace.s2)) {
                        Text(
                            when (state) {
                                LockState.LOCKED -> stringResource(R.string.lock_locked)
                                LockState.UNLOCKED -> stringResource(R.string.lock_unlocked)
                                LockState.UNKNOWN -> stringResource(R.string.lock_unknown)
                            },
                            style = PorcelainText.heading1.copy(fontWeight = FontWeight.W600),
                        )
                        // Unlocked is an attention state: one amber lamp pixel, never colour alone.
                        if (state == LockState.UNLOCKED) Box(Modifier.size(PorcelainSpace.pixel).background(Porcelain.colors.signal))
                    }
                    Text(
                        text = when {
                            ui.loading -> stringResource(R.string.home_refreshing)

                            status != null -> ui.statusAsOfEpochMs?.let {
                                stringResource(R.string.home_updated, relativeTime(it, now, context))
                            } ?: stringResource(R.string.home_updated_unknown)

                            else -> stringResource(R.string.home_not_checked)
                        },
                        style = PorcelainText.bodySm,
                        color = mutedText,
                    )
                }
            }
        }

        ui.error?.let {
            Text(it, color = Porcelain.colors.warn, style = PorcelainText.body)
        }

        if (status != null) {
            status.evBatteryPercent?.let { pct ->
                BatteryMeter(percent = pct)
            }
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(PorcelainSpace.s2),
                verticalArrangement = Arrangement.spacedBy(PorcelainSpace.s2),
            ) {
                status.rangeMiles?.let {
                    AnimatedStatTile(R.drawable.ic_pg_route, stringResource(R.string.home_range_value, it), stringResource(R.string.home_range))
                }
                status.twelveVoltPercent?.let {
                    AnimatedStatTile(R.drawable.ic_pg_battery, "$it%", stringResource(R.string.home_twelve_volt))
                }
                AnimatedStatTile(
                    icon = R.drawable.ic_pg_bolt,
                    value = if (status.engineRunning) stringResource(R.string.state_on) else stringResource(R.string.state_off),
                    label = stringResource(R.string.home_engine),
                )
            }
            status.anyDoorOpen?.takeIf { it }?.let {
                Text(stringResource(R.string.home_door_open), color = Porcelain.colors.warn, style = PorcelainText.body)
            }
        } else if (ui.loading) {
            // First-ever load with nothing cached.
            PgProgress()
        }

        parkedLabel?.let {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = PorcelainSpace.touchTarget)
                    .clickable { openParkedInMaps(context, parkedLat, parkedLng, it) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(PorcelainSpace.s2),
            ) {
                PgIcon(R.drawable.ic_pg_pin, contentDescription = null, size = 18.dp)
                Text(stringResource(R.string.home_parked_near, it), style = PorcelainText.body, modifier = Modifier.weight(1f))
                PgIcon(R.drawable.ic_pg_arrow_right, contentDescription = stringResource(R.string.cd_open_maps), size = 18.dp, tint = mutedText)
            }
        }
    }
}

@Composable
private fun BatteryMeter(percent: Int) {
    val animatedPct by animateIntAsState(targetValue = percent, animationSpec = tween(200), label = "pct")
    Column(verticalArrangement = Arrangement.spacedBy(PorcelainSpace.s2)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PorcelainSpace.s2)) {
                PgIcon(R.drawable.ic_pg_battery, contentDescription = null, size = 18.dp)
                Text(stringResource(R.string.home_drive_battery), style = PorcelainText.body)
            }
            Text("$animatedPct%", style = PorcelainText.data.copy(fontSize = 17.sp, lineHeight = 20.sp, fontWeight = FontWeight.W500))
        }
        SegmentMeter(value = percent, contentDescription = stringResource(R.string.home_battery_cd, percent))
    }
}

@Composable
private fun AnimatedStatTile(icon: Int, value: String, label: String) {
    AnimatedContent(
        targetState = value,
        transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(200)) },
        label = "tileValue",
    ) { v ->
        StatTile(icon = icon, value = v, label = label)
    }
}

// ── Activity log ────────────────────────────────────────────────────
@Composable
private fun ActivityLogCard(log: List<LogEntry>) {
    PgCard(contentPadding = PaddingValues(horizontal = PorcelainSpace.s5, vertical = PorcelainSpace.s2), spacing = 0.dp) {
        if (log.isEmpty()) {
            Text(
                stringResource(R.string.home_no_activity),
                style = PorcelainText.body,
                color = mutedText,
                modifier = Modifier.padding(vertical = PorcelainSpace.s3),
            )
        } else {
            log.forEachIndexed { i, entry ->
                if (i > 0) Hairline()
                LogRow(entry)
            }
        }
    }
}

@Composable
private fun LogRow(entry: LogEntry, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val time = remember(entry.timestamp) { TimeFormat.clock(context, entry.timestamp, seconds = true) }
    val c = Porcelain.colors
    val color = when (entry.level) {
        LogLevel.SUCCESS -> c.teal
        LogLevel.WARN -> c.signal
        LogLevel.ERROR -> c.danger
        LogLevel.INFO -> c.lineStrong
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = PorcelainSpace.s2),
        horizontalArrangement = Arrangement.spacedBy(PorcelainSpace.s3),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            Modifier
                .padding(top = 7.dp)
                .size(6.dp)
                .background(color),
        )
        Text(time, style = PorcelainText.data, color = mutedText)
        Text(entry.message, style = PorcelainText.body, modifier = Modifier.weight(1f))
    }
}

/** A check or lock is running (as opposed to a settled outcome or resting). */
private fun DetectionState.isInFlight(): Boolean =
    this == DetectionState.ARMED ||
        this == DetectionState.CONFIRMING ||
        this == DetectionState.GRACE ||
        this == DetectionState.VERIFYING ||
        this == DetectionState.AWAITING_CONFIRM ||
        this == DetectionState.LOCKING

private fun relativeTime(epochMs: Long, now: Long, context: android.content.Context): String {
    val diff = now - epochMs
    return when {
        diff < 45_000 -> context.getString(R.string.time_just_now)
        diff < 3_600_000 -> context.getString(R.string.time_min_ago, (diff / 60_000).coerceAtLeast(1).toInt())
        diff < 86_400_000 -> TimeFormat.clock(context, epochMs)
        else -> TimeFormat.dateAndClock(context, epochMs)
    }
}

/** A recomposing clock that ticks every [intervalMs] so relative timestamps stay fresh. */
@Composable
private fun rememberNow(intervalMs: Long = 15_000L): Long {
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(intervalMs)
            now = System.currentTimeMillis()
        }
    }
    return now
}

/** Opens the parked spot in Google Maps (by coordinates when known, else a search on the label). */
private fun openParkedInMaps(context: Context, lat: Double?, lng: Double?, label: String?) {
    val query = label?.let { Uri.encode(it) } ?: "Parked+car"
    val geoUri = if (lat != null && lng != null) {
        Uri.parse("geo:$lat,$lng?q=$lat,$lng($query)")
    } else if (label != null) {
        Uri.parse("geo:0,0?q=${Uri.encode(label)}")
    } else return
    val webUri = if (lat != null && lng != null) {
        Uri.parse("https://www.google.com/maps/search/?api=1&query=$lat,$lng")
    } else {
        Uri.parse("https://www.google.com/maps/search/?api=1&query=$query")
    }
    try {
        // Prefer the Google Maps app, then any maps app, then the browser.
        context.startActivity(
            Intent(Intent.ACTION_VIEW, geoUri).setPackage("com.google.android.apps.maps"),
        )
    } catch (_: ActivityNotFoundException) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, geoUri))
        } catch (_: ActivityNotFoundException) {
            context.startActivity(Intent(Intent.ACTION_VIEW, webUri))
        }
    }
}

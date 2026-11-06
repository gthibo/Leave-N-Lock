package io.github.gthibo.leavenlock.ui.settings

import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.gthibo.leavenlock.R
import io.github.gthibo.leavenlock.data.settings.NotificationField
import io.github.gthibo.leavenlock.data.settings.RunMode
import io.github.gthibo.leavenlock.data.settings.ThemeMode
import io.github.gthibo.leavenlock.ui.components.ButtonVariant
import io.github.gthibo.leavenlock.ui.components.CardTone
import io.github.gthibo.leavenlock.ui.components.IconWell
import io.github.gthibo.leavenlock.ui.components.PgButton
import io.github.gthibo.leavenlock.ui.components.PgCard
import io.github.gthibo.leavenlock.ui.components.PgChoiceRow
import io.github.gthibo.leavenlock.ui.components.PgProgress
import io.github.gthibo.leavenlock.ui.components.PgRadio
import io.github.gthibo.leavenlock.ui.components.PgSlider
import io.github.gthibo.leavenlock.ui.components.PgTopBar
import io.github.gthibo.leavenlock.ui.components.SwitchRow
import io.github.gthibo.leavenlock.ui.components.mutedText
import io.github.gthibo.leavenlock.ui.theme.porcelain.Porcelain
import io.github.gthibo.leavenlock.ui.theme.porcelain.PorcelainSpace
import io.github.gthibo.leavenlock.ui.theme.porcelain.PorcelainText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onLogin: () -> Unit,
    onStats: () -> Unit,
    onHelp: () -> Unit,
    onAbout: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val extras by viewModel.extras.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Ringtone/sound picker for the custom lock sound.
    val soundPicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val uri = result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            viewModel.setCustomLockSoundUri(uri?.toString())
        }
    }

    // Backup: pick a destination file (export) / a file to restore.
    val exportPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let(viewModel::exportToUri)
    }
    val restorePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::restoreFromUri)
    }
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    LaunchedEffect(notice) {
        notice?.let {
            android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_LONG).show()
            viewModel.clearNotice()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Porcelain.colors.surface,
        topBar = { PgTopBar(title = stringResource(R.string.settings_title), onBack = onBack) },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(start = PorcelainSpace.screenMargin, end = PorcelainSpace.screenMargin, top = PorcelainSpace.s1, bottom = PorcelainSpace.s6),
            verticalArrangement = Arrangement.spacedBy(PorcelainSpace.s4),
        ) {
            PgCard(
                tone = CardTone.Hero,
                cut = 24.dp,
                eyebrow = stringResource(R.string.settings_hero_eyebrow),
                pixels = listOf("w.w", "ddw", "w.m"),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PorcelainSpace.s4)) {
                    IconWell(R.drawable.ic_pg_sliders, size = 48.dp, iconSize = 24.dp)
                    Text(stringResource(R.string.settings_title), style = PorcelainText.status.copy(fontSize = 28.sp, lineHeight = 34.sp))
                }
                Text(stringResource(R.string.settings_hero_subtitle), style = PorcelainText.body, color = mutedText)
            }

            // Safety / run mode.
            Section(stringResource(R.string.sec_safety)) {
                RowToggle(
                    title = stringResource(R.string.set_demo_title),
                    subtitle = stringResource(R.string.set_demo_sub),
                    checked = settings.demoMode,
                    onCheckedChange = viewModel::setDemoMode,
                    first = true,
                )
                GroupTitle(stringResource(R.string.set_locking_behaviour))
                PgRadio(
                    label = stringResource(R.string.set_dry_run),
                    hint = stringResource(R.string.set_dry_run_hint),
                    selected = settings.runMode == RunMode.DRY_RUN,
                    onClick = { viewModel.setRunMode(RunMode.DRY_RUN) },
                )
                PgRadio(
                    label = stringResource(R.string.set_armed),
                    hint = stringResource(R.string.set_armed_hint),
                    selected = settings.runMode == RunMode.ARMED,
                    onClick = { viewModel.setRunMode(RunMode.ARMED) },
                )
                RowToggle(
                    title = stringResource(R.string.set_confirm_title),
                    subtitle = stringResource(R.string.set_confirm_sub),
                    checked = settings.requireConfirmationBeforeLock,
                    onCheckedChange = viewModel::setRequireConfirmation,
                )
            }

            // Account.
            Section(stringResource(R.string.sec_account)) {
                Text(settings.accountEmail ?: stringResource(R.string.set_not_signed_in), style = PorcelainText.body)
                // Tie the buttons to the same signal as the email above so they never disagree.
                val signedIn = extras.signedIn || settings.accountEmail != null
                if (signedIn) {
                    Row(horizontalArrangement = Arrangement.spacedBy(PorcelainSpace.s2)) {
                        PgButton(stringResource(R.string.set_sign_out), viewModel::signOut, Modifier.weight(1f), ButtonVariant.Secondary)
                        PgButton(stringResource(R.string.set_reload_vehicles), viewModel::loadVehicles, Modifier.weight(1f), ButtonVariant.Tonal)
                    }
                    PgButton(
                        stringResource(R.string.set_check_creds),
                        viewModel::checkCredentials,
                        Modifier.fillMaxWidth(),
                        ButtonVariant.Tonal,
                        icon = R.drawable.ic_pg_refresh,
                    )
                } else {
                    PgButton(stringResource(R.string.action_sign_in), onLogin, Modifier.fillMaxWidth())
                }
            }

            // Vehicle.
            Section(stringResource(R.string.sec_vehicle)) {
                if (extras.loadingVehicles) {
                    PgProgress()
                } else if (extras.vehicles.isEmpty()) {
                    Text(stringResource(R.string.set_no_vehicles), style = PorcelainText.body, color = mutedText)
                    PgButton(stringResource(R.string.set_load_vehicles), viewModel::loadVehicles, Modifier.fillMaxWidth(), ButtonVariant.Tonal, icon = R.drawable.ic_pg_car)
                } else {
                    extras.vehicles.forEach { v ->
                        SelectableRow(
                            title = v.nickname,
                            subtitle = v.model,
                            selected = settings.vehicleId == v.id,
                            onClick = { viewModel.selectVehicle(v) },
                        )
                    }
                }
            }

            // Bluetooth trigger.
            Section(stringResource(R.string.sec_car_bt)) {
                Text(
                    settings.carBluetoothName?.let { stringResource(R.string.set_car_bt_selected, it) } ?: stringResource(R.string.set_car_bt_pick),
                    style = PorcelainText.body,
                )
                PgButton(stringResource(R.string.set_refresh_devices), viewModel::refreshDevices, Modifier.fillMaxWidth(), ButtonVariant.Tonal, icon = R.drawable.ic_pg_bluetooth)
                extras.pairedDevices.forEach { d ->
                    SelectableRow(
                        title = d.name,
                        subtitle = d.mac,
                        selected = settings.carBluetoothMac == d.mac,
                        onClick = { viewModel.selectCarDevice(d) },
                    )
                }
            }

            // Detection.
            Section(stringResource(R.string.sec_detection)) {
                RowToggle(
                    stringResource(R.string.set_det_bt_title),
                    stringResource(R.string.set_det_bt_sub),
                    settings.useBluetoothTrigger,
                    viewModel::setUseBluetooth,
                    first = true,
                )
                RowToggle(
                    stringResource(R.string.set_det_activity_title),
                    stringResource(R.string.set_det_activity_sub),
                    settings.useActivityRecognition,
                    viewModel::setUseActivity,
                )
                RowToggle(
                    stringResource(R.string.set_det_geo_title),
                    stringResource(R.string.set_det_geo_sub),
                    settings.useGeofence,
                    viewModel::setUseGeofence,
                )
                if (settings.useGeofence) {
                    PgSlider(
                        label = stringResource(R.string.set_geo_radius, settings.geofenceRadiusMeters),
                        value = settings.geofenceRadiusMeters.toFloat(),
                        onValueChange = { viewModel.setGeofenceRadius(it.toInt()) },
                        valueRange = 10f..100f,
                    )
                }
                RowToggle(
                    stringResource(R.string.set_require_walkaway_title),
                    stringResource(R.string.set_require_walkaway_sub),
                    settings.requireWalkAwayConfirmation,
                    viewModel::setRequireWalkAway,
                )
            }

            // Lock reliability.
            Section(stringResource(R.string.sec_reliability)) {
                RowToggle(
                    stringResource(R.string.set_verify_lock_title),
                    stringResource(R.string.set_verify_lock_sub),
                    settings.verifyLock,
                    viewModel::setVerifyLock,
                    first = true,
                )
                RowToggle(
                    stringResource(R.string.set_dont_lock_open_title),
                    stringResource(R.string.set_dont_lock_open_sub),
                    settings.dontLockIfOpen,
                    viewModel::setDontLockIfOpen,
                )
                RowToggle(
                    stringResource(R.string.set_departure_title),
                    stringResource(R.string.set_departure_sub),
                    settings.departureSummary,
                    viewModel::setDepartureSummary,
                )
                PgSlider(
                    label = if (settings.retryWindowMinutes == 0) {
                        stringResource(R.string.set_retry_window_off)
                    } else {
                        stringResource(R.string.set_retry_window_value, settings.retryWindowMinutes)
                    },
                    description = stringResource(R.string.set_retry_window_sub),
                    value = settings.retryWindowMinutes.toFloat(),
                    onValueChange = { viewModel.setRetryWindowMinutes(it.toInt()) },
                    valueRange = 0f..15f,
                    steps = 14,
                )
            }

            // Timing.
            Section(stringResource(R.string.sec_timing)) {
                PgSlider(
                    label = stringResource(R.string.set_grace, settings.graceSeconds),
                    value = settings.graceSeconds.toFloat(),
                    onValueChange = { viewModel.setGrace(it.toInt()) },
                    valueRange = 5f..180f,
                )
            }

            // Low 12V battery warning.
            Section(stringResource(R.string.sec_low_volt)) {
                RowToggle(
                    stringResource(R.string.set_low_volt_title),
                    stringResource(R.string.set_low_volt_sub),
                    settings.lowVoltageAlert,
                    viewModel::setLowVoltageAlert,
                    first = true,
                )
                if (settings.lowVoltageAlert) {
                    PgSlider(
                        label = stringResource(R.string.set_low_volt_threshold, settings.lowVoltageThreshold),
                        value = settings.lowVoltageThreshold.toFloat(),
                        onValueChange = { viewModel.setLowVoltageThreshold(it.toInt()) },
                        valueRange = 10f..90f,
                    )
                }
            }

            // Behaviour.
            Section(stringResource(R.string.sec_behaviour)) {
                RowToggle(
                    stringResource(R.string.set_auto_refresh_open_title),
                    stringResource(R.string.set_auto_refresh_open_sub),
                    settings.autoRefreshOnOpen,
                    viewModel::setAutoRefreshOnOpen,
                    first = true,
                )
                GroupTitle(stringResource(R.string.set_bg_check_title), stringResource(R.string.set_bg_check_sub))
                PgChoiceRow(
                    options = listOf(
                        0 to stringResource(R.string.set_off),
                        15 to stringResource(R.string.set_15m),
                        30 to stringResource(R.string.set_30m),
                        60 to stringResource(R.string.set_60m),
                    ),
                    selected = settings.autoRefreshIntervalMinutes,
                    onSelect = viewModel::setAutoRefreshInterval,
                )
                RowToggle(
                    stringResource(R.string.set_vibrate_title),
                    stringResource(R.string.set_vibrate_sub),
                    settings.hapticOnLock,
                    viewModel::setHapticOnLock,
                )
                RowToggle(
                    stringResource(R.string.set_sound_title),
                    stringResource(R.string.set_sound_sub),
                    settings.soundOnLock,
                    viewModel::setSoundOnLock,
                )
                if (settings.soundOnLock) {
                    val soundLabel = remember(settings.customLockSoundUri) {
                        settings.customLockSoundUri?.let { uriStr ->
                            runCatching {
                                RingtoneManager.getRingtone(context, Uri.parse(uriStr))?.getTitle(context)
                            }.getOrNull()
                        }
                    }
                    Text(
                        stringResource(R.string.set_sound_current, soundLabel ?: stringResource(R.string.set_sound_default)),
                        style = PorcelainText.bodySm,
                        color = mutedText,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(PorcelainSpace.s2)) {
                        PgButton(stringResource(R.string.set_sound_choose), modifier = Modifier.weight(1f), variant = ButtonVariant.Tonal, onClick = {
                            val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                                putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALL)
                                putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, context.getString(R.string.set_sound_pick_title))
                                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, false)
                                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                                settings.customLockSoundUri?.let {
                                    putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(it))
                                }
                            }
                            soundPicker.launch(intent)
                        })
                        if (settings.customLockSoundUri != null) {
                            PgButton(stringResource(R.string.set_sound_use_default), { viewModel.setCustomLockSoundUri(null) }, Modifier.weight(1f), ButtonVariant.Secondary)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(PorcelainSpace.s2)) {
                        PgButton(stringResource(R.string.set_sound_test), { viewModel.testLockSound() }, Modifier.weight(1f), ButtonVariant.Tonal)
                        PgButton(stringResource(R.string.set_sound_play_default), { viewModel.playDefaultSound() }, Modifier.weight(1f), ButtonVariant.Secondary)
                    }
                }
                RowToggle(
                    stringResource(R.string.set_remember_title),
                    stringResource(R.string.set_remember_sub),
                    settings.rememberParkedLocation,
                    viewModel::setRememberParkedLocation,
                )
            }

            // Active-hours schedule.
            Section(stringResource(R.string.sec_schedule)) {
                RowToggle(
                    stringResource(R.string.schedule_enabled_title),
                    stringResource(R.string.schedule_enabled_sub),
                    settings.scheduleEnabled,
                    viewModel::setScheduleEnabled,
                    first = true,
                )
                if (settings.scheduleEnabled) {
                    PgSlider(
                        label = stringResource(R.string.schedule_from, formatMinutes(settings.scheduleStartMinutes)),
                        value = settings.scheduleStartMinutes.toFloat(),
                        onValueChange = { viewModel.setScheduleStart((it / 15).toInt() * 15) },
                        valueRange = 0f..1425f,
                    )
                    PgSlider(
                        label = stringResource(R.string.schedule_until, formatMinutes(settings.scheduleEndMinutes)),
                        value = settings.scheduleEndMinutes.toFloat(),
                        onValueChange = { viewModel.setScheduleEnd((it / 15).toInt() * 15) },
                        valueRange = 0f..1425f,
                    )
                }
            }

            // Notification.
            Section(stringResource(R.string.sec_notification)) {
                RowToggle(
                    title = stringResource(R.string.set_pin_title),
                    subtitle = stringResource(R.string.set_pin_sub),
                    checked = settings.pinNotification,
                    onCheckedChange = viewModel::setPinNotification,
                    first = true,
                )
                RowToggle(
                    title = stringResource(R.string.set_badge_title),
                    subtitle = stringResource(R.string.set_badge_sub),
                    checked = settings.showAppBadge,
                    onCheckedChange = viewModel::setShowAppBadge,
                )
                RowToggle(
                    title = stringResource(R.string.set_notif_icon_title),
                    subtitle = stringResource(R.string.set_notif_icon_sub),
                    checked = settings.showNotificationIcon,
                    onCheckedChange = viewModel::setShowNotificationIcon,
                )
                RowToggle(
                    title = stringResource(R.string.set_locknow_title),
                    subtitle = stringResource(R.string.set_locknow_sub),
                    checked = settings.showLockNowAction,
                    onCheckedChange = viewModel::setShowLockNowAction,
                )
                RowToggle(
                    title = stringResource(R.string.set_showstatus_title),
                    subtitle = stringResource(R.string.set_showstatus_sub),
                    checked = settings.showStatusInNotification,
                    onCheckedChange = viewModel::setShowStatusInNotification,
                )
                if (settings.showStatusInNotification) {
                    GroupTitle(stringResource(R.string.set_show_details))
                    NotificationField.entries.forEach { field ->
                        SwitchRow(
                            title = stringResource(field.labelRes),
                            description = null,
                            checked = field in settings.notificationFields,
                            onCheckedChange = { viewModel.toggleNotificationField(field, it) },
                        )
                    }
                }
            }

            // Appearance.
            Section(stringResource(R.string.sec_appearance)) {
                GroupTitle(stringResource(R.string.set_theme))
                ThemeMode.entries.forEach { mode ->
                    PgRadio(
                        label = stringResource(
                            when (mode) {
                                ThemeMode.SYSTEM -> R.string.set_theme_system
                                ThemeMode.LIGHT -> R.string.set_theme_light
                                ThemeMode.DARK -> R.string.set_theme_dark
                            },
                        ),
                        selected = settings.themeMode == mode,
                        onClick = { viewModel.setThemeMode(mode) },
                    )
                }
            }

            // Keep running.
            Section(stringResource(R.string.sec_keep_running)) {
                Text(stringResource(R.string.set_keep_intro), style = PorcelainText.body)
                Text(stringResource(R.string.set_keep_tips), style = PorcelainText.bodySm, color = mutedText)
                Row(horizontalArrangement = Arrangement.spacedBy(PorcelainSpace.s2)) {
                    PgButton(stringResource(R.string.set_battery_settings), { openBatterySettings(context) }, Modifier.weight(1f), ButtonVariant.Tonal)
                    PgButton(stringResource(R.string.set_app_info), { openAppInfo(context) }, Modifier.weight(1f), ButtonVariant.Secondary)
                }
            }

            // Diagnostics.
            Section(stringResource(R.string.sec_diagnostics)) {
                Text(stringResource(R.string.set_diag_intro), style = PorcelainText.body)
                PgButton(stringResource(R.string.set_open_stats), onStats, Modifier.fillMaxWidth(), ButtonVariant.Tonal)
            }

            // Help.
            Section(stringResource(R.string.sec_help)) {
                Text(stringResource(R.string.set_help_intro), style = PorcelainText.body)
                PgButton(stringResource(R.string.set_open_help), onHelp, Modifier.fillMaxWidth(), ButtonVariant.Secondary)
            }

            // About.
            Section(stringResource(R.string.sec_about)) {
                Text(stringResource(R.string.set_about_intro), style = PorcelainText.body)
                PgButton(stringResource(R.string.set_open_about), onAbout, Modifier.fillMaxWidth(), ButtonVariant.Secondary)
            }

            // Backup & restore.
            Section(stringResource(R.string.sec_backup)) {
                Text(stringResource(R.string.set_backup_intro), style = PorcelainText.body)
                PgButton(stringResource(R.string.set_backup_export_app), viewModel::exportToAppFolder, Modifier.fillMaxWidth(), ButtonVariant.Tonal)
                PgButton(
                    stringResource(R.string.set_backup_export_custom),
                    { exportPicker.launch(viewModel.suggestedBackupFileName()) },
                    Modifier.fillMaxWidth(),
                    ButtonVariant.Secondary,
                )
                PgButton(
                    stringResource(R.string.set_backup_restore),
                    { restorePicker.launch(arrayOf("application/json", "application/octet-stream", "text/*")) },
                    Modifier.fillMaxWidth(),
                    ButtonVariant.Secondary,
                )
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    PgCard(title = title, spacing = PorcelainSpace.s2) { content() }
}

/** A bold sub-heading inside a section, with an optional muted line under it. */
@Composable
private fun GroupTitle(title: String, subtitle: String? = null) {
    Column(Modifier.fillMaxWidth().padding(top = PorcelainSpace.s2)) {
        Text(title, style = PorcelainText.bodyStrong)
        subtitle?.let { Text(it, style = PorcelainText.body.copy(fontSize = 14.sp, lineHeight = 20.sp), color = mutedText) }
    }
}

private fun formatMinutes(minutes: Int): String = "%02d:%02d".format(minutes / 60, minutes % 60)

/** Opens the system battery-optimization list so the user can exempt LEAVE/LOCK. */
private fun openBatterySettings(context: Context) {
    val opened = runCatching {
        context.startActivity(
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }.isSuccess
    if (!opened) openAppInfo(context)
}

/** Opens this app's system settings page (battery, notifications, permissions). */
private fun openAppInfo(context: Context) {
    runCatching {
        context.startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", context.packageName, null),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

@Composable
private fun RowToggle(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    first: Boolean = false,
) {
    SwitchRow(title, subtitle, checked, onCheckedChange, divider = !first)
}

@Composable
private fun SelectableRow(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    PgRadio(label = title, hint = subtitle, selected = selected, onClick = onClick)
}

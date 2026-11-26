package io.github.gthibo.leavenlock.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.gthibo.leavenlock.data.bluelink.BlueLinkProvider
import io.github.gthibo.leavenlock.data.bluelink.model.CommandResult
import io.github.gthibo.leavenlock.data.bluelink.model.LockState
import io.github.gthibo.leavenlock.data.bluelink.model.VehicleStatus
import io.github.gthibo.leavenlock.data.bluelink.model.mergedOnto
import io.github.gthibo.leavenlock.data.settings.AppSettings
import io.github.gthibo.leavenlock.data.settings.SettingsRepository
import io.github.gthibo.leavenlock.data.status.StatusCache
import io.github.gthibo.leavenlock.domain.ActivityLog
import io.github.gthibo.leavenlock.domain.AutoLockController
import io.github.gthibo.leavenlock.domain.AutoLockUiState
import io.github.gthibo.leavenlock.domain.LogEntry
import io.github.gthibo.leavenlock.domain.ManualCommand
import io.github.gthibo.leavenlock.domain.ManualOutcome
import io.github.gthibo.leavenlock.domain.manualCommandLogLine
import io.github.gthibo.leavenlock.domain.StatusSummary
import io.github.gthibo.leavenlock.service.AutoLockService
import io.github.gthibo.leavenlock.work.StatusRefreshWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.content.Context
import javax.inject.Inject
import io.github.gthibo.leavenlock.data.settings.RunMode

/** UI state for the live vehicle status card. */
data class VehicleStatusUi(
    val loading: Boolean = false,
    val status: VehicleStatus? = null,
    /** The card's single "Updated …" time (see [io.github.gthibo.leavenlock.data.status.CachedStatus.statusAsOfEpochMs]). */
    val statusAsOfEpochMs: Long? = null,
    val error: String? = null,
    /** True when the session expired and the user needs to sign in again. */
    val needsReauth: Boolean = false,
    /** True when the 12V battery is below the user's warning threshold. */
    val lowVoltage: Boolean = false,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val settingsRepo: SettingsRepository,
    private val controller: AutoLockController,
    private val provider: BlueLinkProvider,
    private val statusCache: StatusCache,
    private val metrics: io.github.gthibo.leavenlock.data.metrics.ApiMetrics,
    private val secureStore: io.github.gthibo.leavenlock.data.secure.SecureStore,
    private val activityLog: ActivityLog,
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsRepo.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

    val autoLock: StateFlow<AutoLockUiState> = controller.state

    val log: StateFlow<List<LogEntry>> = activityLog.entries

    private val _vehicleStatus = MutableStateFlow(VehicleStatusUi())
    val vehicleStatus: StateFlow<VehicleStatusUi> = _vehicleStatus

    // Transient one-shot messages surfaced as a toast (rate-limit, low 12V, throttle…).
    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice
    fun clearNotice() { _notice.value = null }

    init {
        // The card always shows the shared status cache, so auto-lock, the background refresh and
        // manual commands all show up here the moment they are saved.
        viewModelScope.launch {
            statusCache.cached.collect { c ->
                _vehicleStatus.update { it.copy(status = c.toVehicleStatus(), statusAsOfEpochMs = c.statusAsOfEpochMs()) }
            }
        }
        // Pull a fresh snapshot on open (respects the auto-refresh-on-open setting).
        refreshStatus(force = false)
        // Service start + WorkManager scheduling do disk I/O — keep them off the main thread.
        viewModelScope.launch(Dispatchers.IO) {
            val s = settingsRepo.settings.first()
            if (s.enabled) AutoLockService.startWatching(appContext)
            // (Re)schedule the periodic background refresh to match the setting.
            StatusRefreshWorker.schedule(appContext, s.autoRefreshIntervalMinutes)
        }
    }

    fun setEnabled(enabled: Boolean) = viewModelScope.launch {
        settingsRepo.update { it.copy(enabled = enabled) }
        if (enabled) AutoLockService.startWatching(appContext) else AutoLockService.stopWatching(appContext)
    }

    fun runNow() {
        // Drive the whole flow through the foreground service so the user gets the live
        // notification plus haptic/sound feedback, exactly like a real Bluetooth trigger.
        AutoLockService.start(appContext)
    }

    fun cancel() = controller.cancel()

    /** Whether a BlueLink PIN is stored, so the UI knows to ask for it before a manual lock. */
    fun hasPin(): Boolean = secureStore.loadPin() != null

    private val _lockResult = MutableStateFlow<String?>(null)
    val lockResult: StateFlow<String?> = _lockResult
    fun clearLockResult() { _lockResult.value = null }

    /** Manually lock now — gated by the BlueLink PIN when one is stored. */
    fun manualLock(pin: String?) = runManual(ManualCommand.LOCK, pin)

    /** Manually unlock now — gated by the BlueLink PIN when one is stored. */
    fun manualUnlock(pin: String?) = runManual(ManualCommand.UNLOCK, pin)

    /**
     * Runs a lock/unlock pressed on Home. The on-screen result goes to [lockResult]; every outcome
     * (sent or refused) also gets one Activity-log line via [manualCommandLogLine]. No sound:
     * the user is looking at the screen, and sounds stay reserved for auto-lock.
     */
    private fun runManual(command: ManualCommand, pin: String?) = viewModelScope.launch {
        val s = settingsRepo.settings.first()
        fun finish(outcome: ManualOutcome, onScreen: String) {
            _lockResult.value = onScreen
            val (level, line) = manualCommandLogLine(command, outcome)
            activityLog.add(level, line)
        }
        if (s.runMode != RunMode.ARMED) {
            finish(ManualOutcome.DryRun, "Dry run — no ${command.noun} command sent.")
            return@launch
        }
        val stored = secureStore.loadPin()
        if (stored != null && pin != stored) {
            finish(ManualOutcome.WrongPin, "Incorrect PIN.")
            return@launch
        }
        val vehicleId = s.vehicleId ?: run {
            finish(ManualOutcome.NoVehicle, "No vehicle selected.")
            return@launch
        }
        val client = provider.client(s)
        if (!s.demoMode && !client.ensureFreshSession()) {
            finish(ManualOutcome.SessionExpired, "Session expired — sign in again.")
            _vehicleStatus.update { it.copy(needsReauth = true) }
            return@launch
        }
        val result = when (command) {
            ManualCommand.LOCK -> client.lock(vehicleId)
            ManualCommand.UNLOCK -> client.unlock(vehicleId)
        }
        val sent = ManualOutcome.Sent(result)
        when (result) {
            is CommandResult.Success -> {
                finish(sent, "${command.pastTense} ✓")
                // Hyundai's cached snapshot lags the command by minutes; saving it as a command
                // keeps older reads from flipping the card back (see CachedStatus.after).
                saveCommandResult(s) {
                    when (command) {
                        ManualCommand.LOCK -> it.copy(lockState = LockState.LOCKED, anyDoorOpen = false)
                        ManualCommand.UNLOCK -> it.copy(lockState = LockState.UNLOCKED)
                    }
                }
            }
            CommandResult.RateLimited -> finish(sent, "Rate-limited — try again shortly.")
            CommandResult.NotAuthenticated -> {
                finish(sent, "Not signed in.")
                _vehicleStatus.update { it.copy(needsReauth = true) }
            }
            is CommandResult.Failure ->
                finish(sent, "${command.noun.replaceFirstChar { it.uppercase() }} failed: ${result.reason}")
        }
    }

    /** Save a finished lock/unlock to the shared cache (card, widget, notification). */
    private suspend fun saveCommandResult(s: AppSettings, change: (VehicleStatus) -> VehicleStatus) {
        val current = _vehicleStatus.value.status ?: return
        val next = change(current)
        statusCache.saveStatus(
            next,
            StatusSummary.build(next, s.notificationFields, StatusSummary.Labels.from(appContext)),
            fromCommand = true,
        )
    }

    /** Switch the active vehicle (per-vehicle picker on Home). */
    fun selectVehicle(v: io.github.gthibo.leavenlock.data.settings.KnownVehicle) = viewModelScope.launch {
        settingsRepo.update { it.copy(vehicleId = v.id, vehicleNickname = v.nickname) }
        statusCache.clear()
        refreshStatus(force = false)
    }

    fun refreshStatus(force: Boolean) = viewModelScope.launch {
        val s = settingsRepo.settings.first()
        if (s.vehicleId == null && !s.demoMode) {
            _vehicleStatus.update { it.copy(loading = false, error = "No vehicle selected.") }
            return@launch
        }
        if (!force && !s.autoRefreshOnOpen) return@launch
        // On open (not a manual/pull refresh), skip the network round-trip when the cached snapshot
        // is still fresh — the card already shows it, so opening the app repeatedly no longer spams
        // the API or stutters. A manual refresh (force=true) always fetches.
        if (force) {
            // Respect the API's rate-limit cooldown so we don't get further throttled.
            val snap = metrics.snapshot.value
            if (snap.isRateLimited()) {
                val secs = ((snap.rateLimitedUntilEpochMs!! - System.currentTimeMillis()) / 1000).coerceAtLeast(1)
                val msg = "Rate-limited — try again in ${secs}s."
                _vehicleStatus.update { it.copy(loading = false, error = msg) }
                _notice.value = msg
                return@launch
            }
        }
        val vehicleId = s.vehicleId ?: "demo-ioniq5"
        _vehicleStatus.update { it.copy(loading = true, error = null) }
        val client = provider.client(s)
        // All the Keystore/token/network work runs off the main thread so the UI never stutters.
        // null result = session expired (needs re-auth).
        val result: Result<VehicleStatus>? = withContext(Dispatchers.IO) {
            if (!s.demoMode && !client.ensureFreshSession()) null
            // Always Hyundai's cached snapshot: forceRefresh = true wakes the car (12V drain, rate
            // limit), so only the auto-lock verify step asks for a live read.
            else runCatching { client.status(vehicleId, forceRefresh = false) }
        }
        if (result == null) {
            _vehicleStatus.update { it.copy(loading = false, error = "Session expired — please sign in again.", needsReauth = true) }
            return@launch
        }
        result
            .onSuccess { fresh ->
                // Merge so a partial (force-refresh) response never wipes existing detail — and the
                // notification/widget summary is built from the merged status too.
                val merged = fresh
                val low = isLowVoltage(merged, s)
                // The card's status and time arrive through the cache collector in init.
                _vehicleStatus.update { ui ->
                    ui.copy(
                        loading = false,
                        error = null,
                        needsReauth = false,
                        lowVoltage = low,
                    )
                }
                if (low) merged.twelveVoltPercent?.let { _notice.value = "12V battery low ($it%). Consider charging or driving soon." }
                statusCache.saveStatus(merged, StatusSummary.build(merged, s.notificationFields, StatusSummary.Labels.from(appContext)))
            }
            .onFailure { e ->
                // Preserve the visible status; only report the error.
                _vehicleStatus.update { it.copy(loading = false, error = e.message ?: "Couldn't read status.") }
            }
    }

    private fun isLowVoltage(status: VehicleStatus, s: AppSettings): Boolean =
        s.lowVoltageAlert && (status.twelveVoltPercent?.let { it < s.lowVoltageThreshold } ?: false)

    private companion object {
        // Don't re-fetch on app open if the cached status is younger than this.
        const val ON_OPEN_FRESH_MS = 5 * 60_000L
    }
}

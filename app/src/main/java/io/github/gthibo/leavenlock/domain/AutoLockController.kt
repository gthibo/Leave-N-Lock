package io.github.gthibo.leavenlock.domain

import io.github.gthibo.leavenlock.data.bluelink.BlueLinkProvider
import io.github.gthibo.leavenlock.data.bluelink.model.CommandResult
import io.github.gthibo.leavenlock.data.settings.RunMode
import io.github.gthibo.leavenlock.data.settings.SettingsRepository
import io.github.gthibo.leavenlock.domain.detection.DetectionEvent
import io.github.gthibo.leavenlock.domain.detection.DetectionState
import io.github.gthibo.leavenlock.domain.detection.LockStateMachine
import io.github.gthibo.leavenlock.domain.usecase.LockDecision
import io.github.gthibo.leavenlock.domain.usecase.LockPolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/** Snapshot the UI observes. */
data class AutoLockUiState(
    val detection: DetectionState = DetectionState.IDLE,
    val graceRemaining: Int = 0,
    val lastLockAtEpochMs: Long? = null,
    val statusSummary: String? = null,
)

/**
 * Single source of truth for a lock attempt. Owns timers + side effects; delegates
 * transition logic to [LockStateMachine] and the lock/skip decision to [LockPolicy].
 *
 * Called from the Bluetooth receiver, activity-recognition callbacks, and the UI.
 */
@Singleton
class AutoLockController @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val appContext: android.content.Context,
    private val settingsRepo: SettingsRepository,
    private val provider: BlueLinkProvider,
    private val locationHelper: io.github.gthibo.leavenlock.data.location.LocationHelper,
    private val statusCache: io.github.gthibo.leavenlock.data.status.StatusCache,
    private val log: ActivityLog,
) {
    internal var scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    internal var clock: () -> Long = { System.currentTimeMillis() }
    private val mutex = Mutex()
    // Localised words for the notification status line.
    private val summaryLabels get() = StatusSummary.Labels.from(appContext)

    private val _state = MutableStateFlow(AutoLockUiState())
    val state: StateFlow<AutoLockUiState> = _state

    private var machine: LockStateMachine? = null
    private var runJob: Job? = null

    @Volatile private var skipGrace = false
    // Set when a walk-away signal (Activity Recognition or geofence) confirms you left the car.
    @Volatile private var walkAwayConfirmed = false

    /** Bluetooth (or manual) trigger: begin an evaluation. */
    fun onTriggerFired() {
        runJob?.cancel()
        runJob = scope.launch { runEvaluation() }
    }

    /** Skip the remaining grace period and lock immediately (notification "Lock now"). */
    fun lockNow() {
        skipGrace = true
        if (runJob?.isActive != true) onTriggerFired()
    }

    fun onWalkingConfirmed() = scope.launch {
        walkAwayConfirmed = true
        machine?.let { advance(it.next(_state.value.detection, DetectionEvent.WalkingConfirmed)) }
    }

    fun onMovedBeyondGeofence() = scope.launch {
        walkAwayConfirmed = true
        machine?.let { advance(it.next(_state.value.detection, DetectionEvent.MovedBeyondGeofence)) }
    }

    fun cancel() {
        runJob?.cancel()
        machine?.let { advance(it.next(_state.value.detection, DetectionEvent.UserCancelled)) }
        _state.value = _state.value.copy(detection = DetectionState.ABORTED, graceRemaining = 0)
    }

    /** Manual "run now" for testing from the UI. */
    fun runNow() = onTriggerFired()

    private suspend fun runEvaluation() = mutex.withLock {
        try {
        skipGrace = false
        walkAwayConfirmed = false
        val settings = settingsRepo.settings.first()
        if (settings.vehicleId == null) {
            log.add(LogLevel.WARN, "No vehicle selected; ignoring trigger.")
            return
        }
        // Respect the optional active-hours schedule.
        val nowMinutes = java.util.Calendar.getInstance().let {
            it.timeInMillis = clock()
            it.get(java.util.Calendar.HOUR_OF_DAY) * 60 + it.get(java.util.Calendar.MINUTE)
        }
        if (!settings.isWithinSchedule(nowMinutes)) {
            _state.value = _state.value.copy(detection = DetectionState.SKIPPED, graceRemaining = 0)
            log.add(LogLevel.INFO, "Outside active hours — not locking.")
            return
        }
        // Optionally remember where the car parked.
        if (settings.rememberParkedLocation) {
            locationHelper.currentParkedPlace()?.let { place ->
                settingsRepo.update { it.copy(parkedLabel = place.label, parkedLat = place.lat, parkedLng = place.lng) }
                place.label?.let { log.add(LogLevel.INFO, "Parked near $it.") }
            }
        }
        val sm = LockStateMachine(settings).also { machine = it }
        advance(sm.next(DetectionState.IDLE, DetectionEvent.CarBluetoothDisconnected))
        log.add(LogLevel.INFO, "Trigger fired — evaluating whether you left the car.")

        // Wait until confirmation signals promote us to GRACE (or abort/timeout).
        // Geofence confirmation: capture where the car is now, then watch for you walking beyond the
        // configured radius during the confirm window.
        val geoStart: android.location.Location? =
            if (settings.useGeofence) locationHelper.currentLocation() else null
        var lastGeoCheckMs = 0L
        val confirmDeadline = clock() + CONFIRM_TIMEOUT_MS
        while (_state.value.detection == DetectionState.CONFIRMING) {
            if (clock() > confirmDeadline) {
                // No corroboration in time. If the user requires a walk-away confirmation, don't
                // lock on the Bluetooth signal alone — skip. Otherwise proceed on BT.
                if (settings.requireWalkAwayConfirmation && !walkAwayConfirmed) {
                    _state.value = _state.value.copy(detection = DetectionState.SKIPPED, graceRemaining = 0)
                    log.add(LogLevel.INFO, "No walk-away confirmation within the window — not locking.")
                    return
                }
                advance(DetectionState.GRACE)
                break
            }
            if (geoStart != null && clock() - lastGeoCheckMs > GEO_POLL_MS) {
                lastGeoCheckMs = clock()
                val here = locationHelper.currentLocation()
                if (here != null && geoStart.distanceTo(here) >= settings.geofenceRadiusMeters) {
                    log.add(LogLevel.INFO, "Moved beyond the geofence — confirmed you left.")
                    walkAwayConfirmed = true
                    machine?.let { advance(it.next(_state.value.detection, DetectionEvent.MovedBeyondGeofence)) }
                }
            }
            delay(250)
        }
        if (_state.value.detection == DetectionState.ABORTED) {
            log.add(LogLevel.INFO, "Aborted before grace period.")
            return
        }

        // Grace countdown, cancellable if the user returns (job cancelled).
        for (remaining in settings.graceSeconds downTo 1) {
            if (skipGrace) {
                log.add(LogLevel.INFO, "Lock now requested — skipping the wait.")
                break
            }
            _state.value = _state.value.copy(detection = DetectionState.GRACE, graceRemaining = remaining)
            delay(1000)
        }
        advance(sm.next(DetectionState.GRACE, DetectionEvent.GraceElapsed))

        // Verify current lock state before acting.
        val client = provider.client(settings)
        if (!settings.demoMode && !client.ensureFreshSession()) {
            failAndNotify("Session expired — please sign in again.", NOTICE_SIGNED_OUT)
            return
        }
        // Read the CACHED status (carstatus/latest) — never wake the car for the pre-lock check.
        // This matches the evcc reference implementation and avoids hitting Hyundai's live-poll
        // rate limit ("temporary 503"). Manual refresh from Home remains a live poll.
        // A walk-away often coincides with the phone switching signal, so one hung request must not
        // leave the car unlocked: retry a few times with a per-try cap before giving up.
        val rawStatus = readStatusWithRetry(client, settings.vehicleId) ?: return
// Do not use the shared, unscoped cache for a lock decision.
            val status = rawStatus
        // Build the user-customisable status line for the notification.
        val summary = if (settings.showStatusInNotification) {
            StatusSummary.build(status, settings.notificationFields, summaryLabels).ifBlank { null }
        } else null
        _state.value = _state.value.copy(statusSummary = summary)
        statusCache.saveStatus(status, StatusSummary.build(status, settings.notificationFields, summaryLabels))

        when (val decision = LockPolicy.decide(status, dontLockIfOpen = settings.dontLockIfOpen, nowEpochMs = clock())) {
            is LockDecision.Skip -> {
                // A door/window skip means the car is NOT locked, so it gets its own state instead of
                // the generic "already secure" SKIPPED. Every other skip keeps SKIPPED.
                val blockedByOpen = decision.reason == "A door is open" ||
                    decision.reason == "A window is open"
                _state.value = _state.value.copy(
                    detection = if (blockedByOpen) DetectionState.NOT_LOCKED_OPEN else DetectionState.SKIPPED,
                    graceRemaining = 0,
                )
                log.add(LogLevel.INFO, "Skipped: ${decision.reason}.")
                maybePostDeparture(settings, status, locked = false, skipReason = decision.reason)
            }
            LockDecision.Lock -> {
                if (settings.requireConfirmationBeforeLock) {
                    _state.value = _state.value.copy(detection = DetectionState.AWAITING_CONFIRM)
                    log.add(LogLevel.INFO, "Waiting for you to confirm the lock…")
                    val deadline = clock() + CONFIRM_LOCK_TIMEOUT_MS
                    // "Lock now" (notification/UI) flips skipGrace to confirm.
                    while (!skipGrace && clock() < deadline) delay(250)
                    if (!skipGrace) {
                        _state.value = _state.value.copy(detection = DetectionState.ABORTED, graceRemaining = 0)
                        log.add(LogLevel.INFO, "Lock not confirmed in time — aborted.")
                        return
                    }
                    log.add(LogLevel.SUCCESS, "Lock confirmed.")
                }
                performLock(settings, client, status)
            }
        }
        } catch (c: kotlinx.coroutines.CancellationException) {
            throw c
        } catch (t: Throwable) {
            android.util.Log.w("AutoLockController", "evaluation failed", t)
            failAndNotify("LEAVE/LOCK hit an error: ${t.message ?: t.javaClass.simpleName}", NOTICE_ERROR, saidNotLocked = false)
        }
    }

    /**
     * Pre-lock status read with up to [STATUS_READ_ATTEMPTS] tries, each capped at
     * [STATUS_READ_TIMEOUT_MS], pausing 2 s then 4 s between tries. Null (after failing + notifying)
     * when every try fails.
     */
    private suspend fun readStatusWithRetry(
        client: io.github.gthibo.leavenlock.data.bluelink.BlueLinkClient,
        vehicleId: String,
    ): io.github.gthibo.leavenlock.data.bluelink.model.VehicleStatus? {
        var lastError = "unknown error"
        for (attempt in 1..STATUS_READ_ATTEMPTS) {
            try {
                return kotlinx.coroutines.withTimeout(STATUS_READ_TIMEOUT_MS) {
                    client.status(vehicleId, forceRefresh = false)
                }
            } catch (timeout: kotlinx.coroutines.TimeoutCancellationException) {
                lastError = "no answer within ${STATUS_READ_TIMEOUT_MS / 1000}s"
            } catch (c: kotlinx.coroutines.CancellationException) {
                throw c
            } catch (e: Exception) {
                lastError = e.message ?: e.javaClass.simpleName
            }
            if (attempt < STATUS_READ_ATTEMPTS) {
                val pauseMs = 2_000L * attempt
                log.add(
                    LogLevel.INFO,
                    "Status read failed (try $attempt/$STATUS_READ_ATTEMPTS): $lastError — retrying in ${pauseMs / 1000}s.",
                )
                delay(pauseMs)
            }
        }
        failAndNotify("Could not read vehicle status: $lastError", NOTICE_NO_STATUS)
        return null
    }
    private suspend fun commandStillAllowed(
        original: io.github.gthibo.leavenlock.data.settings.AppSettings,
    ): Boolean {
        val current = settingsRepo.settings.first()

        val reason = when {
            !current.enabled ->
                "Watching is off"

            current.runMode != RunMode.ARMED ->
                "Dry Run is selected"

            current.vehicleId != original.vehicleId ->
                "Selected vehicle changed"

            current.accountEmail != original.accountEmail ->
                "Account changed"

            current.demoMode != original.demoMode ->
                "Demo mode changed"

            current.dontLockIfOpen != original.dontLockIfOpen ->
                "Open-door safeguard changed"

            else -> null
        }

        if (reason == null) return true

        _state.value = _state.value.copy(
            detection = DetectionState.ABORTED,
            graceRemaining = 0,
        )
        log.add(LogLevel.WARN, "Lock attempt aborted: $reason.")
        return false
    }
    private suspend fun performLock(
        settings: io.github.gthibo.leavenlock.data.settings.AppSettings,
        client: io.github.gthibo.leavenlock.data.bluelink.BlueLinkClient,
        status: io.github.gthibo.leavenlock.data.bluelink.model.VehicleStatus,
    ) {
        _state.value = _state.value.copy(detection = DetectionState.LOCKING)
        if (settings.runMode == RunMode.DRY_RUN) {
            log.add(
                LogLevel.INFO,
                "DRY RUN: would attempt to lock. No command sent; lock state unchanged."
            )
            _state.value = _state.value.copy(
                detection = DetectionState.SKIPPED,
                graceRemaining = 0,
            )
            return
        }

        // Send the lock, retrying for up to retryWindowMinutes on retriable failures (car asleep /
        // temporary 503). The first retry re-registers the CCSP device in case a stale/throttled
        // device id is the blocker.
        val deadline = clock() + settings.retryWindowMinutes.coerceAtLeast(0) * 60_000L
        var attempt = 0
        var backoffMs = 20_000L
        while (true) {
            if (!commandStillAllowed(settings)) return

            attempt++
            when (val result = client.lock(settings.vehicleId!!)) {
                is CommandResult.Success -> {
                    log.add(
                        LogLevel.INFO,
                        "Lock command returned success; physical lock state is not yet confirmed."
                    )

                    if (!settings.verifyLock) {
                        _state.value = _state.value.copy(
                            detection = DetectionState.SENT_UNVERIFIED,
                            graceRemaining = 0,
                        )
                        log.add(
                            LogLevel.WARN,
                            "Verification is disabled. Not recording a confirmed lock."
                        )
                        return
                    }

                    val confirmed = verifyLocked(settings, client, status)

                    if (!confirmed) {
                        fail(
                            "Lock command returned success, but locked state could not be confirmed. No extra command sent."
                        )
                        maybePostDeparture(settings, status, locked = false, notConfirmed = true)
                        return
                    }

                    if (!commandStillAllowed(settings)) return

                    onLocked(settings, status)
                    log.add(
                        LogLevel.SUCCESS,
                        "A newer cached status reports locked."
                    )
                    maybePostDeparture(settings, status, locked = true)
                    return
                }
                CommandResult.NotAuthenticated -> {
                    failAndNotify("Not signed in — please log in again.", NOTICE_SIGNED_OUT)
                    return
                }
                CommandResult.RateLimited, is CommandResult.Failure -> {
                    val reason = (result as? CommandResult.Failure)?.reason ?: "rate-limited"
                    val retriable = clock() < deadline
                    if (!retriable) {
                        fail("Lock failed: $reason")
                        maybePostDeparture(settings, status, locked = false, skipReason = reason)
                        return
                    }
                    log.add(LogLevel.INFO, "Lock retry $attempt in ${backoffMs / 1000}s (car may be asleep)…")
                    delay(backoffMs)
                    backoffMs = (backoffMs * 2).coerceAtMost(120_000L)
                }
            }
        }
    }

    /** Reflects the locked state in UI + notification summary + widget cache. */
    private suspend fun onLocked(
        settings: io.github.gthibo.leavenlock.data.settings.AppSettings,
        status: io.github.gthibo.leavenlock.data.bluelink.model.VehicleStatus,
    ) {
        val locked = status.copy(
            lockState = io.github.gthibo.leavenlock.data.bluelink.model.LockState.LOCKED,
            anyDoorOpen = false,
        )
        val summary = if (settings.showStatusInNotification) {
            StatusSummary.build(locked, settings.notificationFields, summaryLabels).ifBlank { null }
        } else null
        _state.value = _state.value.copy(
            detection = DetectionState.LOCKED,
            lastLockAtEpochMs = clock(),
            statusSummary = summary,
        )
        statusCache.saveStatus(locked, StatusSummary.build(locked, settings.notificationFields, summaryLabels))
    }

    /** Read cached status once; never send another command from verification. */
    private suspend fun verifyLocked(
        settings: io.github.gthibo.leavenlock.data.settings.AppSettings,
        client: io.github.gthibo.leavenlock.data.bluelink.BlueLinkClient,
        status: io.github.gthibo.leavenlock.data.bluelink.model.VehicleStatus,
    ): Boolean {
        val check = try {
            client.status(
                settings.vehicleId ?: return false,
                forceRefresh = false,
            )
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            log.add(
                LogLevel.WARN,
                "Lock command succeeded, but status could not be read."
            )
            return false
        }

        if (
            check.lockState !=
            io.github.gthibo.leavenlock.data.bluelink.model.LockState.LOCKED
        ) {
            log.add(
                LogLevel.WARN,
                "Lock command succeeded, but cached status does not confirm locked. No extra command sent."
            )
            return false
        }

        // Reject unknown, future, or old timestamps.
        val now = clock()
        if (
            check.timestamp <= 0L ||
            check.timestamp > now ||
            now - check.timestamp > 5 * 60_000L
        ) {
            log.add(
                LogLevel.WARN,
                "Cached status says locked, but its timestamp is unknown or outside the allowed age."
            )
            return false
        }

        // Require a newer snapshot than the pre-command status.
        if (check.timestamp <= status.timestamp) {
            log.add(
                LogLevel.WARN,
                "Cached status says locked, but it is not newer than the pre-command snapshot."
            )
            return false
        }

        return true
    }

    /** Posts the optional one-shot departure summary ("Locked ✓ · 72% · doors closed · Parked …"). */
    private suspend fun maybePostDeparture(
        settings: io.github.gthibo.leavenlock.data.settings.AppSettings,
        status: io.github.gthibo.leavenlock.data.bluelink.model.VehicleStatus,
        locked: Boolean,
        skipReason: String? = null,
        notConfirmed: Boolean = false,
    ) {
        if (!settings.departureSummary) return
        val ctx = appContext
        val parts = mutableListOf<String>()
        parts += when {
            locked -> ctx.getString(io.github.gthibo.leavenlock.R.string.departure_locked)
            notConfirmed -> ctx.getString(io.github.gthibo.leavenlock.R.string.departure_not_confirmed)
            else -> ctx.getString(io.github.gthibo.leavenlock.R.string.departure_not_locked)
        }
        status.evBatteryPercent?.let { parts += "$it%" }
        if (status.isOpenSomewhere) {
            parts += ctx.getString(io.github.gthibo.leavenlock.R.string.departure_open)
        } else if (status.anyDoorOpen == false) {
            parts += ctx.getString(io.github.gthibo.leavenlock.R.string.departure_closed)
        }
        val parked = settingsRepo.settings.first().parkedLabel
        parked?.takeIf { it.isNotBlank() }?.let { parts += ctx.getString(io.github.gthibo.leavenlock.R.string.departure_parked, it) }
        val title = if (locked) ctx.getString(io.github.gthibo.leavenlock.R.string.departure_title_locked)
        else ctx.getString(io.github.gthibo.leavenlock.R.string.departure_title_skipped)
        val body = skipReason?.takeIf { !locked }?.let { "$it · ${parts.joinToString(" · ")}" }
            ?: parts.joinToString(" · ")
        io.github.gthibo.leavenlock.service.AutoLockNotification.postDeparture(ctx, title, body)
    }

    /**
     * [fail] plus a one-off "LEAVE/LOCK" summary notification, so giving up is never silent.
     * Gated by the departure-summary setting; a notification problem never breaks the flow.
     */
    private suspend fun failAndNotify(message: String, notice: String, saidNotLocked: Boolean = true) {
        fail(message)
        try {
            if (!settingsRepo.settings.first().departureSummary) return
            val ctx = appContext
            val body = if (saidNotLocked) {
                "$notice · ${ctx.getString(io.github.gthibo.leavenlock.R.string.departure_not_locked)}"
            } else notice
            io.github.gthibo.leavenlock.service.AutoLockNotification.postDeparture(
                ctx,
                ctx.getString(io.github.gthibo.leavenlock.R.string.departure_title_skipped),
                body,
            )
        } catch (c: kotlinx.coroutines.CancellationException) {
            throw c
        } catch (t: Throwable) {
            android.util.Log.w("AutoLockController", "failure notice not posted", t)
        }
    }

    private fun fail(message: String) {
        log.add(LogLevel.ERROR, message)
        _state.value = _state.value.copy(detection = DetectionState.ERROR, graceRemaining = 0)
    }

    private fun advance(next: DetectionState) {
        _state.value = _state.value.copy(detection = next)
    }

    private companion object {
        const val CONFIRM_TIMEOUT_MS = 20_000L
        const val CONFIRM_LOCK_TIMEOUT_MS = 120_000L
        const val GEO_POLL_MS = 3_000L
        const val STATUS_READ_ATTEMPTS = 3
        const val STATUS_READ_TIMEOUT_MS = 15_000L
        const val NOTICE_NO_STATUS = "Couldn't reach Hyundai to check the car"
        const val NOTICE_SIGNED_OUT = "Signed out of BlueLink — sign in again"
        const val NOTICE_ERROR = "LEAVE/LOCK hit an error — check that your car is locked"
    }
}

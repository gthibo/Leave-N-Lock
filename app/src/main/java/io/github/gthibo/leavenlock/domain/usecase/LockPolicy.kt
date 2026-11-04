package io.github.gthibo.leavenlock.domain.usecase

import io.github.gthibo.leavenlock.data.bluelink.model.LockState
import io.github.gthibo.leavenlock.data.bluelink.model.VehicleStatus

sealed interface LockDecision {
    data object Lock : LockDecision
    data class Skip(val reason: String) : LockDecision
}

object LockPolicy {
    // Provisional conservative limit, not a Hyundai requirement.
    private const val MAX_STATUS_AGE_MS = 5 * 60_000L

    fun decide(
        status: VehicleStatus,
        dontLockIfOpen: Boolean = false,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): LockDecision = when {
        status.lockState == LockState.LOCKED ->
            LockDecision.Skip("Already locked")

        status.lockState == LockState.UNKNOWN ->
            LockDecision.Skip("Lock state unknown")

        status.engineRunning ->
            LockDecision.Skip("Engine running")

        status.timestamp <= 0L ->
            LockDecision.Skip("Vehicle report time unknown")

        status.timestamp > nowEpochMs ->
            LockDecision.Skip("Vehicle report time is in the future")

        nowEpochMs - status.timestamp > MAX_STATUS_AGE_MS ->
            LockDecision.Skip("Vehicle status too old")

        dontLockIfOpen && status.anyDoorOpen == true ->
            LockDecision.Skip("A door is open")

        dontLockIfOpen && status.anyWindowOpen == true ->
            LockDecision.Skip("A window is open")

        dontLockIfOpen && status.anyDoorOpen == null ->
            LockDecision.Skip("Door state unknown")

        dontLockIfOpen && status.anyWindowOpen == null ->
            LockDecision.Skip("Window state unknown")

        else -> LockDecision.Lock
    }
}
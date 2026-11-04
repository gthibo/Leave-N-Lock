package io.github.gthibo.leavenlock.domain.usecase

import io.github.gthibo.leavenlock.data.bluelink.model.LockState
import io.github.gthibo.leavenlock.data.bluelink.model.VehicleStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class LockPolicyTest {

    private val testNow = 1_700_000_000_000L

    private fun status(
        lock: LockState = LockState.UNLOCKED,
        engine: Boolean = false,
        timestamp: Long = 1_700_000_000_000L,
        doorOpen: Boolean? = false,
        windowOpen: Boolean? = false,
    ) = VehicleStatus(
        lockState = lock,
        engineRunning = engine,
        batteryCharging = null,
        timestamp = timestamp,
        anyDoorOpen = doorOpen,
        anyWindowOpen = windowOpen,
    )

    private fun assertSkipped(
        expectedReason: String,
        snapshot: VehicleStatus,
        guard: Boolean = true,
    ) {
        assertEquals(
            LockDecision.Skip(expectedReason),
            LockPolicy.decide(
                snapshot,
                dontLockIfOpen = guard,
                nowEpochMs = testNow,
            ),
        )
    }

    @Test
    fun locksWhenUnlockedEngineOffAndOpenStatesKnownClosed() {
        assertEquals(
            LockDecision.Lock,
            LockPolicy.decide(
                status(),
                dontLockIfOpen = true,
                nowEpochMs = testNow,
            ),
        )
    }

    @Test
    fun skipsWhenAlreadyLocked() {
        assertSkipped(
            "Already locked",
            status(lock = LockState.LOCKED),
        )
    }

    @Test
    fun skipsWhenEngineRunning() {
        assertSkipped("Engine running", status(engine = true))
    }

    @Test
    fun skipsWhenLockStateUnknown() {
        assertSkipped(
            "Lock state unknown",
            status(lock = LockState.UNKNOWN),
        )
    }

    @Test
    fun skipsWhenReportTimeUnknown() {
        assertSkipped(
            "Vehicle report time unknown",
            status(timestamp = 0L),
        )
    }

    @Test
    fun skipsWhenReportTimeNegative() {
        assertSkipped(
            "Vehicle report time unknown",
            status(timestamp = -1L),
        )
    }

    @Test
    fun unknownReportTimeStillSkipsWithOpenGuardOff() {
        assertSkipped(
            "Vehicle report time unknown",
            status(timestamp = 0L),
            guard = false,
        )
    }

    @Test
    fun skipsWhenDoorOpenAndGuardOn() {
        assertSkipped("A door is open", status(doorOpen = true))
    }

    @Test
    fun skipsWhenWindowOpenAndGuardOn() {
        assertSkipped("A window is open", status(windowOpen = true))
    }

    @Test
    fun skipsWhenDoorStateUnknownAndGuardOn() {
        assertSkipped("Door state unknown", status(doorOpen = null))
    }

    @Test
    fun skipsWhenWindowStateUnknownAndGuardOn() {
        assertSkipped(
            "Window state unknown",
            status(windowOpen = null),
        )
    }

    @Test
    fun openGuardOffDoesNotBlockOpenDoorOrWindow() {
        assertEquals(
            LockDecision.Lock,
            LockPolicy.decide(
                status(doorOpen = true, windowOpen = true),
                dontLockIfOpen = false,
                nowEpochMs = testNow,
            ),
        )
    }

    @Test
    fun openGuardOffDoesNotBlockUnknownDoorOrWindowStates() {
        assertEquals(
            LockDecision.Lock,
            LockPolicy.decide(
                status(doorOpen = null, windowOpen = null),
                dontLockIfOpen = false,
                nowEpochMs = testNow,
            ),
        )
    }

    @Test
    fun skipsWhenStatusOlderThanFiveMinutes() {
        assertSkipped(
            "Vehicle status too old",
            status(timestamp = testNow - 300_001L),
        )
    }

    @Test
    fun acceptsStatusExactlyFiveMinutesOld() {
        assertEquals(
            LockDecision.Lock,
            LockPolicy.decide(
                status(timestamp = testNow - 300_000L),
                dontLockIfOpen = true,
                nowEpochMs = testNow,
            ),
        )
    }

    @Test
    fun skipsWhenReportTimeIsInTheFuture() {
        assertSkipped(
            "Vehicle report time is in the future",
            status(timestamp = testNow + 1L),
        )
    }
}
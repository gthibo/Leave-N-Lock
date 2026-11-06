package io.github.gthibo.leavenlock.data.status

import io.github.gthibo.leavenlock.data.bluelink.model.LockState
import io.github.gthibo.leavenlock.data.bluelink.model.VehicleStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CachedStatusTest {

    @Test
    fun nullWhenNothingStored() {
        assertNull(CachedStatus().toVehicleStatus())
    }

    @Test
    fun reconstructsFullStatus() {
        val cached = CachedStatus(
            lockState = "LOCKED",
            hasStatus = true,
            engineRunning = false,
            evBatteryPercent = 72,
            rangeMiles = 318,
            twelveVoltPercent = 88,
            anyDoorOpen = false,
        )
        val vs = cached.toVehicleStatus()!!
        assertEquals(LockState.LOCKED, vs.lockState)
        assertEquals(72, vs.evBatteryPercent)
        assertEquals(318, vs.rangeMiles)
        assertEquals(88, vs.twelveVoltPercent)
        assertEquals(false, vs.anyDoorOpen)
    }

    @Test
    fun unknownLockStateForBadString() {
        val vs = CachedStatus(lockState = "GARBAGE", hasStatus = true).toVehicleStatus()!!
        assertEquals(LockState.UNKNOWN, vs.lockState)
    }

    @Test
    fun carReportTimeSurvivesTheCache() {
        val vs = CachedStatus(hasStatus = true, reportedAtEpochMs = 1_762_190_469_000L).toVehicleStatus()!!
        assertEquals(1_762_190_469_000L, vs.reportedAtEpochMs)
    }

    @Test
    fun carReportTimeDefaultsToUnknown() {
        assertNull(CachedStatus(hasStatus = true).toVehicleStatus()!!.reportedAtEpochMs)
    }

    // ---- Saving reads vs. commands ----

    private val t0 = 1_762_190_000_000L

    private fun status(lock: LockState, reportedAt: Long?, door: Boolean? = false) = VehicleStatus(
        lockState = lock,
        engineRunning = false,
        batteryCharging = null,
        timestamp = reportedAt ?: 0L,
        anyDoorOpen = door,
        reportedAtEpochMs = reportedAt,
    )

    private fun afterLockCommand(): CachedStatus =
        CachedStatus()
            .after(status(LockState.UNLOCKED, reportedAt = t0 - 60_000L), "unlocked", t0 - 30_000L, fromCommand = false)
            .after(status(LockState.LOCKED, reportedAt = t0 - 60_000L), "locked", t0, fromCommand = true)

    @Test
    fun commandSetsLockStateAndCommandTime() {
        val c = afterLockCommand()
        assertEquals("LOCKED", c.lockState)
        assertEquals(t0, c.commandAtEpochMs)
        assertEquals(t0, c.lastLockedAtEpochMs)
        assertEquals(t0, c.statusAsOfEpochMs())
    }

    @Test
    fun readReportedBeforeTheCommandKeepsTheCommandedLock() {
        // Hyundai's cached snapshot still shows the car unlocked from before the lock.
        val c = afterLockCommand()
            .after(status(LockState.UNLOCKED, reportedAt = t0 - 60_000L, door = true), "unlocked", t0 + 10_000L, fromCommand = false)
        assertEquals("LOCKED", c.lockState)
        assertEquals(false, c.anyDoorOpen)
        assertEquals("locked", c.summary)
        assertEquals(t0 + 10_000L, c.updatedAtEpochMs)
    }

    @Test
    fun readWithNoReportTimeKeepsTheCommandedLock() {
        val c = afterLockCommand()
            .after(status(LockState.UNLOCKED, reportedAt = null), "unlocked", t0 + 10_000L, fromCommand = false)
        assertEquals("LOCKED", c.lockState)
    }

    @Test
    fun readReportedAfterTheCommandWins() {
        // Someone unlocked the car with the fob after the app locked it.
        val c = afterLockCommand()
            .after(status(LockState.UNLOCKED, reportedAt = t0 + 5_000L), "unlocked", t0 + 10_000L, fromCommand = false)
        assertEquals("UNLOCKED", c.lockState)
        assertEquals("unlocked", c.summary)
        assertEquals(t0 + 5_000L, c.statusAsOfEpochMs())
    }

    @Test
    fun commandHoldEndsAfterThirtyMinutes() {
        val late = t0 + CachedStatus.COMMAND_HOLD_MS
        val c = afterLockCommand()
            .after(status(LockState.UNLOCKED, reportedAt = null), "unlocked", late, fromCommand = false)
        assertEquals("UNLOCKED", c.lockState)
    }

    @Test
    fun repeatedLockedReadsDoNotResetLockedTime() {
        val first = CachedStatus().after(status(LockState.LOCKED, reportedAt = t0), "locked", t0, fromCommand = false)
        val second = first.after(status(LockState.LOCKED, reportedAt = t0 + 60_000L), "locked", t0 + 120_000L, fromCommand = false)
        assertEquals(t0, second.lastLockedAtEpochMs)
    }

    @Test
    fun statusAsOfFallsBackToSaveTime() {
        val c = CachedStatus().after(status(LockState.LOCKED, reportedAt = null), "locked", t0, fromCommand = false)
        assertEquals(t0, c.statusAsOfEpochMs())
        assertNull(CachedStatus().statusAsOfEpochMs())
    }
}

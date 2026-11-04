package io.github.gthibo.leavenlock.data.status

import io.github.gthibo.leavenlock.data.bluelink.model.LockState
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
            rangeKm = 318,
            twelveVoltPercent = 88,
            anyDoorOpen = false,
        )
        val vs = cached.toVehicleStatus()!!
        assertEquals(LockState.LOCKED, vs.lockState)
        assertEquals(72, vs.evBatteryPercent)
        assertEquals(318, vs.rangeKm)
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
}

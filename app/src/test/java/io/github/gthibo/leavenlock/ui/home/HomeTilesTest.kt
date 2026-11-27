package io.github.gthibo.leavenlock.ui.home

import io.github.gthibo.leavenlock.data.bluelink.model.LockState
import io.github.gthibo.leavenlock.data.bluelink.model.VehicleStatus
import io.github.gthibo.leavenlock.data.settings.NotificationField
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeTilesTest {

    private val status = VehicleStatus(
        lockState = LockState.UNLOCKED,
        engineRunning = false,
        batteryCharging = false,
        timestamp = 0L,
        evBatteryPercent = 72,
        rangeMiles = 318,
        twelveVoltPercent = 88,
        climateOn = false,
    )

    @Test
    fun showsOnlyTheTurnedOnDetails() {
        assertEquals(
            listOf(HomeTile.RANGE, HomeTile.CLIMATE),
            homeTiles(status, setOf(NotificationField.CLIMATE, NotificationField.RANGE)),
        )
    }

    @Test
    fun allDetailsInStableOrder() {
        assertEquals(
            listOf(HomeTile.RANGE, HomeTile.TWELVE_VOLT, HomeTile.CAR_ON, HomeTile.CLIMATE),
            homeTiles(status, NotificationField.entries.toSet()),
        )
    }

    @Test
    fun lockStateAndDriveBatteryHaveNoTile() {
        assertEquals(
            emptyList<HomeTile>(),
            homeTiles(status, setOf(NotificationField.LOCK_STATE, NotificationField.EV_BATTERY)),
        )
    }

    @Test
    fun skipsValuesTheCarDidNotReport() {
        val bare = status.copy(rangeMiles = null, twelveVoltPercent = null, climateOn = null)
        assertEquals(listOf(HomeTile.CAR_ON), homeTiles(bare, NotificationField.entries.toSet()))
    }
}

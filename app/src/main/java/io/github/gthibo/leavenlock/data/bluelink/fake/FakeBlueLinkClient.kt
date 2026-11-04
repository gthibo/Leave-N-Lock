package io.github.gthibo.leavenlock.data.bluelink.fake

import io.github.gthibo.leavenlock.data.bluelink.BlueLinkClient
import io.github.gthibo.leavenlock.data.bluelink.model.CommandResult
import io.github.gthibo.leavenlock.data.bluelink.model.LockState
import io.github.gthibo.leavenlock.data.bluelink.model.Vehicle
import io.github.gthibo.leavenlock.data.bluelink.model.VehicleStatus
import kotlinx.coroutines.delay
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory client used for demo mode and unit tests (no account or car needed).
 * It simulates realistic latency and a vehicle that starts UNLOCKED.
 */
@Singleton
class FakeBlueLinkClient @Inject constructor() : BlueLinkClient {

    private val authed = AtomicReference(false)
    private val lockState = AtomicReference(LockState.UNLOCKED)

    override suspend fun isAuthenticated(): Boolean = authed.get()

    override suspend fun loginWithPassword(username: String, password: String): CommandResult {
        delay(600)
        authed.set(true)
        return CommandResult.Success("Signed in (simulated) as $username")
    }

    override suspend fun ensureFreshSession(): Boolean = authed.get()

    override suspend fun vehicles(): List<Vehicle> = listOf(
        Vehicle(
            id = "demo-ioniq5",
            vin = "KMHXXXXXXXXXXXXXX",
            nickname = "My Ioniq 5",
            model = "IONIQ 5",
        ),
    )

    override suspend fun status(vehicleId: String, forceRefresh: Boolean): VehicleStatus {
        delay(if (forceRefresh) 900 else 150)
        return VehicleStatus(
            lockState = lockState.get(),
            engineRunning = false,
            batteryCharging = false,
            timestamp = System.currentTimeMillis(),
            evBatteryPercent = 72,
            rangeKm = 318,
            climateOn = false,
            twelveVoltPercent = 88,
            anyDoorOpen = lockState.get() == LockState.UNLOCKED,
        )
    }

    override suspend fun lock(vehicleId: String): CommandResult {
        delay(1200)
        lockState.set(LockState.LOCKED)
        return CommandResult.Success("Vehicle locked (simulated)")
    }

    override suspend fun unlock(vehicleId: String): CommandResult {
        delay(1200)
        lockState.set(LockState.UNLOCKED)
        return CommandResult.Success("Vehicle unlocked (simulated)")
    }

    override suspend fun clearSession() {
        authed.set(false)
    }

    /** Test helper to force a starting state. */
    fun setLockStateForTest(state: LockState) = lockState.set(state)
}

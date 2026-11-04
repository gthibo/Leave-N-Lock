package io.github.gthibo.leavenlock.data.bluelink

import io.github.gthibo.leavenlock.data.bluelink.model.CommandResult
import io.github.gthibo.leavenlock.data.bluelink.model.Vehicle
import io.github.gthibo.leavenlock.data.bluelink.model.VehicleStatus

/**
 * Contract for talking to the Hyundai BlueLink (US) backend.
 *
 * Implementations:
 *  - [io.github.gthibo.leavenlock.data.bluelink.us.UsBlueLinkClient] for the real US service.
 *  - [io.github.gthibo.leavenlock.data.bluelink.fake.FakeBlueLinkClient] for tests + demo mode.
 *
 * All calls are suspending and safe to run off the main thread.
 */
interface BlueLinkClient {

    /** True once we hold a valid (or refreshable) session. */
    suspend fun isAuthenticated(): Boolean

    /** Sign in with the BlueLink account email + password. */
    suspend fun loginWithPassword(username: String, password: String): CommandResult

    /** Ensure the access token is fresh, refreshing if needed. */
    suspend fun ensureFreshSession(): Boolean

    /** List vehicles on the account. */
    suspend fun vehicles(): List<Vehicle>

    /** Fetch a lock-relevant status snapshot. May be cached or force-refreshed. */
    suspend fun status(vehicleId: String, forceRefresh: Boolean): VehicleStatus

    /** Send the remote lock command. */
    suspend fun lock(vehicleId: String): CommandResult

    /** Send the remote unlock command (used only for manual testing from the UI). */
    suspend fun unlock(vehicleId: String): CommandResult

    /** Drop the local session (logout). */
    suspend fun clearSession()
}

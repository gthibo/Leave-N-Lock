package io.github.gthibo.leavenlock.data.status

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.gthibo.leavenlock.data.bluelink.model.LockState
import io.github.gthibo.leavenlock.data.bluelink.model.VehicleStatus
import io.github.gthibo.leavenlock.data.bluelink.model.kmToMiles
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.statusDataStore: DataStore<Preferences> by preferencesDataStore(name = "autolock_status_cache")

/**
 * Last-known vehicle status, persisted so the home screen and widget render the previous state
 * instantly on open (no blanks) and stay in sync in real time. Stores the full status so the
 * detailed info (battery/range/engine/12V/doors) survives app restarts.
 */
data class CachedStatus(
    val lockState: String = "UNKNOWN",
    val summary: String = "",
    val updatedAtEpochMs: Long = 0L,
    val hasStatus: Boolean = false,
    val engineRunning: Boolean = false,
    val evBatteryPercent: Int? = null,
    /** Remaining range in miles. */
    val rangeMiles: Int? = null,
    val twelveVoltPercent: Int? = null,
    val anyDoorOpen: Boolean? = null,
    val climateOn: Boolean? = null,
    val batteryCharging: Boolean? = null,
    /** When the car was last confirmed LOCKED (0 = never), for the widget's "locked X ago". */
    val lastLockedAtEpochMs: Long = 0L,
    /** When the car itself last reported (from [VehicleStatus.reportedAtEpochMs]); null = unknown. */
    val reportedAtEpochMs: Long? = null,
    /** When a lock/unlock command last succeeded (0 = never). Its lock state wins over older reads. */
    val commandAtEpochMs: Long = 0L,
) {
    /** Reconstruct a [VehicleStatus] from the cache, or null if nothing has been stored yet. */
    fun toVehicleStatus(): VehicleStatus? {
        if (!hasStatus) return null
        return VehicleStatus(
            lockState = runCatching { LockState.valueOf(lockState) }.getOrDefault(LockState.UNKNOWN),
            engineRunning = engineRunning,
            batteryCharging = batteryCharging,
            timestamp = updatedAtEpochMs,
            evBatteryPercent = evBatteryPercent,
            rangeMiles = rangeMiles,
            climateOn = climateOn,
            twelveVoltPercent = twelveVoltPercent,
            anyDoorOpen = anyDoorOpen,
            reportedAtEpochMs = reportedAtEpochMs,
        )
    }

    /**
     * The one "Updated …" time for the Home card: the newer of the car's own report and the last
     * successful command. Falls back to when the app last saved a status if neither is known.
     */
    fun statusAsOfEpochMs(): Long? {
        val best = maxOf(reportedAtEpochMs ?: 0L, commandAtEpochMs)
        return when {
            best > 0L -> best
            updatedAtEpochMs > 0L -> updatedAtEpochMs
            else -> null
        }
    }

    /**
     * The cache after saving [status]. [fromCommand] = the status reflects a lock/unlock command
     * Hyundai just reported as done. A plain status read that the car reported before the last
     * command (or with no report time) keeps the command's lock + door state, because Hyundai's
     * cached snapshot often lags the command by minutes. That hold ends after [COMMAND_HOLD_MS].
     */
    fun after(status: VehicleStatus, summary: String, nowEpochMs: Long, fromCommand: Boolean): CachedStatus {
        val readIsOlderThanCommand = !fromCommand &&
            commandAtEpochMs > 0L &&
            nowEpochMs - commandAtEpochMs < COMMAND_HOLD_MS &&
            (status.reportedAtEpochMs ?: 0L) < commandAtEpochMs
        val newLock = if (readIsOlderThanCommand) lockState else status.lockState.name
        val newDoor = if (readIsOlderThanCommand) anyDoorOpen else status.anyDoorOpen
        val becameLocked = newLock == LockState.LOCKED.name &&
            (lockState != LockState.LOCKED.name || lastLockedAtEpochMs == 0L || fromCommand)
        return copy(
            lockState = newLock,
            // The caller built the summary from the older read, so it would show the wrong lock state.
            summary = if (readIsOlderThanCommand) this.summary else summary,
            updatedAtEpochMs = nowEpochMs,
            hasStatus = true,
            engineRunning = status.engineRunning,
            evBatteryPercent = status.evBatteryPercent,
            rangeMiles = status.rangeMiles,
            twelveVoltPercent = status.twelveVoltPercent,
            anyDoorOpen = newDoor,
            climateOn = status.climateOn,
            batteryCharging = status.batteryCharging,
            reportedAtEpochMs = status.reportedAtEpochMs ?: reportedAtEpochMs.takeIf { readIsOlderThanCommand },
            lastLockedAtEpochMs = if (becameLocked) nowEpochMs else lastLockedAtEpochMs,
            commandAtEpochMs = if (fromCommand) nowEpochMs else commandAtEpochMs,
        )
    }

    companion object {
        /** How long a command's lock state outranks older status reads. */
        const val COMMAND_HOLD_MS = 30 * 60_000L
    }
}

@Singleton
class StatusCache @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    internal object Keys {
        val LOCK = stringPreferencesKey("lock_state")
        val SUMMARY = stringPreferencesKey("summary")
        val UPDATED = longPreferencesKey("updated_at")
        val HAS = booleanPreferencesKey("has_status")
        val ENGINE = booleanPreferencesKey("engine")
        val EV = intPreferencesKey("ev_battery")
        val RANGE = intPreferencesKey("range_mi")

        /** Pre-miles builds saved range in km here; read once (converted), dropped on the next write. */
        val LEGACY_RANGE_KM = intPreferencesKey("range_km")
        val TWELVE = intPreferencesKey("twelve_volt")
        val DOOR = booleanPreferencesKey("any_door_open")
        val CLIMATE = booleanPreferencesKey("climate_on")
        val CHARGING = booleanPreferencesKey("battery_charging")
        val LAST_LOCKED = longPreferencesKey("last_locked_at")
        val REPORTED = longPreferencesKey("car_reported_at")
        val COMMAND_AT = longPreferencesKey("command_at")
    }

    /** Preference (de)serialisation; pure, so tests can round-trip it without a DataStore. */
    internal companion object {
        fun read(p: Preferences) = CachedStatus(
            lockState = p[Keys.LOCK] ?: "UNKNOWN",
            summary = p[Keys.SUMMARY] ?: "",
            updatedAtEpochMs = p[Keys.UPDATED] ?: 0L,
            hasStatus = p[Keys.HAS] ?: false,
            engineRunning = p[Keys.ENGINE] ?: false,
            evBatteryPercent = p[Keys.EV],
            rangeMiles = p[Keys.RANGE] ?: p[Keys.LEGACY_RANGE_KM]?.let { kmToMiles(it.toDouble()) },
            twelveVoltPercent = p[Keys.TWELVE],
            anyDoorOpen = p[Keys.DOOR],
            climateOn = p[Keys.CLIMATE],
            batteryCharging = p[Keys.CHARGING],
            lastLockedAtEpochMs = p[Keys.LAST_LOCKED] ?: 0L,
            reportedAtEpochMs = p[Keys.REPORTED],
            commandAtEpochMs = p[Keys.COMMAND_AT] ?: 0L,
        )

        fun write(p: MutablePreferences, c: CachedStatus) {
            p[Keys.LOCK] = c.lockState
            p[Keys.SUMMARY] = c.summary
            p[Keys.UPDATED] = c.updatedAtEpochMs
            p[Keys.HAS] = c.hasStatus
            p[Keys.ENGINE] = c.engineRunning
            c.evBatteryPercent?.let { p[Keys.EV] = it } ?: p.remove(Keys.EV)
            c.rangeMiles?.let { p[Keys.RANGE] = it } ?: p.remove(Keys.RANGE)
            p.remove(Keys.LEGACY_RANGE_KM)
            c.twelveVoltPercent?.let { p[Keys.TWELVE] = it } ?: p.remove(Keys.TWELVE)
            c.anyDoorOpen?.let { p[Keys.DOOR] = it } ?: p.remove(Keys.DOOR)
            c.climateOn?.let { p[Keys.CLIMATE] = it } ?: p.remove(Keys.CLIMATE)
            c.batteryCharging?.let { p[Keys.CHARGING] = it } ?: p.remove(Keys.CHARGING)
            c.reportedAtEpochMs?.let { p[Keys.REPORTED] = it } ?: p.remove(Keys.REPORTED)
            p[Keys.LAST_LOCKED] = c.lastLockedAtEpochMs
            p[Keys.COMMAND_AT] = c.commandAtEpochMs
        }
    }

    val cached: Flow<CachedStatus> = context.statusDataStore.data.map(::read)

    /** Forget everything (the cache holds one vehicle; called when the selected vehicle changes). */
    suspend fun clear() {
        context.statusDataStore.edit { it.clear() }
        io.github.gthibo.leavenlock.widget.AutoLockWidget.refresh(context)
    }

    /** Save just the lock state + summary (used when a full status isn't available). */
    suspend fun save(lockState: String, summary: String) {
        context.statusDataStore.edit {
            it[Keys.LOCK] = lockState
            it[Keys.SUMMARY] = summary
            it[Keys.UPDATED] = System.currentTimeMillis()
        }
        io.github.gthibo.leavenlock.widget.AutoLockWidget.refresh(context)
    }

    /**
     * Save the full vehicle status so detailed info persists across restarts. Pass
     * [fromCommand] = true when [status] reflects a lock/unlock Hyundai just reported as done;
     * see [CachedStatus.after] for how that outranks older reads.
     */
    suspend fun saveStatus(status: VehicleStatus, summary: String, fromCommand: Boolean = false) {
        context.statusDataStore.edit {
            write(it, read(it).after(status, summary, System.currentTimeMillis(), fromCommand))
        }
        // Redraw any placed home-screen widgets.
        io.github.gthibo.leavenlock.widget.AutoLockWidget.refresh(context)
    }
}

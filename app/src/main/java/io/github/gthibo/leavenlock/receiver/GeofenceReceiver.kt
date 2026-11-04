package io.github.gthibo.leavenlock.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import io.github.gthibo.leavenlock.data.detection.GeofenceManager
import io.github.gthibo.leavenlock.data.settings.SettingsRepository
import io.github.gthibo.leavenlock.domain.ActivityLog
import io.github.gthibo.leavenlock.domain.LogLevel
import io.github.gthibo.leavenlock.service.AutoLockService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Battery-friendly "you left" trigger: the OS wakes this when the phone exits the car's geofence,
 * so AutoLock can evaluate without a persistent service or polling.
 */
@AndroidEntryPoint
class GeofenceReceiver : BroadcastReceiver() {

    @Inject lateinit var settingsRepo: SettingsRepository
    @Inject lateinit var geofenceManager: GeofenceManager
    @Inject lateinit var log: ActivityLog

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError()) return
        if (event.geofenceTransition != Geofence.GEOFENCE_TRANSITION_EXIT) return

        val pending = goAsync()
        scope.launch {
            try {
                val s = settingsRepo.settings.first()
                if (s.enabled && s.useGeofence) {
                    log.add(LogLevel.INFO, "Left the car's geofence — evaluating.")
                    AutoLockService.start(context)
                }
                // One-shot: re-registered on the next Bluetooth arrival.
                geofenceManager.remove()
            } finally {
                pending.finish()
            }
        }
    }
}

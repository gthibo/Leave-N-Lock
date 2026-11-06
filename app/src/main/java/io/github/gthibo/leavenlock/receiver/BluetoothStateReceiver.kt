package io.github.gthibo.leavenlock.receiver

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.gthibo.leavenlock.domain.ActivityLog
import io.github.gthibo.leavenlock.domain.AutoLockController
import io.github.gthibo.leavenlock.domain.LogLevel
import io.github.gthibo.leavenlock.data.settings.SettingsRepository
import io.github.gthibo.leavenlock.service.AutoLockService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Primary "left the car" trigger: fires when the phone disconnects from the car's
 * paired Bluetooth device. Also cancels a pending lock if we reconnect (user got back in).
 */
@AndroidEntryPoint
class BluetoothStateReceiver : BroadcastReceiver() {

    @Inject lateinit var settingsRepo: SettingsRepository
    @Inject lateinit var controller: AutoLockController
    @Inject lateinit var log: ActivityLog
    @Inject lateinit var locationHelper: io.github.gthibo.leavenlock.data.location.LocationHelper
    @Inject lateinit var geofenceManager: io.github.gthibo.leavenlock.data.detection.GeofenceManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        @Suppress("DEPRECATION")
        val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        val deviceMac = device?.address ?: return

        val pending = goAsync()
        scope.launch {
            try {
                val settings = settingsRepo.settings.first()
                if (!settings.enabled || !settings.useBluetoothTrigger) return@launch
                if (!deviceMac.equalsIgnoreCase(settings.carBluetoothMac)) return@launch

                when (action) {
                    BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                        log.add(LogLevel.INFO, "Car Bluetooth disconnected — starting evaluation.")
                        AutoLockService.start(context)
                    }
                    BluetoothDevice.ACTION_ACL_CONNECTED -> {
                        controller.cancel(reason = "Car Bluetooth reconnected")
                        // Arrival: register a battery-friendly geofence around the car so leaving is
                        // detected even without polling or a persistent service.
                        if (settings.useGeofence) {
                            locationHelper.currentLocation()?.let { loc ->
                                geofenceManager.register(loc.latitude, loc.longitude, settings.geofenceRadiusMeters)
                                log.add(LogLevel.INFO, "Arrived at the car — geofence armed.")
                            }
                        }
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }

    private fun String.equalsIgnoreCase(other: String?): Boolean =
        other != null && this.equals(other, ignoreCase = true)
}

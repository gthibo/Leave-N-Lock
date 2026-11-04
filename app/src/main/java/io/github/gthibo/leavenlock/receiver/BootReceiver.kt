package io.github.gthibo.leavenlock.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
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

/** After a reboot, resume persistent background watching if AutoLock is enabled. */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var log: ActivityLog
    @Inject lateinit var settingsRepo: SettingsRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        scope.launch {
            try {
                if (settingsRepo.settings.first().enabled) {
                    AutoLockService.startWatching(context)
                    log.add(LogLevel.INFO, "Device restarted — Leave-N-Lock resumed watching.")
                }
            } finally {
                pending.finish()
            }
        }
    }
}

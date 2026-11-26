package io.github.gthibo.leavenlock.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import io.github.gthibo.leavenlock.MainActivity
import io.github.gthibo.leavenlock.R
import io.github.gthibo.leavenlock.data.sound.LockSound

/**
 * Pure naming rules for the sound channels. A channel's sound can't change after it's created,
 * so each lock sound gets its own channel id and the old ones are deleted.
 */
object SoundChannelIds {
    const val START = "sound_start_v1"
    const val LOCK_PREFIX = "sound_lock_"

    /** Channel id for the lock sound: one for the built-in chime, one per custom sound URI. */
    fun lockChannelId(customUri: String?): String {
        val uri = customUri?.takeIf { it.isNotBlank() } ?: return LOCK_PREFIX + "default"
        // String.hashCode is specified by the JLS, so the id is stable across runs and devices.
        return LOCK_PREFIX + "c" + Integer.toHexString(uri.hashCode())
    }

    /** Lock-sound channels that aren't [currentId] any more and should be deleted. */
    fun staleLockChannels(existingIds: Collection<String>, currentId: String): List<String> =
        existingIds.filter { it.startsWith(LOCK_PREFIX) && it != currentId }
}

/**
 * Plays the auto-lock start chime and the lock sound as notification sounds. Android 17 (when the
 * app targets API 37) mutes audio an app plays itself from a foreground service the system started
 * in the background — exactly how `AutoLockService` runs after a Bluetooth disconnect. Notification
 * sounds are played by the system, so they still sound. Each cue is a short notification with words
 * that removes itself after a few seconds. The user's notification volume, Do Not Disturb and
 * per-channel settings apply; nothing plays if notifications are off.
 */
object SoundCues {

    private const val CUE_ID = 4203
    private const val CUE_TIMEOUT_MS = 10_000L

    fun playStart(context: Context) {
        val channel = ensureStartChannel(context) ?: return
        post(context, channel, R.string.cue_start_title, R.string.cue_start_body)
    }

    fun playLock(context: Context, customUri: String?) {
        val channel = ensureLockChannel(context, customUri) ?: return
        post(context, channel, R.string.cue_lock_title, R.string.cue_lock_body)
    }

    /** Creates the sound channels up front so they show in system settings before the first run. */
    fun ensureChannels(context: Context, customUri: String?) {
        ensureStartChannel(context)
        ensureLockChannel(context, customUri)
    }

    private fun ensureStartChannel(context: Context): String? {
        val manager = manager(context) ?: return null
        if (manager.getNotificationChannel(SoundChannelIds.START) == null) {
            manager.createNotificationChannel(
                soundChannel(
                    SoundChannelIds.START,
                    context.getString(R.string.sound_channel_start_name),
                    context.getString(R.string.sound_channel_start_desc),
                    LockSound.startUri(context),
                ),
            )
        }
        return SoundChannelIds.START
    }

    private fun ensureLockChannel(context: Context, customUri: String?): String? {
        val manager = manager(context) ?: return null
        val id = SoundChannelIds.lockChannelId(customUri)
        SoundChannelIds.staleLockChannels(manager.notificationChannels.map { it.id }, id)
            .forEach { runCatching { manager.deleteNotificationChannel(it) } }
        if (manager.getNotificationChannel(id) == null) {
            manager.createNotificationChannel(
                soundChannel(
                    id,
                    context.getString(R.string.sound_channel_lock_name),
                    context.getString(R.string.sound_channel_lock_desc),
                    LockSound.lockUri(context, customUri),
                ),
            )
        }
        return id
    }

    private fun soundChannel(id: String, name: String, desc: String, sound: Uri) =
        NotificationChannel(id, name, NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = desc
            setSound(
                sound,
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            // The service vibrates itself ("Vibrate on lock"), so the channel doesn't.
            enableVibration(false)
            setShowBadge(false)
        }

    private fun post(context: Context, channelId: String, titleRes: Int, bodyRes: Int) {
        val nm = NotificationManagerCompat.from(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        if (!nm.areNotificationsEnabled()) return
        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_stat_leavelock)
            .setColor(ContextCompat.getColor(context, R.color.pg_teal))
            .setContentTitle(context.getString(titleRes))
            .setContentText(context.getString(bodyRes))
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setTimeoutAfter(CUE_TIMEOUT_MS)
            .setContentIntent(contentIntent)
            .build()
        try {
            nm.notify(CUE_ID, notification)
        } catch (_: SecurityException) {
            // Notifications permission revoked between the check and the post: skip the sound.
        }
    }

    private fun manager(context: Context) =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
}

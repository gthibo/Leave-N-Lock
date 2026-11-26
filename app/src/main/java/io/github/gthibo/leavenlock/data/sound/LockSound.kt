package io.github.gthibo.leavenlock.data.sound

import android.content.ContentResolver
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import io.github.gthibo.leavenlock.R

/**
 * The lock and start sounds. During auto-lock they play as notification sounds
 * (`service/SoundCues`), because Android 17 mutes audio an app plays from a background service.
 * The Settings "Test" / "Play default" previews run while the app is on screen, so they play the
 * same files directly.
 */
object LockSound {

    fun defaultLockUri(context: Context): Uri = rawUri(context, R.raw.chime_lock)

    fun startUri(context: Context): Uri = rawUri(context, R.raw.chime_start)

    /** The lock sound in use: the user's custom sound when set, otherwise the built-in chime. */
    fun lockUri(context: Context, customUri: String?): Uri =
        customUri?.takeIf { it.isNotBlank() }?.let(Uri::parse) ?: defaultLockUri(context)

    /** Preview the configured lock sound (Settings "Test"); falls back to the chime. */
    fun play(context: Context, customUri: String?) {
        if (!preview(context, lockUri(context, customUri))) preview(context, defaultLockUri(context))
    }

    /** Preview the built-in chime (Settings "Play default"). */
    fun playDefault(context: Context) {
        preview(context, defaultLockUri(context))
    }

    private fun preview(context: Context, uri: Uri): Boolean = runCatching {
        val ringtone = RingtoneManager.getRingtone(context, uri) ?: return false
        ringtone.audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        ringtone.play()
        true
    }.getOrDefault(false)

    private fun rawUri(context: Context, resId: Int): Uri = Uri.Builder()
        .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
        .authority(context.packageName)
        .appendPath(context.resources.getResourceTypeName(resId))
        .appendPath(context.resources.getResourceEntryName(resId))
        .build()
}

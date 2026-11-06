package io.github.gthibo.leavenlock.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import io.github.gthibo.leavenlock.MainActivity
import io.github.gthibo.leavenlock.R
import io.github.gthibo.leavenlock.data.status.StatusCache
import io.github.gthibo.leavenlock.domain.AutoLockController
import io.github.gthibo.leavenlock.service.AutoLockService
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * Home-screen widget: glanceable lock state + summary, and one button that follows the saved
 * lock state (see [widgetActionFor]): one-tap "Lock now" when unlocked or unknown; "Unlock…"
 * when locked, which only opens the app on Home's unlock confirmation (PIN, Armed mode), never
 * unlocking from the home screen in one tap.
 */
class AutoLockWidget : AppWidgetProvider() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WidgetEntryPoint {
        fun statusCache(): StatusCache
        fun controller(): AutoLockController
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val entry = EntryPointAccessors.fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
        val cached = runBlocking { entry.statusCache().cached.first() }
        val action = widgetActionFor(cached.lockState, entry.controller().state.value.detection)

        for (id in ids) {
            val views = RemoteViews(context.packageName, R.layout.widget_autolock).apply {
                // Status always has words; the colour stays on-teal-deep (no colour-only meaning).
                val lockLabel = when (cached.lockState) {
                    "LOCKED" -> context.getString(R.string.lock_locked)
                    "UNLOCKED" -> context.getString(R.string.lock_unlocked)
                    else -> context.getString(R.string.lock_unknown)
                }
                val label = if (cached.lockState == "LOCKED" && cached.lastLockedAtEpochMs > 0) {
                    "$lockLabel · ${agoLabel(cached.lastLockedAtEpochMs)}"
                } else {
                    lockLabel
                }
                setTextViewText(R.id.widget_lock_state, label)
                setTextViewText(R.id.widget_summary, cached.summary)

                setOnClickPendingIntent(android.R.id.background, openAppIntent(context))
                bindButton(context, action)
            }
            manager.updateAppWidget(id, views)
        }
    }

    private fun RemoteViews.bindButton(context: Context, action: WidgetAction) {
        val (text, background, textColor) = when (action) {
            WidgetAction.LOCK -> Triple(R.string.action_lock_now, R.drawable.widget_button_bg, R.color.pg_on_teal_deep)
            // Danger is the design system's tone for risky actions on the real car.
            WidgetAction.UNLOCK -> Triple(R.string.widget_unlock, R.drawable.widget_button_danger_bg, R.color.pg_on_danger)
            WidgetAction.IN_FLIGHT -> Triple(R.string.widget_auto_lock_running, R.drawable.widget_button_bg, R.color.pg_on_teal_deep_muted)
        }
        setTextViewText(R.id.widget_lock_button, context.getString(text))
        setInt(R.id.widget_lock_button, "setBackgroundResource", background)
        setTextColor(R.id.widget_lock_button, context.getColor(textColor))
        val click = when (action) {
            WidgetAction.LOCK -> lockNowIntent(context)
            WidgetAction.UNLOCK -> confirmUnlockIntent(context)
            WidgetAction.IN_FLIGHT -> openAppIntent(context)
        }
        setOnClickPendingIntent(R.id.widget_lock_button, click)
    }

    /** Opens Home with its unlock confirmation showing; the unlock itself is sent from there. */
    private fun confirmUnlockIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            4,
            Intent(context, MainActivity::class.java)
                .setAction(MainActivity.ACTION_CONFIRM_UNLOCK)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun openAppIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun lockNowIntent(context: Context): PendingIntent {
        val intent = Intent(context, AutoLockService::class.java).setAction(AutoLockService.ACTION_LOCK_NOW)
        return PendingIntent.getService(
            context,
            3,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    /** Compact relative time for the "Locked X ago" widget line. */
    private fun agoLabel(epochMs: Long): String {
        val mins = ((System.currentTimeMillis() - epochMs) / 60_000L).toInt()
        return when {
            mins <= 0 -> "just now"
            mins < 60 -> "${mins}m ago"
            mins < 24 * 60 -> "${mins / 60}h ago"
            else -> "${mins / (24 * 60)}d ago"
        }
    }

    companion object {
        /** Ask the launcher to redraw the widget after a status change. */
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, AutoLockWidget::class.java))
            if (ids.isNotEmpty()) {
                val intent = Intent(context, AutoLockWidget::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                }
                context.sendBroadcast(intent)
            }
        }
    }
}

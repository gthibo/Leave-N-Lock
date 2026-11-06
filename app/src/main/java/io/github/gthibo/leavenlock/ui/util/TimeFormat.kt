package io.github.gthibo.leavenlock.ui.util

import android.content.Context
import android.text.format.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Clock times for display, following the phone's 12/24-hour setting (12-hour with AM/PM unless
 * the user turned on 24-hour time). Use these instead of hard-coded "HH:mm" patterns.
 */
object TimeFormat {

    /** e.g. "3:42 PM" or "15:42"; with [seconds], "3:42:07 PM" or "15:42:07". */
    fun clock(context: Context, epochMs: Long, seconds: Boolean = false): String =
        format(context, epochMs, if (seconds) "jms" else "jm")

    /** e.g. "Nov 5, 3:42 PM" or "Nov 5, 15:42". */
    fun dateAndClock(context: Context, epochMs: Long): String =
        format(context, epochMs, "MMMdjm")

    /** [skeleton] uses "j", which [pattern] swaps for the device's hour style. */
    private fun format(context: Context, epochMs: Long, skeleton: String): String {
        val locale = Locale.getDefault()
        val pattern = DateFormat.getBestDateTimePattern(locale, pattern(skeleton, DateFormat.is24HourFormat(context)))
        return SimpleDateFormat(pattern, locale).format(Date(epochMs))
    }

    /** The skeleton with "j" pinned to 12-hour ("h", plus AM/PM) or 24-hour ("H"). */
    internal fun pattern(skeleton: String, is24Hour: Boolean): String =
        skeleton.replace("j", if (is24Hour) "H" else "h")
}

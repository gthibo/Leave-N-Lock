package io.github.gthibo.leavenlock.widget

import io.github.gthibo.leavenlock.domain.detection.DetectionState

/** What the widget's one button offers. */
enum class WidgetAction {
    /** One tap sends "Lock now" through [io.github.gthibo.leavenlock.service.AutoLockService]. */
    LOCK,

    /**
     * Opens the app on the same unlock confirmation as Home's "Unlock now" (PIN, Armed mode).
     * The widget never unlocks in one tap.
     */
    UNLOCK,

    /** An auto-lock run is under way; the tap opens the app, where Cancel lives. */
    IN_FLIGHT,
}

/**
 * The widget action for the saved lock state ([lockState] is `CachedStatus.lockState`) and the
 * current auto-lock run. Only a saved LOCKED offers Unlock; unlocked or unknown keeps Lock now,
 * because locking is the safe default (the lock path still reads status before it sends).
 */
fun widgetActionFor(lockState: String, detection: DetectionState): WidgetAction = when {
    detection.isRunning() -> WidgetAction.IN_FLIGHT
    lockState == "LOCKED" -> WidgetAction.UNLOCK
    else -> WidgetAction.LOCK
}

/** A run between the trigger and its result. ARMED (watching, nothing sent) is not a run. */
private fun DetectionState.isRunning(): Boolean = when (this) {
    DetectionState.CONFIRMING, DetectionState.GRACE, DetectionState.VERIFYING,
    DetectionState.AWAITING_CONFIRM, DetectionState.LOCKING -> true
    else -> false
}

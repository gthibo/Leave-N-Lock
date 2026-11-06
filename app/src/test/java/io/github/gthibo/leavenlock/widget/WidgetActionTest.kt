package io.github.gthibo.leavenlock.widget

import io.github.gthibo.leavenlock.domain.detection.DetectionState
import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetActionTest {

    @Test
    fun `locked offers unlock`() {
        assertEquals(WidgetAction.UNLOCK, widgetActionFor("LOCKED", DetectionState.IDLE))
        assertEquals(WidgetAction.UNLOCK, widgetActionFor("LOCKED", DetectionState.ARMED))
    }

    @Test
    fun `unlocked offers lock`() {
        assertEquals(WidgetAction.LOCK, widgetActionFor("UNLOCKED", DetectionState.IDLE))
    }

    @Test
    fun `unknown or unreadable state offers lock, never unlock`() {
        assertEquals(WidgetAction.LOCK, widgetActionFor("UNKNOWN", DetectionState.IDLE))
        assertEquals(WidgetAction.LOCK, widgetActionFor("", DetectionState.IDLE))
        assertEquals(WidgetAction.LOCK, widgetActionFor("locked", DetectionState.IDLE))
    }

    @Test
    fun `a running auto-lock wins over the saved state`() {
        val running = listOf(
            DetectionState.CONFIRMING, DetectionState.GRACE, DetectionState.VERIFYING,
            DetectionState.AWAITING_CONFIRM, DetectionState.LOCKING,
        )
        for (d in running) {
            assertEquals(d.name, WidgetAction.IN_FLIGHT, widgetActionFor("LOCKED", d))
            assertEquals(d.name, WidgetAction.IN_FLIGHT, widgetActionFor("UNLOCKED", d))
        }
    }

    @Test
    fun `finished runs fall back to the saved state`() {
        val done = listOf(
            DetectionState.LOCKED, DetectionState.SKIPPED, DetectionState.ABORTED,
            DetectionState.ERROR, DetectionState.SENT_UNVERIFIED, DetectionState.NOT_LOCKED_OPEN,
        )
        for (d in done) {
            assertEquals(d.name, WidgetAction.UNLOCK, widgetActionFor("LOCKED", d))
            assertEquals(d.name, WidgetAction.LOCK, widgetActionFor("UNLOCKED", d))
        }
    }
}

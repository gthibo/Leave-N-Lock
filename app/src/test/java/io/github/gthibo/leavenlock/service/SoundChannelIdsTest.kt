package io.github.gthibo.leavenlock.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SoundChannelIdsTest {

    @Test
    fun `no custom sound uses the default lock channel`() {
        assertEquals("sound_lock_default", SoundChannelIds.lockChannelId(null))
        assertEquals("sound_lock_default", SoundChannelIds.lockChannelId(""))
        assertEquals("sound_lock_default", SoundChannelIds.lockChannelId("  "))
    }

    @Test
    fun `each custom sound gets its own stable channel`() {
        val a = SoundChannelIds.lockChannelId("content://media/internal/audio/media/12")
        val b = SoundChannelIds.lockChannelId("content://media/internal/audio/media/13")
        assertTrue(a.startsWith(SoundChannelIds.LOCK_PREFIX))
        assertNotEquals(a, b)
        assertNotEquals("sound_lock_default", a)
        assertEquals(a, SoundChannelIds.lockChannelId("content://media/internal/audio/media/12"))
    }

    @Test
    fun `stale lock channels are the other lock channels only`() {
        val current = SoundChannelIds.lockChannelId("content://x/1")
        val existing = listOf(
            "autolock_activity_v4",
            "autolock_activity_min_v1",
            SoundChannelIds.START,
            "sound_lock_default",
            current,
            "sound_lock_cdeadbeef",
        )
        assertEquals(
            listOf("sound_lock_default", "sound_lock_cdeadbeef"),
            SoundChannelIds.staleLockChannels(existing, current),
        )
        assertEquals(emptyList<String>(), SoundChannelIds.staleLockChannels(listOf(current), current))
    }
}

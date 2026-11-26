package io.github.gthibo.leavenlock.data.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FixRulesTest {

    private fun fix(accuracy: Float?, ageMs: Long = 0) = FixQuality(accuracyMeters = accuracy, ageMs = ageMs)

    @Test
    fun freshPreciseFixIsGoodEnough() {
        assertTrue(FixRules.isGoodEnough(fix(8f, ageMs = 1_000)))
        assertTrue(FixRules.isGoodEnough(fix(FixRules.GOOD_ACCURACY_METERS)))
    }

    @Test
    fun blockLevelFixIsNotGoodEnough() {
        // What balanced power typically returns: Wi-Fi / cell, ~100 m.
        assertFalse(FixRules.isGoodEnough(fix(120f)))
    }

    @Test
    fun fixWithoutAccuracyIsNotGoodEnough() {
        assertFalse(FixRules.isGoodEnough(fix(null)))
    }

    @Test
    fun staleFixIsNeverUsed() {
        // A cached fix from while still driving in: precise, but from the wrong place.
        val stale = fix(5f, ageMs = FixRules.MAX_FIX_AGE_MS + 1)
        assertFalse(FixRules.isFresh(stale))
        assertFalse(FixRules.isGoodEnough(stale))
        assertNull(FixRules.best(listOf("old" to stale)))
    }

    @Test
    fun fixFromTheFutureIsNotFresh() {
        assertFalse(FixRules.isFresh(fix(5f, ageMs = -60_000)))
        // Small clock jitter between the fix and our read is fine.
        assertTrue(FixRules.isFresh(fix(5f, ageMs = -500)))
    }

    @Test
    fun bestPicksTheMostAccurateFreshFix() {
        val best = FixRules.best(
            listOf(
                "balanced" to fix(90f),
                "gps" to fix(12f),
                "stale-gps" to fix(3f, ageMs = 60_000),
            )
        )
        assertEquals("gps", best)
    }

    @Test
    fun bestPrefersAnyAccuracyOverUnknown() {
        assertEquals("known", FixRules.best(listOf("unknown" to fix(null), "known" to fix(200f))))
    }

    @Test
    fun bestFallsBackToAFixWithUnknownAccuracy() {
        assertEquals("unknown", FixRules.best(listOf("unknown" to fix(null))))
    }

    @Test
    fun bestOfNothingIsNull() {
        assertNull(FixRules.best(emptyList<Pair<String, FixQuality>>()))
    }
}

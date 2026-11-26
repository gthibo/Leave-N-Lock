package io.github.gthibo.leavenlock.data.location

/** How good one location fix is: reported accuracy (68% radius, metres) and how old it is. */
data class FixQuality(val accuracyMeters: Float?, val ageMs: Long)

/**
 * Pure rules for choosing a location fix (no Android types, unit-tested in `FixRulesTest`).
 *
 * The parked spot used to come from a balanced-power fix (Wi-Fi/cell, often 100 m+ off) that
 * could also be up to a minute old, i.e. from while the car was still driving in. These rules
 * keep only fresh fixes and prefer the most accurate one.
 */
object FixRules {
    /** Oldest fix we accept. Walking pace (~1.4 m/s) moves you only a few metres in this time. */
    const val MAX_FIX_AGE_MS = 5_000L

    /** A fix this accurate (or better) is used straight away, without a fallback request. */
    const val GOOD_ACCURACY_METERS = 50f

    /** Tolerated clock jitter for a fix that looks slightly newer than "now". */
    private const val FUTURE_TOLERANCE_MS = 1_000L

    fun isFresh(q: FixQuality): Boolean = q.ageMs in -FUTURE_TOLERANCE_MS..MAX_FIX_AGE_MS

    fun isGoodEnough(q: FixQuality): Boolean =
        isFresh(q) && q.accuracyMeters != null && q.accuracyMeters <= GOOD_ACCURACY_METERS

    /** The most accurate fresh candidate (unknown accuracy ranks last), or null if none is fresh. */
    fun <T> best(candidates: List<Pair<T, FixQuality>>): T? =
        candidates
            .filter { isFresh(it.second) }
            .minByOrNull { it.second.accuracyMeters ?: Float.MAX_VALUE }
            ?.first
}

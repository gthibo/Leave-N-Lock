package io.github.gthibo.leavenlock.data.sound

/**
 * The soft EV-style chime, synthesised from notes (no recorded audio). Each tone is a bell-like
 * blend of a fundamental plus a couple of quiet harmonics, shaped by a quick attack and a gentle
 * exponential decay, with the notes overlapped into a shimmering arpeggio.
 *
 * The app no longer plays this itself: Android 17 mutes audio an app plays from a background
 * service, so the chimes ship as `res/raw/chime_lock.wav` / `chime_start.wav` and play as
 * notification sounds (see `service/SoundCues`). [render] is the single source for those files;
 * `EvChimeTest` fails if they drift and can rewrite them.
 */
object EvChime {

    const val SAMPLE_RATE = 44_100

    data class Note(val freqHz: Double, val startSec: Double, val durSec: Double, val gain: Double)

    // Ascending D5 · F#5 · A5 · D6 shimmer — a confident "secured" flourish.
    val LOCK = listOf(
        Note(587.33, 0.00, 0.55, 0.30),
        Note(739.99, 0.09, 0.55, 0.26),
        Note(880.00, 0.18, 0.65, 0.28),
        Note(1174.66, 0.28, 0.80, 0.24),
    )

    // A softer two-note cue for "auto-lock started" (A5 · D6).
    val START = listOf(
        Note(880.00, 0.00, 0.40, 0.26),
        Note(1174.66, 0.10, 0.50, 0.20),
    )

    /**
     * Mono 16-bit PCM at [SAMPLE_RATE]. Uses [StrictMath] so the output is bit-identical on every
     * JVM (the checked-in WAVs are compared against it).
     */
    fun render(notes: List<Note>): ShortArray {
        val totalSec = notes.maxOf { it.startSec + it.durSec } + 0.15
        val totalSamples = (totalSec * SAMPLE_RATE).toInt()
        val mix = DoubleArray(totalSamples)

        for (note in notes) {
            val startSample = (note.startSec * SAMPLE_RATE).toInt()
            val durSamples = (note.durSec * SAMPLE_RATE).toInt()
            val attack = (0.006 * SAMPLE_RATE).toInt().coerceAtLeast(1)
            val tau = note.durSec * 0.32
            val w = 2.0 * StrictMath.PI * note.freqHz
            for (i in 0 until durSamples) {
                val idx = startSample + i
                if (idx >= totalSamples) break
                val t = i.toDouble() / SAMPLE_RATE
                // Attack ramp then exponential decay.
                val env = (if (i < attack) i.toDouble() / attack else 1.0) * StrictMath.exp(-t / tau)
                val tone = StrictMath.sin(w * t) + 0.35 * StrictMath.sin(2 * w * t) +
                    0.12 * StrictMath.sin(3 * w * t)
                mix[idx] += note.gain * env * tone
            }
        }

        return ShortArray(totalSamples) { i ->
            (mix[i].coerceIn(-0.95, 0.95) * Short.MAX_VALUE).toInt().toShort()
        }
    }
}

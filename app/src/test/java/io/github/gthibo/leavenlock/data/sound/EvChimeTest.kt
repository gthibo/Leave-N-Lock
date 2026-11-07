package io.github.gthibo.leavenlock.data.sound

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * The bundled chimes (`res/raw/chime_*.wav`) must be exactly what [EvChime.render] produces.
 * After changing the notes, regenerate them with
 * `LEAVENLOCK_WRITE_CHIMES=1 ./gradlew testDebugUnitTest --tests '*EvChimeTest*'`.
 */
class EvChimeTest {

    private val rawDir = File("src/main/res/raw")

    private fun expected(notes: List<EvChime.Note>) =
        Wav.encodeMono16(EvChime.render(notes), EvChime.SAMPLE_RATE)

    private fun checkOrWrite(name: String, notes: List<EvChime.Note>) {
        val file = File(rawDir, name)
        val bytes = expected(notes)
        if (System.getenv("LEAVENLOCK_WRITE_CHIMES") == "1") {
            rawDir.mkdirs()
            file.writeBytes(bytes)
        }
        assertTrue("$file is missing; regenerate it (see class doc)", file.exists())
        assertArrayEquals("$file is out of date; regenerate it (see class doc)", bytes, file.readBytes())
    }

    @Test
    fun `bundled lock chime matches the synth`() = checkOrWrite("chime_lock.wav", EvChime.LOCK)

    @Test
    fun `bundled start chime matches the synth`() = checkOrWrite("chime_start.wav", EvChime.START)

    @Test
    fun `render length covers the last note plus tail`() {
        val samples = EvChime.render(EvChime.LOCK)
        val expectedSec = 0.28 + 0.80 + 0.15
        assertEquals((expectedSec * EvChime.SAMPLE_RATE).toInt(), samples.size)
        // Starts silent (attack ramp) and never clips past the 0.95 ceiling.
        assertEquals(0, samples[0].toInt())
        val ceiling = (0.95 * Short.MAX_VALUE).toInt()
        assertTrue(samples.all { it in -ceiling..ceiling })
        assertTrue(samples.any { it > 1000 })
    }

    @Test
    fun `wav header describes mono 16-bit pcm`() {
        val pcm = shortArrayOf(1, -2, 3)
        val wav = Wav.encodeMono16(pcm, 44_100)
        assertEquals(44 + 6, wav.size)
        assertEquals("RIFF", String(wav, 0, 4, Charsets.US_ASCII))
        assertEquals("WAVE", String(wav, 8, 4, Charsets.US_ASCII))
        assertEquals("data", String(wav, 36, 4, Charsets.US_ASCII))
        val b = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(36 + 6, b.getInt(4))
        assertEquals(1, b.getShort(20).toInt()) // PCM
        assertEquals(1, b.getShort(22).toInt()) // mono
        assertEquals(44_100, b.getInt(24))
        assertEquals(88_200, b.getInt(28))
        assertEquals(16, b.getShort(34).toInt())
        assertEquals(6, b.getInt(40))
        assertEquals(-2, b.getShort(46).toInt())
    }
}

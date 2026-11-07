package io.github.gthibo.leavenlock.data.sound

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Minimal RIFF/WAVE writer for mono 16-bit PCM (used to build the bundled chime files). */
object Wav {

    fun encodeMono16(samples: ShortArray, sampleRate: Int): ByteArray {
        val dataBytes = samples.size * 2
        val buf = ByteBuffer.allocate(44 + dataBytes).order(ByteOrder.LITTLE_ENDIAN)
        buf.put("RIFF".toByteArray(Charsets.US_ASCII))
        buf.putInt(36 + dataBytes)
        buf.put("WAVE".toByteArray(Charsets.US_ASCII))
        buf.put("fmt ".toByteArray(Charsets.US_ASCII))
        buf.putInt(16) // PCM fmt chunk size
        buf.putShort(1) // PCM
        buf.putShort(1) // mono
        buf.putInt(sampleRate)
        buf.putInt(sampleRate * 2) // byte rate
        buf.putShort(2) // block align
        buf.putShort(16) // bits per sample
        buf.put("data".toByteArray(Charsets.US_ASCII))
        buf.putInt(dataBytes)
        samples.forEach { buf.putShort(it) }
        return buf.array()
    }
}

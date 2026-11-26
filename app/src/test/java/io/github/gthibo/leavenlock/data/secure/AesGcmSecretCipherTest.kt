package io.github.gthibo.leavenlock.data.secure

import java.util.Base64
import javax.crypto.KeyGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AesGcmSecretCipherTest {
    private val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private val cipher = AesGcmSecretCipher { key }

    @Test
    fun roundTripsAsciiEmptyAndNonAscii() {
        for (value in listOf("1234", "", "pässwörd ✓")) {
            assertEquals(value, cipher.decrypt(cipher.encrypt(value, "service_pin"), "service_pin"))
        }
    }

    @Test
    fun sameValueEncryptsDifferently() {
        assertNotEquals(cipher.encrypt("1234", "a"), cipher.encrypt("1234", "a"))
    }

    @Test
    fun blobStartsWithV1Prefix() {
        assertTrue(cipher.encrypt("1234", "a").startsWith("v1:"))
    }

    @Test
    fun wrongAadFails() {
        val blob = cipher.encrypt("1234", "service_pin")
        assertThrows(Exception::class.java) { cipher.decrypt(blob, "us_password") }
    }

    @Test
    fun tamperedCiphertextFails() {
        val blob = cipher.encrypt("1234", "a")
        val bytes = Base64.getDecoder().decode(blob.removePrefix("v1:"))
        bytes[bytes.size - 1] = (bytes[bytes.size - 1].toInt() xor 1).toByte()
        val tampered = "v1:" + Base64.getEncoder().encodeToString(bytes)
        assertThrows(Exception::class.java) { cipher.decrypt(tampered, "a") }
    }

    @Test
    fun truncatedBlobFails() {
        assertThrows(Exception::class.java) { cipher.decrypt("v1:AAAA", "a") }
    }

    @Test
    fun unknownPrefixFails() {
        val blob = cipher.encrypt("1234", "a").replaceFirst("v1:", "v9:")
        assertThrows(Exception::class.java) { cipher.decrypt(blob, "a") }
    }
}

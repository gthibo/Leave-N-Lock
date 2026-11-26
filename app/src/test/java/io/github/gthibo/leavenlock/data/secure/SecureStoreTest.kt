package io.github.gthibo.leavenlock.data.secure

import java.security.ProviderException
import javax.crypto.AEADBadTagException
import javax.crypto.KeyGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SecureStoreTest {
    private val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private val fake = InMemorySharedPreferences()
    private val store = SecureStore(
        prefs = { fake },
        cipher = AesGcmSecretCipher { key },
        legacy = NoLegacySecrets,
        warn = {},
    )

    @Test
    fun savesAndLoadsPin() {
        store.savePin("1234")
        assertEquals("1234", store.loadPin())
    }

    @Test
    fun savesLoadsAndClearsUsCredentials() {
        store.savePin("1234")
        store.saveUsCredentials("me@x.com", "hunter2")
        assertEquals("me@x.com", store.loadUsUsername())
        assertEquals("hunter2", store.loadUsPassword())
        store.clearUsCredentials()
        assertNull(store.loadUsUsername())
        assertNull(store.loadUsPassword())
        assertEquals("1234", store.loadPin())
    }

    @Test
    fun clearEmptiesValuesButKeepsMarker() {
        store.savePin("1234")
        store.saveUsCredentials("me@x.com", "hunter2")
        store.clear()
        assertNull(store.loadPin())
        assertNull(store.loadUsUsername())
        assertNull(store.loadUsPassword())
        assertEquals(1, fake.getInt("migrated", 0))
    }

    @Test
    fun storedValuesNeverContainPlaintext() {
        store.savePin("1234")
        store.saveUsCredentials("me@x.com", "hunter2")
        val values = fake.all.values.filterIsInstance<String>()
        assertTrue(values.isNotEmpty())
        for (v in values) {
            assertFalse(v.contains("1234"))
            assertFalse(v.contains("hunter2"))
            assertFalse(v.contains("me@x.com"))
            assertTrue(v.startsWith("v1:"))
        }
    }

    @Test
    fun decryptFailureRemovesValueAndReturnsNull() {
        fake.edit().putString("service_pin", "v1:garbage").commit()
        assertNull(store.loadPin())
        assertFalse(fake.contains("service_pin"))
    }

    @Test
    fun missingValuesReturnNull() {
        assertNull(store.loadPin())
        assertNull(store.loadUsUsername())
        assertNull(store.loadUsPassword())
    }

    private fun storeWith(
        cipher: SecretCipher,
        warnings: MutableList<String> = mutableListOf(),
        primeKey: () -> Unit = {},
    ) = SecureStore({ fake }, cipher, NoLegacySecrets, { warnings += it }, primeKey)

    private val throwingCipher = AesGcmSecretCipher { throw ProviderException("keystore down") }

    @Test
    fun keystoreErrorOnDecryptKeepsValue() {
        store.savePin("1234")
        val warnings = mutableListOf<String>()
        val broken = storeWith(throwingCipher, warnings)
        assertNull(broken.loadPin())
        assertTrue(fake.contains("service_pin"))
        assertEquals(listOf("Secure value unreadable: ProviderException"), warnings)
        assertEquals("1234", store.loadPin())
    }

    @Test
    fun commitFailureWarnsAndStoresNothing() {
        val warnings = mutableListOf<String>()
        val s = storeWith(AesGcmSecretCipher { key }, warnings)
        s.loadPin() // open while commits still work
        fake.failCommits = true
        s.savePin("1234")
        assertEquals(listOf("Secure value not saved: commit failed"), warnings)
        assertFalse(fake.contains("service_pin"))
    }

    @Test
    fun encryptFailureWarnsAndStoresNothing() {
        val warnings = mutableListOf<String>()
        val s = storeWith(throwingCipher, warnings)
        s.saveUsCredentials("me@x.com", "hunter2")
        assertEquals(listOf("Secure value not saved: ProviderException"), warnings)
        assertFalse(fake.contains("us_username"))
        assertFalse(fake.contains("us_password"))
    }

    @Test
    fun warmUpPrimesKeyOnce() {
        var calls = 0
        val s = storeWith(AesGcmSecretCipher { key }) { calls++ }
        s.warmUp()
        s.warmUp()
        s.loadPin()
        assertEquals(1, calls)
    }

    @Test
    fun throwingPrimerIsWarnedNotThrown() {
        val warnings = mutableListOf<String>()
        val s = storeWith(AesGcmSecretCipher { key }, warnings) { throw ProviderException("x") }
        s.warmUp()
        assertEquals(listOf("Keystore key unavailable: ProviderException"), warnings)
    }

    /** Wraps a real cipher, counts decrypts, and runs [beforeDecrypt] before each one. */
    private class CountingCipher(
        private val inner: SecretCipher,
        var beforeDecrypt: (Int) -> Unit = {},
    ) : SecretCipher {
        var decrypts = 0
            private set

        override fun encrypt(plain: String, aad: String) = inner.encrypt(plain, aad)
        override fun decrypt(blob: String, aad: String): String {
            decrypts++
            beforeDecrypt(decrypts)
            return inner.decrypt(blob, aad)
        }
    }

    @Test
    fun loadsServedFromCacheAfterOpen() {
        store.savePin("1234")
        val counting = CountingCipher(AesGcmSecretCipher { key })
        val s = storeWith(counting)
        s.warmUp()
        val afterWarmUp = counting.decrypts
        assertEquals(1, afterWarmUp)
        repeat(5) { assertEquals("1234", s.loadPin()) }
        assertEquals(afterWarmUp, counting.decrypts)
    }

    @Test
    fun transientDecryptFailureIsRetriedNextCall() {
        store.savePin("1234")
        val counting = CountingCipher(AesGcmSecretCipher { key }) { n ->
            if (n == 1) throw ProviderException("strongbox busy")
        }
        val s = storeWith(counting)
        assertNull(s.loadPin())
        assertTrue(fake.contains("service_pin"))
        assertEquals("1234", s.loadPin())
        assertTrue(fake.contains("service_pin"))
    }

    @Test
    fun saveUpdatesCacheAndFailedSaveDoesNot() {
        store.savePin("1234")
        assertEquals("1234", store.loadPin())
        fake.failCommits = true
        store.savePin("5678")
        assertEquals("1234", store.loadPin())
    }

    @Test
    fun clearRemovesFromCache() {
        store.saveUsCredentials("me@x.com", "hunter2")
        val counting = CountingCipher(AesGcmSecretCipher { key })
        val s = storeWith(counting)
        s.warmUp()
        val before = counting.decrypts
        s.clearUsCredentials()
        assertNull(s.loadUsUsername())
        assertNull(s.loadUsPassword())
        assertEquals(before, counting.decrypts)
    }

    @Test
    fun badBlobRemovalDoesNotDeleteConcurrentSave() {
        store.savePin("1234")
        val newer = fake.getString("service_pin", null)
        fake.edit().putString("service_pin", "v1:garbage").commit()
        val counting = CountingCipher(AesGcmSecretCipher { key }) { n ->
            if (n == 1) {
                // A newer value lands between the read of the blob and its removal.
                fake.edit().putString("service_pin", newer).commit()
                throw AEADBadTagException("tampered")
            }
        }
        val s = storeWith(counting)
        assertNull(s.loadPin())
        assertEquals(newer, fake.getString("service_pin", null))
        assertEquals("1234", s.loadPin())
    }
}

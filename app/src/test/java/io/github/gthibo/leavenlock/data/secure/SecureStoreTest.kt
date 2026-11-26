package io.github.gthibo.leavenlock.data.secure

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
}

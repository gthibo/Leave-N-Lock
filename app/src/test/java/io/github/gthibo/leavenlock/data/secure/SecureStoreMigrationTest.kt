package io.github.gthibo.leavenlock.data.secure

import java.security.ProviderException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.crypto.KeyGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SecureStoreMigrationTest {
    private val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private val fake = InMemorySharedPreferences()
    private val warnings = mutableListOf<String>()

    /** Old store stand-in. [exists] is true until deleted, when secrets or a read failure are set. */
    private class FakeLegacy(
        var secrets: LegacySecrets? = null,
        var throwOnRead: Boolean = false,
    ) : LegacySecretSource {
        private val readCount = AtomicInteger()
        val reads get() = readCount.get()
        var deletes = 0
            private set

        override fun exists(): Boolean = deletes == 0 && (secrets != null || throwOnRead)
        override fun read(): LegacySecrets {
            readCount.incrementAndGet()
            if (throwOnRead) throw ProviderException("old key gone")
            return secrets!!
        }
        override fun delete() {
            deletes++
        }
    }

    private fun store(legacy: LegacySecretSource, cipher: SecretCipher = AesGcmSecretCipher { key }) =
        SecureStore({ fake }, cipher, legacy, { synchronized(warnings) { warnings += it } })

    private val marker get() = fake.getInt("migrated", 0)

    @Test
    fun freshInstallSetsMarkerWithoutReadingLegacy() {
        val legacy = FakeLegacy()
        store(legacy).warmUp()
        assertEquals(0, legacy.reads)
        assertEquals(1, marker)
    }

    @Test
    fun migratesPinAndCredentials() {
        val legacy = FakeLegacy(LegacySecrets("1234", "me@x.com", "hunter2"))
        val s = store(legacy)
        assertEquals("1234", s.loadPin())
        assertEquals("me@x.com", s.loadUsUsername())
        assertEquals("hunter2", s.loadUsPassword())
        assertEquals(1, legacy.deletes)
        assertEquals(1, marker)
        for (v in fake.all.values.filterIsInstance<String>()) {
            assertTrue(v.startsWith("v1:"))
            assertFalse(v.contains("1234") || v.contains("hunter2") || v.contains("me@x.com"))
        }
        assertEquals(emptyList<String>(), warnings)
    }

    @Test
    fun migratesPinOnly() {
        val legacy = FakeLegacy(LegacySecrets("1234", null, null))
        val s = store(legacy)
        assertEquals("1234", s.loadPin())
        assertNull(s.loadUsUsername())
        assertNull(s.loadUsPassword())
        assertFalse(fake.contains("us_username"))
        assertFalse(fake.contains("us_password"))
        assertEquals(1, legacy.deletes)
        assertEquals(1, marker)
    }

    @Test
    fun unreadableOldStoreStartsEmptyAndDeletesIt() {
        val legacy = FakeLegacy(throwOnRead = true)
        val s = store(legacy)
        assertNull(s.loadPin())
        assertNull(s.loadUsUsername())
        assertNull(s.loadUsPassword())
        assertEquals(1, marker)
        assertEquals(1, legacy.deletes)
        assertEquals(listOf("Old secure store unreadable: ProviderException"), warnings)
    }

    @Test
    fun failedWriteKeepsOldFileAndRetries() {
        val legacy = FakeLegacy(LegacySecrets("1234", "me@x.com", "hunter2"))
        fake.failCommits = true
        val first = store(legacy)
        assertNull(first.loadPin())
        assertNull(first.loadUsUsername())
        assertEquals(0, legacy.deletes)
        assertFalse(fake.contains("migrated"))
        assertEquals(1, legacy.reads) // no retry within the same instance
        assertEquals(listOf("Migration not saved: commit failed"), warnings)

        fake.failCommits = false
        val second = store(legacy)
        assertEquals("1234", second.loadPin())
        assertEquals("me@x.com", second.loadUsUsername())
        assertEquals("hunter2", second.loadUsPassword())
        assertEquals(1, legacy.deletes)
        assertEquals(1, marker)
    }

    @Test
    fun saveAfterFailedMigrationIsNotOverwrittenOnRetry() {
        val legacy = FakeLegacy(LegacySecrets("1234", "me@x.com", "hunter2"))
        fake.failCommits = true
        val first = store(legacy)
        assertNull(first.loadPin())
        fake.failCommits = false
        first.savePin("9999")

        val second = store(legacy)
        assertEquals("9999", second.loadPin())
        assertEquals("me@x.com", second.loadUsUsername())
        assertEquals("hunter2", second.loadUsPassword())
        assertEquals(1, legacy.deletes)
        assertEquals(1, marker)
    }

    @Test
    fun cipherFailureKeepsOldFile() {
        val legacy = FakeLegacy(LegacySecrets("1234", "me@x.com", "hunter2"))
        val s = store(legacy, AesGcmSecretCipher { throw ProviderException("keystore down") })
        assertNull(s.loadPin())
        assertEquals(0, legacy.deletes)
        assertFalse(fake.contains("migrated"))
        assertTrue(fake.all.isEmpty())
        assertEquals(listOf("Migration not saved: ProviderException"), warnings)
    }

    @Test
    fun markerSetWithLingeringOldFileDeletesItWithoutRecopying() {
        store(NoLegacySecrets).savePin("9999")
        assertEquals(1, marker)
        val legacy = FakeLegacy(LegacySecrets("1234", null, null))
        val s = store(legacy)
        assertEquals("9999", s.loadPin())
        assertEquals(0, legacy.reads)
        assertEquals(1, legacy.deletes)
    }

    @Test
    fun failedDeleteIsWarnedAndRetriedNextOpen() {
        var failDelete = true
        val legacy = object : LegacySecretSource {
            var deletes = 0
            override fun exists() = deletes == 0
            override fun read() = LegacySecrets("1234", null, null)
            override fun delete() {
                if (failDelete) throw SecurityException("nope")
                deletes++
            }
        }
        assertEquals("1234", store(legacy).loadPin())
        assertEquals(1, marker)
        assertEquals(listOf("Old secure store not deleted: SecurityException"), warnings)

        failDelete = false
        assertEquals("1234", store(legacy).loadPin())
        assertEquals(1, legacy.deletes)
    }

    @Test
    fun concurrentFirstOpenMigratesOnce() {
        val legacy = FakeLegacy(LegacySecrets("1234", null, null))
        val s = store(legacy)
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(8)
        val results = (1..8).map {
            pool.submit<String?> {
                start.await()
                s.loadPin()
            }
        }
        start.countDown()
        val pins = results.map { it.get(10, TimeUnit.SECONDS) }
        pool.shutdown()
        assertEquals(1, legacy.reads)
        assertEquals(List(8) { "1234" }, pins)
    }
}

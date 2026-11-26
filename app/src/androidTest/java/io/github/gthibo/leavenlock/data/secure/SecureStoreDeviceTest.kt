package io.github.gthibo.leavenlock.data.secure

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.EntryPointAccessors
import io.github.gthibo.leavenlock.AutoLockApp
import java.io.File
import java.security.KeyStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Runs SecureStore against the real Android Keystore and the real old EncryptedSharedPreferences. */
@RunWith(AndroidJUnit4::class)
class SecureStoreDeviceTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val legacy = LegacySecureStoreReader(context)
    private val legacyFile get() = File(context.filesDir.parent, "shared_prefs/autolock_secure.xml")

    @Before
    fun wipe() {
        // Let the app's own start-up warm-up finish first so it can't race with the test.
        EntryPointAccessors.fromApplication(context, AutoLockApp.AppEntryPoint::class.java)
            .secureStore().warmUp()
        context.getSharedPreferences("secure_v2", Context.MODE_PRIVATE).edit().clear().commit()
        legacy.delete()
    }

    @Test
    fun keystoreRoundTripSurvivesReopen() {
        SecureStore(context).savePin("2468")

        assertEquals("2468", SecureStore(context).loadPin())
    }

    @Test
    fun migratesRealEncryptedSharedPreferences() {
        assertTrue(
            legacy.open().edit()
                .putString("service_pin", "1357")
                .putString("us_username", "driver@example.com")
                .putString("us_password", "test-password")
                .commit(),
        )
        assertTrue(legacyFile.exists())

        val store = SecureStore(context)
        store.warmUp()

        assertEquals("1357", store.loadPin())
        assertEquals("driver@example.com", store.loadUsUsername())
        assertEquals("test-password", store.loadUsPassword())
        assertFalse(legacyFile.exists())
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        assertFalse(keyStore.containsAlias("_androidx_security_master_key_"))
    }
}

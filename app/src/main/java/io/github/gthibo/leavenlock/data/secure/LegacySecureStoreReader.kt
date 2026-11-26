@file:Suppress("DEPRECATION")

package io.github.gthibo.leavenlock.data.secure

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.io.File
import java.security.KeyStore

/**
 * Reads the old EncryptedSharedPreferences store ("autolock_secure") so [SecureStore] can migrate
 * it once. The only file that touches androidx.security.crypto; it opens the file exactly as the
 * old SecureStore did. Never logs values.
 */
class LegacySecureStoreReader(private val context: Context) : LegacySecretSource {

    override fun exists(): Boolean =
        File(context.filesDir.parent, "shared_prefs/$PREFS_FILE.xml").exists()

    override fun read(): LegacySecrets {
        val prefs = open()
        return LegacySecrets(
            pin = prefs.getString(KEY_PIN, null),
            usUsername = prefs.getString(KEY_US_USERNAME, null),
            usPassword = prefs.getString(KEY_US_PASSWORD, null),
        )
    }

    override fun delete() {
        context.deleteSharedPreferences(PREFS_FILE)
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (keyStore.containsAlias(MASTER_KEY_ALIAS)) keyStore.deleteEntry(MASTER_KEY_ALIAS)
    }

    private fun buildMasterKey(): MasterKey {
        val builder = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        // Prefer hardware-backed StrongBox when the device has a secure element.
        return runCatching {
            builder.setRequestStrongBoxBacked(true).build()
        }.getOrElse {
            MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        }
    }

    /** Opens the old file as the old SecureStore did; internal so device tests can seed it. */
    internal fun open(): SharedPreferences =
        EncryptedSharedPreferences.create(
            context,
            PREFS_FILE,
            buildMasterKey(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )

    internal companion object {
        const val PREFS_FILE = "autolock_secure"
        const val MASTER_KEY_ALIAS = "_androidx_security_master_key_"
        const val KEY_PIN = "service_pin"
        const val KEY_US_USERNAME = "us_username"
        const val KEY_US_PASSWORD = "us_password"
    }
}

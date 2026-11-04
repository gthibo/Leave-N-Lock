package io.github.gthibo.leavenlock.data.secure

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Encrypted storage backed by the Android Keystore. Holds the BlueLink PIN and sign-in.
 */
@Singleton
class SecureStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val prefs: SharedPreferences by lazy { openPrefs() }

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

    private fun createEncryptedPrefs(): SharedPreferences =
        EncryptedSharedPreferences.create(
            context,
            PREFS_FILE,
            buildMasterKey(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )

    /**
     * Opens the encrypted store. If the keystore entry or file is corrupted (e.g. after a
     * restore or key rotation), we wipe and recreate rather than crash — the user simply
     * re-authenticates. No plaintext fallback is ever used.
     */
    private fun openPrefs(): SharedPreferences = try {
        createEncryptedPrefs()
    } catch (_: Exception) {
        context.deleteSharedPreferences(PREFS_FILE)
        runCatching { File(context.filesDir.parent, "shared_prefs/$PREFS_FILE.xml").delete() }
        createEncryptedPrefs()
    }

    /** BlueLink service PIN, sent with every remote lock/unlock command. */
    fun savePin(pin: String) = prefs.edit().putString(KEY_PIN, pin).apply()
    fun loadPin(): String? = prefs.getString(KEY_PIN, null)

    /**
     * US BlueLink email + password (encrypted). Hyundai US tokens last ~30 min and refreshing
     * them requires the password, so background auto-lock can't stay signed in without it.
     * Cleared on US sign-out or a failed US sign-in. Never logged, never backed up.
     */
    fun saveUsCredentials(username: String, password: String) = prefs.edit()
        .putString(KEY_US_USERNAME, username)
        .putString(KEY_US_PASSWORD, password)
        .apply()
    fun loadUsUsername(): String? = prefs.getString(KEY_US_USERNAME, null)
    fun loadUsPassword(): String? = prefs.getString(KEY_US_PASSWORD, null)
    fun clearUsCredentials() = prefs.edit().remove(KEY_US_USERNAME).remove(KEY_US_PASSWORD).apply()

    fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val PREFS_FILE = "autolock_secure"
        const val KEY_PIN = "service_pin"
        const val KEY_US_USERNAME = "us_username"
        const val KEY_US_PASSWORD = "us_password"
    }
}

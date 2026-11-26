package io.github.gthibo.leavenlock.data.secure

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Storage for the BlueLink PIN and US sign-in. Each value is an AES-GCM blob (key in the Android
 * Keystore) in a plain prefs file, with the pref key as AAD. Never logs values.
 */
@Singleton
class SecureStore internal constructor(
    prefs: () -> SharedPreferences,
    private val cipher: SecretCipher,
    private val legacy: LegacySecretSource,
    private val warn: (String) -> Unit,
) {
    @Inject
    constructor(@ApplicationContext context: Context) : this(
        prefs = { context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE) },
        cipher = AesGcmSecretCipher { KeystoreKey.getOrCreate() },
        legacy = NoLegacySecrets,
        warn = { Log.w("SecureStore", it) },
    )

    private val prefs: SharedPreferences by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { open(prefs) }

    private fun open(source: () -> SharedPreferences): SharedPreferences {
        val opened = source()
        if (!opened.contains(KEY_MIGRATED) && !legacy.exists()) {
            opened.edit().putInt(KEY_MIGRATED, 1).commit()
        }
        return opened
    }

    /** Opens the store now, off the hot path. */
    fun warmUp() {
        prefs
    }

    /** BlueLink service PIN, sent with every remote lock/unlock command. */
    fun savePin(pin: String) = save(KEY_PIN to pin)
    fun loadPin(): String? = load(KEY_PIN)

    /**
     * US BlueLink email + password. Hyundai US tokens last ~30 min and refreshing them requires the
     * password, so background auto-lock can't stay signed in without it. Cleared on US sign-out or a
     * failed US sign-in. Never logged, never backed up.
     */
    fun saveUsCredentials(username: String, password: String) =
        save(KEY_US_USERNAME to username, KEY_US_PASSWORD to password)
    fun loadUsUsername(): String? = load(KEY_US_USERNAME)
    fun loadUsPassword(): String? = load(KEY_US_PASSWORD)
    fun clearUsCredentials() {
        prefs.edit().remove(KEY_US_USERNAME).remove(KEY_US_PASSWORD).commit()
    }

    /** Removes the secrets; keeps the migration marker so old data is never resurrected. */
    fun clear() {
        prefs.edit().remove(KEY_PIN).remove(KEY_US_USERNAME).remove(KEY_US_PASSWORD).commit()
    }

    private fun save(vararg entries: Pair<String, String>) {
        val editor = prefs.edit()
        entries.forEach { (key, value) -> editor.putString(key, cipher.encrypt(value, key)) }
        editor.commit()
    }

    private fun load(key: String): String? {
        val blob = prefs.getString(key, null) ?: return null
        return try {
            cipher.decrypt(blob, key)
        } catch (e: Exception) {
            warn("Secure value unreadable: ${e.javaClass.simpleName}")
            prefs.edit().remove(key).commit()
            null
        }
    }

    private companion object {
        const val PREFS_FILE = "secure_v2"
        const val KEY_MIGRATED = "migrated"
        const val KEY_PIN = "service_pin"
        const val KEY_US_USERNAME = "us_username"
        const val KEY_US_PASSWORD = "us_password"
    }
}

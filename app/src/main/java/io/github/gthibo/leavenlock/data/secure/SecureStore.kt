package io.github.gthibo.leavenlock.data.secure

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.crypto.AEADBadTagException
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
    private val primeKey: () -> Unit = {},
) {
    @Inject
    constructor(@ApplicationContext context: Context) : this(
        prefs = { context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE) },
        cipher = AesGcmSecretCipher { KeystoreKey.getOrCreate() },
        legacy = LegacySecureStoreReader(context),
        warn = { Log.w("SecureStore", it) },
        primeKey = { KeystoreKey.getOrCreate() },
    )

    private val source = prefs
    private val lock = Any()
    private var opened: SharedPreferences? = null

    /** Decrypted values, guarded by [lock]. In memory only, never logged. */
    private val cache = HashMap<String, String>()

    /**
     * Runs [block] under [lock] with the opened prefs, opening them first if needed. `justOpened`
     * is true when this call did the open (which already tried every value once).
     */
    private fun <T> withPrefs(block: (prefs: SharedPreferences, justOpened: Boolean) -> T): T =
        synchronized(lock) {
            opened?.let { return@synchronized block(it, false) }
            val p = open(source)
            opened = p
            block(p, true)
        }

    private fun open(source: () -> SharedPreferences): SharedPreferences {
        val opened = source()
        try {
            primeKey()
        } catch (e: Exception) {
            warn("Keystore key unavailable: ${e.javaClass.simpleName}")
        }
        try {
            migrate(opened)
        } catch (e: Exception) {
            warn("Migration not saved: ${e.javaClass.simpleName}")
        }
        SECRET_KEYS.forEach { decryptLocked(opened, it) }
        return opened
    }

    /**
     * Moves the old store's values into [target] once (design §5). Runs inside the synchronized
     * open, so once per instance. A failure before the commit leaves both files untouched and is
     * retried by the next instance (next process); until then reads return null.
     */
    private fun migrate(target: SharedPreferences) {
        if (target.contains(KEY_MIGRATED)) {
            if (legacy.exists()) deleteLegacy()
            return
        }
        if (!legacy.exists()) {
            target.edit().putInt(KEY_MIGRATED, 1).commit()
            return
        }
        val old = try {
            legacy.read()
        } catch (e: Exception) {
            warn("Old secure store unreadable: ${e.javaClass.simpleName}")
            if (target.edit().putInt(KEY_MIGRATED, 1).commit()) {
                deleteLegacy()
            } else {
                warn("Migration not saved: commit failed")
            }
            return
        }
        // Without a marker, a value already in the target was saved after a failed migration,
        // so it is newer than the old store's: never overwrite it.
        val encrypted = listOf(KEY_PIN to old.pin, KEY_US_USERNAME to old.usUsername,
            KEY_US_PASSWORD to old.usPassword)
            .filter { (key, _) -> !target.contains(key) }
            .mapNotNull { (key, value) -> value?.let { key to cipher.encrypt(it, key) } }
        val editor = target.edit()
        encrypted.forEach { (key, blob) -> editor.putString(key, blob) }
        editor.putInt(KEY_MIGRATED, 1)
        if (editor.commit()) {
            deleteLegacy()
        } else {
            warn("Migration not saved: commit failed")
        }
    }

    private fun deleteLegacy() {
        try {
            legacy.delete()
        } catch (e: Exception) {
            warn("Old secure store not deleted: ${e.javaClass.simpleName}")
        }
    }

    /**
     * Opens the store, primes the Keystore key and decrypts every value into memory now, so callers
     * can do it off the main thread.
     */
    fun warmUp() {
        withPrefs { _, _ -> }
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
    fun clearUsCredentials() = remove(KEY_US_USERNAME, KEY_US_PASSWORD)

    /** Removes the secrets; keeps the migration marker so old data is never resurrected. */
    fun clear() = remove(*SECRET_KEYS)

    private fun remove(vararg keys: String) = withPrefs { prefs, _ ->
        val editor = prefs.edit()
        keys.forEach { editor.remove(it) }
        editor.commit()
        keys.forEach { cache.remove(it) }
    }

    private fun save(vararg entries: Pair<String, String>) = withPrefs { prefs, _ ->
        try {
            val encrypted = entries.map { (key, value) -> key to cipher.encrypt(value, key) }
            val editor = prefs.edit()
            encrypted.forEach { (key, blob) -> editor.putString(key, blob) }
            if (editor.commit()) {
                cache.putAll(entries)
            } else {
                warn("Secure value not saved: commit failed")
            }
        } catch (e: Exception) {
            warn("Secure value not saved: ${e.javaClass.simpleName}")
        }
    }

    /** Served from memory; a value the open could not decrypt is tried again on the next call. */
    private fun load(key: String): String? = withPrefs { prefs, justOpened ->
        cache[key] ?: if (justOpened) null else decryptLocked(prefs, key)
    }

    /** Decrypts [key] into [cache]. Call only while holding [lock]. */
    private fun decryptLocked(prefs: SharedPreferences, key: String): String? {
        val blob = prefs.getString(key, null) ?: return null
        return try {
            cipher.decrypt(blob, key).also { cache[key] = it }
        } catch (e: Exception) {
            warn("Secure value unreadable: ${e.javaClass.simpleName}")
            // Only drop the value when the blob itself is provably bad; a transient Keystore
            // error must not destroy a secret that may still decrypt later. Remove it only if it
            // is still the blob we read, so a newer value is never deleted.
            if ((e is AEADBadTagException || e is IllegalArgumentException ||
                    e is KeyPermanentlyInvalidatedException) &&
                prefs.getString(key, null) == blob
            ) {
                prefs.edit().remove(key).commit()
            }
            null
        }
    }

    private companion object {
        const val PREFS_FILE = "secure_v2"
        const val KEY_MIGRATED = "migrated"
        const val KEY_PIN = "service_pin"
        const val KEY_US_USERNAME = "us_username"
        const val KEY_US_PASSWORD = "us_password"
        val SECRET_KEYS = arrayOf(KEY_PIN, KEY_US_USERNAME, KEY_US_PASSWORD)
    }
}

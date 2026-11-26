package io.github.gthibo.leavenlock.data.secure

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.ProviderException
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * The AES-256-GCM key for [AesGcmSecretCipher], held in the Android Keystore. Deliberately has no
 * user-authentication or unlocked-device requirement: auto-lock reads the PIN while the phone is locked.
 */
object KeystoreKey {
    private const val PROVIDER = "AndroidKeyStore"

    /** Key handles by alias, cached after the first successful get or create. */
    private val cached = HashMap<String, SecretKey>()

    @Synchronized
    fun getOrCreate(alias: String = "leavenlock_secure_v1"): SecretKey =
        cached[alias] ?: getOrGenerate(alias).also { cached[alias] = it }

    private fun getOrGenerate(alias: String): SecretKey {
        val keyStore = KeyStore.getInstance(PROVIDER).apply { load(null) }
        (keyStore.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                return generate(alias, strongBox = true)
            } catch (_: GeneralSecurityException) {
                // StrongBox rejected the spec; fall through to the regular Keystore.
            } catch (_: ProviderException) {
                // No secure element (StrongBoxUnavailableException) or an OEM StrongBox failure.
            }
        }
        return generate(alias, strongBox = false)
    }

    private fun generate(alias: String, strongBox: Boolean): SecretKey {
        val spec = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .apply {
                if (strongBox && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) setIsStrongBoxBacked(true)
            }
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
            .apply { init(spec) }
            .generateKey()
    }
}

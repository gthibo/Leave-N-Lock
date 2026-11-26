package io.github.gthibo.leavenlock.data.secure

import java.security.GeneralSecurityException
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Encrypts short secrets to a self-describing string blob. Never log plain values or blobs. */
interface SecretCipher {
    /** Returns `"v1:" + Base64(iv ‖ ciphertext)`; [aad] binds the blob to its storage slot. */
    fun encrypt(plain: String, aad: String): String

    /**
     * Throws [GeneralSecurityException] on any failure (wrong key/AAD, tampering) or
     * [IllegalArgumentException] for a malformed blob.
     */
    fun decrypt(blob: String, aad: String): String
}

/** AES-256-GCM. [key] is read on every call so a Keystore key can be (re)created lazily. */
class AesGcmSecretCipher(private val key: () -> SecretKey) : SecretCipher {

    override fun encrypt(plain: String, aad: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        // No IV supplied: Keystore keys require the provider to generate it.
        cipher.init(Cipher.ENCRYPT_MODE, key())
        cipher.updateAAD(aad.toByteArray(Charsets.UTF_8))
        val ct = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return PREFIX + Base64.getEncoder().encodeToString(cipher.iv + ct)
    }

    override fun decrypt(blob: String, aad: String): String {
        require(blob.startsWith(PREFIX)) { "Unknown blob version" }
        val raw = Base64.getDecoder().decode(blob.substring(PREFIX.length))
        require(raw.size >= IV_BYTES + TAG_BYTES) { "Blob too short" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, raw, 0, IV_BYTES))
        cipher.updateAAD(aad.toByteArray(Charsets.UTF_8))
        return String(cipher.doFinal(raw, IV_BYTES, raw.size - IV_BYTES), Charsets.UTF_8)
    }

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val PREFIX = "v1:"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
        const val TAG_BYTES = TAG_BITS / 8
    }
}

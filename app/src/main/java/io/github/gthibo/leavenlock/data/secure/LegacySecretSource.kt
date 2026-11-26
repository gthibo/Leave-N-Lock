package io.github.gthibo.leavenlock.data.secure

/** Secrets held by the previous (EncryptedSharedPreferences) store. */
data class LegacySecrets(
    val pin: String?,
    val usUsername: String?,
    val usPassword: String?,
) {
    override fun toString(): String = "LegacySecrets(redacted)"
}

/** Reads and removes the old encrypted file so its contents can move to the new store. */
interface LegacySecretSource {
    fun exists(): Boolean
    fun read(): LegacySecrets
    fun delete()
}

/** Used when there is nothing to migrate. */
object NoLegacySecrets : LegacySecretSource {
    override fun exists(): Boolean = false
    override fun read(): LegacySecrets = LegacySecrets(null, null, null)
    override fun delete() = Unit
}

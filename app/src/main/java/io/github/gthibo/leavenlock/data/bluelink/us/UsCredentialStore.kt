package io.github.gthibo.leavenlock.data.bluelink.us

/** US sign-in details. The password is needed because Hyundai US tokens last ~30 min and
 *  even the refresh grant requires it (hyundai_kia_connect_api `refresh_access_token`). */
data class UsCredentials(val username: String, val password: String) {
    override fun toString(): String = "UsCredentials(<redacted>)"
}

/**
 * Where [UsBlueLinkClient] keeps the US sign-in between app restarts. In the app this is the
 * encrypted `SecureStore` (Keystore-backed, never logged or backed up); tests use a fake.
 */
interface UsCredentialStore {
    fun load(): UsCredentials?
    fun save(credentials: UsCredentials)
    fun clear()

    /** Keeps nothing: sign-in lives in memory only. */
    object None : UsCredentialStore {
        override fun load(): UsCredentials? = null
        override fun save(credentials: UsCredentials) {}
        override fun clear() {}
    }
}

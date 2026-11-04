package io.github.gthibo.leavenlock.data.bluelink

import io.github.gthibo.leavenlock.data.bluelink.us.UsBlueLinkClient
import io.github.gthibo.leavenlock.data.bluelink.us.UsCredentialStore
import io.github.gthibo.leavenlock.data.bluelink.us.UsCredentials
import io.github.gthibo.leavenlock.data.bluelink.fake.FakeBlueLinkClient
import io.github.gthibo.leavenlock.data.metrics.ApiMetrics
import io.github.gthibo.leavenlock.data.secure.SecureStore
import io.github.gthibo.leavenlock.data.settings.AppSettings
import io.github.gthibo.leavenlock.domain.ActivityLog
import io.github.gthibo.leavenlock.domain.LogLevel
import io.ktor.client.HttpClient
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Chooses the right [BlueLinkClient] for the current settings and caches it.
 * Demo mode -> fake client, otherwise the US BlueLink client. Every client is wrapped
 * in [MeteredBlueLinkClient] so all calls feed the Statistics screen.
 */
@Singleton
class BlueLinkProvider @Inject constructor(
    private val httpClient: HttpClient,
    private val secureStore: SecureStore,
    private val fakeClient: FakeBlueLinkClient,
    private val metrics: ApiMetrics,
    private val activityLog: ActivityLog,
) {
    @Volatile private var cached: Pair<Boolean, BlueLinkClient>? = null

    fun client(settings: AppSettings): BlueLinkClient {
        val key = settings.demoMode
        cached?.let { if (it.first == key) return it.second }
        val client = build(settings)
        cached = key to client
        return client
    }

    private fun build(settings: AppSettings): BlueLinkClient {
        val base = if (settings.demoMode) {
            fakeClient
        } else {
            UsBlueLinkClient(
                httpClient,
                pinProvider = { secureStore.loadPin() },
                credentialStore = SecureUsCredentialStore(secureStore),
                diag = { activityLog.add(LogLevel.INFO, it) },
            )
        }
        return MeteredBlueLinkClient(base, metrics)
    }

    fun invalidate() { cached = null }
}

/** Adapts the encrypted [SecureStore] to the US client's credential locker. */
internal class SecureUsCredentialStore(private val secureStore: SecureStore) : UsCredentialStore {
    override fun load(): UsCredentials? {
        val user = secureStore.loadUsUsername() ?: return null
        val pass = secureStore.loadUsPassword() ?: return null
        return UsCredentials(user, pass)
    }
    override fun save(credentials: UsCredentials) =
        secureStore.saveUsCredentials(credentials.username, credentials.password)
    override fun clear() = secureStore.clearUsCredentials()
}

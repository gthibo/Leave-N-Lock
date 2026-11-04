package io.github.gthibo.leavenlock.data.bluelink

import io.github.gthibo.leavenlock.data.bluelink.fake.FakeBlueLinkClient
import io.github.gthibo.leavenlock.data.metrics.ApiMetrics
import io.github.gthibo.leavenlock.data.secure.SecureStore
import io.github.gthibo.leavenlock.data.settings.AppSettings
import io.github.gthibo.leavenlock.domain.ActivityLog
import io.ktor.client.HttpClient
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure mock tests: every collaborator is a mock, and building a client performs no I/O, so these
 * never touch the network or a real vehicle.
 */
class BlueLinkProviderTest {

    private val fake = mockk<FakeBlueLinkClient>(relaxed = true)
    private val provider = BlueLinkProvider(
        httpClient = mockk<HttpClient>(relaxed = true),
        secureStore = mockk<SecureStore>(relaxed = true),
        fakeClient = fake,
        metrics = mockk<ApiMetrics>(relaxed = true),
        activityLog = ActivityLog(),
    )

    private fun settings(demo: Boolean = false) = AppSettings(demoMode = demo)

    // ---- which client is chosen -------------------------------------------------------------

    @Test
    fun demoModeUsesTheFakeClient() = runTest {
        coEvery { fake.isAuthenticated() } returns true
        val client = provider.client(settings(demo = true))
        assertTrue(client.isAuthenticated())
        coVerify(exactly = 1) { fake.isAuthenticated() }
    }

    @Test
    fun withoutDemoModeTheRealUsClientIsUsedNotTheFake() = runTest {
        // Nothing saved in the (mocked) secure store, so the real US client isn't signed in.
        val client = provider.client(settings(demo = false))
        assertFalse(client.isAuthenticated())
        coVerify(exactly = 0) { fake.isAuthenticated() }
    }

    @Test
    fun everyClientIsWrappedSoCallsFeedTheStatisticsScreen() = runTest {
        assertTrue(provider.client(settings(demo = false)) is MeteredBlueLinkClient)
        assertTrue(provider.client(settings(demo = true)) is MeteredBlueLinkClient)
    }

    // ---- caching ----------------------------------------------------------------------------

    @Test
    fun theSameSettingsReuseTheCachedClient() {
        val first = provider.client(settings())
        assertSame(first, provider.client(settings()))
    }

    @Test
    fun settingsThatDoNotAffectTheClientDoNotRebuildIt() {
        val first = provider.client(settings())
        val second = provider.client(settings().copy(enabled = true, graceSeconds = 99))
        assertSame(first, second)
    }

    @Test
    fun togglingDemoModeBuildsANewClient() {
        val real = provider.client(settings(demo = false))
        val demo = provider.client(settings(demo = true))
        assertNotSame(real, demo)
    }

    @Test
    fun invalidateForcesAFreshClientEvenForIdenticalSettings() {
        val first = provider.client(settings())
        provider.invalidate()
        assertNotSame(first, provider.client(settings()))
    }
}

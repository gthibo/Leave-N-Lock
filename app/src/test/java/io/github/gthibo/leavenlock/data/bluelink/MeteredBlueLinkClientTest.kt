package io.github.gthibo.leavenlock.data.bluelink

import io.github.gthibo.leavenlock.data.bluelink.model.CommandResult
import io.github.gthibo.leavenlock.data.bluelink.model.Vehicle
import io.github.gthibo.leavenlock.data.bluelink.model.VehicleStatus
import io.github.gthibo.leavenlock.data.metrics.ApiMetrics
import io.github.gthibo.leavenlock.data.metrics.ApiOutcome
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.confirmVerified
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Pure mock tests: the wrapped client is a mock, so no network and no real BlueLink calls.
 * They pin down what gets written to [ApiMetrics] for every kind of call.
 */
class MeteredBlueLinkClientTest {

    private val delegate = mockk<BlueLinkClient>()
    private val metrics = mockk<ApiMetrics>(relaxed = true)
    private val client = MeteredBlueLinkClient(delegate, metrics)

    // ---- pass-through calls that are NOT recorded -------------------------------------------

    @Test
    fun isAuthenticatedPassesThroughAndIsNotRecorded() = runTest {
        coEvery { delegate.isAuthenticated() } returns true
        assertTrue(client.isAuthenticated())
        verify(exactly = 0) { metrics.record(any(), any(), any(), any()) }
    }

    @Test
    fun clearSessionPassesThroughWithoutRecording() = runTest {
        coEvery { delegate.clearSession() } returns Unit
        client.clearSession()
        coVerify(exactly = 1) { delegate.clearSession() }
        verify(exactly = 0) { metrics.record(any(), any(), any(), any()) }
    }

    // ---- ensureFreshSession -----------------------------------------------------------------

    @Test
    fun freshSessionIsRecordedAsSuccess() = runTest {
        coEvery { delegate.ensureFreshSession() } returns true
        assertTrue(client.ensureFreshSession())
        verify(exactly = 1) { metrics.record("ensureFreshSession", any(), ApiOutcome.SUCCESS, null) }
    }

    @Test
    fun staleSessionIsRecordedAsUnauthenticatedAndReturnsFalse() = runTest {
        coEvery { delegate.ensureFreshSession() } returns false
        assertEquals(false, client.ensureFreshSession())
        verify(exactly = 1) { metrics.record("ensureFreshSession", any(), ApiOutcome.UNAUTHENTICATED, null) }
    }

    // ---- command results: outcome mapping ---------------------------------------------------

    @Test
    fun lockSuccessIsRecordedAsSuccessWithNoDetail() = runTest {
        val result = CommandResult.Success("Locked")
        coEvery { delegate.lock("v1") } returns result
        assertSame(result, client.lock("v1"))
        verify(exactly = 1) { metrics.record("lock", any(), ApiOutcome.SUCCESS, null) }
    }

    @Test
    fun lockFailureIsRecordedAsFailureWithTheReasonAsDetail() = runTest {
        val result = CommandResult.Failure("5031 Unavailable")
        coEvery { delegate.lock("v1") } returns result
        assertSame(result, client.lock("v1"))
        verify(exactly = 1) { metrics.record("lock", any(), ApiOutcome.FAILURE, "5031 Unavailable") }
    }

    @Test
    fun rateLimitedIsRecordedAsRateLimited() = runTest {
        coEvery { delegate.lock("v1") } returns CommandResult.RateLimited
        assertSame(CommandResult.RateLimited, client.lock("v1"))
        verify(exactly = 1) { metrics.record("lock", any(), ApiOutcome.RATE_LIMITED, null) }
    }

    @Test
    fun notAuthenticatedIsRecordedAsUnauthenticated() = runTest {
        coEvery { delegate.lock("v1") } returns CommandResult.NotAuthenticated
        assertSame(CommandResult.NotAuthenticated, client.lock("v1"))
        verify(exactly = 1) { metrics.record("lock", any(), ApiOutcome.UNAUTHENTICATED, null) }
    }

    // ---- operation names --------------------------------------------------------------------

    @Test
    fun everyCommandIsRecordedUnderItsOwnOperationName() = runTest {
        val ok = CommandResult.Success("ok")
        coEvery { delegate.unlock("v1") } returns ok
        coEvery { delegate.loginWithPassword("u", "pw") } returns ok

        client.unlock("v1")
        client.loginWithPassword("u", "pw")

        verify(exactly = 1) { metrics.record("unlock", any(), ApiOutcome.SUCCESS, null) }
        verify(exactly = 1) { metrics.record("loginWithPassword", any(), ApiOutcome.SUCCESS, null) }
    }

    @Test
    fun vehiclesAreReturnedAndRecorded() = runTest {
        val list = listOf(mockk<Vehicle>())
        coEvery { delegate.vehicles() } returns list
        assertSame(list, client.vehicles())
        verify(exactly = 1) { metrics.record("vehicles", any(), ApiOutcome.SUCCESS, null) }
    }

    @Test
    fun statusIsRecordedDifferentlyForForcedAndNormalReads() = runTest {
        val status = mockk<VehicleStatus>()
        coEvery { delegate.status("v1", false) } returns status
        coEvery { delegate.status("v1", true) } returns status

        assertSame(status, client.status("v1", forceRefresh = false))
        assertSame(status, client.status("v1", forceRefresh = true))

        verify(exactly = 1) { metrics.record("status", any(), ApiOutcome.SUCCESS, null) }
        verify(exactly = 1) { metrics.record("status(refresh)", any(), ApiOutcome.SUCCESS, null) }
    }

    // ---- exceptions -------------------------------------------------------------------------

    @Test
    fun aThrowingCommandIsRecordedAsFailureWithTheMessageAndRethrown() = runTest {
        val boom = IllegalStateException("socket closed")
        coEvery { delegate.lock("v1") } throws boom
        try {
            client.lock("v1")
            fail("expected the exception to propagate")
        } catch (t: IllegalStateException) {
            assertSame(boom, t)
        }
        verify(exactly = 1) { metrics.record("lock", any(), ApiOutcome.FAILURE, "socket closed") }
    }

    @Test
    fun aThrowingStatusReadIsRecordedAsFailureAndRethrown() = runTest {
        coEvery { delegate.status("v1", true) } throws RuntimeException("timeout")
        try {
            client.status("v1", forceRefresh = true)
            fail("expected the exception to propagate")
        } catch (t: RuntimeException) {
            assertEquals("timeout", t.message)
        }
        verify(exactly = 1) { metrics.record("status(refresh)", any(), ApiOutcome.FAILURE, "timeout") }
    }

    @Test
    fun aThrowingSessionRefreshIsRecordedAsFailureAndRethrown() = runTest {
        coEvery { delegate.ensureFreshSession() } throws RuntimeException("dns")
        try {
            client.ensureFreshSession()
            fail("expected the exception to propagate")
        } catch (t: RuntimeException) {
            assertEquals("dns", t.message)
        }
        verify(exactly = 1) { metrics.record("ensureFreshSession", any(), ApiOutcome.FAILURE, "dns") }
    }

    // ---- privacy + timing -------------------------------------------------------------------

    @Test
    fun successMessagesAreNeverCopiedIntoTheMetrics() = runTest {
        // A success message may carry account details; only Failure reasons are kept as detail.
        coEvery { delegate.loginWithPassword("me@example.com", "pw") } returns
            CommandResult.Success("Signed in as me@example.com")
        client.loginWithPassword("me@example.com", "pw")
        verify(exactly = 1) { metrics.record("loginWithPassword", any(), ApiOutcome.SUCCESS, null) }
        confirmVerified(metrics)
    }

    @Test
    fun recordedDurationIsNeverNegative() = runTest {
        coEvery { delegate.lock("v1") } returns CommandResult.Success("ok")
        val duration = slot<Long>()
        client.lock("v1")
        verify { metrics.record("lock", capture(duration), any(), any()) }
        assertTrue(duration.captured >= 0)
    }
}

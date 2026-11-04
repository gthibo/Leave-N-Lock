package io.github.gthibo.leavenlock.domain

import android.content.Context
import android.location.Location
import io.github.gthibo.leavenlock.R
import io.github.gthibo.leavenlock.data.bluelink.BlueLinkClient
import io.github.gthibo.leavenlock.data.bluelink.BlueLinkProvider
import io.github.gthibo.leavenlock.data.bluelink.model.CommandResult
import io.github.gthibo.leavenlock.data.bluelink.model.LockState
import io.github.gthibo.leavenlock.data.bluelink.model.VehicleStatus
import io.github.gthibo.leavenlock.data.location.LocationHelper
import io.github.gthibo.leavenlock.data.location.ParkedPlace
import io.github.gthibo.leavenlock.data.settings.AppSettings
import io.github.gthibo.leavenlock.data.settings.RunMode
import io.github.gthibo.leavenlock.data.settings.SettingsRepository
import io.github.gthibo.leavenlock.data.status.StatusCache
import io.github.gthibo.leavenlock.domain.detection.DetectionState
import io.github.gthibo.leavenlock.service.AutoLockNotification
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AutoLockControllerTest {

    private class Fixture(testScope: CoroutineScope) {
        val settings = MutableStateFlow(
            AppSettings(
                enabled = true,
                runMode = RunMode.ARMED,
                vehicleId = "test-vehicle",
                accountEmail = "test@example.invalid",
                useActivityRecognition = false,
                useGeofence = false,
                graceSeconds = 1,
                requireConfirmationBeforeLock = false,
                rememberParkedLocation = false,
                showStatusInNotification = false,
                notificationFields = emptySet(),
                departureSummary = false,
                retryWindowMinutes = 0,
                verifyLock = true,
                dontLockIfOpen = true,
            )
        )

        val client = mockk<BlueLinkClient>()
        val repository = mockk<SettingsRepository>()
        val cache = mockk<StatusCache>(relaxed = true)
        val locationHelper = mockk<LocationHelper>(relaxed = true)
        val log = ActivityLog()
        val context = mockk<Context>(relaxed = true)
        val controller: AutoLockController

        init {
            every { context.getString(any<Int>()) } returns "test"

            every { repository.settings } returns settings
            // Saving settings writes straight back into the fake settings flow, like the real thing would.
            coEvery { repository.update(any()) } coAnswers {
                val transform = firstArg<(AppSettings) -> AppSettings>()
                settings.value = transform(settings.value)
            }

            val provider = mockk<BlueLinkProvider>()
            every { provider.client(any()) } returns client

            coEvery { client.ensureFreshSession() } returns true
            coEvery { client.lock(any()) } returns
                    CommandResult.Success("Test command accepted")

            controller = AutoLockController(
                appContext = context,
                settingsRepo = repository,
                provider = provider,
                locationHelper = locationHelper,
                statusCache = cache,
                log = log,
            )

            controller.scope.coroutineContext[Job]?.cancel()
            controller.scope = testScope
        }

        fun unlocked(
            timestamp: Long = System.currentTimeMillis(),
            doorOpen: Boolean? = false,
            windowOpen: Boolean? = false,
        ) = VehicleStatus(
            lockState = LockState.UNLOCKED,
            engineRunning = false,
            batteryCharging = null,
            timestamp = timestamp,
            anyDoorOpen = doorOpen,
            anyWindowOpen = windowOpen,
        )

        fun locked(timestamp: Long = System.currentTimeMillis()) = VehicleStatus(
            lockState = LockState.LOCKED,
            engineRunning = false,
            batteryCharging = null,
            timestamp = timestamp,
            anyDoorOpen = false,
            anyWindowOpen = false,
        )
    }

    @Test
    fun dryRunReachesEvaluationButSendsNoCommand() = runTest {
        val f = Fixture(backgroundScope)
        f.settings.value = f.settings.value.copy(
            runMode = RunMode.DRY_RUN,
        )
        coEvery { f.client.status(any(), any()) } returns f.unlocked()

        f.controller.onTriggerFired()
        runCurrent()

        assertEquals(
            DetectionState.GRACE,
            f.controller.state.value.detection,
        )

        advanceTimeBy(1_000)
        runCurrent()

        coVerify(exactly = 1) { f.client.status("test-vehicle", false) }
        coVerify(exactly = 0) { f.client.lock(any()) }
        assertEquals(
            DetectionState.SKIPPED,
            f.controller.state.value.detection,
        )
        assertNull(f.controller.state.value.lastLockAtEpochMs)
        assertTrue(
            f.log.entries.value.any {
                it.message.contains("DRY RUN")
            }
        )
    }

    @Test
    fun watchingOffDuringGraceBlocksCommand() = runTest {
        val f = Fixture(backgroundScope)
        coEvery { f.client.status(any(), any()) } returns f.unlocked()

        f.controller.onTriggerFired()
        runCurrent()

        assertEquals(
            DetectionState.GRACE,
            f.controller.state.value.detection,
        )

        f.settings.value = f.settings.value.copy(enabled = false)

        advanceTimeBy(1_000)
        runCurrent()

        coVerify(exactly = 1) { f.client.status("test-vehicle", false) }
        coVerify(exactly = 0) { f.client.lock(any()) }
        assertEquals(
            DetectionState.ABORTED,
            f.controller.state.value.detection,
        )
        assertNull(f.controller.state.value.lastLockAtEpochMs)
        assertTrue(
            f.log.entries.value.any {
                it.message.contains("Watching is off")
            }
        )
    }

    @Test
    fun dryRunDuringGraceBlocksCommand() = runTest {
        val f = Fixture(backgroundScope)
        coEvery { f.client.status(any(), any()) } returns f.unlocked()

        f.controller.onTriggerFired()
        runCurrent()

        assertEquals(
            DetectionState.GRACE,
            f.controller.state.value.detection,
        )

        f.settings.value = f.settings.value.copy(runMode = RunMode.DRY_RUN)

        advanceTimeBy(1_000)
        runCurrent()

        coVerify(exactly = 1) { f.client.status("test-vehicle", false) }
        coVerify(exactly = 0) { f.client.lock(any()) }
        assertEquals(
            DetectionState.ABORTED,
            f.controller.state.value.detection,
        )
        assertNull(f.controller.state.value.lastLockAtEpochMs)
        assertTrue(
            f.log.entries.value.any {
                it.message.contains("Dry Run is selected")
            }
        )
        coVerify(exactly = 1) { f.cache.saveStatus(any(), any()) }
    }

    @Test
    fun unreadableVerificationDoesNotRecordConfirmedLock() = runTest {
        val f = Fixture(backgroundScope)
        var reads = 0

        coEvery { f.client.status(any(), any()) } coAnswers {
            reads++
            if (reads == 1) f.unlocked()
            else throw IllegalStateException("Test read failure")
        }

        f.controller.onTriggerFired()
        runCurrent()
        advanceTimeBy(1_000)
        runCurrent()

        coVerify(exactly = 1) { f.client.lock("test-vehicle") }
        coVerify(exactly = 2) { f.client.status("test-vehicle", false) }
        assertEquals(
            DetectionState.ERROR,
            f.controller.state.value.detection,
        )
        assertNull(f.controller.state.value.lastLockAtEpochMs)

        // Only the pre-command status should have been cached.
        coVerify(exactly = 1) { f.cache.saveStatus(any(), any()) }
    }

    @Test
    fun unlockedVerificationDoesNotSendAnotherLock() = runTest {
        val f = Fixture(backgroundScope)
        coEvery { f.client.status(any(), any()) } returns f.unlocked()

        f.controller.onTriggerFired()
        runCurrent()
        advanceTimeBy(1_000)
        runCurrent()

        coVerify(exactly = 1) { f.client.lock("test-vehicle") }
        coVerify(exactly = 2) { f.client.status("test-vehicle", false) }
        assertEquals(
            DetectionState.ERROR,
            f.controller.state.value.detection,
        )
        assertNull(f.controller.state.value.lastLockAtEpochMs)
        coVerify(exactly = 1) { f.cache.saveStatus(any(), any()) }
    }

    @Test
    fun successfulLockVerificationConfirmsLockedAndStampsTime() = runTest {
        val f = Fixture(backgroundScope)
        val preLockTime = System.currentTimeMillis() - 10_000L
        val postLockTime = System.currentTimeMillis() - 1_000L
        var reads = 0

        coEvery { f.client.status(any(), any()) } coAnswers {
            reads++
            if (reads == 1) f.unlocked(preLockTime)
            else f.locked(postLockTime)
        }

        f.controller.onTriggerFired()
        runCurrent()

        assertEquals(
            DetectionState.GRACE,
            f.controller.state.value.detection,
        )

        advanceTimeBy(1_000)
        runCurrent()

        coVerify(exactly = 1) { f.client.lock("test-vehicle") }
        coVerify(exactly = 2) { f.client.status("test-vehicle", false) }
        assertEquals(
            DetectionState.LOCKED,
            f.controller.state.value.detection,
        )
        val stampedTime = f.controller.state.value.lastLockAtEpochMs
        assertNotNull(stampedTime)
        assertTrue(stampedTime!! >= postLockTime)

        // Both pre-lock status and verified locked status are cached.
        coVerify(exactly = 2) { f.cache.saveStatus(any(), any()) }

        assertTrue(
            f.log.entries.value.any {
                it.message.contains("A newer cached status reports locked")
            }
        )
    }

    @Test
    fun staleVerificationTimestampDoesNotRecordConfirmedLock() = runTest {
        val f = Fixture(backgroundScope)
        val snapshotTime = System.currentTimeMillis() - 10_000L
        var reads = 0

        coEvery { f.client.status(any(), any()) } coAnswers {
            reads++
            if (reads == 1) f.unlocked(snapshotTime)
            // Even though lockState reports LOCKED, the timestamp hasn't advanced past the pre-lock read.
            else f.locked(snapshotTime)
        }

        f.controller.onTriggerFired()
        runCurrent()
        advanceTimeBy(1_000)
        runCurrent()

        coVerify(exactly = 1) { f.client.lock("test-vehicle") }
        coVerify(exactly = 2) { f.client.status("test-vehicle", false) }
        assertEquals(
            DetectionState.ERROR,
            f.controller.state.value.detection,
        )
        assertNull(f.controller.state.value.lastLockAtEpochMs)

        // Only the pre-command status should be cached.
        coVerify(exactly = 1) { f.cache.saveStatus(any(), any()) }

        assertTrue(
            f.log.entries.value.any {
                it.message.contains("not newer than the pre-command snapshot")
            }
        )
    }

    @Test
    fun futureVerificationTimestampDoesNotRecordConfirmedLock() = runTest {
        val f = Fixture(backgroundScope)
        val now = System.currentTimeMillis()
        val preLockTime = now - 10_000L
        val futureTimestamp = now + 60_000L // 1 minute in the future
        var reads = 0

        coEvery { f.client.status(any(), any()) } coAnswers {
            reads++
            if (reads == 1) f.unlocked(preLockTime)
            else f.locked(futureTimestamp)
        }

        f.controller.onTriggerFired()
        runCurrent()
        advanceTimeBy(1_000)
        runCurrent()

        coVerify(exactly = 1) { f.client.lock("test-vehicle") }
        coVerify(exactly = 2) { f.client.status("test-vehicle", false) }
        assertEquals(
            DetectionState.ERROR,
            f.controller.state.value.detection,
        )
        assertNull(f.controller.state.value.lastLockAtEpochMs)
        coVerify(exactly = 1) { f.cache.saveStatus(any(), any()) }

        assertTrue(
            f.log.entries.value.any {
                it.message.contains("outside the allowed age")
            }
        )
    }

    @Test
    fun unknownVerificationTimestampDoesNotRecordConfirmedLock() = runTest {
        val f = Fixture(backgroundScope)
        val now = System.currentTimeMillis()
        val preLockTime = now - 10_000L
        val unknownTimestamp = 0L
        var reads = 0

        coEvery { f.client.status(any(), any()) } coAnswers {
            reads++
            if (reads == 1) f.unlocked(preLockTime)
            else f.locked(unknownTimestamp)
        }

        f.controller.onTriggerFired()
        runCurrent()
        advanceTimeBy(1_000)
        runCurrent()

        coVerify(exactly = 1) { f.client.lock("test-vehicle") }
        coVerify(exactly = 2) { f.client.status("test-vehicle", false) }
        assertEquals(
            DetectionState.ERROR,
            f.controller.state.value.detection,
        )
        assertNull(f.controller.state.value.lastLockAtEpochMs)
        coVerify(exactly = 1) { f.cache.saveStatus(any(), any()) }

        assertTrue(
            f.log.entries.value.any {
                it.message.contains("outside the allowed age")
            }
        )
    }

    @Test
    fun userConfirmationRequiredLocksWhenConfirmed() = runTest {
        val f = Fixture(backgroundScope)
        f.settings.value = f.settings.value.copy(
            requireConfirmationBeforeLock = true,
        )
        val preLockTime = System.currentTimeMillis() - 10_000L
        val postLockTime = System.currentTimeMillis() - 1_000L
        var reads = 0

        coEvery { f.client.status(any(), any()) } coAnswers {
            reads++
            if (reads == 1) f.unlocked(preLockTime)
            else f.locked(postLockTime)
        }

        f.controller.onTriggerFired()
        runCurrent()

        assertEquals(
            DetectionState.GRACE,
            f.controller.state.value.detection,
        )

        advanceTimeBy(1_000)
        runCurrent()

        assertEquals(
            DetectionState.AWAITING_CONFIRM,
            f.controller.state.value.detection,
        )
        coVerify(exactly = 0) { f.client.lock(any()) }

        f.controller.lockNow()
        runCurrent()
        advanceTimeBy(250)
        runCurrent()

        coVerify(exactly = 1) { f.client.lock("test-vehicle") }
        coVerify(exactly = 2) { f.client.status("test-vehicle", false) }
        assertEquals(
            DetectionState.LOCKED,
            f.controller.state.value.detection,
        )
        assertNotNull(f.controller.state.value.lastLockAtEpochMs)
        assertTrue(
            f.log.entries.value.any {
                it.message.contains("Lock confirmed")
            }
        )
    }

    @Test
    fun userConfirmationRequiredAbortsOnTimeout() = runTest {
        val f = Fixture(backgroundScope)
        var currentTime = 1_700_000_000_000L
        f.controller.clock = { currentTime }
        f.settings.value = f.settings.value.copy(
            requireConfirmationBeforeLock = true,
        )
        val preLockTime = currentTime - 10_000L
        coEvery { f.client.status(any(), any()) } returns f.unlocked(preLockTime)

        f.controller.onTriggerFired()
        runCurrent()

        assertEquals(
            DetectionState.GRACE,
            f.controller.state.value.detection,
        )

        advanceTimeBy(1_000)
        runCurrent()

        assertEquals(
            DetectionState.AWAITING_CONFIRM,
            f.controller.state.value.detection,
        )
        coVerify(exactly = 0) { f.client.lock(any()) }

        // Advance past the 120-second confirmation deadline
        currentTime += 120_001L
        advanceTimeBy(250)
        runCurrent()

        assertEquals(
            DetectionState.ABORTED,
            f.controller.state.value.detection,
        )
        assertNull(f.controller.state.value.lastLockAtEpochMs)
        coVerify(exactly = 0) { f.client.lock(any()) }
        assertTrue(
            f.log.entries.value.any {
                it.message.contains("Lock not confirmed in time — aborted")
            }
        )
    }

    @Test
    fun openDoorSkipsLockEvaluationWhenGuardEnabled() = runTest {
        val f = Fixture(backgroundScope)
        f.settings.value = f.settings.value.copy(
            dontLockIfOpen = true,
        )
        coEvery { f.client.status(any(), any()) } returns f.unlocked(doorOpen = true)

        f.controller.onTriggerFired()
        runCurrent()

        assertEquals(
            DetectionState.GRACE,
            f.controller.state.value.detection,
        )

        advanceTimeBy(1_000)
        runCurrent()

        coVerify(exactly = 1) { f.client.status("test-vehicle", false) }
        coVerify(exactly = 0) { f.client.lock(any()) }
        assertEquals(
            DetectionState.NOT_LOCKED_OPEN,
            f.controller.state.value.detection,
        )
        assertNull(f.controller.state.value.lastLockAtEpochMs)
        assertTrue(
            f.log.entries.value.any {
                it.message.contains("Skipped: A door is open.")
            }
        )
    }

    @Test
    fun openWindowSkipsLockEvaluationWhenGuardEnabled() = runTest {
        val f = Fixture(backgroundScope)
        f.settings.value = f.settings.value.copy(
            dontLockIfOpen = true,
        )
        coEvery { f.client.status(any(), any()) } returns f.unlocked(windowOpen = true)

        f.controller.onTriggerFired()
        runCurrent()

        advanceTimeBy(1_000)
        runCurrent()

        coVerify(exactly = 1) { f.client.status("test-vehicle", false) }
        coVerify(exactly = 0) { f.client.lock(any()) }
        assertEquals(
            DetectionState.NOT_LOCKED_OPEN,
            f.controller.state.value.detection,
        )
        assertNull(f.controller.state.value.lastLockAtEpochMs)
        assertTrue(
            f.log.entries.value.any {
                it.message.contains("Skipped: A window is open.")
            }
        )
    }

    @Test
    fun openDoorAllowsLockWhenGuardDisabled() = runTest {
        val f = Fixture(backgroundScope)
        f.settings.value = f.settings.value.copy(
            dontLockIfOpen = false,
        )
        val preLockTime = System.currentTimeMillis() - 10_000L
        val postLockTime = System.currentTimeMillis() - 1_000L
        var reads = 0

        coEvery { f.client.status(any(), any()) } coAnswers {
            reads++
            if (reads == 1) f.unlocked(timestamp = preLockTime, doorOpen = true)
            else f.locked(postLockTime)
        }

        f.controller.onTriggerFired()
        runCurrent()
        advanceTimeBy(1_000)
        runCurrent()

        coVerify(exactly = 1) { f.client.lock("test-vehicle") }
        assertEquals(
            DetectionState.LOCKED,
            f.controller.state.value.detection,
        )
    }

    @Test
    fun outsideActiveHoursScheduleSkipsEvaluation() = runTest {
        val f = Fixture(backgroundScope)
        f.settings.value = f.settings.value.copy(
            scheduleEnabled = true,
            scheduleStartMinutes = 8 * 60, // 08:00
            scheduleEndMinutes = 20 * 60,  // 20:00
        )
        val cal = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 22)
            set(java.util.Calendar.MINUTE, 30)
        }
        f.controller.clock = { cal.timeInMillis }

        f.controller.onTriggerFired()
        runCurrent()

        assertEquals(
            DetectionState.SKIPPED,
            f.controller.state.value.detection,
        )
        assertEquals(0, f.controller.state.value.graceRemaining)
        coVerify(exactly = 0) { f.client.status(any(), any()) }
        coVerify(exactly = 0) { f.client.lock(any()) }
        assertTrue(
            f.log.entries.value.any {
                it.message.contains("Outside active hours — not locking.")
            }
        )
    }

    @Test
    fun insideActiveHoursScheduleProceedsWithEvaluation() = runTest {
        val f = Fixture(backgroundScope)
        f.settings.value = f.settings.value.copy(
            scheduleEnabled = true,
            scheduleStartMinutes = 8 * 60, // 08:00
            scheduleEndMinutes = 20 * 60,  // 20:00
        )
        val cal = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 14)
            set(java.util.Calendar.MINUTE, 0)
        }
        f.controller.clock = { cal.timeInMillis }

        f.controller.onTriggerFired()
        runCurrent()

        assertEquals(
            DetectionState.GRACE,
            f.controller.state.value.detection,
        )
    }

    @Test
    fun userCancelDuringGraceAbortsFlow() = runTest {
        val f = Fixture(backgroundScope)
        f.controller.onTriggerFired()
        runCurrent()

        assertEquals(
            DetectionState.GRACE,
            f.controller.state.value.detection,
        )

        f.controller.cancel()
        runCurrent()

        assertEquals(
            DetectionState.ABORTED,
            f.controller.state.value.detection,
        )
        assertEquals(0, f.controller.state.value.graceRemaining)

        advanceTimeBy(2_000)
        runCurrent()

        assertEquals(
            DetectionState.ABORTED,
            f.controller.state.value.detection,
        )
        coVerify(exactly = 0) { f.client.status(any(), any()) }
        coVerify(exactly = 0) { f.client.lock(any()) }
    }

    @Test
    fun retriableLockFailureRetriesAndRecovers() = runTest {
        val f = Fixture(backgroundScope)
        f.settings.value = f.settings.value.copy(
            retryWindowMinutes = 2,
        )
        val preLockTime = System.currentTimeMillis() - 10_000L
        val postLockTime = System.currentTimeMillis() - 1_000L
        var lockAttempts = 0
        var statusReads = 0

        coEvery { f.client.status(any(), any()) } coAnswers {
            statusReads++
            if (statusReads == 1) f.unlocked(preLockTime)
            else f.locked(postLockTime)
        }
        coEvery { f.client.lock(any()) } coAnswers {
            lockAttempts++
            if (lockAttempts == 1) CommandResult.Failure("5031 Unavailable")
            else CommandResult.Success("Locked")
        }

        f.controller.onTriggerFired()
        runCurrent()
        advanceTimeBy(1_000) // grace countdown
        runCurrent()

        // First attempt failed and entered the 20s backoff delay
        coVerify(exactly = 1) { f.client.lock("test-vehicle") }
        assertTrue(
            f.log.entries.value.any {
                it.message.contains("Lock retry 1 in")
            }
        )

        // Advance through backoff delay
        advanceTimeBy(20_000)
        runCurrent()

        // Second attempt succeeded and verified
        coVerify(exactly = 2) { f.client.lock("test-vehicle") }
        assertEquals(
            DetectionState.LOCKED,
            f.controller.state.value.detection,
        )
        assertNotNull(f.controller.state.value.lastLockAtEpochMs)
    }

    @Test
    fun nonRetriableLockFailureFailsImmediately() = runTest {
        val f = Fixture(backgroundScope)
        f.settings.value = f.settings.value.copy(
            retryWindowMinutes = 0,
        )
        coEvery { f.client.status(any(), any()) } returns f.unlocked()
        coEvery { f.client.lock(any()) } returns CommandResult.Failure("Vehicle offline")

        f.controller.onTriggerFired()
        runCurrent()
        advanceTimeBy(1_000)
        runCurrent()

        coVerify(exactly = 1) { f.client.lock("test-vehicle") }
        assertEquals(
            DetectionState.ERROR,
            f.controller.state.value.detection,
        )
        assertNull(f.controller.state.value.lastLockAtEpochMs)
        assertTrue(
            f.log.entries.value.any {
                it.message.contains("Lock failed: Vehicle offline")
            }
        )
    }

    @Test
    fun activityRecognitionRequiredWaitsForWalkingConfirmation() = runTest {
        val f = Fixture(backgroundScope)
        f.settings.value = f.settings.value.copy(
            useActivityRecognition = true,
            useGeofence = false,
        )
        val preLockTime = System.currentTimeMillis() - 10_000L
        val postLockTime = System.currentTimeMillis() - 1_000L
        var reads = 0

        coEvery { f.client.status(any(), any()) } coAnswers {
            reads++
            if (reads == 1) f.unlocked(preLockTime)
            else f.locked(postLockTime)
        }

        f.controller.onTriggerFired()
        runCurrent()

        assertEquals(
            DetectionState.CONFIRMING,
            f.controller.state.value.detection,
        )

        f.controller.onWalkingConfirmed()
        runCurrent()

        assertEquals(
            DetectionState.GRACE,
            f.controller.state.value.detection,
        )

        // Advance past delay(250) in confirmation loop, then 1s grace countdown
        advanceTimeBy(250)
        advanceTimeBy(1_000)
        runCurrent()

        coVerify(exactly = 1) { f.client.lock("test-vehicle") }
        assertEquals(
            DetectionState.LOCKED,
            f.controller.state.value.detection,
        )
    }

    @Test
    fun activityRecognitionRequiredAbortsWhenNoConfirmationAndRequired() = runTest {
        val f = Fixture(backgroundScope)
        var testTime = 1_000_000L
        f.controller.clock = { testTime }
        f.settings.value = f.settings.value.copy(
            useActivityRecognition = true,
            useGeofence = false,
            requireWalkAwayConfirmation = true,
        )

        f.controller.onTriggerFired()
        runCurrent()

        assertEquals(
            DetectionState.CONFIRMING,
            f.controller.state.value.detection,
        )

        // Advance beyond the 20-second confirm timeout window
        testTime += 20_001L
        advanceTimeBy(250)
        runCurrent()

        assertEquals(
            DetectionState.SKIPPED,
            f.controller.state.value.detection,
        )
        assertEquals(0, f.controller.state.value.graceRemaining)
        coVerify(exactly = 0) { f.client.lock(any()) }
        assertTrue(
            f.log.entries.value.any {
                it.message.contains("No walk-away confirmation within the window — not locking.")
            }
        )
    }

    @Test
    fun activityRecognitionTimeoutFallsBackToGraceWhenNotStrictlyRequired() = runTest {
        val f = Fixture(backgroundScope)
        var testTime = 1_000_000L
        f.controller.clock = { testTime }
        f.settings.value = f.settings.value.copy(
            useActivityRecognition = true,
            useGeofence = false,
            requireWalkAwayConfirmation = false,
        )
        val preLockTime = testTime - 10_000L
        val postLockTime = testTime - 1_000L
        var reads = 0

        coEvery { f.client.status(any(), any()) } coAnswers {
            reads++
            if (reads == 1) f.unlocked(preLockTime)
            else f.locked(postLockTime)
        }

        f.controller.onTriggerFired()
        runCurrent()

        assertEquals(
            DetectionState.CONFIRMING,
            f.controller.state.value.detection,
        )

        // Advance beyond the 20-second confirm timeout window
        testTime += 20_001L
        advanceTimeBy(250)
        runCurrent()

        // Without strict requirement, it falls back to grace
        assertEquals(
            DetectionState.GRACE,
            f.controller.state.value.detection,
        )

        advanceTimeBy(1_000)
        runCurrent()

        coVerify(exactly = 1) { f.client.lock("test-vehicle") }
        assertEquals(
            DetectionState.LOCKED,
            f.controller.state.value.detection,
        )
    }

    @Test
    fun lockNowDuringGraceImmediatelyLocksWithoutWaiting() = runTest {
        val f = Fixture(backgroundScope)
        f.settings.value = f.settings.value.copy(
            graceSeconds = 45,
        )
        val preLockTime = System.currentTimeMillis() - 10_000L
        val postLockTime = System.currentTimeMillis() - 1_000L
        var reads = 0

        coEvery { f.client.status(any(), any()) } coAnswers {
            reads++
            if (reads == 1) f.unlocked(preLockTime)
            else f.locked(postLockTime)
        }

        f.controller.onTriggerFired()
        runCurrent()

        assertEquals(
            DetectionState.GRACE,
            f.controller.state.value.detection,
        )
        assertEquals(45, f.controller.state.value.graceRemaining)

        f.controller.lockNow()
        runCurrent()
        advanceTimeBy(1_000)
        runCurrent()

        coVerify(exactly = 1) { f.client.lock("test-vehicle") }
        assertEquals(
            DetectionState.LOCKED,
            f.controller.state.value.detection,
        )
        assertTrue(
            f.log.entries.value.any {
                it.message.contains("Lock now requested — skipping the wait.")
            }
        )
    }

    @Test
    fun expiredSessionFailsAndDoesNotSendLock() = runTest {
        val f = Fixture(backgroundScope)
        coEvery { f.client.ensureFreshSession() } returns false

        f.controller.onTriggerFired()
        runCurrent()
        advanceTimeBy(1_000)
        runCurrent()

        coVerify(exactly = 0) { f.client.status(any(), any()) }
        coVerify(exactly = 0) { f.client.lock(any()) }
        assertEquals(
            DetectionState.ERROR,
            f.controller.state.value.detection,
        )
        assertTrue(
            f.log.entries.value.any {
                it.message.contains("Session expired — please sign in again.")
            }
        )
    }

    @Test
    fun parkedLocationIsRecordedWhenRememberEnabled() = runTest {
        val f = Fixture(backgroundScope)
        f.settings.value = f.settings.value.copy(
            rememberParkedLocation = true,
        )
        coEvery { f.locationHelper.currentParkedPlace() } returns
                ParkedPlace(lat = 29.7030, lng = -98.1245, label = "12 Market St, New Braunfels")
        coEvery { f.client.status(any(), any()) } returns f.unlocked()

        f.controller.onTriggerFired()
        runCurrent()

        coVerify(exactly = 1) { f.locationHelper.currentParkedPlace() }
        assertEquals("12 Market St, New Braunfels", f.settings.value.parkedLabel)
        assertEquals(29.7030, f.settings.value.parkedLat)
        assertEquals(-98.1245, f.settings.value.parkedLng)
        assertTrue(
            f.log.entries.value.any {
                it.message.contains("Parked near 12 Market St, New Braunfels")
            }
        )
        // Remembering the spot must not change the lock flow itself.
        assertEquals(
            DetectionState.GRACE,
            f.controller.state.value.detection,
        )
    }

    @Test
    fun parkedLocationIsNotReadWhenRememberDisabled() = runTest {
        val f = Fixture(backgroundScope)
        f.settings.value = f.settings.value.copy(
            rememberParkedLocation = false,
        )
        coEvery { f.client.status(any(), any()) } returns f.unlocked()

        f.controller.onTriggerFired()
        runCurrent()

        coVerify(exactly = 0) { f.locationHelper.currentParkedPlace() }
        assertNull(f.settings.value.parkedLabel)
        assertNull(f.settings.value.parkedLat)
        assertNull(f.settings.value.parkedLng)
    }

    @Test
    fun missingVehicleIdLogsWarningAndExitsCleanly() = runTest {
        val f = Fixture(backgroundScope)
        f.settings.value = f.settings.value.copy(
            vehicleId = null,
        )

        f.controller.onTriggerFired()
        runCurrent()
        advanceTimeBy(2_000)
        runCurrent()

        // Nothing started: still idle, no countdown, no calls to the car's servers.
        assertEquals(
            DetectionState.IDLE,
            f.controller.state.value.detection,
        )
        assertEquals(0, f.controller.state.value.graceRemaining)
        coVerify(exactly = 0) { f.client.ensureFreshSession() }
        coVerify(exactly = 0) { f.client.status(any(), any()) }
        coVerify(exactly = 0) { f.client.lock(any()) }
        assertNull(f.controller.state.value.lastLockAtEpochMs)
        assertTrue(
            f.log.entries.value.any {
                it.message.contains("No vehicle selected; ignoring trigger.")
            }
        )
    }

    @Test
    fun verificationDisabledSendsLockWithoutCheckAndMarksItUnverified() = runTest {
        val f = Fixture(backgroundScope)
        f.settings.value = f.settings.value.copy(
            verifyLock = false,
        )
        coEvery { f.client.status(any(), any()) } returns f.unlocked()

        f.controller.onTriggerFired()
        runCurrent()
        advanceTimeBy(1_000)
        runCurrent()

        // The lock command goes out once, but the status is read only once (before locking).
        coVerify(exactly = 1) { f.client.lock("test-vehicle") }
        coVerify(exactly = 1) { f.client.status("test-vehicle", false) }
        // Not "skipped" (which reads as "already secure"): the command was sent but never checked.
        assertEquals(
            DetectionState.SENT_UNVERIFIED,
            f.controller.state.value.detection,
        )
        assertNull(f.controller.state.value.lastLockAtEpochMs)

        // Only the pre-command status is cached; no "locked" snapshot is fabricated.
        coVerify(exactly = 1) { f.cache.saveStatus(any(), any()) }
        assertTrue(
            f.log.entries.value.any {
                it.message.contains("Verification is disabled. Not recording a confirmed lock.")
            }
        )
    }

    @Test
    fun vehicleChangedDuringGraceBlocksCommand() = runTest {
        val f = Fixture(backgroundScope)
        coEvery { f.client.status(any(), any()) } returns f.unlocked()

        f.controller.onTriggerFired()
        runCurrent()
        assertEquals(DetectionState.GRACE, f.controller.state.value.detection)

        f.settings.value = f.settings.value.copy(vehicleId = "some-other-vehicle")

        advanceTimeBy(1_000)
        runCurrent()

        coVerify(exactly = 0) { f.client.lock(any()) }
        assertEquals(DetectionState.ABORTED, f.controller.state.value.detection)
        assertNull(f.controller.state.value.lastLockAtEpochMs)
        assertTrue(
            f.log.entries.value.any { it.message.contains("Selected vehicle changed") }
        )
    }

    @Test
    fun accountChangedDuringGraceBlocksCommand() = runTest {
        val f = Fixture(backgroundScope)
        coEvery { f.client.status(any(), any()) } returns f.unlocked()

        f.controller.onTriggerFired()
        runCurrent()
        assertEquals(DetectionState.GRACE, f.controller.state.value.detection)

        f.settings.value = f.settings.value.copy(accountEmail = "someone-else@example.invalid")

        advanceTimeBy(1_000)
        runCurrent()

        coVerify(exactly = 0) { f.client.lock(any()) }
        assertEquals(DetectionState.ABORTED, f.controller.state.value.detection)
        assertNull(f.controller.state.value.lastLockAtEpochMs)
        assertTrue(
            f.log.entries.value.any { it.message.contains("Account changed") }
        )
    }

    @Test
    fun demoModeChangedDuringGraceBlocksCommand() = runTest {
        val f = Fixture(backgroundScope)
        coEvery { f.client.status(any(), any()) } returns f.unlocked()

        f.controller.onTriggerFired()
        runCurrent()
        assertEquals(DetectionState.GRACE, f.controller.state.value.detection)

        f.settings.value = f.settings.value.copy(demoMode = !f.settings.value.demoMode)

        advanceTimeBy(1_000)
        runCurrent()

        coVerify(exactly = 0) { f.client.lock(any()) }
        assertEquals(DetectionState.ABORTED, f.controller.state.value.detection)
        assertNull(f.controller.state.value.lastLockAtEpochMs)
        assertTrue(
            f.log.entries.value.any { it.message.contains("Demo mode changed") }
        )
    }

    @Test
    fun openDoorGuardChangedDuringGraceBlocksCommand() = runTest {
        val f = Fixture(backgroundScope)
        coEvery { f.client.status(any(), any()) } returns f.unlocked()

        f.controller.onTriggerFired()
        runCurrent()
        assertEquals(DetectionState.GRACE, f.controller.state.value.detection)

        // The guard was ON when the evaluation started; switching it OFF mid-flight must abort.
        f.settings.value = f.settings.value.copy(dontLockIfOpen = false)

        advanceTimeBy(1_000)
        runCurrent()

        coVerify(exactly = 0) { f.client.lock(any()) }
        assertEquals(DetectionState.ABORTED, f.controller.state.value.detection)
        assertNull(f.controller.state.value.lastLockAtEpochMs)
        assertTrue(
            f.log.entries.value.any { it.message.contains("Open-door safeguard changed") }
        )
    }

    @Test
    fun settingsChangedAfterLockCommandDoesNotRecordConfirmedLock() = runTest {
        val f = Fixture(backgroundScope)
        val preLockTime = System.currentTimeMillis() - 10_000L
        val postLockTime = System.currentTimeMillis() - 1_000L
        var reads = 0

        coEvery { f.client.status(any(), any()) } coAnswers {
            reads++
            if (reads == 1) f.unlocked(preLockTime)
            else f.locked(postLockTime)
        }
        // Watching gets switched off at the moment the lock command is being sent.
        coEvery { f.client.lock(any()) } coAnswers {
            f.settings.value = f.settings.value.copy(enabled = false)
            CommandResult.Success("Test command accepted")
        }

        f.controller.onTriggerFired()
        runCurrent()
        advanceTimeBy(1_000)
        runCurrent()

        coVerify(exactly = 1) { f.client.lock("test-vehicle") }
        assertEquals(DetectionState.ABORTED, f.controller.state.value.detection)
        assertNull(f.controller.state.value.lastLockAtEpochMs)
        // Only the pre-command status was cached; no "locked" snapshot was recorded.
        coVerify(exactly = 1) { f.cache.saveStatus(any(), any()) }
        assertTrue(
            f.log.entries.value.any { it.message.contains("Watching is off") }
        )
    }

    @Test
    fun settingsChangedDuringRetryBackoffBlocksSecondAttempt() = runTest {
        val f = Fixture(backgroundScope)
        f.settings.value = f.settings.value.copy(retryWindowMinutes = 2)
        coEvery { f.client.status(any(), any()) } returns f.unlocked()
        coEvery { f.client.lock(any()) } returns CommandResult.Failure("5031 Unavailable")

        f.controller.onTriggerFired()
        runCurrent()
        advanceTimeBy(1_000) // grace countdown
        runCurrent()

        // First attempt failed and we're now waiting out the backoff.
        coVerify(exactly = 1) { f.client.lock("test-vehicle") }

        f.settings.value = f.settings.value.copy(enabled = false)

        advanceTimeBy(20_000)
        runCurrent()

        // The retry must NOT go out.
        coVerify(exactly = 1) { f.client.lock("test-vehicle") }
        assertEquals(DetectionState.ABORTED, f.controller.state.value.detection)
        assertNull(f.controller.state.value.lastLockAtEpochMs)
        assertTrue(
            f.log.entries.value.any { it.message.contains("Watching is off") }
        )
    }

    // ---- Geofence walk-away tests -------------------------------------------------------------

    /**
     * Fakes two GPS readings: where the car is (first call) and where you are now (later calls).
     * [distanceMeters] is read every time the controller measures how far apart they are, so a
     * test can change it mid-run to "walk away".
     */
    private fun stubGeofenceLocations(f: Fixture, distanceMeters: () -> Float) {
        val carSpot = mockk<Location>()
        val yourSpot = mockk<Location>()
        every { carSpot.distanceTo(any()) } answers { distanceMeters() }
        coEvery { f.locationHelper.currentLocation() } returnsMany listOf(carSpot, yourSpot)
    }

    /** First status read = unlocked, later reads = locked with a newer timestamp. */
    private fun stubUnlockedThenLocked(f: Fixture, now: Long) {
        var reads = 0
        coEvery { f.client.status(any(), any()) } coAnswers {
            reads++
            if (reads == 1) f.unlocked(now - 10_000L) else f.locked(now - 1_000L)
        }
    }

    @Test
    fun geofenceMovedBeyondRadiusConfirmsThenLocks() = runTest {
        val f = Fixture(backgroundScope)
        var testTime = 1_000_000L
        f.controller.clock = { testTime }
        f.settings.value = f.settings.value.copy(
            useGeofence = true,
            geofenceRadiusMeters = 25,
        )
        var distance = 10f // still standing next to the car
        stubGeofenceLocations(f) { distance }
        stubUnlockedThenLocked(f, testTime)

        f.controller.onTriggerFired()
        runCurrent()

        // Inside the radius: keep waiting, no countdown, no lock.
        assertEquals(DetectionState.CONFIRMING, f.controller.state.value.detection)
        coVerify(exactly = 0) { f.client.lock(any()) }

        // Walk away and let the next 3-second location check happen.
        distance = 40f
        testTime += 3_001L
        advanceTimeBy(250)
        runCurrent()

        assertEquals(DetectionState.GRACE, f.controller.state.value.detection)
        assertTrue(
            f.log.entries.value.any {
                it.message.contains("Moved beyond the geofence — confirmed you left.")
            }
        )

        advanceTimeBy(250)
        runCurrent()
        advanceTimeBy(1_000)
        runCurrent()

        coVerify(exactly = 1) { f.client.lock("test-vehicle") }
        assertEquals(DetectionState.LOCKED, f.controller.state.value.detection)
    }

    @Test
    fun geofenceDistanceExactlyAtRadiusCountsAsLeft() = runTest {
        val f = Fixture(backgroundScope)
        f.settings.value = f.settings.value.copy(
            useGeofence = true,
            geofenceRadiusMeters = 25,
        )
        stubGeofenceLocations(f) { 25f } // right on the line
        coEvery { f.client.status(any(), any()) } returns f.unlocked()

        f.controller.onTriggerFired()
        runCurrent()

        assertEquals(DetectionState.GRACE, f.controller.state.value.detection)
        coVerify(exactly = 0) { f.client.lock(any()) }
    }

    @Test
    fun geofenceStayingInsideRadiusSkipsWhenWalkAwayRequired() = runTest {
        val f = Fixture(backgroundScope)
        var testTime = 1_000_000L
        f.controller.clock = { testTime }
        f.settings.value = f.settings.value.copy(
            useGeofence = true,
            geofenceRadiusMeters = 25,
            requireWalkAwayConfirmation = true,
        )
        stubGeofenceLocations(f) { 24.9f } // just inside the line

        f.controller.onTriggerFired()
        runCurrent()

        assertEquals(DetectionState.CONFIRMING, f.controller.state.value.detection)

        // The 20-second confirmation window runs out with you still next to the car.
        testTime += 20_001L
        advanceTimeBy(250)
        runCurrent()

        assertEquals(DetectionState.SKIPPED, f.controller.state.value.detection)
        assertEquals(0, f.controller.state.value.graceRemaining)
        coVerify(exactly = 0) { f.client.status(any(), any()) }
        coVerify(exactly = 0) { f.client.lock(any()) }
        assertTrue(
            f.log.entries.value.any {
                it.message.contains("No walk-away confirmation within the window — not locking.")
            }
        )
    }

    @Test
    fun geofenceStayingInsideRadiusFallsBackToGraceWhenNotRequired() = runTest {
        val f = Fixture(backgroundScope)
        var testTime = 1_000_000L
        f.controller.clock = { testTime }
        f.settings.value = f.settings.value.copy(
            useGeofence = true,
            geofenceRadiusMeters = 25,
            requireWalkAwayConfirmation = false,
        )
        stubGeofenceLocations(f) { 10f }
        stubUnlockedThenLocked(f, testTime)

        f.controller.onTriggerFired()
        runCurrent()

        assertEquals(DetectionState.CONFIRMING, f.controller.state.value.detection)

        testTime += 20_001L
        advanceTimeBy(250)
        runCurrent()

        // Walk-away confirmation isn't strictly required, so Bluetooth alone is enough after the window.
        assertEquals(DetectionState.GRACE, f.controller.state.value.detection)

        advanceTimeBy(1_000)
        runCurrent()

        coVerify(exactly = 1) { f.client.lock("test-vehicle") }
        assertEquals(DetectionState.LOCKED, f.controller.state.value.detection)
    }

    @Test
    fun geofenceLocationUnavailableSkipsWhenWalkAwayRequired() = runTest {
        val f = Fixture(backgroundScope)
        var testTime = 1_000_000L
        f.controller.clock = { testTime }
        f.settings.value = f.settings.value.copy(
            useGeofence = true,
            requireWalkAwayConfirmation = true,
        )
        // No GPS fix (e.g. location permission missing): the controller can never confirm you left.
        coEvery { f.locationHelper.currentLocation() } returns null

        f.controller.onTriggerFired()
        runCurrent()

        assertEquals(DetectionState.CONFIRMING, f.controller.state.value.detection)

        testTime += 20_001L
        advanceTimeBy(250)
        runCurrent()

        assertEquals(DetectionState.SKIPPED, f.controller.state.value.detection)
        // Only the one starting-point read; with no starting point there is nothing to poll.
        coVerify(exactly = 1) { f.locationHelper.currentLocation() }
        coVerify(exactly = 0) { f.client.status(any(), any()) }
        coVerify(exactly = 0) { f.client.lock(any()) }
    }

    @Test
    fun externalGeofenceExitEventConfirmsAndLocks() = runTest {
        val f = Fixture(backgroundScope)
        f.settings.value = f.settings.value.copy(
            useGeofence = true,
        )
        coEvery { f.locationHelper.currentLocation() } returns null
        stubUnlockedThenLocked(f, System.currentTimeMillis())

        f.controller.onTriggerFired()
        runCurrent()

        assertEquals(DetectionState.CONFIRMING, f.controller.state.value.detection)

        // A geofence-exit event arrives from outside the controller's own polling.
        f.controller.onMovedBeyondGeofence()
        runCurrent()

        assertEquals(DetectionState.GRACE, f.controller.state.value.detection)

        advanceTimeBy(250)
        runCurrent()
        advanceTimeBy(1_000)
        runCurrent()

        coVerify(exactly = 1) { f.client.lock("test-vehicle") }
        assertEquals(DetectionState.LOCKED, f.controller.state.value.detection)
    }

    @Test
    fun activityAndGeofenceBothRequiredBeforeGrace() = runTest {
        val f = Fixture(backgroundScope)
        f.settings.value = f.settings.value.copy(
            useActivityRecognition = true,
            useGeofence = true,
            geofenceRadiusMeters = 25,
        )
        stubGeofenceLocations(f) { 40f } // geofence says you've left
        stubUnlockedThenLocked(f, System.currentTimeMillis())

        f.controller.onTriggerFired()
        runCurrent()

        // Geofence is satisfied, but footsteps haven't been detected yet: still waiting.
        assertTrue(
            f.log.entries.value.any { it.message.contains("Moved beyond the geofence") }
        )
        assertEquals(DetectionState.CONFIRMING, f.controller.state.value.detection)
        coVerify(exactly = 0) { f.client.lock(any()) }

        f.controller.onWalkingConfirmed()
        runCurrent()

        assertEquals(DetectionState.GRACE, f.controller.state.value.detection)

        advanceTimeBy(250)
        runCurrent()
        advanceTimeBy(1_000)
        runCurrent()

        coVerify(exactly = 1) { f.client.lock("test-vehicle") }
        assertEquals(DetectionState.LOCKED, f.controller.state.value.detection)
    }

    // ---- Lock failure paths -------------------------------------------------------------------

    @Test
    fun notAuthenticatedDuringLockFailsWithoutRetrying() = runTest {
        val f = Fixture(backgroundScope)
        // A retry window is open on purpose: "not signed in" must never be retried.
        f.settings.value = f.settings.value.copy(retryWindowMinutes = 2)
        coEvery { f.client.status(any(), any()) } returns f.unlocked()
        coEvery { f.client.lock(any()) } returns CommandResult.NotAuthenticated

        f.controller.onTriggerFired()
        runCurrent()
        advanceTimeBy(1_000) // grace countdown
        runCurrent()
        advanceTimeBy(120_000) // plenty of time for a hidden retry to show up
        runCurrent()

        coVerify(exactly = 1) { f.client.lock("test-vehicle") }
        assertEquals(DetectionState.ERROR, f.controller.state.value.detection)
        assertNull(f.controller.state.value.lastLockAtEpochMs)
        coVerify(exactly = 1) { f.cache.saveStatus(any(), any()) }
        assertTrue(
            f.log.entries.value.any {
                it.message.contains("Not signed in — please log in again.")
            }
        )
    }

    @Test
    fun rateLimitedLockRetriesInsideWindowAndRecovers() = runTest {
        val f = Fixture(backgroundScope)
        f.settings.value = f.settings.value.copy(retryWindowMinutes = 2)
        val preLockTime = System.currentTimeMillis() - 10_000L
        val postLockTime = System.currentTimeMillis() - 1_000L
        var lockAttempts = 0
        var statusReads = 0

        coEvery { f.client.status(any(), any()) } coAnswers {
            statusReads++
            if (statusReads == 1) f.unlocked(preLockTime) else f.locked(postLockTime)
        }
        coEvery { f.client.lock(any()) } coAnswers {
            lockAttempts++
            if (lockAttempts == 1) CommandResult.RateLimited
            else CommandResult.Success("Locked")
        }

        f.controller.onTriggerFired()
        runCurrent()
        advanceTimeBy(1_000) // grace countdown
        runCurrent()

        // First attempt was rate-limited: no error yet, just a pause before trying again.
        coVerify(exactly = 1) { f.client.lock("test-vehicle") }
        assertNull(f.controller.state.value.lastLockAtEpochMs)
        assertTrue(
            f.log.entries.value.any {
                it.message.contains("Lock retry 1 in")
            }
        )

        advanceTimeBy(20_000) // backoff
        runCurrent()

        coVerify(exactly = 2) { f.client.lock("test-vehicle") }
        assertEquals(DetectionState.LOCKED, f.controller.state.value.detection)
        assertNotNull(f.controller.state.value.lastLockAtEpochMs)
    }

    @Test
    fun rateLimitedLockFailsWhenRetryWindowIsGone() = runTest {
        val f = Fixture(backgroundScope)
        f.settings.value = f.settings.value.copy(retryWindowMinutes = 0)
        coEvery { f.client.status(any(), any()) } returns f.unlocked()
        coEvery { f.client.lock(any()) } returns CommandResult.RateLimited

        f.controller.onTriggerFired()
        runCurrent()
        advanceTimeBy(1_000)
        runCurrent()

        coVerify(exactly = 1) { f.client.lock("test-vehicle") }
        assertEquals(DetectionState.ERROR, f.controller.state.value.detection)
        assertNull(f.controller.state.value.lastLockAtEpochMs)
        assertTrue(
            f.log.entries.value.any {
                it.message.contains("Lock failed: rate-limited")
            }
        )
    }

    @Test
    fun retryWindowExhaustedStopsSendingLockCommands() = runTest {
        val f = Fixture(backgroundScope)
        // Tie the controller's clock to the test's fake time so the 1-minute window really runs out.
        val start = System.currentTimeMillis()
        f.controller.clock = { start + testScheduler.currentTime }
        f.settings.value = f.settings.value.copy(retryWindowMinutes = 1)
        coEvery { f.client.status(any(), any()) } returns f.unlocked(start - 10_000L)
        coEvery { f.client.lock(any()) } returns CommandResult.Failure("5031 Unavailable")

        f.controller.onTriggerFired()
        runCurrent()
        advanceTimeBy(1_000) // grace countdown, then attempt 1
        runCurrent()
        coVerify(exactly = 1) { f.client.lock("test-vehicle") }

        advanceTimeBy(20_000) // first backoff, then attempt 2
        runCurrent()
        coVerify(exactly = 2) { f.client.lock("test-vehicle") }
        assertNotEquals(DetectionState.ERROR, f.controller.state.value.detection)

        advanceTimeBy(40_000) // second backoff, then attempt 3 lands as the window closes
        runCurrent()
        coVerify(exactly = 3) { f.client.lock("test-vehicle") }
        assertEquals(DetectionState.ERROR, f.controller.state.value.detection)
        assertTrue(
            f.log.entries.value.any {
                it.message.contains("Lock failed: 5031 Unavailable")
            }
        )

        // Long after the window: nothing more is ever sent.
        advanceTimeBy(600_000)
        runCurrent()
        coVerify(exactly = 3) { f.client.lock("test-vehicle") }
        assertNull(f.controller.state.value.lastLockAtEpochMs)
    }

    @Test
    fun preLockStatusReadFailureErrorsAndSendsNoLock() = runTest {
        val f = Fixture(backgroundScope)
        coEvery { f.client.status(any(), any()) } throws IllegalStateException("Test read failure")

        f.controller.onTriggerFired()
        runCurrent()
        advanceTimeBy(1_000)
        runCurrent()
        advanceTimeBy(10_000) // the 2 s and 4 s pauses between the three tries
        runCurrent()

        // Three tries (retry on a hiccup), then give up without locking.
        coVerify(exactly = 3) { f.client.status("test-vehicle", false) }
        coVerify(exactly = 0) { f.client.lock(any()) }
        assertEquals(DetectionState.ERROR, f.controller.state.value.detection)
        assertNull(f.controller.state.value.lastLockAtEpochMs)
        coVerify(exactly = 0) { f.cache.saveStatus(any(), any()) }
        assertTrue(
            f.log.entries.value.any {
                it.message.contains("Could not read vehicle status: Test read failure")
            }
        )
    }

    @Test
    fun unexpectedExceptionIsCaughtAndControllerStaysUsable() = runTest {
        val f = Fixture(backgroundScope)
        // This call sits outside the status-read safety net, so it exercises the catch-all.
        coEvery { f.client.ensureFreshSession() } throws IllegalStateException("Test explosion")

        f.controller.onTriggerFired()
        runCurrent()
        advanceTimeBy(1_000)
        runCurrent()

        assertEquals(DetectionState.ERROR, f.controller.state.value.detection)
        coVerify(exactly = 0) { f.client.status(any(), any()) }
        coVerify(exactly = 0) { f.client.lock(any()) }
        assertTrue(
            f.log.entries.value.any {
                it.message.contains("Leave-N-Lock hit an error: Test explosion")
            }
        )

        // One bad run must not wedge the controller: the next trigger works normally.
        coEvery { f.client.ensureFreshSession() } returns true
        stubUnlockedThenLocked(f, System.currentTimeMillis())

        f.controller.onTriggerFired()
        runCurrent()
        advanceTimeBy(1_000)
        runCurrent()

        coVerify(exactly = 1) { f.client.lock("test-vehicle") }
        assertEquals(DetectionState.LOCKED, f.controller.state.value.detection)
    }

    // ---- Departure summary notification -------------------------------------------------------

    /** Gives each notification word its real text, so the summary line can be checked exactly. */
    private fun stubDepartureStrings(f: Fixture) {
        every { f.context.getString(R.string.departure_title_locked) } returns "Car secured"
        every { f.context.getString(R.string.departure_title_skipped) } returns "Leave-N-Lock"
        every { f.context.getString(R.string.departure_locked) } returns "Locked ✓"
        every { f.context.getString(R.string.departure_not_locked) } returns "Not locked"
        every { f.context.getString(R.string.departure_open) } returns "door/window open"
        every { f.context.getString(R.string.departure_closed) } returns "doors closed"
        every { f.context.getString(R.string.departure_not_confirmed) } returns "Lock sent, not confirmed"
        every {
            f.context.getString(R.string.departure_parked, "12 Market St")
        } returns "Parked near 12 Market St"
    }

    /**
     * Swaps the real notification (which needs a phone) for a stand-in that just records what it
     * was asked to post, runs [block], then puts the real one back.
     */
    private inline fun withFakeDepartureNotification(block: () -> Unit) {
        mockkObject(AutoLockNotification)
        every { AutoLockNotification.postDeparture(any(), any(), any()) } just Runs
        try {
            block()
        } finally {
            unmockkObject(AutoLockNotification)
        }
    }

    @Test
    fun departureSummaryAfterConfirmedLockPostsLockedSummary() = runTest {
        withFakeDepartureNotification {
            val f = Fixture(backgroundScope)
            stubDepartureStrings(f)
            f.settings.value = f.settings.value.copy(
                departureSummary = true,
                parkedLabel = "12 Market St",
            )
            val preLockTime = System.currentTimeMillis() - 10_000L
            val postLockTime = System.currentTimeMillis() - 1_000L
            var reads = 0
            coEvery { f.client.status(any(), any()) } coAnswers {
                reads++
                if (reads == 1) f.unlocked(preLockTime).copy(evBatteryPercent = 72)
                else f.locked(postLockTime)
            }

            f.controller.onTriggerFired()
            runCurrent()
            advanceTimeBy(1_000)
            runCurrent()

            assertEquals(DetectionState.LOCKED, f.controller.state.value.detection)
            verify(exactly = 1) {
                AutoLockNotification.postDeparture(
                    f.context,
                    "Car secured",
                    "Locked ✓ · 72% · doors closed · Parked near 12 Market St",
                )
            }
        }
    }

    @Test
    fun departureSummaryAfterSkipPostsReasonAndIgnoresBlankParkedLabel() = runTest {
        withFakeDepartureNotification {
            val f = Fixture(backgroundScope)
            stubDepartureStrings(f)
            f.settings.value = f.settings.value.copy(
                departureSummary = true,
                parkedLabel = "   ", // blank, so no "Parked near" part
            )
            coEvery { f.client.status(any(), any()) } returns f.unlocked(doorOpen = true)

            f.controller.onTriggerFired()
            runCurrent()
            advanceTimeBy(1_000)
            runCurrent()

            assertEquals(DetectionState.NOT_LOCKED_OPEN, f.controller.state.value.detection)
            coVerify(exactly = 0) { f.client.lock(any()) }
            verify(exactly = 1) {
                AutoLockNotification.postDeparture(
                    f.context,
                    "Leave-N-Lock",
                    "A door is open · Not locked · door/window open",
                )
            }
        }
    }

    @Test
    fun departureSummaryAfterFailedLockPostsNotLockedWithFailureReason() = runTest {
        withFakeDepartureNotification {
            val f = Fixture(backgroundScope)
            stubDepartureStrings(f)
            f.settings.value = f.settings.value.copy(
                departureSummary = true,
                retryWindowMinutes = 0,
            )
            coEvery { f.client.status(any(), any()) } returns f.unlocked()
            coEvery { f.client.lock(any()) } returns CommandResult.Failure("Vehicle offline")

            f.controller.onTriggerFired()
            runCurrent()
            advanceTimeBy(1_000)
            runCurrent()

            assertEquals(DetectionState.ERROR, f.controller.state.value.detection)
            verify(exactly = 1) {
                AutoLockNotification.postDeparture(
                    f.context,
                    "Leave-N-Lock",
                    "Vehicle offline · Not locked · doors closed",
                )
            }
        }
    }

    @Test
    fun departureSummaryOffPostsNothing() = runTest {
        withFakeDepartureNotification {
            val f = Fixture(backgroundScope) // departureSummary is false in the fixture
            stubDepartureStrings(f)
            val preLockTime = System.currentTimeMillis() - 10_000L
            val postLockTime = System.currentTimeMillis() - 1_000L
            var reads = 0
            coEvery { f.client.status(any(), any()) } coAnswers {
                reads++
                if (reads == 1) f.unlocked(preLockTime) else f.locked(postLockTime)
            }

            f.controller.onTriggerFired()
            runCurrent()
            advanceTimeBy(1_000)
            runCurrent()

            assertEquals(DetectionState.LOCKED, f.controller.state.value.detection)
            verify(exactly = 0) { AutoLockNotification.postDeparture(any(), any(), any()) }
        }
    }

    @Test
    fun departureSummaryAfterUnconfirmedLockPostsNotConfirmedSummary() = runTest {
        withFakeDepartureNotification {
            val f = Fixture(backgroundScope)
            stubDepartureStrings(f)
            f.settings.value = f.settings.value.copy(departureSummary = true)
            // The car keeps reporting "unlocked" after the command, so the lock can't be confirmed.
            coEvery { f.client.status(any(), any()) } returns f.unlocked()

            f.controller.onTriggerFired()
            runCurrent()
            advanceTimeBy(1_000)
            runCurrent()

            assertEquals(DetectionState.ERROR, f.controller.state.value.detection)
            // One command only; the notification must not trigger another.
            coVerify(exactly = 1) { f.client.lock("test-vehicle") }
            assertNull(f.controller.state.value.lastLockAtEpochMs)
            verify(exactly = 1) {
                AutoLockNotification.postDeparture(
                    f.context,
                    "Leave-N-Lock",
                    "Lock sent, not confirmed · doors closed",
                )
            }
        }
    }

    // ---- Door/window skip gets its own state; other skips are unchanged -----------------------

    @Test
    fun alreadyLockedStillEndsSkippedNotOpenState() = runTest {
        val f = Fixture(backgroundScope)
        coEvery { f.client.status(any(), any()) } returns f.locked()

        f.controller.onTriggerFired()
        runCurrent()
        advanceTimeBy(1_000)
        runCurrent()

        // Truly secure: keeps the generic "already secure" SKIPPED state.
        assertEquals(DetectionState.SKIPPED, f.controller.state.value.detection)
        coVerify(exactly = 0) { f.client.lock(any()) }
        assertTrue(
            f.log.entries.value.any { it.message.contains("Skipped: Already locked.") }
        )
    }

    @Test
    fun engineRunningStillEndsSkippedNotOpenState() = runTest {
        val f = Fixture(backgroundScope)
        coEvery { f.client.status(any(), any()) } returns f.unlocked().copy(engineRunning = true)

        f.controller.onTriggerFired()
        runCurrent()
        advanceTimeBy(1_000)
        runCurrent()

        assertEquals(DetectionState.SKIPPED, f.controller.state.value.detection)
        coVerify(exactly = 0) { f.client.lock(any()) }
        assertTrue(
            f.log.entries.value.any { it.message.contains("Skipped: Engine running.") }
        )
    }

    // ---- Status-read retry + "gave up" notification (Session 8, after live run 2) -------------

    @Test
    fun statusReadHiccupIsRetriedAndTheCarStillLocks() = runTest {
        val f = Fixture(backgroundScope)
        val preLockTime = System.currentTimeMillis() - 10_000L
        val postLockTime = System.currentTimeMillis() - 1_000L
        var reads = 0
        coEvery { f.client.status(any(), any()) } coAnswers {
            reads++
            when (reads) {
                1 -> throw IllegalStateException("Request timeout has expired")
                2 -> f.unlocked(preLockTime)
                else -> f.locked(postLockTime)
            }
        }

        f.controller.onTriggerFired()
        runCurrent()
        advanceTimeBy(1_000)
        runCurrent()
        advanceTimeBy(3_000) // the 2 s pause before the second try
        runCurrent()

        coVerify(exactly = 1) { f.client.lock("test-vehicle") }
        assertEquals(DetectionState.LOCKED, f.controller.state.value.detection)
        assertTrue(
            f.log.entries.value.any {
                it.message.contains("Status read failed (try 1/3): Request timeout has expired — retrying in 2s.")
            }
        )
    }

    @Test
    fun hungStatusReadIsCutOffAfter15sAndRetried() = runTest {
        val f = Fixture(backgroundScope)
        coEvery { f.client.status(any(), any()) } coAnswers {
            kotlinx.coroutines.awaitCancellation()
        }

        f.controller.onTriggerFired()
        runCurrent()
        advanceTimeBy(1_000)
        runCurrent()
        advanceTimeBy(14_000)
        runCurrent()
        coVerify(exactly = 1) { f.client.status(any(), any()) } // still waiting on try 1
        advanceTimeBy(2_000 + 2_000) // try 1 cut off at 15 s, then the 2 s pause
        runCurrent()
        coVerify(exactly = 2) { f.client.status(any(), any()) }
        advanceTimeBy(60_000)
        runCurrent()

        coVerify(exactly = 3) { f.client.status(any(), any()) }
        coVerify(exactly = 0) { f.client.lock(any()) }
        assertEquals(DetectionState.ERROR, f.controller.state.value.detection)
        assertTrue(
            f.log.entries.value.any { it.message == "Could not read vehicle status: no answer within 15s" }
        )
    }

    @Test
    fun statusReadGiveUpPostsNotLockedNotice() = runTest {
        withFakeDepartureNotification {
            val f = Fixture(backgroundScope)
            stubDepartureStrings(f)
            f.settings.value = f.settings.value.copy(departureSummary = true)
            coEvery { f.client.status(any(), any()) } throws IllegalStateException("Test read failure")

            f.controller.onTriggerFired()
            runCurrent()
            advanceTimeBy(20_000)
            runCurrent()

            verify(exactly = 1) {
                AutoLockNotification.postDeparture(
                    f.context,
                    "Leave-N-Lock",
                    "Couldn't reach Hyundai to check the car · Not locked",
                )
            }
        }
    }

    @Test
    fun expiredSessionPostsSignedOutNotice() = runTest {
        withFakeDepartureNotification {
            val f = Fixture(backgroundScope)
            stubDepartureStrings(f)
            f.settings.value = f.settings.value.copy(departureSummary = true)
            coEvery { f.client.ensureFreshSession() } returns false

            f.controller.onTriggerFired()
            runCurrent()
            advanceTimeBy(1_000)
            runCurrent()

            verify(exactly = 1) {
                AutoLockNotification.postDeparture(
                    f.context,
                    "Leave-N-Lock",
                    "Signed out of BlueLink — sign in again · Not locked",
                )
            }
        }
    }

    @Test
    fun notSignedInDuringLockPostsSignedOutNotice() = runTest {
        withFakeDepartureNotification {
            val f = Fixture(backgroundScope)
            stubDepartureStrings(f)
            f.settings.value = f.settings.value.copy(departureSummary = true)
            coEvery { f.client.status(any(), any()) } returns f.unlocked()
            coEvery { f.client.lock(any()) } returns CommandResult.NotAuthenticated

            f.controller.onTriggerFired()
            runCurrent()
            advanceTimeBy(1_000)
            runCurrent()

            assertEquals(DetectionState.ERROR, f.controller.state.value.detection)
            verify(exactly = 1) {
                AutoLockNotification.postDeparture(
                    f.context,
                    "Leave-N-Lock",
                    "Signed out of BlueLink — sign in again · Not locked",
                )
            }
        }
    }

    @Test
    fun unexpectedErrorPostsCheckYourCarNotice() = runTest {
        withFakeDepartureNotification {
            val f = Fixture(backgroundScope)
            stubDepartureStrings(f)
            f.settings.value = f.settings.value.copy(departureSummary = true)
            coEvery { f.client.ensureFreshSession() } throws IllegalStateException("Test explosion")

            f.controller.onTriggerFired()
            runCurrent()
            advanceTimeBy(1_000)
            runCurrent()

            assertEquals(DetectionState.ERROR, f.controller.state.value.detection)
            verify(exactly = 1) {
                AutoLockNotification.postDeparture(
                    f.context,
                    "Leave-N-Lock",
                    "Leave-N-Lock hit an error — check that your car is locked",
                )
            }
        }
    }

    @Test
    fun failureNoticeRespectsDepartureSummaryOff() = runTest {
        withFakeDepartureNotification {
            val f = Fixture(backgroundScope) // departureSummary is false in the fixture
            stubDepartureStrings(f)
            coEvery { f.client.status(any(), any()) } throws IllegalStateException("Test read failure")

            f.controller.onTriggerFired()
            runCurrent()
            advanceTimeBy(20_000)
            runCurrent()

            assertEquals(DetectionState.ERROR, f.controller.state.value.detection)
            verify(exactly = 0) { AutoLockNotification.postDeparture(any(), any(), any()) }
        }
    }

    @Test
    fun brokenFailureNoticeDoesNotBreakTheController() = runTest {
        mockkObject(AutoLockNotification)
        try {
            every { AutoLockNotification.postDeparture(any(), any(), any()) } throws IllegalStateException("no shade")
            val f = Fixture(backgroundScope)
            stubDepartureStrings(f)
            f.settings.value = f.settings.value.copy(departureSummary = true)
            coEvery { f.client.ensureFreshSession() } returns false

            f.controller.onTriggerFired()
            runCurrent()
            advanceTimeBy(1_000)
            runCurrent()
            assertEquals(DetectionState.ERROR, f.controller.state.value.detection)

            // The next trigger still runs normally. (Summary off: the existing success summary has
            // no such guard, and this test is only about the new failure notice.)
            f.settings.value = f.settings.value.copy(departureSummary = false)
            coEvery { f.client.ensureFreshSession() } returns true
            val preLockTime = System.currentTimeMillis() - 10_000L
            val postLockTime = System.currentTimeMillis() - 1_000L
            var reads = 0
            coEvery { f.client.status(any(), any()) } coAnswers {
                reads++
                if (reads == 1) f.unlocked(preLockTime) else f.locked(postLockTime)
            }
            f.controller.onTriggerFired()
            runCurrent()
            advanceTimeBy(1_000)
            runCurrent()
            assertEquals(DetectionState.LOCKED, f.controller.state.value.detection)
        } finally {
            unmockkObject(AutoLockNotification)
        }
    }
}

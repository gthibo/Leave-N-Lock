package io.github.gthibo.leavenlock.domain

import io.github.gthibo.leavenlock.data.bluelink.model.CommandResult
import org.junit.Assert.assertEquals
import org.junit.Test

/** Activity-log lines for lock/unlock pressed on the Home screen. */
class ManualCommandLogTest {

    private fun line(command: ManualCommand, outcome: ManualOutcome) =
        manualCommandLogLine(command, outcome)

    @Test
    fun successIsLoggedAsSuccess() {
        assertEquals(
            LogLevel.SUCCESS to "Locked from the app.",
            line(ManualCommand.LOCK, ManualOutcome.Sent(CommandResult.Success("ok"))),
        )
        assertEquals(
            LogLevel.SUCCESS to "Unlocked from the app.",
            line(ManualCommand.UNLOCK, ManualOutcome.Sent(CommandResult.Success("ok"))),
        )
    }

    @Test
    fun failureCarriesTheReason() {
        assertEquals(
            LogLevel.ERROR to "Unlock from the app failed: timeout.",
            line(ManualCommand.UNLOCK, ManualOutcome.Sent(CommandResult.Failure("timeout"))),
        )
    }

    @Test
    fun rateLimitAndSignInProblemsAreWarnings() {
        assertEquals(
            LogLevel.WARN to "Lock from the app rate-limited by Hyundai.",
            line(ManualCommand.LOCK, ManualOutcome.Sent(CommandResult.RateLimited)),
        )
        assertEquals(
            LogLevel.WARN to "Lock from the app failed: not signed in.",
            line(ManualCommand.LOCK, ManualOutcome.Sent(CommandResult.NotAuthenticated)),
        )
        assertEquals(
            LogLevel.WARN to "Unlock from the app not sent: session expired.",
            line(ManualCommand.UNLOCK, ManualOutcome.SessionExpired),
        )
    }

    @Test
    fun refusalsBeforeSendingAreLogged() {
        assertEquals(
            LogLevel.WARN to "Unlock from the app refused: incorrect PIN.",
            line(ManualCommand.UNLOCK, ManualOutcome.WrongPin),
        )
        assertEquals(
            LogLevel.WARN to "Lock from the app not sent: no vehicle selected.",
            line(ManualCommand.LOCK, ManualOutcome.NoVehicle),
        )
        assertEquals(
            LogLevel.INFO to "Dry run — unlock from the app not sent.",
            line(ManualCommand.UNLOCK, ManualOutcome.DryRun),
        )
    }
}

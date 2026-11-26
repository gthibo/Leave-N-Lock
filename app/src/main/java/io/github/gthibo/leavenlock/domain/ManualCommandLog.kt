package io.github.gthibo.leavenlock.domain

import io.github.gthibo.leavenlock.data.bluelink.model.CommandResult

/** A lock or unlock the user pressed on the Home screen (not an auto-lock run). */
enum class ManualCommand(val noun: String, val pastTense: String) {
    LOCK("lock", "Locked"),
    UNLOCK("unlock", "Unlocked"),
}

/** How a manual lock/unlock ended: refused before sending, or Hyundai's answer. */
sealed interface ManualOutcome {
    data object DryRun : ManualOutcome
    data object WrongPin : ManualOutcome
    data object NoVehicle : ManualOutcome
    data object SessionExpired : ManualOutcome
    data class Sent(val result: CommandResult) : ManualOutcome
}

/**
 * The Activity-log line for a manual lock/unlock. Never includes the PIN or credentials;
 * a failure reason is the client's own message, already shown on screen.
 */
fun manualCommandLogLine(command: ManualCommand, outcome: ManualOutcome): Pair<LogLevel, String> {
    val what = "${command.noun.replaceFirstChar { it.uppercase() }} from the app"
    return when (outcome) {
        ManualOutcome.DryRun -> LogLevel.INFO to "Dry run — ${command.noun} from the app not sent."
        ManualOutcome.WrongPin -> LogLevel.WARN to "$what refused: incorrect PIN."
        ManualOutcome.NoVehicle -> LogLevel.WARN to "$what not sent: no vehicle selected."
        ManualOutcome.SessionExpired -> LogLevel.WARN to "$what not sent: session expired."
        is ManualOutcome.Sent -> when (val r = outcome.result) {
            is CommandResult.Success -> LogLevel.SUCCESS to "${command.pastTense} from the app."
            is CommandResult.Failure -> LogLevel.ERROR to "$what failed: ${r.reason.trimEnd('.')}."
            CommandResult.RateLimited -> LogLevel.WARN to "$what rate-limited by Hyundai."
            CommandResult.NotAuthenticated -> LogLevel.WARN to "$what failed: not signed in."
        }
    }
}

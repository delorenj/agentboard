package com.zellij.keyboard.input

import com.zellij.keyboard.core.KeyCommand

sealed interface KeyEventSendOutcome {
    data object Accepted : KeyEventSendOutcome

    data object Rejected : KeyEventSendOutcome

    data class Failed(val cause: RuntimeException) : KeyEventSendOutcome
}

data class KeyEventSendAttempt(
    val eventIndex: Int,
    val event: AndroidKeyEventSpec,
    val outcome: KeyEventSendOutcome,
)

/**
 * Result returned to the service. [Completed] means every event was attempted,
 * not that the receiving connection accepted every event.
 */
sealed interface KeyEventEmissionResult {
    data class Completed(
        val plan: AndroidKeyEventPlan,
        val attempts: List<KeyEventSendAttempt>,
    ) : KeyEventEmissionResult {
        val allAccepted: Boolean
            get() = attempts.all { it.outcome == KeyEventSendOutcome.Accepted }

        val acceptedCount: Int
            get() = attempts.count { it.outcome == KeyEventSendOutcome.Accepted }

        val rejectedCount: Int
            get() = attempts.count { it.outcome == KeyEventSendOutcome.Rejected }

        val failedCount: Int
            get() = attempts.count { it.outcome is KeyEventSendOutcome.Failed }
    }

    data class NotEmitted(
        val reason: TerminalKeyEventPlanResult.UnsupportedCharacter,
    ) : KeyEventEmissionResult
}

/**
 * Shared pure dispatch loop for the Android adapter.
 *
 * A rejected or runtime-failed send is recorded and iteration continues through
 * the complete plan, including all modifier UP events.
 */
internal object KeyEventPlanDispatcher {
    fun dispatch(
        command: KeyCommand,
        send: (eventIndex: Int, event: AndroidKeyEventSpec) -> Boolean,
    ): KeyEventEmissionResult =
        when (val result = TerminalKeyEventPlanner.plan(command)) {
            is TerminalKeyEventPlanResult.Ready -> dispatch(result.plan, send)
            is TerminalKeyEventPlanResult.UnsupportedCharacter ->
                KeyEventEmissionResult.NotEmitted(result)
        }

    fun dispatch(
        plan: AndroidKeyEventPlan,
        send: (eventIndex: Int, event: AndroidKeyEventSpec) -> Boolean,
    ): KeyEventEmissionResult.Completed {
        val attempts =
            plan.events.mapIndexed { index, event ->
                val outcome =
                    try {
                        if (send(index, event)) {
                            KeyEventSendOutcome.Accepted
                        } else {
                            KeyEventSendOutcome.Rejected
                        }
                    } catch (failure: RuntimeException) {
                        KeyEventSendOutcome.Failed(failure)
                    }

                KeyEventSendAttempt(
                    eventIndex = index,
                    event = event,
                    outcome = outcome,
                )
            }

        return KeyEventEmissionResult.Completed(
            plan = plan,
            attempts = attempts,
        )
    }
}

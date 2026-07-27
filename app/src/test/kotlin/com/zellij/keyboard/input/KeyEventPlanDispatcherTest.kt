package com.zellij.keyboard.input

import com.zellij.keyboard.core.Key
import com.zellij.keyboard.core.KeyCommand
import com.zellij.keyboard.core.KeyModifiers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class KeyEventPlanDispatcherTest {
    @Test
    fun `dispatch attempts the complete sequence after rejection and runtime failure`() {
        val attemptedIndices = mutableListOf<Int>()
        val failure = IllegalStateException("connection failed")
        val result =
            KeyEventPlanDispatcher.dispatch(
                command =
                    KeyCommand(
                        key = Key.Character('A'),
                        modifiers = KeyModifiers(ctrl = true, alt = true),
                    ),
            ) { eventIndex, _ ->
                attemptedIndices += eventIndex
                when (eventIndex) {
                    0 -> false
                    3 -> throw failure
                    else -> true
                }
            }

        val completed = assertIs<KeyEventEmissionResult.Completed>(result)
        assertEquals(completed.plan.events.indices.toList(), attemptedIndices)
        assertEquals(completed.plan.events.size, completed.attempts.size)
        assertIs<KeyEventSendOutcome.Rejected>(completed.attempts[0].outcome)
        assertEquals(
            failure,
            assertIs<KeyEventSendOutcome.Failed>(completed.attempts[3].outcome).cause,
        )
        assertIs<KeyEventSendOutcome.Accepted>(completed.attempts.last().outcome)
        assertFalse(completed.allAccepted)
        assertEquals(6, completed.acceptedCount)
        assertEquals(1, completed.rejectedCount)
        assertEquals(1, completed.failedCount)
    }

    @Test
    fun `all accepted result is explicit`() {
        val result =
            KeyEventPlanDispatcher.dispatch(KeyCommand(Key.Named.ENTER)) { _, _ -> true }

        val completed = assertIs<KeyEventEmissionResult.Completed>(result)
        assertTrue(completed.allAccepted)
        assertEquals(2, completed.acceptedCount)
        assertEquals(0, completed.rejectedCount)
        assertEquals(0, completed.failedCount)
    }

    @Test
    fun `unsupported command invokes no sender and reports not emitted`() {
        var sendCount = 0

        val result =
            KeyEventPlanDispatcher.dispatch(KeyCommand(Key.Character('é'))) { _, _ ->
                sendCount += 1
                true
            }

        assertEquals(0, sendCount)
        assertEquals(
            KeyEventEmissionResult.NotEmitted(
                TerminalKeyEventPlanResult.UnsupportedCharacter('é'),
            ),
            result,
        )
    }
}

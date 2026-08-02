package com.zellij.keyboard.input

import com.zellij.keyboard.core.AgentCommand
import com.zellij.keyboard.core.AgentCommandPlanner
import com.zellij.keyboard.core.Key
import com.zellij.keyboard.core.KeyCommand
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class KeyCommandSequenceDispatcherTest {
    @Test
    fun `rejected character aborts before enter`() {
        val commands = AgentCommandPlanner.plan(AgentCommand.CONTINUE)
        val attempted = mutableListOf<KeyCommand>()

        val result =
            KeyCommandSequenceDispatcher.dispatch(commands) { command ->
                attempted += command
                if (attempted.size == 4) rejected(command) else accepted(command)
            }

        assertEquals(commands.take(4), attempted)
        assertTrue(attempted.none { it.key == Key.Named.ENTER })
        assertEquals(3, assertIs<KeyCommandSequenceResult.Aborted>(result).failedCommandIndex)
    }

    @Test
    fun `unsupported character aborts without attempting later commands`() {
        val unsupported = KeyCommand(Key.Character('é'))
        val enter = KeyCommand(Key.Named.ENTER)
        val attempted = mutableListOf<KeyCommand>()

        val result =
            KeyCommandSequenceDispatcher.dispatch(listOf(unsupported, enter)) { command ->
                attempted += command
                KeyEventPlanDispatcher.dispatch(command) { _, _ -> true }
            }

        assertEquals(listOf(unsupported), attempted)
        assertIs<KeyEventEmissionResult.NotEmitted>(
            assertIs<KeyCommandSequenceResult.Aborted>(result).emissionResult,
        )
    }

    @Test
    fun `enter is emitted only after every command is accepted`() {
        val commands = AgentCommandPlanner.plan(AgentCommand.CODEX)
        val attempted = mutableListOf<KeyCommand>()

        val result =
            KeyCommandSequenceDispatcher.dispatch(commands) { command ->
                attempted += command
                accepted(command)
            }

        assertIs<KeyCommandSequenceResult.Completed>(result)
        assertEquals(commands, attempted)
        assertEquals(Key.Named.ENTER, attempted.last().key)
    }

    private fun accepted(command: KeyCommand): KeyEventEmissionResult =
        KeyEventPlanDispatcher.dispatch(command) { _, _ -> true }

    private fun rejected(command: KeyCommand): KeyEventEmissionResult =
        KeyEventPlanDispatcher.dispatch(command) { eventIndex, _ -> eventIndex != 0 }
}

package com.zellij.keyboard.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AgentCommandTest {
    @Test
    fun `agent launch commands use the configured shell wrappers`() {
        assertEquals(
            mapOf(
                AgentCommand.CLAUDE to "claude",
                AgentCommand.GEMINI to "gemini",
                AgentCommand.CODEX to "codex",
                AgentCommand.HERMES to "hermes",
                AgentCommand.KIMI to "kimi",
                AgentCommand.CONTINUE to "agent-continue",
            ),
            AgentCommand.entries.associateWith(AgentCommand::shellCommand),
        )
    }

    @Test
    fun `command metadata is nonempty and shell-keyboard safe`() {
        AgentCommand.entries.forEach { command ->
            assertTrue(command.label.isNotBlank())
            assertTrue(command.accessibilityDescription.isNotBlank())
            assertTrue(command.shellCommand.matches(Regex("[a-z-]+")))
        }
    }

    @Test
    fun `continue types the host wrapper and presses enter`() {
        assertEquals(
            "agent-continue".map { KeyCommand(Key.Character(it)) } +
                KeyCommand(Key.Named.ENTER),
            AgentCommandPlanner.plan(AgentCommand.CONTINUE),
        )
    }

    @Test
    fun `every deck command ends in exactly one enter`() {
        AgentCommand.entries.forEach { command ->
            val plan = AgentCommandPlanner.plan(command)

            assertEquals(KeyCommand(Key.Named.ENTER), plan.last(), command.name)
            assertEquals(
                command.shellCommand,
                plan.dropLast(1).joinToString("") { keyCommand ->
                    (keyCommand.key as Key.Character).value.toString()
                },
                command.name,
            )
        }
    }
}

package com.zellij.keyboard.core

private val SHELL_SAFE_AGENT_COMMAND = Regex("[a-z][a-z0-9-]*")

/** Shell entrypoints exposed by the high-level Agentboard command deck. */
enum class AgentCommand(
    val label: String,
    val shellCommand: String,
    val accessibilityDescription: String,
) {
    CLAUDE("Claude", "claude", "Start Claude"),
    GEMINI("Gemini", "gemini", "Start Gemini"),
    CODEX("Codex", "codex", "Start Codex"),
    HERMES("Hermes", "hermes", "Start Hermes"),
    KIMI("Kimi", "kimi", "Start Kimi"),
    CONTINUE("Continue", "agent-continue", "Continue the last agent used in this directory");

    init {
        require(shellCommand.matches(SHELL_SAFE_AGENT_COMMAND)) {
            "Agent commands must be simple shell-safe executable names"
        }
    }
}

/** Converts a deck action into the exact physical key sequence sent to the terminal. */
object AgentCommandPlanner {
    fun plan(command: AgentCommand): List<KeyCommand> =
        buildList {
            command.shellCommand.forEach { character ->
                add(KeyCommand(Key.Character(character)))
            }
            add(KeyCommand(Key.Named.ENTER))
        }
}

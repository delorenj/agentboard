package com.zellij.keyboard.core

enum class KeyModifier {
    CTRL,
    ALT,
}

/** Immutable modifier flags understood by the future Android KeyEvent emitter. */
data class KeyModifiers(
    val ctrl: Boolean = false,
    val alt: Boolean = false,
) {
    operator fun plus(other: KeyModifiers): KeyModifiers =
        KeyModifiers(
            ctrl = ctrl || other.ctrl,
            alt = alt || other.alt,
        )

    companion object {
        val NONE = KeyModifiers()
        val CTRL = KeyModifiers(ctrl = true)
        val ALT = KeyModifiers(alt = true)
    }
}

/**
 * Logical keys intentionally independent of Android key-code constants.
 * [Character] represents a physical character key, not committed text.
 */
sealed interface Key {
    data class Character(val value: Char) : Key

    enum class Named : Key {
        ARROW_UP,
        ARROW_DOWN,
        ARROW_LEFT,
        ARROW_RIGHT,
        TAB,
        ESCAPE,
        ENTER,
        BACKSPACE,
        SPACE,
    }
}

/**
 * A single logical key stroke. An Android adapter can translate this into a
 * paired ACTION_DOWN/ACTION_UP sequence with the requested meta-state.
 */
data class KeyCommand(
    val key: Key,
    val modifiers: KeyModifiers = KeyModifiers.NONE,
) {
    fun withAdditionalModifiers(additional: KeyModifiers): KeyCommand =
        copy(modifiers = modifiers + additional)
}

/**
 * The result of UI intent handling before platform key emission.
 * Cancelled and no-op operations deliberately carry no command.
 */
sealed interface CommandOperation {
    data class Emit(val command: KeyCommand) : CommandOperation

    data object Cancelled : CommandOperation

    data object NoOp : CommandOperation
}

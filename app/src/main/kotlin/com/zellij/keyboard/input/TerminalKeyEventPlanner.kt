package com.zellij.keyboard.input

import android.view.KeyEvent
import com.zellij.keyboard.core.Key
import com.zellij.keyboard.core.KeyCommand

/**
 * Maps logical terminal commands to physical Android key events.
 *
 * This planner only reads compile-time Android constants; it calls no Android
 * APIs and its immutable output is safe to inspect in ordinary JVM tests.
 *
 * Modifiers are pressed in the stable order Ctrl, Alt, explicit/derived Shift and
 * released in reverse. Modifier DOWN specs include the newly pressed modifier
 * in their meta state. Modifier UP specs describe the state after release.
 */
object TerminalKeyEventPlanner {
    fun plan(command: KeyCommand): TerminalKeyEventPlanResult {
        val resolvedKey =
            when (val key = command.key) {
                is Key.Character ->
                    resolveCharacter(key.value)
                        ?: return TerminalKeyEventPlanResult.UnsupportedCharacter(key.value)
                is Key.Named -> resolveNamedKey(key)
            }

        val modifiers =
            buildList {
                if (command.modifiers.ctrl) add(PhysicalModifier.CTRL)
                if (command.modifiers.alt) add(PhysicalModifier.ALT)
                if (command.modifiers.shift || resolvedKey.requiresShift) {
                    add(PhysicalModifier.SHIFT)
                }
            }

        val events = mutableListOf<AndroidKeyEventSpec>()
        val modifierDownIndices = mutableMapOf<PhysicalModifier, Int>()
        val activeModifiers = linkedSetOf<PhysicalModifier>()

        modifiers.forEach { modifier ->
            activeModifiers += modifier
            modifierDownIndices[modifier] = events.size
            events +=
                AndroidKeyEventSpec(
                    action = AndroidKeyEventAction.DOWN,
                    keyCode = modifier.keyCode,
                    metaState = metaStateOf(activeModifiers),
                    downEventIndex = events.size,
                )
        }

        val targetDownIndex = events.size
        val targetMetaState = metaStateOf(activeModifiers)
        events +=
            AndroidKeyEventSpec(
                action = AndroidKeyEventAction.DOWN,
                keyCode = resolvedKey.keyCode,
                metaState = targetMetaState,
                downEventIndex = targetDownIndex,
            )
        events +=
            AndroidKeyEventSpec(
                action = AndroidKeyEventAction.UP,
                keyCode = resolvedKey.keyCode,
                metaState = targetMetaState,
                downEventIndex = targetDownIndex,
            )

        modifiers.asReversed().forEach { modifier ->
            activeModifiers -= modifier
            events +=
                AndroidKeyEventSpec(
                    action = AndroidKeyEventAction.UP,
                    keyCode = modifier.keyCode,
                    metaState = metaStateOf(activeModifiers),
                    downEventIndex = checkNotNull(modifierDownIndices[modifier]),
                )
        }

        return TerminalKeyEventPlanResult.Ready(
            AndroidKeyEventPlan(events.toList()),
        )
    }

    private fun resolveCharacter(value: Char): ResolvedPhysicalKey? =
        when {
            value in 'a'..'z' ->
                ResolvedPhysicalKey(
                    keyCode = KeyEvent.KEYCODE_A + (value - 'a'),
                )
            value in 'A'..'Z' ->
                ResolvedPhysicalKey(
                    keyCode = KeyEvent.KEYCODE_A + (value - 'A'),
                    requiresShift = true,
                )
            else -> resolveNonLetterCharacter(value)
        }

    @Suppress("CyclomaticComplexMethod")
    private fun resolveNonLetterCharacter(value: Char): ResolvedPhysicalKey? =
        when (value) {
            '0' -> key(KeyEvent.KEYCODE_0)
            '1' -> key(KeyEvent.KEYCODE_1)
            '2' -> key(KeyEvent.KEYCODE_2)
            '3' -> key(KeyEvent.KEYCODE_3)
            '4' -> key(KeyEvent.KEYCODE_4)
            '5' -> key(KeyEvent.KEYCODE_5)
            '6' -> key(KeyEvent.KEYCODE_6)
            '7' -> key(KeyEvent.KEYCODE_7)
            '8' -> key(KeyEvent.KEYCODE_8)
            '9' -> key(KeyEvent.KEYCODE_9)
            ' ' -> key(KeyEvent.KEYCODE_SPACE)
            '`' -> key(KeyEvent.KEYCODE_GRAVE)
            '~' -> shifted(KeyEvent.KEYCODE_GRAVE)
            '-' -> key(KeyEvent.KEYCODE_MINUS)
            '_' -> shifted(KeyEvent.KEYCODE_MINUS)
            '=' -> key(KeyEvent.KEYCODE_EQUALS)
            '+' -> shifted(KeyEvent.KEYCODE_EQUALS)
            '[' -> key(KeyEvent.KEYCODE_LEFT_BRACKET)
            '{' -> shifted(KeyEvent.KEYCODE_LEFT_BRACKET)
            ']' -> key(KeyEvent.KEYCODE_RIGHT_BRACKET)
            '}' -> shifted(KeyEvent.KEYCODE_RIGHT_BRACKET)
            '\\' -> key(KeyEvent.KEYCODE_BACKSLASH)
            '|' -> shifted(KeyEvent.KEYCODE_BACKSLASH)
            ';' -> key(KeyEvent.KEYCODE_SEMICOLON)
            ':' -> shifted(KeyEvent.KEYCODE_SEMICOLON)
            '\'' -> key(KeyEvent.KEYCODE_APOSTROPHE)
            '"' -> shifted(KeyEvent.KEYCODE_APOSTROPHE)
            ',' -> key(KeyEvent.KEYCODE_COMMA)
            '<' -> shifted(KeyEvent.KEYCODE_COMMA)
            '.' -> key(KeyEvent.KEYCODE_PERIOD)
            '>' -> shifted(KeyEvent.KEYCODE_PERIOD)
            '/' -> key(KeyEvent.KEYCODE_SLASH)
            '?' -> shifted(KeyEvent.KEYCODE_SLASH)
            '!' -> shifted(KeyEvent.KEYCODE_1)
            '@' -> shifted(KeyEvent.KEYCODE_2)
            '#' -> shifted(KeyEvent.KEYCODE_3)
            '$' -> shifted(KeyEvent.KEYCODE_4)
            '%' -> shifted(KeyEvent.KEYCODE_5)
            '^' -> shifted(KeyEvent.KEYCODE_6)
            '&' -> shifted(KeyEvent.KEYCODE_7)
            '*' -> shifted(KeyEvent.KEYCODE_8)
            '(' -> shifted(KeyEvent.KEYCODE_9)
            ')' -> shifted(KeyEvent.KEYCODE_0)
            else -> null
        }

    private fun resolveNamedKey(key: Key.Named): ResolvedPhysicalKey =
        key(
            when (key) {
                Key.Named.ARROW_UP -> KeyEvent.KEYCODE_DPAD_UP
                Key.Named.ARROW_DOWN -> KeyEvent.KEYCODE_DPAD_DOWN
                Key.Named.ARROW_LEFT -> KeyEvent.KEYCODE_DPAD_LEFT
                Key.Named.ARROW_RIGHT -> KeyEvent.KEYCODE_DPAD_RIGHT
                Key.Named.TAB -> KeyEvent.KEYCODE_TAB
                Key.Named.ESCAPE -> KeyEvent.KEYCODE_ESCAPE
                Key.Named.ENTER -> KeyEvent.KEYCODE_ENTER
                Key.Named.BACKSPACE -> KeyEvent.KEYCODE_DEL
                Key.Named.SPACE -> KeyEvent.KEYCODE_SPACE
            },
        )

    private fun key(keyCode: Int): ResolvedPhysicalKey =
        ResolvedPhysicalKey(keyCode = keyCode)

    private fun shifted(keyCode: Int): ResolvedPhysicalKey =
        ResolvedPhysicalKey(
            keyCode = keyCode,
            requiresShift = true,
        )

    private fun metaStateOf(modifiers: Set<PhysicalModifier>): Int =
        modifiers.fold(0) { metaState, modifier ->
            metaState or modifier.normalizedMetaState
        }

    private data class ResolvedPhysicalKey(
        val keyCode: Int,
        val requiresShift: Boolean = false,
    )

    private enum class PhysicalModifier(
        val keyCode: Int,
        val normalizedMetaState: Int,
    ) {
        CTRL(
            keyCode = KeyEvent.KEYCODE_CTRL_LEFT,
            normalizedMetaState = KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON,
        ),
        ALT(
            keyCode = KeyEvent.KEYCODE_ALT_LEFT,
            normalizedMetaState = KeyEvent.META_ALT_ON or KeyEvent.META_ALT_LEFT_ON,
        ),
        SHIFT(
            keyCode = KeyEvent.KEYCODE_SHIFT_LEFT,
            normalizedMetaState = KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON,
        ),
    }
}

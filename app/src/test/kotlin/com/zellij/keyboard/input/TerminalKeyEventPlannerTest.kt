package com.zellij.keyboard.input

import android.view.KeyEvent
import com.zellij.keyboard.core.Key
import com.zellij.keyboard.core.KeyCommand
import com.zellij.keyboard.core.KeyModifiers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class TerminalKeyEventPlannerTest {
    @Test
    fun `letters use physical alphabet keycodes and uppercase derives shift`() {
        ('a'..'z').forEachIndexed { offset, letter ->
            val lowerPlan = ready(KeyCommand(Key.Character(letter)))
            val upperPlan = ready(KeyCommand(Key.Character(letter.uppercaseChar())))
            val expectedKeyCode = KeyEvent.KEYCODE_A + offset

            assertEquals(2, lowerPlan.events.size)
            assertEquals(expectedKeyCode, targetEvents(lowerPlan).singleKeyCode())
            assertEquals(0, targetEvents(lowerPlan).first().metaState)

            assertEquals(4, upperPlan.events.size)
            assertEquals(expectedKeyCode, targetEvents(upperPlan).singleKeyCode())
            assertEquals(SHIFT_META, targetEvents(upperPlan).first().metaState)
        }
    }

    @Test
    fun `digits use Android digit keycodes rather than character values`() {
        val expected =
            mapOf(
                '0' to KeyEvent.KEYCODE_0,
                '1' to KeyEvent.KEYCODE_1,
                '2' to KeyEvent.KEYCODE_2,
                '3' to KeyEvent.KEYCODE_3,
                '4' to KeyEvent.KEYCODE_4,
                '5' to KeyEvent.KEYCODE_5,
                '6' to KeyEvent.KEYCODE_6,
                '7' to KeyEvent.KEYCODE_7,
                '8' to KeyEvent.KEYCODE_8,
                '9' to KeyEvent.KEYCODE_9,
            )

        expected.forEach { (character, keyCode) ->
            val plan = ready(KeyCommand(Key.Character(character)))

            assertEquals(keyCode, targetEvents(plan).singleKeyCode())
            assertEquals(2, plan.events.size)
        }
    }

    @Test
    fun `punctuation variants use their base physical key plus derived shift`() {
        val expected =
            mapOf(
                '`' to ExpectedKey(KeyEvent.KEYCODE_GRAVE),
                '~' to ExpectedKey(KeyEvent.KEYCODE_GRAVE, shifted = true),
                '-' to ExpectedKey(KeyEvent.KEYCODE_MINUS),
                '_' to ExpectedKey(KeyEvent.KEYCODE_MINUS, shifted = true),
                '=' to ExpectedKey(KeyEvent.KEYCODE_EQUALS),
                '+' to ExpectedKey(KeyEvent.KEYCODE_EQUALS, shifted = true),
                '[' to ExpectedKey(KeyEvent.KEYCODE_LEFT_BRACKET),
                '{' to ExpectedKey(KeyEvent.KEYCODE_LEFT_BRACKET, shifted = true),
                ']' to ExpectedKey(KeyEvent.KEYCODE_RIGHT_BRACKET),
                '}' to ExpectedKey(KeyEvent.KEYCODE_RIGHT_BRACKET, shifted = true),
                '\\' to ExpectedKey(KeyEvent.KEYCODE_BACKSLASH),
                '|' to ExpectedKey(KeyEvent.KEYCODE_BACKSLASH, shifted = true),
                ';' to ExpectedKey(KeyEvent.KEYCODE_SEMICOLON),
                ':' to ExpectedKey(KeyEvent.KEYCODE_SEMICOLON, shifted = true),
                '\'' to ExpectedKey(KeyEvent.KEYCODE_APOSTROPHE),
                '"' to ExpectedKey(KeyEvent.KEYCODE_APOSTROPHE, shifted = true),
                ',' to ExpectedKey(KeyEvent.KEYCODE_COMMA),
                '<' to ExpectedKey(KeyEvent.KEYCODE_COMMA, shifted = true),
                '.' to ExpectedKey(KeyEvent.KEYCODE_PERIOD),
                '>' to ExpectedKey(KeyEvent.KEYCODE_PERIOD, shifted = true),
                '/' to ExpectedKey(KeyEvent.KEYCODE_SLASH),
                '?' to ExpectedKey(KeyEvent.KEYCODE_SLASH, shifted = true),
                '!' to ExpectedKey(KeyEvent.KEYCODE_1, shifted = true),
                '@' to ExpectedKey(KeyEvent.KEYCODE_2, shifted = true),
                '#' to ExpectedKey(KeyEvent.KEYCODE_3, shifted = true),
                '$' to ExpectedKey(KeyEvent.KEYCODE_4, shifted = true),
                '%' to ExpectedKey(KeyEvent.KEYCODE_5, shifted = true),
                '^' to ExpectedKey(KeyEvent.KEYCODE_6, shifted = true),
                '&' to ExpectedKey(KeyEvent.KEYCODE_7, shifted = true),
                '*' to ExpectedKey(KeyEvent.KEYCODE_8, shifted = true),
                '(' to ExpectedKey(KeyEvent.KEYCODE_9, shifted = true),
                ')' to ExpectedKey(KeyEvent.KEYCODE_0, shifted = true),
            )

        expected.forEach { (character, expectedKey) ->
            val plan = ready(KeyCommand(Key.Character(character)))
            val target = targetEvents(plan)

            assertEquals(expectedKey.keyCode, target.singleKeyCode(), "keycode for $character")
            assertEquals(
                if (expectedKey.shifted) SHIFT_META else 0,
                target.first().metaState,
                "meta state for $character",
            )
            assertEquals(if (expectedKey.shifted) 4 else 2, plan.events.size)
        }
    }

    @Test
    fun `space and named terminal keys map to physical Android keycodes`() {
        val expected =
            mapOf(
                Key.Named.ARROW_UP to KeyEvent.KEYCODE_DPAD_UP,
                Key.Named.ARROW_DOWN to KeyEvent.KEYCODE_DPAD_DOWN,
                Key.Named.ARROW_LEFT to KeyEvent.KEYCODE_DPAD_LEFT,
                Key.Named.ARROW_RIGHT to KeyEvent.KEYCODE_DPAD_RIGHT,
                Key.Named.TAB to KeyEvent.KEYCODE_TAB,
                Key.Named.ESCAPE to KeyEvent.KEYCODE_ESCAPE,
                Key.Named.ENTER to KeyEvent.KEYCODE_ENTER,
                Key.Named.BACKSPACE to KeyEvent.KEYCODE_DEL,
                Key.Named.SPACE to KeyEvent.KEYCODE_SPACE,
            )

        expected.forEach { (key, keyCode) ->
            assertEquals(
                keyCode,
                targetEvents(ready(KeyCommand(key))).singleKeyCode(),
                "keycode for $key",
            )
        }
        assertEquals(
            KeyEvent.KEYCODE_SPACE,
            targetEvents(ready(KeyCommand(Key.Character(' ')))).singleKeyCode(),
        )
    }

    @Test
    fun `ctrl alt and derived shift use stable nested order and normalized meta states`() {
        val plan =
            ready(
                KeyCommand(
                    key = Key.Character('?'),
                    modifiers = KeyModifiers(ctrl = true, alt = true),
                ),
            )

        assertEquals(
            listOf(
                KeyEvent.KEYCODE_CTRL_LEFT,
                KeyEvent.KEYCODE_ALT_LEFT,
                KeyEvent.KEYCODE_SHIFT_LEFT,
                KeyEvent.KEYCODE_SLASH,
                KeyEvent.KEYCODE_SLASH,
                KeyEvent.KEYCODE_SHIFT_LEFT,
                KeyEvent.KEYCODE_ALT_LEFT,
                KeyEvent.KEYCODE_CTRL_LEFT,
            ),
            plan.events.map(AndroidKeyEventSpec::keyCode),
        )
        assertEquals(
            listOf(
                AndroidKeyEventAction.DOWN,
                AndroidKeyEventAction.DOWN,
                AndroidKeyEventAction.DOWN,
                AndroidKeyEventAction.DOWN,
                AndroidKeyEventAction.UP,
                AndroidKeyEventAction.UP,
                AndroidKeyEventAction.UP,
                AndroidKeyEventAction.UP,
            ),
            plan.events.map(AndroidKeyEventSpec::action),
        )
        assertEquals(
            listOf(
                CTRL_META,
                CTRL_META or ALT_META,
                CTRL_META or ALT_META or SHIFT_META,
                CTRL_META or ALT_META or SHIFT_META,
                CTRL_META or ALT_META or SHIFT_META,
                CTRL_META or ALT_META,
                CTRL_META,
                0,
            ),
            plan.events.map(AndroidKeyEventSpec::metaState),
        )
        assertEquals(
            listOf(0, 1, 2, 3, 3, 2, 1, 0),
            plan.events.map(AndroidKeyEventSpec::downEventIndex),
        )
    }

    @Test
    fun `unknown characters fail explicitly without an event plan`() {
        assertEquals(
            TerminalKeyEventPlanResult.UnsupportedCharacter('é'),
            TerminalKeyEventPlanner.plan(KeyCommand(Key.Character('é'))),
        )
    }

    private fun ready(command: KeyCommand): AndroidKeyEventPlan =
        assertIs<TerminalKeyEventPlanResult.Ready>(
            TerminalKeyEventPlanner.plan(command),
        ).plan

    private fun targetEvents(plan: AndroidKeyEventPlan): List<AndroidKeyEventSpec> =
        plan.events.filter { it.keyCode !in MODIFIER_KEY_CODES }

    private fun List<AndroidKeyEventSpec>.singleKeyCode(): Int {
        assertEquals(2, size)
        assertEquals(AndroidKeyEventAction.DOWN, first().action)
        assertEquals(AndroidKeyEventAction.UP, last().action)
        return first().keyCode.also { assertEquals(it, last().keyCode) }
    }

    private data class ExpectedKey(
        val keyCode: Int,
        val shifted: Boolean = false,
    )

    private companion object {
        const val CTRL_META = KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON
        const val ALT_META = KeyEvent.META_ALT_ON or KeyEvent.META_ALT_LEFT_ON
        const val SHIFT_META = KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON

        val MODIFIER_KEY_CODES =
            setOf(
                KeyEvent.KEYCODE_CTRL_LEFT,
                KeyEvent.KEYCODE_ALT_LEFT,
                KeyEvent.KEYCODE_SHIFT_LEFT,
            )
    }
}

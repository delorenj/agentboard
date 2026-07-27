package com.zellij.keyboard.core

import kotlin.test.Test
import kotlin.test.assertEquals

class GestureShortcutMapperTest {
    @Test
    fun `focus gestures map to pane shortcuts`() {
        val expected =
            mapOf(
                GestureResult.Tap to ctrl('h'),
                GestureResult.LongPress to ctrl('w'),
                swipe(SwipeDirection.UP) to ctrl(Key.Named.ARROW_UP),
                swipe(SwipeDirection.DOWN) to ctrl(Key.Named.ARROW_DOWN),
                swipe(SwipeDirection.LEFT) to ctrl(Key.Named.ARROW_LEFT),
                swipe(SwipeDirection.RIGHT) to ctrl(Key.Named.ARROW_RIGHT),
            )

        expected.forEach { (gesture, command) ->
            assertEquals(
                CommandOperation.Emit(command),
                GestureShortcutMapper.map(GesturePad.FOCUS, gesture),
                "Unexpected Focus mapping for $gesture",
            )
        }
        assertEquals(
            CommandOperation.Cancelled,
            GestureShortcutMapper.map(GesturePad.FOCUS, GestureResult.Cancelled),
        )
    }

    @Test
    fun `tabs gestures map to tab shortcuts`() {
        val expected =
            mapOf(
                GestureResult.Tap to KeyCommand(Key.Named.TAB),
                GestureResult.LongPress to ctrl('r'),
                swipe(SwipeDirection.UP) to ctrl('t'),
                swipe(SwipeDirection.DOWN) to ctrl('w'),
                swipe(SwipeDirection.LEFT) to ctrl('p'),
                swipe(SwipeDirection.RIGHT) to ctrl('n'),
            )

        expected.forEach { (gesture, command) ->
            assertEquals(
                CommandOperation.Emit(command),
                GestureShortcutMapper.map(GesturePad.TABS, gesture),
                "Unexpected Tabs mapping for $gesture",
            )
        }
        assertEquals(
            CommandOperation.Cancelled,
            GestureShortcutMapper.map(GesturePad.TABS, GestureResult.Cancelled),
        )
    }

    private fun swipe(direction: SwipeDirection): GestureResult =
        GestureResult.Swipe(direction)

    private fun ctrl(character: Char): KeyCommand =
        KeyCommand(Key.Character(character), KeyModifiers.CTRL)

    private fun ctrl(key: Key): KeyCommand =
        KeyCommand(key, KeyModifiers.CTRL)
}

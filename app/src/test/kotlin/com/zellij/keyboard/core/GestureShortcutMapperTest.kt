package com.zellij.keyboard.core

import kotlin.test.Test
import kotlin.test.assertEquals

class GestureShortcutMapperTest {
    @Test
    fun `pane swipes map to directional focus shortcuts`() {
        val expected =
            mapOf(
                swipe(SwipeDirection.UP) to ctrlShift(Key.Named.ARROW_UP),
                swipe(SwipeDirection.DOWN) to ctrlShift(Key.Named.ARROW_DOWN),
                swipe(SwipeDirection.LEFT) to ctrlShift(Key.Named.ARROW_LEFT),
                swipe(SwipeDirection.RIGHT) to ctrlShift(Key.Named.ARROW_RIGHT),
            )

        expected.forEach { (gesture, command) ->
            assertEquals(
                CommandOperation.Emit(command),
                GestureShortcutMapper.map(GesturePad.PANES, gesture),
                "Unexpected Panes mapping for $gesture",
            )
        }
        assertEquals(
            CommandOperation.NoOp,
            GestureShortcutMapper.map(GesturePad.PANES, GestureResult.Tap),
        )
        assertEquals(
            CommandOperation.NoOp,
            GestureShortcutMapper.map(GesturePad.PANES, GestureResult.LongPress),
        )
        assertEquals(
            CommandOperation.Cancelled,
            GestureShortcutMapper.map(GesturePad.PANES, GestureResult.Cancelled),
        )
    }

    @Test
    fun `tabs only react to horizontal swipes`() {
        val expected =
            mapOf(
                swipe(SwipeDirection.LEFT) to ctrlShift(Key.Character(',')),
                swipe(SwipeDirection.RIGHT) to ctrlShift(Key.Character('.')),
            )

        expected.forEach { (gesture, command) ->
            assertEquals(
                CommandOperation.Emit(command),
                GestureShortcutMapper.map(GesturePad.TABS, gesture),
                "Unexpected Tabs mapping for $gesture",
            )
        }
        listOf(
            GestureResult.Tap,
            GestureResult.LongPress,
            swipe(SwipeDirection.UP),
            swipe(SwipeDirection.DOWN),
        ).forEach { gesture ->
            assertEquals(
                CommandOperation.NoOp,
                GestureShortcutMapper.map(GesturePad.TABS, gesture),
                "Tabs should ignore $gesture",
            )
        }
        assertEquals(
            CommandOperation.Cancelled,
            GestureShortcutMapper.map(GesturePad.TABS, GestureResult.Cancelled),
        )
    }

    private fun swipe(direction: SwipeDirection): GestureResult =
        GestureResult.Swipe(direction)

    private fun ctrlShift(key: Key): KeyCommand =
        KeyCommand(key, KeyModifiers.CTRL_SHIFT)
}

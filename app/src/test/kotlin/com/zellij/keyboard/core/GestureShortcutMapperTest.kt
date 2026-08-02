package com.zellij.keyboard.core

import kotlin.test.Test
import kotlin.test.assertEquals

class GestureShortcutMapperTest {
    @Test
    fun `pane swipes map to direct Zellij driver actions`() {
        SwipeDirection.entries.forEach { direction ->
            assertEquals(
                GestureAction.DriveZellij(direction.remotePaneAction),
                GestureShortcutMapper.map(GesturePad.PANES, swipe(direction)),
                "Unexpected Panes mapping for $direction",
            )
        }
    }

    @Test
    fun `tab horizontal swipes map to direct Zellij driver actions`() {
        assertEquals(
            GestureAction.DriveZellij(ZellijRemoteAction.TAB_PREVIOUS),
            GestureShortcutMapper.map(GesturePad.TABS, swipe(SwipeDirection.LEFT)),
        )
        assertEquals(
            GestureAction.DriveZellij(ZellijRemoteAction.TAB_NEXT),
            GestureShortcutMapper.map(GesturePad.TABS, swipe(SwipeDirection.RIGHT)),
        )
    }

    @Test
    fun `tab vertical swipes are ignored`() {
        listOf(SwipeDirection.UP, SwipeDirection.DOWN).forEach { direction ->
            assertEquals(
                GestureAction.Ignored,
                GestureShortcutMapper.map(GesturePad.TABS, swipe(direction)),
            )
        }
    }

    @Test
    fun `top zone tap invokes mic and hold continues the last agent`() {
        assertEquals(
            GestureAction.Microphone,
            GestureShortcutMapper.map(GesturePad.TABS, GestureResult.Tap),
        )
        assertEquals(
            GestureAction.ContinueLastAgent,
            GestureShortcutMapper.map(GesturePad.TABS, GestureResult.LongPress),
        )
    }

    @Test
    fun `pane taps are inert and cancellation stays explicit`() {
        assertEquals(
            GestureAction.Ignored,
            GestureShortcutMapper.map(GesturePad.PANES, GestureResult.Tap),
        )
        assertEquals(
            GestureAction.Ignored,
            GestureShortcutMapper.map(GesturePad.PANES, GestureResult.LongPress),
        )
        GesturePad.entries.forEach { pad ->
            assertEquals(
                GestureAction.Cancelled,
                GestureShortcutMapper.map(pad, GestureResult.Cancelled),
            )
        }
    }

    private fun swipe(direction: SwipeDirection): GestureResult =
        GestureResult.Swipe(direction)

    private val SwipeDirection.remotePaneAction: ZellijRemoteAction
        get() =
            when (this) {
                SwipeDirection.UP -> ZellijRemoteAction.PANE_UP
                SwipeDirection.DOWN -> ZellijRemoteAction.PANE_DOWN
                SwipeDirection.LEFT -> ZellijRemoteAction.PANE_LEFT
                SwipeDirection.RIGHT -> ZellijRemoteAction.PANE_RIGHT
            }
}

package com.zellij.keyboard.core

enum class GesturePad {
    FOCUS,
    TABS,
}

/** Product-contract mappings for the two Zellij gesture pads. */
object GestureShortcutMapper {
    fun map(pad: GesturePad, gesture: GestureResult): CommandOperation {
        if (gesture == GestureResult.Cancelled) {
            return CommandOperation.Cancelled
        }

        val command =
            when (pad) {
                GesturePad.FOCUS -> mapFocus(gesture)
                GesturePad.TABS -> mapTabs(gesture)
            }
        return CommandOperation.Emit(command)
    }

    private fun mapFocus(gesture: GestureResult): KeyCommand =
        when (gesture) {
            GestureResult.Tap -> ctrl('h')
            GestureResult.LongPress -> ctrl('w')
            is GestureResult.Swipe ->
                ctrl(
                    when (gesture.direction) {
                        SwipeDirection.UP -> Key.Named.ARROW_UP
                        SwipeDirection.DOWN -> Key.Named.ARROW_DOWN
                        SwipeDirection.LEFT -> Key.Named.ARROW_LEFT
                        SwipeDirection.RIGHT -> Key.Named.ARROW_RIGHT
                    },
                )
            GestureResult.Cancelled -> error("cancelled gestures are handled before mapping")
        }

    private fun mapTabs(gesture: GestureResult): KeyCommand =
        when (gesture) {
            GestureResult.Tap -> KeyCommand(Key.Named.TAB)
            GestureResult.LongPress -> ctrl('r')
            is GestureResult.Swipe ->
                when (gesture.direction) {
                    SwipeDirection.UP -> ctrl('t')
                    SwipeDirection.DOWN -> ctrl('w')
                    SwipeDirection.LEFT -> ctrl('p')
                    SwipeDirection.RIGHT -> ctrl('n')
                }
            GestureResult.Cancelled -> error("cancelled gestures are handled before mapping")
        }

    private fun ctrl(character: Char): KeyCommand =
        KeyCommand(
            key = Key.Character(character),
            modifiers = KeyModifiers.CTRL,
        )

    private fun ctrl(key: Key): KeyCommand =
        KeyCommand(
            key = key,
            modifiers = KeyModifiers.CTRL,
        )
}

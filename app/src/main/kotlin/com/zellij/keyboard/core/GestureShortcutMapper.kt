package com.zellij.keyboard.core

enum class GesturePad {
    TABS,
    PANES,
}

/** Product-contract mappings for the stacked Zellij joystick zones. */
object GestureShortcutMapper {
    fun map(pad: GesturePad, gesture: GestureResult): CommandOperation {
        if (gesture == GestureResult.Cancelled) {
            return CommandOperation.Cancelled
        }

        return when (pad) {
            GesturePad.TABS -> mapTabs(gesture)
            GesturePad.PANES -> mapPanes(gesture)
        }
    }

    private fun mapPanes(gesture: GestureResult): CommandOperation =
        when (gesture) {
            GestureResult.Tap,
            GestureResult.LongPress,
            -> CommandOperation.NoOp
            is GestureResult.Swipe ->
                CommandOperation.Emit(
                    ctrlShift(
                        when (gesture.direction) {
                            SwipeDirection.UP -> Key.Named.ARROW_UP
                            SwipeDirection.DOWN -> Key.Named.ARROW_DOWN
                            SwipeDirection.LEFT -> Key.Named.ARROW_LEFT
                            SwipeDirection.RIGHT -> Key.Named.ARROW_RIGHT
                        },
                    ),
                )
            GestureResult.Cancelled -> error("cancelled gestures are handled before mapping")
        }

    private fun mapTabs(gesture: GestureResult): CommandOperation =
        when (gesture) {
            GestureResult.Tap,
            GestureResult.LongPress,
            -> CommandOperation.NoOp
            is GestureResult.Swipe ->
                when (gesture.direction) {
                    SwipeDirection.UP,
                    SwipeDirection.DOWN,
                    -> CommandOperation.NoOp
                    SwipeDirection.LEFT -> CommandOperation.Emit(ctrlShift(Key.Character(',')))
                    SwipeDirection.RIGHT -> CommandOperation.Emit(ctrlShift(Key.Character('.')))
                }
            GestureResult.Cancelled -> error("cancelled gestures are handled before mapping")
        }

    private fun ctrlShift(key: Key): KeyCommand =
        KeyCommand(
            key = key,
            modifiers = KeyModifiers.CTRL_SHIFT,
        )
}

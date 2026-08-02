package com.zellij.keyboard.core

enum class GesturePad {
    TABS,
    PANES,
}

/** A complete action produced by one of Agentboard's two gesture zones. */
sealed interface GestureAction {
    data class DriveZellij(val action: ZellijRemoteAction) : GestureAction

    data object Microphone : GestureAction

    data object ContinueLastAgent : GestureAction

    data object Ignored : GestureAction

    data object Cancelled : GestureAction
}

enum class ZellijRemoteAction(val wireName: String) {
    TAB_PREVIOUS("tab-previous"),
    TAB_NEXT("tab-next"),
    PANE_UP("pane-up"),
    PANE_DOWN("pane-down"),
    PANE_LEFT("pane-left"),
    PANE_RIGHT("pane-right"),
}

/** Product mappings expressed as semantic Zellij driver actions. */
object GestureShortcutMapper {
    fun map(
        pad: GesturePad,
        gesture: GestureResult,
    ): GestureAction {
        if (gesture == GestureResult.Cancelled) {
            return GestureAction.Cancelled
        }

        return when (pad) {
            GesturePad.TABS -> mapTabs(gesture)
            GesturePad.PANES -> mapPanes(gesture)
        }
    }

    private fun mapTabs(gesture: GestureResult): GestureAction =
        when (gesture) {
            GestureResult.Tap -> GestureAction.Microphone
            GestureResult.LongPress -> GestureAction.ContinueLastAgent
            is GestureResult.Swipe ->
                when (gesture.direction) {
                    SwipeDirection.LEFT -> drive(ZellijRemoteAction.TAB_PREVIOUS)
                    SwipeDirection.RIGHT -> drive(ZellijRemoteAction.TAB_NEXT)
                    SwipeDirection.UP,
                    SwipeDirection.DOWN,
                    -> GestureAction.Ignored
                }
            GestureResult.Cancelled -> error("cancelled gestures are handled before mapping")
        }

    private fun mapPanes(gesture: GestureResult): GestureAction =
        when (gesture) {
            GestureResult.Tap,
            GestureResult.LongPress,
            -> GestureAction.Ignored
            is GestureResult.Swipe ->
                drive(
                    when (gesture.direction) {
                        SwipeDirection.UP -> ZellijRemoteAction.PANE_UP
                        SwipeDirection.DOWN -> ZellijRemoteAction.PANE_DOWN
                        SwipeDirection.LEFT -> ZellijRemoteAction.PANE_LEFT
                        SwipeDirection.RIGHT -> ZellijRemoteAction.PANE_RIGHT
                    },
                )
            GestureResult.Cancelled -> error("cancelled gestures are handled before mapping")
        }

    private fun drive(action: ZellijRemoteAction): GestureAction =
        GestureAction.DriveZellij(action)
}

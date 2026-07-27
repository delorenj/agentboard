package com.zellij.keyboard.input

/**
 * The two actions emitted for every physical key. This deliberately models no
 * text insertion path: terminal input is always a paired physical key press.
 */
enum class AndroidKeyEventAction {
    DOWN,
    UP,
}

/**
 * Pure description of one Android key event.
 *
 * [downEventIndex] points to the matching [AndroidKeyEventAction.DOWN] spec in
 * the containing plan. The Android adapter uses that relationship to preserve
 * each pair's `downTime` while taking every `eventTime` from uptime.
 */
data class AndroidKeyEventSpec(
    val action: AndroidKeyEventAction,
    val keyCode: Int,
    val metaState: Int,
    val downEventIndex: Int,
)

/** Immutable, deterministic sequence ready for an Android sender adapter. */
class AndroidKeyEventPlan internal constructor(events: List<AndroidKeyEventSpec>) {
    val events: List<AndroidKeyEventSpec> = events.toList()

    init {
        require(events.isNotEmpty()) { "A key event plan cannot be empty" }

        events.forEachIndexed { index, event ->
            require(event.downEventIndex in 0..index) {
                "Event $index has invalid down-event index ${event.downEventIndex}"
            }

            val pairedDown = events[event.downEventIndex]
            require(pairedDown.action == AndroidKeyEventAction.DOWN) {
                "Event $index must reference a DOWN event"
            }
            require(pairedDown.keyCode == event.keyCode) {
                "Event $index must reference a DOWN event for the same keycode"
            }
            if (event.action == AndroidKeyEventAction.DOWN) {
                require(event.downEventIndex == index) {
                    "A DOWN event must reference itself"
                }
            }
        }
    }
}

/** Explicit planning result; unsupported characters never produce a partial plan. */
sealed interface TerminalKeyEventPlanResult {
    data class Ready(val plan: AndroidKeyEventPlan) : TerminalKeyEventPlanResult

    data class UnsupportedCharacter(val character: Char) : TerminalKeyEventPlanResult
}

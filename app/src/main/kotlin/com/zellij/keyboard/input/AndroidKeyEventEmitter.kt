package com.zellij.keyboard.input

import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.inputmethod.InputConnection
import com.zellij.keyboard.core.KeyCommand

/**
 * Stateless Android seam that emits terminal commands only through
 * [InputConnection.sendKeyEvent].
 *
 * This adapter deliberately owns no one-shot state. An InputMethodService
 * should first obtain a current InputConnection, then resolve a non-null
 * command, emit it here, and only then adopt the keyboard planner's state that
 * clears any one-shot modifiers. If no connection exists, the caller should
 * preserve its armed state.
 */
object AndroidKeyEventEmitter {
    private const val REPEAT_COUNT = 0
    private const val SCAN_CODE = 0

    // Mirrors InputMethodService.sendDownUpKeyEvents: generated keys should not
    // force the target UI out of touch mode.
    private const val EVENT_FLAGS =
        KeyEvent.FLAG_SOFT_KEYBOARD or KeyEvent.FLAG_KEEP_TOUCH_MODE

    fun emit(
        inputConnection: InputConnection,
        command: KeyCommand,
    ): KeyEventEmissionResult =
        when (val result = TerminalKeyEventPlanner.plan(command)) {
            is TerminalKeyEventPlanResult.Ready -> emit(inputConnection, result.plan)
            is TerminalKeyEventPlanResult.UnsupportedCharacter ->
                KeyEventEmissionResult.NotEmitted(result)
        }

    private fun emit(
        inputConnection: InputConnection,
        plan: AndroidKeyEventPlan,
    ): KeyEventEmissionResult.Completed {
        val eventTimes = LongArray(plan.events.size)
        var previousEventTime = Long.MIN_VALUE

        return KeyEventPlanDispatcher.dispatch(plan) { eventIndex, event ->
            val observedUptime = SystemClock.uptimeMillis()
            val eventTime = maxOf(observedUptime, previousEventTime)
            previousEventTime = eventTime
            eventTimes[eventIndex] = eventTime

            val keyEvent =
                KeyEvent(
                    eventTimes[event.downEventIndex],
                    eventTime,
                    event.action.toAndroidAction(),
                    event.keyCode,
                    REPEAT_COUNT,
                    KeyEvent.normalizeMetaState(event.metaState),
                    KeyCharacterMap.VIRTUAL_KEYBOARD,
                    SCAN_CODE,
                    EVENT_FLAGS,
                    InputDevice.SOURCE_KEYBOARD,
                )

            inputConnection.sendKeyEvent(keyEvent)
        }
    }

    private fun AndroidKeyEventAction.toAndroidAction(): Int =
        when (this) {
            AndroidKeyEventAction.DOWN -> KeyEvent.ACTION_DOWN
            AndroidKeyEventAction.UP -> KeyEvent.ACTION_UP
        }
}

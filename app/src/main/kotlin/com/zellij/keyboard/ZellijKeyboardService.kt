package com.zellij.keyboard

import android.inputmethodservice.InputMethodService
import android.view.View
import android.view.inputmethod.EditorInfo
import com.zellij.keyboard.core.GesturePad
import com.zellij.keyboard.core.GestureResult
import com.zellij.keyboard.core.GestureShortcutMapper
import com.zellij.keyboard.core.KeyModifier
import com.zellij.keyboard.core.TerminalKeyId
import com.zellij.keyboard.core.TerminalKeyIds
import com.zellij.keyboard.core.TerminalKeyboardEffect
import com.zellij.keyboard.core.TerminalKeyboardInput
import com.zellij.keyboard.core.TerminalKeyboardPlanner
import com.zellij.keyboard.core.TerminalKeyboardState
import com.zellij.keyboard.core.TerminalKeyboardUiChange
import com.zellij.keyboard.input.AndroidKeyEventEmitter

/**
 * Low-latency terminal IME. Every output path is a physical key-event plan;
 * text is never committed directly.
 */
class ZellijKeyboardService : InputMethodService() {
    private var keyboardState = TerminalKeyboardState()
    private var terminalInputView: TerminalKeyboardView? = null

    override fun onCreateInputView(): View {
        resetKeyboardState()
        return TerminalKeyboardView(this).also { view ->
            terminalInputView = view
            view.onKeyPressed = ::handleKeyPress
            view.onGesture = ::handleGesture
            view.render(keyboardState)
        }
    }

    override fun onStartInputView(
        attribute: EditorInfo?,
        restarting: Boolean,
    ) {
        super.onStartInputView(attribute, restarting)
        resetKeyboardState()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        resetKeyboardState()
        super.onFinishInputView(finishingInput)
    }

    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onDestroy() {
        terminalInputView = null
        super.onDestroy()
    }

    private fun handleKeyPress(keyId: TerminalKeyId) {
        val plan =
            TerminalKeyboardPlanner.plan(
                state = keyboardState,
                input = TerminalKeyboardInput.KeyPress(keyId),
            )

        when (val effect = plan.effect) {
            is TerminalKeyboardEffect.Emit -> {
                val inputConnection = currentInputConnection ?: return
                AndroidKeyEventEmitter.emit(inputConnection, effect.command)
                updateKeyboardState(plan.nextState)
            }
            is TerminalKeyboardEffect.StateChanged -> {
                updateKeyboardState(plan.nextState)
                terminalInputView?.announceKeyState(effect.change.keyId())
            }
            TerminalKeyboardEffect.Cancelled,
            TerminalKeyboardEffect.NoOp,
            -> updateKeyboardState(plan.nextState)
        }
    }

    private fun handleGesture(
        pad: GesturePad,
        gesture: GestureResult,
    ) {
        val operation = GestureShortcutMapper.map(pad, gesture)
        val resolution = keyboardState.modifiers.resolve(operation)
        val command = resolution.commandToEmit ?: return
        val inputConnection = currentInputConnection ?: return

        AndroidKeyEventEmitter.emit(inputConnection, command)
        updateKeyboardState(
            keyboardState.copy(modifiers = resolution.nextState),
        )
    }

    private fun updateKeyboardState(nextState: TerminalKeyboardState) {
        keyboardState = nextState
        terminalInputView?.render(keyboardState)
    }

    private fun resetKeyboardState() {
        updateKeyboardState(TerminalKeyboardState())
    }

    private fun TerminalKeyboardUiChange.keyId(): TerminalKeyId =
        when (this) {
            TerminalKeyboardUiChange.ShiftToggled -> TerminalKeyIds.SHIFT
            TerminalKeyboardUiChange.LayerToggled -> TerminalKeyIds.LAYER
            is TerminalKeyboardUiChange.ModifierToggled ->
                when (modifier) {
                    KeyModifier.CTRL -> TerminalKeyIds.CTRL
                    KeyModifier.ALT -> TerminalKeyIds.ALT
                }
        }
}

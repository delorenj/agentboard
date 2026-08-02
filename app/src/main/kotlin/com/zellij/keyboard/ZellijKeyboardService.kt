package com.zellij.keyboard

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.inputmethodservice.InputMethodService
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import com.zellij.keyboard.core.AgentCommand
import com.zellij.keyboard.core.AgentCommandPlanner
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
import com.zellij.keyboard.core.VoiceInputSessionGuard
import com.zellij.keyboard.core.VoicePermissionResult
import com.zellij.keyboard.core.VoiceRecognitionToken
import com.zellij.keyboard.core.VoiceResumeToken
import com.zellij.keyboard.input.AndroidKeyEventEmitter
import com.zellij.keyboard.input.KeyCommandSequenceDispatcher
import com.zellij.keyboard.input.KeyCommandSequenceResult

/** Voice-first terminal IME with physical-key navigation and shell command launchers. */
class ZellijKeyboardService : InputMethodService() {
    private var keyboardState = TerminalKeyboardState()
    private var terminalInputView: TerminalKeyboardView? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    private val voiceSessionGuard = VoiceInputSessionGuard()

    override fun onCreate() {
        super.onCreate()
        MicrophonePermissionContract.onPermissionResult = ::handleMicrophonePermissionResult
    }

    override fun onCreateInputView(): View {
        resetKeyboardState()
        return TerminalKeyboardView(this).also { view ->
            terminalInputView = view
            view.onKeyPressed = ::handleKeyPress
            view.onGesture = ::handleGesture
            view.onAgentCommand = ::handleAgentCommand
            view.onMicrophonePressed = ::handleMicrophonePress
            view.render(keyboardState)
            view.renderMicrophoneState(isListening)
        }
    }

    override fun onStartInputView(
        attribute: EditorInfo?,
        restarting: Boolean,
    ) {
        super.onStartInputView(attribute, restarting)
        stopListening()
        val resumeToken = voiceSessionGuard.onInputViewStarted(isInputViewShown)
        resetKeyboardState()
        resumeToken?.let { token ->
            terminalInputView?.post { startVoiceRecognition(token) }
        }
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        temporarilyDeactivateVoiceInput()
        resetKeyboardState()
        super.onFinishInputView(finishingInput)
    }

    override fun onWindowHidden() {
        temporarilyDeactivateVoiceInput()
        super.onWindowHidden()
    }

    override fun onUnbindInput() {
        deactivateVoiceInputForTargetChange()
        super.onUnbindInput()
    }

    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onDestroy() {
        deactivateVoiceInputForTargetChange()
        MicrophonePermissionContract.onPermissionResult = null
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

    private fun handleAgentCommand(command: AgentCommand) {
        val inputConnection = currentInputConnection ?: return
        when (
            KeyCommandSequenceDispatcher.dispatch(AgentCommandPlanner.plan(command)) { keyCommand ->
                AndroidKeyEventEmitter.emit(inputConnection, keyCommand)
            }
        ) {
            is KeyCommandSequenceResult.Completed -> resetKeyboardState()
            is KeyCommandSequenceResult.Aborted ->
                terminalInputView?.announceStatus(getString(R.string.agent_command_failed))
        }
    }

    private fun handleMicrophonePress() {
        if (isListening) {
            stopListening()
            return
        }

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            voiceSessionGuard.onPermissionRequested()
            terminalInputView?.announceStatus(getString(R.string.voice_permission_needed))
            try {
                startActivity(
                    Intent(this, MicrophonePermissionActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    },
                )
            } catch (_: RuntimeException) {
                voiceSessionGuard.cancelPermissionRequest()
                terminalInputView?.announceStatus(getString(R.string.voice_permission_error))
            }
            return
        }

        startVoiceRecognition()
    }

    private fun handleMicrophonePermissionResult(granted: Boolean) {
        val confirmedGranted =
            granted &&
                checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        when (val result = voiceSessionGuard.onPermissionResult(confirmedGranted, isInputViewShown)) {
            VoicePermissionResult.Ignored,
            VoicePermissionResult.PendingNextInputView,
            -> Unit
            VoicePermissionResult.Denied ->
                if (isInputViewShown) {
                    terminalInputView?.announceStatus(getString(R.string.voice_permission_denied))
                }
            is VoicePermissionResult.ResumeNow ->
                terminalInputView?.post { startVoiceRecognition(result.token) }
        }
    }

    private fun startVoiceRecognition(resumeToken: VoiceResumeToken? = null) {
        if (isListening) {
            return
        }
        if (!isInputViewShown ||
            (resumeToken != null && !voiceSessionGuard.canResume(resumeToken, isInputViewShown))
        ) {
            return
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            terminalInputView?.announceStatus(getString(R.string.voice_permission_denied))
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            terminalInputView?.announceStatus(getString(R.string.voice_recognition_unavailable))
            return
        }

        val inputConnection = currentInputConnection ?: return
        val recognitionToken =
            voiceSessionGuard.beginRecognition(isInputViewShown) ?: return

        try {
            val recognizer = SpeechRecognizer.createSpeechRecognizer(this)
            speechRecognizer = recognizer
            recognizer.setRecognitionListener(
                createRecognitionListener(recognitionToken, inputConnection),
            )
            setListening(true)
            recognizer.startListening(
                Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(
                        RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
                    )
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                },
            )
        } catch (_: RuntimeException) {
            completeRecognition(recognitionToken, getString(R.string.voice_error))
        }
    }

    private fun createRecognitionListener(
        token: VoiceRecognitionToken,
        inputConnection: InputConnection,
    ): RecognitionListener =
        object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                if (isCurrentRecognition(token, inputConnection)) {
                    setListening(true, getString(R.string.voice_listening))
                }
            }

            override fun onBeginningOfSpeech() = Unit

            override fun onRmsChanged(rmsdB: Float) = Unit

            override fun onBufferReceived(buffer: ByteArray?) = Unit

            override fun onEndOfSpeech() = Unit

            override fun onError(error: Int) {
                if (!isCurrentRecognition(token, inputConnection)) {
                    return
                }
                val message =
                    if (error == SpeechRecognizer.ERROR_NO_MATCH ||
                        error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                    ) {
                        getString(R.string.voice_no_match)
                    } else {
                        getString(R.string.voice_error)
                    }
                completeRecognition(token, message)
            }

            override fun onResults(results: Bundle?) {
                if (!isCurrentRecognition(token, inputConnection)) {
                    return
                }
                val transcription =
                    results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        ?.trim()
                        .orEmpty()

                if (transcription.isEmpty()) {
                    completeRecognition(token, getString(R.string.voice_no_match))
                    return
                }

                val inserted = inputConnection.commitText(transcription, 1)
                completeRecognition(
                    token,
                    getString(if (inserted) R.string.voice_inserted else R.string.voice_error),
                )
            }

            override fun onPartialResults(partialResults: Bundle?) = Unit

            override fun onEvent(
                eventType: Int,
                params: Bundle?,
            ) = Unit
        }

    private fun isCurrentRecognition(
        token: VoiceRecognitionToken,
        inputConnection: InputConnection,
    ): Boolean {
        if (!voiceSessionGuard.canCommit(token, isInputViewShown)) {
            return false
        }
        if (currentInputConnection !== inputConnection) {
            completeRecognition(token, status = null)
            return false
        }
        return true
    }

    private fun completeRecognition(
        token: VoiceRecognitionToken,
        status: String?,
    ) {
        voiceSessionGuard.finishRecognition(token)
        releaseSpeechRecognizer(cancel = false)
        setListening(false, status)
    }

    private fun stopListening() {
        voiceSessionGuard.cancelRecognition()
        releaseSpeechRecognizer(cancel = isListening)
        setListening(false)
    }

    private fun temporarilyDeactivateVoiceInput() {
        voiceSessionGuard.onInputViewTemporarilyStopped()
        releaseSpeechRecognizer(cancel = isListening)
        setListening(false)
    }

    private fun deactivateVoiceInputForTargetChange() {
        voiceSessionGuard.onInputTargetChanged()
        releaseSpeechRecognizer(cancel = isListening)
        setListening(false)
    }

    private fun releaseSpeechRecognizer(cancel: Boolean) {
        val recognizer = speechRecognizer ?: return
        speechRecognizer = null
        if (cancel) {
            recognizer.cancel()
        }
        recognizer.destroy()
    }

    private fun setListening(
        listening: Boolean,
        status: String? = null,
    ) {
        isListening = listening
        terminalInputView?.renderMicrophoneState(listening, status)
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

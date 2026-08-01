package com.zellij.keyboard

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.View
import android.view.inputmethod.EditorInfo
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
import com.zellij.keyboard.input.AndroidKeyEventEmitter

/** Voice-first terminal IME with physical-key navigation and shell command launchers. */
class ZellijKeyboardService : InputMethodService() {
    private var keyboardState = TerminalKeyboardState()
    private var terminalInputView: TerminalKeyboardView? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    private var permissionRequestPending = false

    private val permissionResultReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context?,
                intent: Intent?,
            ) {
                if (intent?.action != MicrophonePermissionContract.ACTION_PERMISSION_RESULT) {
                    return
                }

                permissionRequestPending = false
                val granted =
                    intent.getBooleanExtra(MicrophonePermissionContract.EXTRA_GRANTED, false) &&
                        checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
                        PackageManager.PERMISSION_GRANTED
                if (granted) {
                    terminalInputView?.post(::startVoiceRecognition)
                } else {
                    terminalInputView?.announceStatus(getString(R.string.voice_permission_denied))
                }
            }
        }

    private val recognitionListener =
        object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                if (isListening) {
                    setListening(true, getString(R.string.voice_listening))
                }
            }

            override fun onBeginningOfSpeech() = Unit

            override fun onRmsChanged(rmsdB: Float) = Unit

            override fun onBufferReceived(buffer: ByteArray?) = Unit

            override fun onEndOfSpeech() = Unit

            override fun onError(error: Int) {
                if (!isListening) {
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
                if (error == SpeechRecognizer.ERROR_CLIENT ||
                    error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY
                ) {
                    speechRecognizer?.destroy()
                    speechRecognizer = null
                }
                setListening(false, message)
            }

            override fun onResults(results: Bundle?) {
                if (!isListening) {
                    return
                }
                val transcription =
                    results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        ?.trim()
                        .orEmpty()

                if (transcription.isEmpty()) {
                    setListening(false, getString(R.string.voice_no_match))
                    return
                }

                val inserted = currentInputConnection?.commitText(transcription, 1) == true
                setListening(
                    false,
                    getString(
                        if (inserted) R.string.voice_inserted else R.string.voice_error,
                    ),
                )
            }

            override fun onPartialResults(partialResults: Bundle?) = Unit

            override fun onEvent(
                eventType: Int,
                params: Bundle?,
            ) = Unit
        }

    override fun onCreate() {
        super.onCreate()
        registerPermissionResultReceiver()
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
        resetKeyboardState()
        if (permissionRequestPending &&
            checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            permissionRequestPending = false
            terminalInputView?.post(::startVoiceRecognition)
        }
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        stopListening()
        resetKeyboardState()
        super.onFinishInputView(finishingInput)
    }

    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onDestroy() {
        isListening = false
        speechRecognizer?.destroy()
        speechRecognizer = null
        unregisterReceiver(permissionResultReceiver)
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
        AgentCommandPlanner.plan(command).forEach { keyCommand ->
            AndroidKeyEventEmitter.emit(inputConnection, keyCommand)
        }
        resetKeyboardState()
    }

    private fun handleMicrophonePress() {
        if (isListening) {
            stopListening()
            return
        }

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            permissionRequestPending = true
            terminalInputView?.announceStatus(getString(R.string.voice_permission_needed))
            try {
                startActivity(
                    Intent(this, MicrophonePermissionActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    },
                )
            } catch (_: RuntimeException) {
                permissionRequestPending = false
                terminalInputView?.announceStatus(getString(R.string.voice_permission_error))
            }
            return
        }

        permissionRequestPending = false
        startVoiceRecognition()
    }

    private fun startVoiceRecognition() {
        if (isListening) {
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

        val recognizer =
            speechRecognizer
                ?: SpeechRecognizer.createSpeechRecognizer(this).also {
                    it.setRecognitionListener(recognitionListener)
                    speechRecognizer = it
                }

        try {
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
            recognizer.destroy()
            speechRecognizer = null
            setListening(false, getString(R.string.voice_error))
        }
    }

    private fun stopListening() {
        val shouldCancel = isListening
        setListening(false)
        if (shouldCancel) {
            speechRecognizer?.cancel()
        }
    }

    private fun setListening(
        listening: Boolean,
        status: String? = null,
    ) {
        isListening = listening
        terminalInputView?.renderMicrophoneState(listening, status)
    }

    @Suppress("DEPRECATION", "UnspecifiedRegisterReceiverFlag")
    private fun registerPermissionResultReceiver() {
        val filter = IntentFilter(MicrophonePermissionContract.ACTION_PERMISSION_RESULT)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(permissionResultReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(permissionResultReceiver, filter)
        }
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

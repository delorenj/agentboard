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
import com.zellij.keyboard.core.GestureAction
import com.zellij.keyboard.core.GesturePad
import com.zellij.keyboard.core.GestureResult
import com.zellij.keyboard.core.GestureShortcutMapper
import com.zellij.keyboard.core.VoiceInputSessionGuard
import com.zellij.keyboard.core.VoiceEditorFingerprint
import com.zellij.keyboard.core.VoiceInputTarget
import com.zellij.keyboard.core.VoicePermissionResult
import com.zellij.keyboard.core.VoiceRecognitionToken
import com.zellij.keyboard.core.VoiceResumeToken
import com.zellij.keyboard.input.AndroidKeyEventEmitter
import com.zellij.keyboard.input.HttpZellijBridgeClient
import com.zellij.keyboard.input.KeyCommandSequenceDispatcher
import com.zellij.keyboard.input.KeyCommandSequenceResult
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** Voice-first terminal IME with two gesture zones and physical-key Zellij navigation. */
class ZellijKeyboardService : InputMethodService() {
    private var terminalInputView: TerminalKeyboardView? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    private val voiceSessionGuard = VoiceInputSessionGuard()
    private val zellijBridge: HttpZellijBridgeClient by lazy {
        HttpZellijBridgeClient(BuildConfig.ZELLIJ_DRIVER_URL, BuildConfig.ZELLIJ_DRIVER_TOKEN)
    }
    private val zellijBridgeExecutor: ExecutorService =
        Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "agentboard-zellij-bridge")
        }

    override fun onCreate() {
        super.onCreate()
        MicrophonePermissionContract.onPermissionResult = ::handleMicrophonePermissionResult
    }

    override fun onCreateInputView(): View {
        return TerminalKeyboardView(this).also { view ->
            terminalInputView = view
            view.onGesture = ::handleGesture
            view.renderMicrophoneState(isListening)
        }
    }

    override fun onStartInputView(
        attribute: EditorInfo?,
        restarting: Boolean,
    ) {
        super.onStartInputView(attribute, restarting)
        stopListening()
        val resumeToken =
            voiceSessionGuard.onInputViewStarted(
                isShown = isInputViewShown,
                target = currentVoiceInputTarget(attribute),
            )
        resumeToken?.let { token ->
            terminalInputView?.post { startVoiceRecognition(token) }
        }
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        temporarilyDeactivateVoiceInput()
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
        zellijBridgeExecutor.shutdownNow()
        MicrophonePermissionContract.onPermissionResult = null
        terminalInputView = null
        super.onDestroy()
    }

    private fun handleGesture(
        pad: GesturePad,
        gesture: GestureResult,
    ) {
        when (val action = GestureShortcutMapper.map(pad, gesture)) {
            is GestureAction.DriveZellij -> driveZellij(action)
            GestureAction.Microphone -> handleMicrophonePress()
            GestureAction.ContinueLastAgent -> handleAgentCommand(AgentCommand.CONTINUE)
            GestureAction.Ignored,
            GestureAction.Cancelled,
            -> Unit
        }
    }

    private fun driveZellij(action: GestureAction.DriveZellij) {
        if (!zellijBridge.isConfigured) {
            terminalInputView?.announceStatus(getString(R.string.zellij_bridge_not_configured))
            return
        }

        zellijBridgeExecutor.execute {
            if (!zellijBridge.drive(action.action)) {
                terminalInputView?.post {
                    terminalInputView?.announceStatus(getString(R.string.zellij_bridge_failed))
                }
            }
        }
    }

    private fun handleAgentCommand(command: AgentCommand) {
        val inputConnection = currentInputConnection ?: return
        when (
            KeyCommandSequenceDispatcher.dispatch(AgentCommandPlanner.plan(command)) { keyCommand ->
                AndroidKeyEventEmitter.emit(inputConnection, keyCommand)
            }
        ) {
            is KeyCommandSequenceResult.Completed -> Unit
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
            val inputTarget = currentVoiceInputTarget() ?: return
            voiceSessionGuard.onPermissionRequested(inputTarget)
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
        when (
            val result =
                voiceSessionGuard.onPermissionResult(
                    confirmedGranted,
                    isInputViewShown,
                    currentVoiceInputTarget(),
                )
        ) {
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
            (resumeToken != null &&
                !voiceSessionGuard.canResume(
                    resumeToken,
                    isInputViewShown,
                    currentVoiceInputTarget(),
                ))
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
        val inputTarget = currentVoiceInputTarget() ?: return
        val recognitionToken =
            voiceSessionGuard.beginRecognition(isInputViewShown, inputTarget) ?: return

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
        if (!voiceSessionGuard.canCommit(token, isInputViewShown, currentVoiceInputTarget())) {
            return false
        }
        if (currentInputConnection !== inputConnection) {
            completeRecognition(token, status = null)
            return false
        }
        return true
    }

    private fun currentVoiceInputTarget(
        editorInfo: EditorInfo? = currentInputEditorInfo,
    ): VoiceInputTarget? {
        val inputConnection = currentInputConnection ?: return null
        val info = editorInfo ?: return null
        return VoiceInputTarget(
            connectionIdentity = inputConnection,
            editor =
                VoiceEditorFingerprint(
                    packageName = info.packageName,
                    fieldId = info.fieldId,
                    inputType = info.inputType,
                    imeOptions = info.imeOptions,
                    actionId = info.actionId,
                ),
        )
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

}

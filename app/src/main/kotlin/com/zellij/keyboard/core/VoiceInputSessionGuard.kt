package com.zellij.keyboard.core

/** Token proving that a permission resume still targets the input view that requested it. */
class VoiceResumeToken internal constructor(
    internal val inputGeneration: Long,
)

/** Token proving that a speech callback belongs to the current input-view generation. */
class VoiceRecognitionToken internal constructor(
    internal val inputGeneration: Long,
    internal val recognitionSequence: Long,
)

sealed interface VoicePermissionResult {
    data object Ignored : VoicePermissionResult

    data object Denied : VoicePermissionResult

    data object PendingNextInputView : VoicePermissionResult

    data class ResumeNow(val token: VoiceResumeToken) : VoicePermissionResult
}

/**
 * Pure lifecycle gate for microphone permission and recognition callbacks.
 * Every input-view transition invalidates tokens from the previous target.
 */
class VoiceInputSessionGuard {
    private var inputGeneration = 0L
    private var recognitionSequence = 0L
    private var inputViewActive = false
    private var permissionRequestOutstanding = false
    private var resumeAfterPermission = false
    private var recognitionToken: VoiceRecognitionToken? = null

    fun onPermissionRequested() {
        permissionRequestOutstanding = true
        resumeAfterPermission = false
    }

    fun cancelPermissionRequest() {
        permissionRequestOutstanding = false
        resumeAfterPermission = false
    }

    fun onPermissionResult(
        granted: Boolean,
        isShown: Boolean,
    ): VoicePermissionResult {
        if (!permissionRequestOutstanding) {
            return VoicePermissionResult.Ignored
        }
        permissionRequestOutstanding = false
        if (!granted) {
            resumeAfterPermission = false
            return VoicePermissionResult.Denied
        }
        if (inputViewActive && isShown) {
            resumeAfterPermission = false
            return VoicePermissionResult.ResumeNow(VoiceResumeToken(inputGeneration))
        }
        resumeAfterPermission = true
        return VoicePermissionResult.PendingNextInputView
    }

    fun onInputViewStarted(isShown: Boolean): VoiceResumeToken? {
        inputGeneration += 1
        inputViewActive = isShown
        recognitionToken = null

        if (!isShown || !resumeAfterPermission) {
            return null
        }

        resumeAfterPermission = false
        return VoiceResumeToken(inputGeneration)
    }

    fun canResume(
        token: VoiceResumeToken,
        isShown: Boolean,
    ): Boolean =
        inputViewActive && isShown && token.inputGeneration == inputGeneration

    fun beginRecognition(isShown: Boolean): VoiceRecognitionToken? {
        if (!inputViewActive || !isShown) {
            return null
        }

        return VoiceRecognitionToken(
            inputGeneration = inputGeneration,
            recognitionSequence = ++recognitionSequence,
        ).also { recognitionToken = it }
    }

    fun canCommit(
        token: VoiceRecognitionToken,
        isShown: Boolean,
    ): Boolean =
        inputViewActive &&
            isShown &&
            token === recognitionToken &&
            token.inputGeneration == inputGeneration

    fun finishRecognition(token: VoiceRecognitionToken) {
        if (recognitionToken === token) {
            recognitionToken = null
        }
    }

    fun cancelRecognition() {
        recognitionToken = null
    }

    /** Hiding the IME invalidates recognition but preserves an outstanding permission bridge. */
    fun onInputViewTemporarilyStopped() {
        inputGeneration += 1
        inputViewActive = false
        recognitionToken = null
    }

    /** A real input-target change must not carry permission resume state into another target. */
    fun onInputTargetChanged() {
        onInputViewTemporarilyStopped()
        cancelPermissionRequest()
    }
}

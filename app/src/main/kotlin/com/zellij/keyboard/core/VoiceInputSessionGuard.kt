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

    /** Returns false for late results from a request invalidated by a lifecycle stop. */
    fun onPermissionResult(granted: Boolean): Boolean {
        if (!permissionRequestOutstanding) {
            return false
        }
        permissionRequestOutstanding = false
        resumeAfterPermission = granted
        return true
    }

    fun onInputViewStarted(isShown: Boolean): VoiceResumeToken? {
        inputGeneration += 1
        inputViewActive = isShown
        recognitionToken = null

        if (!isShown || !resumeAfterPermission) {
            if (!isShown) {
                resumeAfterPermission = false
            }
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

    fun onInputViewStopped() {
        inputGeneration += 1
        inputViewActive = false
        permissionRequestOutstanding = false
        resumeAfterPermission = false
        recognitionToken = null
    }
}

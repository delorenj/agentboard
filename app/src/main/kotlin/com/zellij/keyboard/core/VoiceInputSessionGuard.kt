package com.zellij.keyboard.core

data class VoiceEditorFingerprint(
    val packageName: String?,
    val fieldId: Int,
    val inputType: Int,
    val imeOptions: Int,
    val actionId: Int,
)

class VoiceInputTarget(
    private val connectionIdentity: Any,
    val editor: VoiceEditorFingerprint,
) {
    fun matches(other: VoiceInputTarget): Boolean =
        connectionIdentity === other.connectionIdentity && editor == other.editor
}

/** Token proving that a permission resume still targets the input view that requested it. */
class VoiceResumeToken internal constructor(
    internal val inputGeneration: Long,
    internal val target: VoiceInputTarget,
)

/** Token proving that a speech callback belongs to the current input-view generation. */
class VoiceRecognitionToken internal constructor(
    internal val inputGeneration: Long,
    internal val recognitionSequence: Long,
    internal val target: VoiceInputTarget,
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
    private var inputTarget: VoiceInputTarget? = null
    private var permissionRequestOutstanding = false
    private var permissionRequestTarget: VoiceInputTarget? = null
    private var resumeAfterPermission = false
    private var recognitionToken: VoiceRecognitionToken? = null

    fun onPermissionRequested(target: VoiceInputTarget) {
        permissionRequestOutstanding = true
        permissionRequestTarget = target
        resumeAfterPermission = false
    }

    fun cancelPermissionRequest() {
        permissionRequestOutstanding = false
        permissionRequestTarget = null
        resumeAfterPermission = false
    }

    fun onPermissionResult(
        granted: Boolean,
        isShown: Boolean,
        target: VoiceInputTarget?,
    ): VoicePermissionResult {
        if (!permissionRequestOutstanding) {
            return VoicePermissionResult.Ignored
        }
        permissionRequestOutstanding = false
        if (!granted) {
            cancelPermissionRequest()
            return VoicePermissionResult.Denied
        }
        val requestedTarget = permissionRequestTarget ?: return VoicePermissionResult.Ignored
        if (inputViewActive && isShown && target != null &&
            inputTarget?.matches(requestedTarget) == true &&
            target.matches(requestedTarget)
        ) {
            resumeAfterPermission = false
            permissionRequestTarget = null
            return VoicePermissionResult.ResumeNow(
                VoiceResumeToken(inputGeneration, requestedTarget),
            )
        }
        if (inputViewActive && isShown) {
            cancelPermissionRequest()
            return VoicePermissionResult.Ignored
        }
        resumeAfterPermission = true
        return VoicePermissionResult.PendingNextInputView
    }

    fun onInputViewStarted(
        isShown: Boolean,
        target: VoiceInputTarget?,
    ): VoiceResumeToken? {
        inputGeneration += 1
        inputViewActive = isShown
        inputTarget = target.takeIf { isShown }
        recognitionToken = null

        val requestedTarget = permissionRequestTarget
        if (isShown && requestedTarget != null &&
            (target == null || !target.matches(requestedTarget))
        ) {
            cancelPermissionRequest()
            return null
        }

        if (!isShown || !resumeAfterPermission) {
            return null
        }

        cancelPermissionRequest()
        if (target == null || requestedTarget == null || !target.matches(requestedTarget)) {
            return null
        }
        return VoiceResumeToken(inputGeneration, target)
    }

    fun canResume(
        token: VoiceResumeToken,
        isShown: Boolean,
        target: VoiceInputTarget?,
    ): Boolean =
        inputViewActive &&
            isShown &&
            token.inputGeneration == inputGeneration &&
            target != null &&
            inputTarget?.matches(token.target) == true &&
            target.matches(token.target)

    fun beginRecognition(
        isShown: Boolean,
        target: VoiceInputTarget?,
    ): VoiceRecognitionToken? {
        val activeTarget = inputTarget
        if (!inputViewActive || !isShown || target == null || activeTarget == null ||
            !target.matches(activeTarget)
        ) {
            return null
        }

        return VoiceRecognitionToken(
            inputGeneration = inputGeneration,
            recognitionSequence = ++recognitionSequence,
            target = activeTarget,
        ).also { recognitionToken = it }
    }

    fun canCommit(
        token: VoiceRecognitionToken,
        isShown: Boolean,
        target: VoiceInputTarget?,
    ): Boolean =
        inputViewActive &&
            isShown &&
            token === recognitionToken &&
            token.inputGeneration == inputGeneration &&
            target != null &&
            inputTarget?.matches(token.target) == true &&
            target.matches(token.target)

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
        inputTarget = null
        recognitionToken = null
    }

    /** A real input-target change must not carry permission resume state into another target. */
    fun onInputTargetChanged() {
        onInputViewTemporarilyStopped()
        cancelPermissionRequest()
    }
}

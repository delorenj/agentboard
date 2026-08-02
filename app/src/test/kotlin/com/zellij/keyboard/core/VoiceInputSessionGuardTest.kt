package com.zellij.keyboard.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VoiceInputSessionGuardTest {
    private val connectionA = Any()
    private val connectionB = Any()
    private val editorA = editor(fieldId = 10)
    private val editorB = editor(fieldId = 20)

    @Test
    fun `request hide grant and same target restart consumes resume exactly once`() {
        val guard = VoiceInputSessionGuard()
        val target = target(connectionA, editorA)
        guard.onInputViewStarted(isShown = true, target = target)
        guard.onPermissionRequested(target)
        guard.onInputViewTemporarilyStopped()
        assertEquals(
            VoicePermissionResult.PendingNextInputView,
            guard.onPermissionResult(granted = true, isShown = false, target = null),
        )

        val restartedTarget = target(connectionA, editorA.copy())
        val resume =
            assertNotNull(
                guard.onInputViewStarted(isShown = true, target = restartedTarget),
            )
        assertTrue(guard.canResume(resume, isShown = true, target = restartedTarget))
        assertNull(guard.onInputViewStarted(isShown = true, target = restartedTarget))
    }

    @Test
    fun `same client switching to a different editor cancels pending resume`() {
        val guard = VoiceInputSessionGuard()
        val requestTarget = target(connectionA, editorA)
        guard.onInputViewStarted(isShown = true, target = requestTarget)
        guard.onPermissionRequested(requestTarget)
        guard.onInputViewTemporarilyStopped()

        assertNull(
            guard.onInputViewStarted(
                isShown = true,
                target = target(connectionA, editorB),
            ),
        )
        assertEquals(
            VoicePermissionResult.Ignored,
            guard.onPermissionResult(
                granted = true,
                isShown = true,
                target = target(connectionA, editorB),
            ),
        )
    }

    @Test
    fun `recreated input connection cancels pending resume even for same editor`() {
        val guard = pendingGrant(target(connectionA, editorA))

        assertNull(
            guard.onInputViewStarted(
                isShown = true,
                target = target(connectionB, editorA.copy()),
            ),
        )
    }

    @Test
    fun `denial and cancellation clear the permission bridge`() {
        val denied = VoiceInputSessionGuard()
        val deniedTarget = target(connectionA, editorA)
        denied.onInputViewStarted(isShown = true, target = deniedTarget)
        denied.onPermissionRequested(deniedTarget)
        denied.onInputViewTemporarilyStopped()
        assertEquals(
            VoicePermissionResult.Denied,
            denied.onPermissionResult(granted = false, isShown = false, target = null),
        )
        assertNull(denied.onInputViewStarted(isShown = true, target = deniedTarget))

        val cancelled = VoiceInputSessionGuard()
        cancelled.onInputViewStarted(isShown = true, target = deniedTarget)
        cancelled.onPermissionRequested(deniedTarget)
        cancelled.cancelPermissionRequest()
        assertEquals(
            VoicePermissionResult.Ignored,
            cancelled.onPermissionResult(
                granted = true,
                isShown = true,
                target = deniedTarget,
            ),
        )
    }

    @Test
    fun `unbind makes a late grant ineligible for another input target`() {
        val guard = VoiceInputSessionGuard()
        val target = target(connectionA, editorA)
        guard.onInputViewStarted(isShown = true, target = target)
        guard.onPermissionRequested(target)
        guard.onInputViewTemporarilyStopped()
        guard.onInputTargetChanged()

        assertEquals(
            VoicePermissionResult.Ignored,
            guard.onPermissionResult(granted = true, isShown = false, target = null),
        )
        assertNull(guard.onInputViewStarted(isShown = true, target = target))
    }

    @Test
    fun `normal hide with no permission request never resumes`() {
        val guard = VoiceInputSessionGuard()
        val target = target(connectionA, editorA)
        guard.onInputViewStarted(isShown = true, target = target)
        guard.onInputViewTemporarilyStopped()

        assertNull(guard.onInputViewStarted(isShown = true, target = target))
    }

    @Test
    fun `shown permission result resumes only the requesting target generation`() {
        val guard = VoiceInputSessionGuard()
        val requestTarget = target(connectionA, editorA)
        guard.onInputViewStarted(isShown = true, target = requestTarget)
        guard.onPermissionRequested(requestTarget)
        val result =
            assertIs<VoicePermissionResult.ResumeNow>(
                guard.onPermissionResult(
                    granted = true,
                    isShown = true,
                    target = requestTarget,
                ),
            )

        assertTrue(guard.canResume(result.token, isShown = true, target = requestTarget))
        assertFalse(
            guard.canResume(
                result.token,
                isShown = true,
                target = target(connectionA, editorB),
            ),
        )
    }

    @Test
    fun `recognition from editor A cannot commit after editor B starts`() {
        val guard = VoiceInputSessionGuard()
        val targetA = target(connectionA, editorA)
        val targetB = target(connectionA, editorB)
        guard.onInputViewStarted(isShown = true, target = targetA)
        val stale = assertNotNull(guard.beginRecognition(isShown = true, target = targetA))

        guard.onInputViewTemporarilyStopped()
        guard.onInputViewStarted(isShown = true, target = targetB)

        assertFalse(guard.canCommit(stale, isShown = true, target = targetB))
    }

    private fun pendingGrant(requestTarget: VoiceInputTarget): VoiceInputSessionGuard =
        VoiceInputSessionGuard().apply {
            onInputViewStarted(isShown = true, target = requestTarget)
            onPermissionRequested(requestTarget)
            onInputViewTemporarilyStopped()
            assertEquals(
                VoicePermissionResult.PendingNextInputView,
                onPermissionResult(granted = true, isShown = false, target = null),
            )
        }

    private fun target(
        connection: Any,
        editor: VoiceEditorFingerprint,
    ): VoiceInputTarget = VoiceInputTarget(connection, editor)

    private fun editor(fieldId: Int): VoiceEditorFingerprint =
        VoiceEditorFingerprint(
            packageName = "com.example.terminal",
            fieldId = fieldId,
            inputType = 1,
            imeOptions = 2,
            actionId = 3,
        )
}

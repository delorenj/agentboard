package com.zellij.keyboard.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VoiceInputSessionGuardTest {
    @Test
    fun `request hide grant and same input restart consumes resume exactly once`() {
        val guard = VoiceInputSessionGuard()
        guard.onInputViewStarted(isShown = true)
        guard.onPermissionRequested()
        guard.onInputViewTemporarilyStopped()
        assertEquals(
            VoicePermissionResult.PendingNextInputView,
            guard.onPermissionResult(granted = true, isShown = false),
        )

        val resume = assertNotNull(guard.onInputViewStarted(isShown = true))
        assertTrue(guard.canResume(resume, isShown = true))
        assertNull(guard.onInputViewStarted(isShown = true))
    }

    @Test
    fun `denial clears the permission bridge`() {
        val guard = VoiceInputSessionGuard()
        guard.onInputViewStarted(isShown = true)
        guard.onPermissionRequested()
        guard.onInputViewTemporarilyStopped()

        assertEquals(
            VoicePermissionResult.Denied,
            guard.onPermissionResult(granted = false, isShown = false),
        )
        assertNull(guard.onInputViewStarted(isShown = true))
    }

    @Test
    fun `cancelled permission request ignores a late result`() {
        val guard = VoiceInputSessionGuard()
        guard.onInputViewStarted(isShown = true)
        guard.onPermissionRequested()
        guard.cancelPermissionRequest()

        assertEquals(
            VoicePermissionResult.Ignored,
            guard.onPermissionResult(granted = true, isShown = true),
        )
    }

    @Test
    fun `unbind makes a late grant ineligible for another input target`() {
        val guard = VoiceInputSessionGuard()
        guard.onInputViewStarted(isShown = true)
        guard.onPermissionRequested()
        guard.onInputViewTemporarilyStopped()
        guard.onInputTargetChanged()

        assertEquals(
            VoicePermissionResult.Ignored,
            guard.onPermissionResult(granted = true, isShown = false),
        )
        assertNull(guard.onInputViewStarted(isShown = true))
    }

    @Test
    fun `normal hide with no permission request never resumes`() {
        val guard = VoiceInputSessionGuard()
        guard.onInputViewStarted(isShown = true)
        guard.onInputViewTemporarilyStopped()

        assertNull(guard.onInputViewStarted(isShown = true))
    }

    @Test
    fun `shown permission result can resume only its current generation`() {
        val guard = VoiceInputSessionGuard()
        guard.onInputViewStarted(isShown = true)
        guard.onPermissionRequested()
        val result =
            assertIs<VoicePermissionResult.ResumeNow>(
                guard.onPermissionResult(granted = true, isShown = true),
            )

        assertTrue(guard.canResume(result.token, isShown = true))
        guard.onInputViewTemporarilyStopped()
        assertFalse(guard.canResume(result.token, isShown = true))
    }

    @Test
    fun `new input target invalidates callbacks from the previous generation`() {
        val guard = VoiceInputSessionGuard()
        guard.onInputViewStarted(isShown = true)
        val stale = assertNotNull(guard.beginRecognition(isShown = true))

        guard.onInputViewTemporarilyStopped()
        assertFalse(guard.canCommit(stale, isShown = true))

        guard.onInputViewStarted(isShown = true)
        val current = assertNotNull(guard.beginRecognition(isShown = true))
        assertTrue(guard.canCommit(current, isShown = true))
        guard.finishRecognition(current)
        assertFalse(guard.canCommit(current, isShown = true))
    }
}

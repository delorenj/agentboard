package com.zellij.keyboard.core

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VoiceInputSessionGuardTest {
    @Test
    fun `permission resume is consumed only by the current shown input view`() {
        val guard = VoiceInputSessionGuard()
        guard.onPermissionRequested()
        assertTrue(guard.onPermissionResult(granted = true))

        val resume = assertNotNull(guard.onInputViewStarted(isShown = true))
        assertTrue(guard.canResume(resume, isShown = true))

        guard.onInputViewStopped()
        assertFalse(guard.canResume(resume, isShown = true))
        assertNull(guard.onInputViewStarted(isShown = true))
    }

    @Test
    fun `hidden and stopped views invalidate pending and active recognition`() {
        val guard = VoiceInputSessionGuard()
        guard.onInputViewStarted(isShown = true)
        val recognition = assertNotNull(guard.beginRecognition(isShown = true))
        assertTrue(guard.canCommit(recognition, isShown = true))
        assertFalse(guard.canCommit(recognition, isShown = false))

        guard.onInputViewStopped()
        assertFalse(guard.canCommit(recognition, isShown = true))
        assertNull(guard.beginRecognition(isShown = true))
    }

    @Test
    fun `new input target invalidates callbacks from the previous generation`() {
        val guard = VoiceInputSessionGuard()
        guard.onInputViewStarted(isShown = true)
        val stale = assertNotNull(guard.beginRecognition(isShown = true))

        guard.onInputViewStarted(isShown = true)
        val current = assertNotNull(guard.beginRecognition(isShown = true))

        assertFalse(guard.canCommit(stale, isShown = true))
        assertTrue(guard.canCommit(current, isShown = true))
        guard.finishRecognition(current)
        assertFalse(guard.canCommit(current, isShown = true))
    }

    @Test
    fun `late permission result after lifecycle stop is ignored`() {
        val guard = VoiceInputSessionGuard()
        guard.onPermissionRequested()
        guard.onInputViewStopped()

        assertFalse(guard.onPermissionResult(granted = true))
        assertNull(guard.onInputViewStarted(isShown = true))
    }
}

package com.zellij.keyboard.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GestureClassifierTest {
    private val thresholds =
        GestureThresholds(
            swipeDistance = 60f,
            longPressDurationMillis = 500L,
            longPressSlop = 10f,
        )

    @Test
    fun `classifies all four swipe directions from the dominant axis`() {
        assertEquals(swipe(SwipeDirection.UP), classifyUpAt(x = 12f, y = -70f))
        assertEquals(swipe(SwipeDirection.DOWN), classifyUpAt(x = -12f, y = 70f))
        assertEquals(swipe(SwipeDirection.LEFT), classifyUpAt(x = -70f, y = 12f))
        assertEquals(swipe(SwipeDirection.RIGHT), classifyUpAt(x = 70f, y = -12f))
    }

    @Test
    fun `swipe distance boundary is inclusive`() {
        assertEquals(swipe(SwipeDirection.RIGHT), classifyUpAt(x = 60f, y = 0f))
        assertEquals(GestureResult.Cancelled, classifyUpAt(x = 59.999f, y = 0f))
    }

    @Test
    fun `vertical axis wins an exact diagonal tie`() {
        assertEquals(swipe(SwipeDirection.DOWN), classifyUpAt(x = 60f, y = 60f))
        assertEquals(swipe(SwipeDirection.UP), classifyUpAt(x = -60f, y = -60f))
    }

    @Test
    fun `tap remains valid at the long press slop boundary`() {
        assertEquals(GestureResult.Tap, classifyUpAt(x = 10f, y = 0f, time = 499L))
    }

    @Test
    fun `movement beyond slop that is shorter than a swipe is cancelled`() {
        assertEquals(GestureResult.Cancelled, classifyUpAt(x = 11f, y = 0f))
    }

    @Test
    fun `tap ends immediately before the long press boundary`() {
        assertEquals(GestureResult.Tap, classifyUpAt(x = 0f, y = 0f, time = 499L))
    }

    @Test
    fun `up at the duration boundary emits only long press`() {
        val classifier = newClassifier()

        assertNull(classifier.onEvent(GestureEvent.Down(0f, 0f, 0L)))
        assertEquals(
            GestureResult.LongPress,
            classifier.onEvent(GestureEvent.Up(0f, 0f, 500L)),
        )
        assertNull(classifier.onEvent(GestureEvent.Up(0f, 0f, 501L)))
        assertFalse(classifier.isTracking)
    }

    @Test
    fun `timer long press consumes the sequence so trailing up cannot tap`() {
        val classifier = newClassifier()

        classifier.onEvent(GestureEvent.Down(0f, 0f, 1_000L))
        assertTrue(classifier.isTracking)
        assertNull(classifier.onTime(1_499L))
        assertEquals(GestureResult.LongPress, classifier.onTime(1_500L))
        assertNull(classifier.onEvent(GestureEvent.Up(0f, 0f, 1_501L)))
        assertNull(classifier.onTime(2_000L))
        assertFalse(classifier.isTracking)
    }

    @Test
    fun `move at the duration boundary can emit long press`() {
        val classifier = newClassifier()

        classifier.onEvent(GestureEvent.Down(0f, 0f, 0L))
        assertEquals(
            GestureResult.LongPress,
            classifier.onEvent(GestureEvent.Move(10f, 0f, 500L)),
        )
    }

    @Test
    fun `leaving long press slop prevents timer classification`() {
        val classifier = newClassifier()

        classifier.onEvent(GestureEvent.Down(0f, 0f, 0L))
        assertNull(classifier.onEvent(GestureEvent.Move(11f, 0f, 100L)))
        assertNull(classifier.onTime(500L))
        assertEquals(
            GestureResult.Cancelled,
            classifier.onEvent(GestureEvent.Up(0f, 0f, 501L)),
        )
    }

    @Test
    fun `cancel emits once and suppresses a trailing up`() {
        val classifier = newClassifier()

        classifier.onEvent(GestureEvent.Down(0f, 0f, 100L))
        assertEquals(
            GestureResult.Cancelled,
            classifier.onEvent(GestureEvent.Cancel(200L)),
        )
        assertNull(classifier.onEvent(GestureEvent.Cancel(201L)))
        assertNull(classifier.onEvent(GestureEvent.Up(0f, 0f, 202L)))
    }

    private fun classifyUpAt(
        x: Float,
        y: Float,
        time: Long = 100L,
    ): GestureResult? {
        val classifier = newClassifier()
        classifier.onEvent(GestureEvent.Down(0f, 0f, 0L))
        return classifier.onEvent(GestureEvent.Up(x, y, time))
    }

    private fun newClassifier(): GestureClassifier = GestureClassifier(thresholds)

    private fun swipe(direction: SwipeDirection): GestureResult =
        GestureResult.Swipe(direction)
}

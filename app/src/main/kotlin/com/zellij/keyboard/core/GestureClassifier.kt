package com.zellij.keyboard.core

import kotlin.math.abs

/**
 * Tunable gesture thresholds. Distances use the caller's coordinate unit
 * (normally pixels) and time uses a monotonic millisecond clock.
 */
data class GestureThresholds(
    val swipeDistance: Float = 60f,
    val longPressDurationMillis: Long = 500L,
    val longPressSlop: Float = 16f,
) {
    init {
        require(swipeDistance.isFinite() && swipeDistance > 0f) {
            "swipeDistance must be finite and positive"
        }
        require(longPressDurationMillis > 0L) {
            "longPressDurationMillis must be positive"
        }
        require(longPressSlop.isFinite() && longPressSlop >= 0f) {
            "longPressSlop must be finite and non-negative"
        }
        require(swipeDistance > longPressSlop) {
            "swipeDistance must be greater than longPressSlop"
        }
    }
}

sealed interface GestureEvent {
    val eventTimeMillis: Long

    data class Down(
        val x: Float,
        val y: Float,
        override val eventTimeMillis: Long,
    ) : GestureEvent

    data class Move(
        val x: Float,
        val y: Float,
        override val eventTimeMillis: Long,
    ) : GestureEvent

    data class Up(
        val x: Float,
        val y: Float,
        override val eventTimeMillis: Long,
    ) : GestureEvent

    data class Cancel(
        override val eventTimeMillis: Long,
    ) : GestureEvent
}

enum class SwipeDirection {
    UP,
    DOWN,
    LEFT,
    RIGHT,
}

/** A terminal classification for one pointer sequence. */
sealed interface GestureResult {
    data object Tap : GestureResult

    data object LongPress : GestureResult

    data class Swipe(val direction: SwipeDirection) : GestureResult

    /** The sequence ended without an actionable gesture. */
    data object Cancelled : GestureResult
}

/**
 * Stateful, platform-independent single-pointer gesture classifier.
 *
 * Every pointer sequence produces at most one [GestureResult]. In particular,
 * once a long press is emitted by [onTime] or [onEvent], the trailing up event
 * is ignored and cannot also produce a tap.
 */
class GestureClassifier(
    private val thresholds: GestureThresholds = GestureThresholds(),
) {
    private data class Tracking(
        val downX: Float,
        val downY: Float,
        val downTimeMillis: Long,
        val latestX: Float,
        val latestY: Float,
        val latestTimeMillis: Long,
        val maximumDistanceSquared: Float,
    )

    private var tracking: Tracking? = null

    val isTracking: Boolean
        get() = tracking != null

    fun onEvent(event: GestureEvent): GestureResult? =
        when (event) {
            is GestureEvent.Down -> onDown(event)
            is GestureEvent.Move -> onMove(event)
            is GestureEvent.Up -> onUp(event)
            is GestureEvent.Cancel -> onCancel(event)
        }

    /**
     * Advances the monotonic clock without requiring a pointer event.
     * Call this from a long-press timer using the same time base as events.
     */
    fun onTime(currentTimeMillis: Long): GestureResult? {
        val current = tracking ?: return null
        requireMonotonic(currentTimeMillis, current.latestTimeMillis)

        val advanced = current.copy(latestTimeMillis = currentTimeMillis)
        tracking = advanced
        return emitLongPressIfReady(advanced)
    }

    private fun onDown(event: GestureEvent.Down): GestureResult? {
        requireSample(event.x, event.y, event.eventTimeMillis)

        val interruptedSequence = tracking != null
        tracking =
            Tracking(
                downX = event.x,
                downY = event.y,
                downTimeMillis = event.eventTimeMillis,
                latestX = event.x,
                latestY = event.y,
                latestTimeMillis = event.eventTimeMillis,
                maximumDistanceSquared = 0f,
            )

        return if (interruptedSequence) GestureResult.Cancelled else null
    }

    private fun onMove(event: GestureEvent.Move): GestureResult? {
        val current = tracking ?: return null
        val updated = current.withSample(event.x, event.y, event.eventTimeMillis)
        tracking = updated
        return emitLongPressIfReady(updated)
    }

    private fun onUp(event: GestureEvent.Up): GestureResult? {
        val current = tracking ?: return null
        val completed = current.withSample(event.x, event.y, event.eventTimeMillis)
        tracking = null

        val elapsed = completed.latestTimeMillis - completed.downTimeMillis
        val stayedWithinLongPressSlop =
            completed.maximumDistanceSquared <= thresholds.longPressSlop.squared()

        if (stayedWithinLongPressSlop && elapsed >= thresholds.longPressDurationMillis) {
            return GestureResult.LongPress
        }

        val deltaX = completed.latestX - completed.downX
        val deltaY = completed.latestY - completed.downY
        val absoluteX = abs(deltaX)
        val absoluteY = abs(deltaY)
        val dominantDistance = maxOf(absoluteX, absoluteY)

        if (dominantDistance >= thresholds.swipeDistance) {
            // Vertical wins an exact diagonal tie, matching the product's
            // inherited gesture behavior while keeping the boundary explicit.
            val direction =
                if (absoluteX > absoluteY) {
                    if (deltaX > 0f) SwipeDirection.RIGHT else SwipeDirection.LEFT
                } else {
                    if (deltaY > 0f) SwipeDirection.DOWN else SwipeDirection.UP
                }
            return GestureResult.Swipe(direction)
        }

        return if (stayedWithinLongPressSlop) {
            GestureResult.Tap
        } else {
            GestureResult.Cancelled
        }
    }

    private fun onCancel(event: GestureEvent.Cancel): GestureResult? {
        val current = tracking ?: return null
        requireMonotonic(event.eventTimeMillis, current.latestTimeMillis)
        tracking = null
        return GestureResult.Cancelled
    }

    private fun emitLongPressIfReady(current: Tracking): GestureResult? {
        val elapsed = current.latestTimeMillis - current.downTimeMillis
        val stayedWithinSlop =
            current.maximumDistanceSquared <= thresholds.longPressSlop.squared()
        if (!stayedWithinSlop || elapsed < thresholds.longPressDurationMillis) {
            return null
        }

        tracking = null
        return GestureResult.LongPress
    }

    private fun Tracking.withSample(x: Float, y: Float, eventTimeMillis: Long): Tracking {
        requireSample(x, y, eventTimeMillis)
        requireMonotonic(eventTimeMillis, latestTimeMillis)

        val deltaX = x - downX
        val deltaY = y - downY
        val distanceSquared = deltaX.squared() + deltaY.squared()
        return copy(
            latestX = x,
            latestY = y,
            latestTimeMillis = eventTimeMillis,
            maximumDistanceSquared = maxOf(maximumDistanceSquared, distanceSquared),
        )
    }

    private fun requireSample(x: Float, y: Float, eventTimeMillis: Long) {
        require(x.isFinite() && y.isFinite()) { "gesture coordinates must be finite" }
        require(eventTimeMillis >= 0L) { "event time must be non-negative" }
    }

    private fun requireMonotonic(currentTimeMillis: Long, previousTimeMillis: Long) {
        require(currentTimeMillis >= previousTimeMillis) {
            "gesture time must be monotonic"
        }
    }

    private fun Float.squared(): Float = this * this
}

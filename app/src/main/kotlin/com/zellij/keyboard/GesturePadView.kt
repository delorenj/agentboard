package com.zellij.keyboard

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Bundle
import android.os.SystemClock
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.zellij.keyboard.core.GestureClassifier
import com.zellij.keyboard.core.GestureEvent
import com.zellij.keyboard.core.GesturePad
import com.zellij.keyboard.core.GestureResult
import com.zellij.keyboard.core.GestureThresholds
import com.zellij.keyboard.core.SwipeDirection
import kotlin.math.max

/**
 * Single-pointer terminal gesture surface backed by the platform-independent
 * [GestureClassifier].
 */
@SuppressLint("ViewConstructor")
internal class GesturePadView(
    context: Context,
    private val pad: GesturePad,
) : View(context) {
    private val longPressDurationMillis = ViewConfiguration.getLongPressTimeout().toLong()
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    private val thresholds =
        GestureThresholds(
            swipeDistance =
                max(
                    resources.getDimension(R.dimen.gesture_swipe_distance),
                    touchSlop + 1f,
                ),
            longPressDurationMillis = longPressDurationMillis,
            longPressSlop = touchSlop,
        )
    private val classifier = GestureClassifier(thresholds)

    private val titlePaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = context.getColor(R.color.gesture_title)
            textAlign = Paint.Align.CENTER
            textSize = spToPx(13f)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
    private val statusPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = context.getColor(R.color.gesture_hint)
            textAlign = Paint.Align.CENTER
            textSize = spToPx(11f)
        }

    private val title: String
    private val semantics: String

    private var listener: ((GestureResult) -> Unit)? = null
    private var longPressDeadlineMillis = 0L
    private var feedbackActive = false
    private var statusHint: String? = null

    private val longPressRunnable =
        object : Runnable {
            override fun run() {
                val now = SystemClock.uptimeMillis()
                val result = classifier.onTime(now)
                if (result != null) {
                    finishGesture(result)
                } else if (classifier.isTracking && now < longPressDeadlineMillis) {
                    postDelayed(this, longPressDeadlineMillis - now)
                }
            }
        }

    private val clearFeedbackRunnable =
        Runnable {
            feedbackActive = false
            invalidate()
        }

    init {
        when (pad) {
            GesturePad.TABS -> {
                title = resources.getString(R.string.tabs_pad_title)
                semantics = resources.getString(R.string.tabs_pad_content_description)
            }
            GesturePad.PANES -> {
                title = resources.getString(R.string.panes_pad_title)
                semantics = resources.getString(R.string.panes_pad_content_description)
            }
        }

        contentDescription = semantics
        isClickable = pad == GesturePad.TABS
        isLongClickable = pad == GesturePad.TABS
        isFocusable = true
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        setWillNotDraw(false)
    }

    fun setOnGestureResultListener(listener: (GestureResult) -> Unit) {
        this.listener = listener
    }

    fun setStatusHint(status: String?) {
        statusHint = status
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (isPressed || feedbackActive) {
            canvas.drawColor(context.getColor(R.color.gesture_background_pressed))
        }

        val centerX = width / 2f
        canvas.drawText(title, centerX, height * 0.51f, titlePaint)
        statusHint?.let { canvas.drawText(it, centerX, height * 0.65f, statusPaint) }
    }

    override fun drawableStateChanged() {
        super.drawableStateChanged()
        invalidate()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean =
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (event.pointerCount != 1) {
                    false
                } else {
                    cancelInterruptedGesture(event.eventTime)
                    parent?.requestDisallowInterceptTouchEvent(true)
                    isPressed = true
                    classifier.onEvent(
                        GestureEvent.Down(
                            x = event.x,
                            y = event.y,
                            eventTimeMillis = event.eventTime,
                        ),
                    )?.let(::finishGesture)
                    scheduleLongPress(event.eventTime)
                    true
                }
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                cancelGesture(event.eventTime)
                true
            }

            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount != 1) {
                    cancelGesture(event.eventTime)
                } else if (classifier.isTracking) {
                    classifier.onEvent(
                        GestureEvent.Move(
                            x = event.x,
                            y = event.y,
                            eventTimeMillis = event.eventTime,
                        ),
                    )?.let(::finishGesture)
                }
                true
            }

            MotionEvent.ACTION_UP -> {
                if (classifier.isTracking) {
                    classifier.onEvent(
                        GestureEvent.Up(
                            x = event.x,
                            y = event.y,
                            eventTimeMillis = event.eventTime,
                        ),
                    )?.let(::finishGesture)
                } else {
                    finishPointerFeedback()
                }
                true
            }

            MotionEvent.ACTION_CANCEL -> {
                cancelGesture(event.eventTime)
                true
            }

            else -> true
        }

    override fun performClick(): Boolean {
        super.performClick()
        dispatchRecognizedGesture(GestureResult.Tap)
        return true
    }

    override fun performLongClick(): Boolean {
        super.performLongClick()
        dispatchRecognizedGesture(GestureResult.LongPress)
        return true
    }

    override fun onInitializeAccessibilityEvent(event: AccessibilityEvent) {
        super.onInitializeAccessibilityEvent(event)
        event.className = View::class.java.name
    }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.className = View::class.java.name
        info.isClickable = pad == GesturePad.TABS
        info.isLongClickable = pad == GesturePad.TABS
        if (pad == GesturePad.PANES) {
            info.addAction(
                AccessibilityNodeInfo.AccessibilityAction(
                    R.id.accessibility_swipe_up,
                    resources.getString(R.string.gesture_action_swipe_up),
                ),
            )
            info.addAction(
                AccessibilityNodeInfo.AccessibilityAction(
                    R.id.accessibility_swipe_down,
                    resources.getString(R.string.gesture_action_swipe_down),
                ),
            )
        }
        info.addAction(
            AccessibilityNodeInfo.AccessibilityAction(
                R.id.accessibility_swipe_left,
                resources.getString(R.string.gesture_action_swipe_left),
            ),
        )
        info.addAction(
            AccessibilityNodeInfo.AccessibilityAction(
                R.id.accessibility_swipe_right,
                resources.getString(R.string.gesture_action_swipe_right),
            ),
        )
    }

    override fun performAccessibilityAction(
        action: Int,
        arguments: Bundle?,
    ): Boolean =
        when (action) {
            AccessibilityNodeInfo.ACTION_CLICK ->
                if (pad == GesturePad.TABS) {
                    performClick()
                } else {
                    super.performAccessibilityAction(action, arguments)
                }
            AccessibilityNodeInfo.ACTION_LONG_CLICK ->
                if (pad == GesturePad.TABS) {
                    performLongClick()
                } else {
                    super.performAccessibilityAction(action, arguments)
                }
            R.id.accessibility_swipe_up ->
                if (pad == GesturePad.PANES) {
                    performAccessibleSwipe(SwipeDirection.UP)
                } else {
                    super.performAccessibilityAction(action, arguments)
                }
            R.id.accessibility_swipe_down ->
                if (pad == GesturePad.PANES) {
                    performAccessibleSwipe(SwipeDirection.DOWN)
                } else {
                    super.performAccessibilityAction(action, arguments)
                }
            R.id.accessibility_swipe_left -> performAccessibleSwipe(SwipeDirection.LEFT)
            R.id.accessibility_swipe_right -> performAccessibleSwipe(SwipeDirection.RIGHT)
            else -> super.performAccessibilityAction(action, arguments)
        }

    override fun onDetachedFromWindow() {
        removeCallbacks(longPressRunnable)
        removeCallbacks(clearFeedbackRunnable)
        if (classifier.isTracking) {
            classifier.onEvent(GestureEvent.Cancel(SystemClock.uptimeMillis()))
        }
        finishPointerFeedback()
        super.onDetachedFromWindow()
    }

    private fun scheduleLongPress(downEventTimeMillis: Long) {
        removeCallbacks(longPressRunnable)
        longPressDeadlineMillis = downEventTimeMillis + thresholds.longPressDurationMillis
        val remainingMillis =
            (longPressDeadlineMillis - SystemClock.uptimeMillis()).coerceAtLeast(0L)
        postDelayed(longPressRunnable, remainingMillis)
    }

    private fun cancelInterruptedGesture(eventTimeMillis: Long) {
        if (classifier.isTracking) {
            classifier.onEvent(GestureEvent.Cancel(eventTimeMillis))?.let(::finishGesture)
        }
    }

    private fun cancelGesture(eventTimeMillis: Long) {
        if (classifier.isTracking) {
            classifier.onEvent(GestureEvent.Cancel(eventTimeMillis))?.let(::finishGesture)
        } else {
            finishPointerFeedback()
        }
    }

    private fun finishGesture(result: GestureResult) {
        finishPointerFeedback()
        when (result) {
            GestureResult.Tap -> performClick()
            GestureResult.LongPress -> performLongClick()
            is GestureResult.Swipe -> dispatchRecognizedGesture(result)
            GestureResult.Cancelled -> listener?.invoke(result)
        }
    }

    private fun finishPointerFeedback() {
        removeCallbacks(longPressRunnable)
        isPressed = false
        parent?.requestDisallowInterceptTouchEvent(false)
    }

    private fun performAccessibleSwipe(direction: SwipeDirection): Boolean {
        dispatchRecognizedGesture(GestureResult.Swipe(direction))
        return true
    }

    private fun dispatchRecognizedGesture(result: GestureResult) {
        feedbackActive = true
        invalidate()
        removeCallbacks(clearFeedbackRunnable)
        postDelayed(clearFeedbackRunnable, FEEDBACK_DURATION_MILLIS)
        announce(feedbackFor(result))
        listener?.invoke(result)
    }

    private fun feedbackFor(result: GestureResult): String {
        val stringId =
            when (pad) {
                GesturePad.PANES ->
                    when (result) {
                        GestureResult.Tap -> R.string.gesture_feedback_ignored
                        GestureResult.LongPress -> R.string.gesture_feedback_ignored
                        is GestureResult.Swipe ->
                            when (result.direction) {
                                SwipeDirection.UP -> R.string.panes_feedback_up
                                SwipeDirection.DOWN -> R.string.panes_feedback_down
                                SwipeDirection.LEFT -> R.string.panes_feedback_left
                                SwipeDirection.RIGHT -> R.string.panes_feedback_right
                            }
                        GestureResult.Cancelled -> error("Cancelled gestures are not announced")
                    }
                GesturePad.TABS ->
                    when (result) {
                        GestureResult.Tap -> R.string.tabs_feedback_microphone
                        GestureResult.LongPress -> R.string.tabs_feedback_continue
                        is GestureResult.Swipe ->
                            when (result.direction) {
                                SwipeDirection.UP -> R.string.gesture_feedback_ignored
                                SwipeDirection.DOWN -> R.string.gesture_feedback_ignored
                                SwipeDirection.LEFT -> R.string.tabs_feedback_left
                                SwipeDirection.RIGHT -> R.string.tabs_feedback_right
                            }
                        GestureResult.Cancelled -> error("Cancelled gestures are not announced")
                    }
            }
        return resources.getString(stringId)
    }

    @Suppress("DEPRECATION")
    private fun announce(message: CharSequence) {
        announceForAccessibility(message)
    }

    private fun spToPx(sp: Float): Float =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            sp,
            resources.displayMetrics,
        )

    private companion object {
        const val FEEDBACK_DURATION_MILLIS = 140L
    }
}

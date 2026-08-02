package com.zellij.keyboard

import android.content.Context
import android.widget.LinearLayout
import com.zellij.keyboard.core.GesturePad
import com.zellij.keyboard.core.GestureResult

/** Two equal gesture zones; Agentboard renders no key or action buttons. */
internal class TerminalKeyboardView(
    context: Context,
) : LinearLayout(context) {
    private val keyGap = resources.getDimensionPixelSize(R.dimen.key_gap)
    private val zoneHeight =
        resources.getDimensionPixelSize(R.dimen.gesture_zones_height) / 2
    private val tabsZone = createGestureZone(GesturePad.TABS)

    var onGesture: ((GesturePad, GestureResult) -> Unit)? = null

    init {
        orientation = VERTICAL
        setBackgroundColor(context.getColor(R.color.keyboard_background))
        val outerPadding = resources.getDimensionPixelSize(R.dimen.keyboard_padding)
        setPadding(outerPadding, outerPadding, outerPadding, outerPadding)

        addView(
            tabsZone,
            LayoutParams(LayoutParams.MATCH_PARENT, zoneHeight).apply {
                bottomMargin = keyGap / 2
            },
        )
        addView(
            createGestureZone(GesturePad.PANES),
            LayoutParams(LayoutParams.MATCH_PARENT, zoneHeight).apply {
                topMargin = keyGap / 2
            },
        )
    }

    fun renderMicrophoneState(
        isListening: Boolean,
        status: String? = null,
    ) {
        tabsZone.setStatusHint(
            if (isListening) resources.getString(R.string.microphone_listening) else null,
        )
        status?.let(::announceStatus)
    }

    fun announceStatus(message: String) {
        @Suppress("DEPRECATION")
        announceForAccessibility(message)
    }

    private fun createGestureZone(pad: GesturePad): GesturePadView =
        GesturePadView(context, pad).apply {
            setOnGestureResultListener { result -> onGesture?.invoke(pad, result) }
        }
}

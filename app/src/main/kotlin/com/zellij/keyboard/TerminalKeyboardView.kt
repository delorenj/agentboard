package com.zellij.keyboard

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.os.Build
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import com.zellij.keyboard.core.GesturePad
import com.zellij.keyboard.core.GestureResult
import com.zellij.keyboard.core.TerminalKeyId
import com.zellij.keyboard.core.TerminalKeyModel
import com.zellij.keyboard.core.TerminalKeyRole
import com.zellij.keyboard.core.TerminalKeyboardLayout
import com.zellij.keyboard.core.TerminalKeyboardPlanner
import com.zellij.keyboard.core.TerminalKeyboardState

/**
 * Purpose-built native terminal keyboard hierarchy. Model rows are rendered
 * with relative width weights and updated in place unless the layer changes.
 */
internal class TerminalKeyboardView(
    context: Context,
) : LinearLayout(context) {
    private val keyboardRows = LinearLayout(context)
    private val keyButtons = linkedMapOf<TerminalKeyId, Button>()
    private var renderedSignature: List<Pair<String, List<TerminalKeyId>>> = emptyList()

    private val keyGap = resources.getDimensionPixelSize(R.dimen.key_gap)
    private val keyHeight = resources.getDimensionPixelSize(R.dimen.key_height)
    private val keyTextColors =
        ColorStateList(
            arrayOf(
                intArrayOf(android.R.attr.state_activated),
                intArrayOf(),
            ),
            intArrayOf(
                context.getColor(R.color.key_text_active),
                context.getColor(R.color.key_text),
            ),
        )

    var onKeyPressed: ((TerminalKeyId) -> Unit)? = null
    var onGesture: ((GesturePad, GestureResult) -> Unit)? = null

    init {
        orientation = VERTICAL
        setBackgroundColor(context.getColor(R.color.keyboard_background))
        val outerPadding = resources.getDimensionPixelSize(R.dimen.keyboard_padding)
        setPadding(outerPadding, outerPadding, outerPadding, outerPadding)

        addGesturePads()

        keyboardRows.orientation = VERTICAL
        keyboardRows.layoutParams =
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = resources.getDimensionPixelSize(R.dimen.section_gap)
            }
        addView(keyboardRows)
    }

    fun render(state: TerminalKeyboardState) {
        val layout = TerminalKeyboardPlanner.layout(state)
        val signature =
            layout.rows.map { row ->
                row.id to row.keys.map(TerminalKeyModel::id)
            }

        if (signature != renderedSignature) {
            rebuildRows(layout)
            renderedSignature = signature
        }

        layout.keys.forEach { model ->
            keyButtons.getValue(model.id).applyModel(model)
        }
    }

    fun announceKeyState(keyId: TerminalKeyId) {
        keyButtons[keyId]?.let { button ->
            button.announce(button.contentDescription)
        }
    }

    private fun addGesturePads() {
        val gestureRow =
            LinearLayout(context).apply {
                orientation = HORIZONTAL
                isBaselineAligned = false
                layoutParams =
                    LayoutParams(
                        LayoutParams.MATCH_PARENT,
                        resources.getDimensionPixelSize(R.dimen.gesture_pad_height),
                    )
            }

        gestureRow.addView(
            createGesturePad(GesturePad.FOCUS),
            LayoutParams(0, LayoutParams.MATCH_PARENT, 1f).apply {
                rightMargin = keyGap / 2
            },
        )
        gestureRow.addView(
            createGesturePad(GesturePad.TABS),
            LayoutParams(0, LayoutParams.MATCH_PARENT, 1f).apply {
                leftMargin = keyGap / 2
            },
        )
        addView(gestureRow)
    }

    private fun createGesturePad(pad: GesturePad): GesturePadView =
        GesturePadView(context, pad).apply {
            setOnGestureResultListener { result -> onGesture?.invoke(pad, result) }
        }

    private fun rebuildRows(layout: TerminalKeyboardLayout) {
        keyboardRows.removeAllViews()
        keyButtons.clear()

        layout.rows.forEach { modelRow ->
            val row =
                LinearLayout(context).apply {
                    orientation = HORIZONTAL
                    gravity = Gravity.CENTER
                    isBaselineAligned = false
                }
            keyboardRows.addView(
                row,
                LayoutParams(LayoutParams.MATCH_PARENT, keyHeight).apply {
                    bottomMargin = keyGap
                },
            )

            modelRow.keys.forEach { model ->
                val button = createKeyButton(model)
                row.addView(
                    button,
                    LayoutParams(0, LayoutParams.MATCH_PARENT, model.widthUnits.toFloat()).apply {
                        leftMargin = keyGap / 2
                        rightMargin = keyGap / 2
                    },
                )
                keyButtons[model.id] = button
            }
        }
    }

    private fun createKeyButton(model: TerminalKeyModel): Button =
        Button(context).apply {
            id = View.generateViewId()
            background = context.getDrawable(R.drawable.terminal_key_background)
            setTextColor(keyTextColors)
            isAllCaps = false
            includeFontPadding = false
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 0)
            minimumWidth = 0
            minimumHeight = 0
            minWidth = 0
            minHeight = 0
            stateListAnimator = null
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
            typeface =
                if (model.role.isTerminalControl()) {
                    Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                } else {
                    Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                }
            setTextSize(
                TypedValue.COMPLEX_UNIT_PX,
                resources.getDimension(
                    if (model.role.isTerminalControl()) {
                        R.dimen.key_control_text_size
                    } else {
                        R.dimen.key_text_size
                    },
                ),
            )
            setOnClickListener { onKeyPressed?.invoke(model.id) }
            applyModel(model)
        }

    @Suppress("DEPRECATION")
    private fun View.announce(message: CharSequence) {
        announceForAccessibility(message)
    }

    private fun Button.applyModel(model: TerminalKeyModel) {
        text = model.label
        contentDescription = model.accessibilityDescription
        isActivated = model.isActive

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            stateDescription =
                when (model.role) {
                    TerminalKeyRole.MODIFIER ->
                        resources.getString(
                            if (model.isActive) R.string.key_state_armed else R.string.key_state_idle,
                        )
                    TerminalKeyRole.SHIFT ->
                        resources.getString(
                            if (model.isActive) R.string.key_state_shift_on
                            else R.string.key_state_shift_off,
                        )
                    TerminalKeyRole.LAYER ->
                        resources.getString(
                            if (model.isActive) R.string.key_state_symbols
                            else R.string.key_state_letters,
                        )
                    else -> null
                }
        }
    }

    private fun TerminalKeyRole.isTerminalControl(): Boolean =
        when (this) {
            TerminalKeyRole.LETTER,
            TerminalKeyRole.DIGIT,
            TerminalKeyRole.PUNCTUATION,
            -> false
            TerminalKeyRole.ESCAPE,
            TerminalKeyRole.TAB,
            TerminalKeyRole.ENTER,
            TerminalKeyRole.BACKSPACE,
            TerminalKeyRole.SPACE,
            TerminalKeyRole.SHIFT,
            TerminalKeyRole.LAYER,
            TerminalKeyRole.MODIFIER,
            -> true
        }
}

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
import com.zellij.keyboard.core.AgentCommand
import com.zellij.keyboard.core.GesturePad
import com.zellij.keyboard.core.GestureResult
import com.zellij.keyboard.core.TerminalKeyId
import com.zellij.keyboard.core.TerminalKeyIds
import com.zellij.keyboard.core.TerminalKeyModel
import com.zellij.keyboard.core.TerminalKeyRole
import com.zellij.keyboard.core.TerminalKeyboardPlanner
import com.zellij.keyboard.core.TerminalKeyboardState

/**
 * Voice-first Agentboard surface: stacked navigation joysticks stay visible in
 * both compact and expanded modes, while the expanded deck exposes agent and
 * essential terminal controls instead of a QWERTY layout.
 */
internal class TerminalKeyboardView(
    context: Context,
) : LinearLayout(context) {
    private val keyButtons = linkedMapOf<TerminalKeyId, Button>()
    private val expandedDeck = LinearLayout(context)
    private lateinit var microphoneButton: Button
    private lateinit var expansionButton: Button
    private var isDeckExpanded = true

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
    var onAgentCommand: ((AgentCommand) -> Unit)? = null
    var onMicrophonePressed: (() -> Unit)? = null

    init {
        orientation = VERTICAL
        setBackgroundColor(context.getColor(R.color.keyboard_background))
        val outerPadding = resources.getDimensionPixelSize(R.dimen.keyboard_padding)
        setPadding(outerPadding, outerPadding, outerPadding, outerPadding)

        addGestureZones()
        addPrimaryActionRow()
        addExpandedDeck()
        renderExpansionState(announceChange = false)
    }

    fun render(state: TerminalKeyboardState) {
        val layout = TerminalKeyboardPlanner.layout(state)
        keyButtons.forEach { (id, button) ->
            layout.key(id)?.let { model -> button.applyModel(model) }
        }
    }

    fun announceKeyState(keyId: TerminalKeyId) {
        keyButtons[keyId]?.let { button ->
            button.announce(button.contentDescription)
        }
    }

    fun renderMicrophoneState(
        isListening: Boolean,
        status: String? = null,
    ) {
        microphoneButton.text =
            resources.getString(
                if (isListening) R.string.microphone_listening else R.string.microphone_action,
            )
        microphoneButton.contentDescription =
            resources.getString(
                if (isListening) {
                    R.string.microphone_listening_description
                } else {
                    R.string.microphone_action_description
                },
            )
        microphoneButton.isActivated = isListening
        status?.let(::announceStatus)
    }

    fun announceStatus(message: String) {
        announce(message)
    }

    private fun addGestureZones() {
        val gestureColumn =
            LinearLayout(context).apply {
                orientation = VERTICAL
                isBaselineAligned = false
            }

        gestureColumn.addView(
            createGesturePad(GesturePad.TABS),
            LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f).apply {
                bottomMargin = keyGap / 2
            },
        )
        gestureColumn.addView(
            createGesturePad(GesturePad.PANES),
            LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f).apply {
                topMargin = keyGap / 2
            },
        )
        addView(
            gestureColumn,
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                resources.getDimensionPixelSize(R.dimen.gesture_zones_height),
            ),
        )
    }

    private fun createGesturePad(pad: GesturePad): GesturePadView =
        GesturePadView(context, pad).apply {
            setOnGestureResultListener { result -> onGesture?.invoke(pad, result) }
        }

    private fun addPrimaryActionRow() {
        val row = createRow(topMargin = true)
        microphoneButton =
            createActionButton(
                label = resources.getString(R.string.microphone_action),
                description = resources.getString(R.string.microphone_action_description),
                emphasized = true,
            ) { onMicrophonePressed?.invoke() }
        row.addWeighted(microphoneButton, 2.4f)

        val continueCommand = AgentCommand.CONTINUE
        row.addWeighted(
            createActionButton(
                label = continueCommand.label,
                description = continueCommand.accessibilityDescription,
            ) { onAgentCommand?.invoke(continueCommand) },
            1.6f,
        )

        expansionButton =
            createActionButton(
                label = resources.getString(R.string.collapse_deck_symbol),
                description = resources.getString(R.string.collapse_deck_description),
            ) {
                isDeckExpanded = !isDeckExpanded
                renderExpansionState(announceChange = true)
            }
        row.addWeighted(expansionButton, 0.8f)
        addView(row)
    }

    private fun addExpandedDeck() {
        expandedDeck.orientation = VERTICAL

        expandedDeck.addView(
            createAgentRow(
                AgentCommand.CLAUDE,
                AgentCommand.GEMINI,
                AgentCommand.CODEX,
            ),
        )
        expandedDeck.addView(
            createAgentRow(
                AgentCommand.HERMES,
                AgentCommand.KIMI,
            ),
        )
        expandedDeck.addView(createTerminalControlRow())
        addView(expandedDeck)
    }

    private fun createAgentRow(vararg commands: AgentCommand): LinearLayout =
        createRow(topMargin = true).apply {
            commands.forEach { command ->
                addWeighted(
                    createActionButton(
                        label = command.label,
                        description = command.accessibilityDescription,
                    ) { onAgentCommand?.invoke(command) },
                    1f,
                )
            }
        }

    private fun createTerminalControlRow(): LinearLayout {
        val state = TerminalKeyboardState()
        val layout = TerminalKeyboardPlanner.layout(state)
        val controls =
            listOf(
                TerminalKeyIds.ESCAPE,
                TerminalKeyIds.CTRL,
                TerminalKeyIds.ALT,
                TerminalKeyIds.TAB,
                TerminalKeyIds.BACKSPACE,
                TerminalKeyIds.ENTER,
            )

        return createRow(topMargin = true).apply {
            controls.forEach { keyId ->
                val model = requireNotNull(layout.key(keyId))
                val button = createTerminalButton(model)
                addWeighted(button, 1f)
                keyButtons[keyId] = button
            }
        }
    }

    private fun createRow(topMargin: Boolean): LinearLayout =
        LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER
            isBaselineAligned = false
            layoutParams =
                LayoutParams(LayoutParams.MATCH_PARENT, keyHeight).apply {
                    if (topMargin) {
                        this.topMargin = resources.getDimensionPixelSize(R.dimen.section_gap)
                    }
                }
        }

    private fun createActionButton(
        label: String,
        description: String,
        emphasized: Boolean = false,
        onClick: () -> Unit,
    ): Button =
        baseButton().apply {
            text = label
            contentDescription = description
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            setTextSize(
                TypedValue.COMPLEX_UNIT_PX,
                resources.getDimension(
                    if (emphasized) R.dimen.key_text_size else R.dimen.key_control_text_size,
                ),
            )
            setOnClickListener { onClick() }
        }

    private fun createTerminalButton(model: TerminalKeyModel): Button =
        baseButton().apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            setTextSize(
                TypedValue.COMPLEX_UNIT_PX,
                resources.getDimension(R.dimen.key_control_text_size),
            )
            setOnClickListener { onKeyPressed?.invoke(model.id) }
            applyModel(model)
        }

    private fun baseButton(): Button =
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
        }

    private fun LinearLayout.addWeighted(
        button: Button,
        weight: Float,
    ) {
        addView(
            button,
            LayoutParams(0, LayoutParams.MATCH_PARENT, weight).apply {
                leftMargin = keyGap / 2
                rightMargin = keyGap / 2
            },
        )
    }

    private fun renderExpansionState(announceChange: Boolean) {
        expandedDeck.visibility = if (isDeckExpanded) VISIBLE else GONE
        expansionButton.isActivated = isDeckExpanded
        expansionButton.text =
            resources.getString(
                if (isDeckExpanded) R.string.collapse_deck_symbol else R.string.expand_deck_symbol,
            )
        expansionButton.contentDescription =
            resources.getString(
                if (isDeckExpanded) {
                    R.string.collapse_deck_description
                } else {
                    R.string.expand_deck_description
                },
            )
        if (announceChange) {
            expansionButton.announce(expansionButton.contentDescription)
        }
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
                    else -> null
                }
        }
    }
}

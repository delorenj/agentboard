package com.zellij.keyboard.core

/** Stable, platform-independent identity for a rendered terminal keyboard key. */
data class TerminalKeyId(val value: String) {
    init {
        require(value.isNotBlank()) { "terminal key ID must not be blank" }
        require(value.all { it.isLowerCase() || it.isDigit() || it == '_' }) {
            "terminal key ID must contain only lowercase letters, digits, or underscores"
        }
    }

    override fun toString(): String = value
}

enum class TerminalKeyboardLayer {
    LETTERS,
    SYMBOLS,
}

/**
 * Shift selects the alternate character on the next character key.
 *
 * Named commands and UI-only operations preserve it because Shift has no
 * meaning for those operations in the platform-independent command model.
 */
enum class TerminalShiftState {
    OFF,
    ONE_SHOT,
}

/** Complete immutable state owned by a future keyboard View or service adapter. */
data class TerminalKeyboardState(
    val layer: TerminalKeyboardLayer = TerminalKeyboardLayer.LETTERS,
    val shift: TerminalShiftState = TerminalShiftState.OFF,
    val modifiers: OneShotModifierState = OneShotModifierState.NONE,
)

enum class TerminalKeyRole {
    LETTER,
    DIGIT,
    PUNCTUATION,
    ESCAPE,
    TAB,
    ENTER,
    BACKSPACE,
    SPACE,
    SHIFT,
    LAYER,
    MODIFIER,
}

/**
 * Render-ready key metadata. [widthUnits] is a relative flex weight rather
 * than a density-specific size, so an Android View can lay out every row.
 */
data class TerminalKeyModel(
    val id: TerminalKeyId,
    val label: String,
    val accessibilityDescription: String,
    val role: TerminalKeyRole,
    val widthUnits: Float = 1f,
    val isActive: Boolean = false,
) {
    init {
        require(label.isNotBlank()) { "terminal key label must not be blank" }
        require(accessibilityDescription.isNotBlank()) {
            "terminal key accessibility description must not be blank"
        }
        require(widthUnits.isFinite() && widthUnits > 0f) {
            "terminal key width must be positive and finite"
        }
    }
}

data class TerminalKeyboardRow(
    val id: String,
    val keys: List<TerminalKeyModel>,
) {
    init {
        require(id.isNotBlank()) { "terminal keyboard row ID must not be blank" }
        require(keys.isNotEmpty()) { "terminal keyboard row must contain keys" }
    }
}

data class TerminalKeyboardLayout(
    val layer: TerminalKeyboardLayer,
    val shift: TerminalShiftState,
    val rows: List<TerminalKeyboardRow>,
) {
    val keys: List<TerminalKeyModel> = rows.flatMap(TerminalKeyboardRow::keys)

    init {
        require(rows.isNotEmpty()) { "terminal keyboard layout must contain rows" }
        require(keys.map(TerminalKeyModel::id).distinct().size == keys.size) {
            "terminal key IDs must be unique within a layout"
        }
    }

    fun key(id: TerminalKeyId): TerminalKeyModel? = keys.firstOrNull { it.id == id }
}

/**
 * Input events accepted by [TerminalKeyboardPlanner].
 *
 * Explicit cancellation and no-op inputs let pointer cancellation and ignored
 * View events preserve every one-shot state without adapter-side special cases.
 */
sealed interface TerminalKeyboardInput {
    data class KeyPress(val keyId: TerminalKeyId) : TerminalKeyboardInput

    data object Cancelled : TerminalKeyboardInput

    data object NoOp : TerminalKeyboardInput
}

sealed interface TerminalKeyboardUiChange {
    data object ShiftToggled : TerminalKeyboardUiChange

    data object LayerToggled : TerminalKeyboardUiChange

    data class ModifierToggled(val modifier: KeyModifier) : TerminalKeyboardUiChange
}

/**
 * Planner output deliberately distinguishes platform command emission from a
 * UI-only state transition, cancellation, and an ignored input.
 */
sealed interface TerminalKeyboardEffect {
    data class Emit(val command: KeyCommand) : TerminalKeyboardEffect

    data class StateChanged(val change: TerminalKeyboardUiChange) : TerminalKeyboardEffect

    data object Cancelled : TerminalKeyboardEffect

    data object NoOp : TerminalKeyboardEffect
}

data class TerminalKeyboardPlan(
    val nextState: TerminalKeyboardState,
    val effect: TerminalKeyboardEffect,
) {
    val commandToEmit: KeyCommand?
        get() = (effect as? TerminalKeyboardEffect.Emit)?.command
}

/** Public IDs for controls an Android adapter may want to identify directly. */
object TerminalKeyIds {
    val ESCAPE = TerminalKeyId("escape")
    val TAB = TerminalKeyId("tab")
    val ENTER = TerminalKeyId("enter")
    val BACKSPACE = TerminalKeyId("backspace")
    val SPACE = TerminalKeyId("space")
    val SHIFT = TerminalKeyId("shift")
    val LAYER = TerminalKeyId("layer")
    val CTRL = TerminalKeyId("ctrl")
    val ALT = TerminalKeyId("alt")
}

package com.zellij.keyboard.core

/**
 * Immutable state for independently toggled, one-shot Ctrl and Alt modifiers.
 *
 * Any emitted key command consumes and clears both armed modifiers. Operations
 * that emit nothing preserve the state, so cancellation cannot lose the user's
 * pending modifier selection.
 */
data class OneShotModifierState(
    val ctrlArmed: Boolean = false,
    val altArmed: Boolean = false,
) {
    val armedModifiers: KeyModifiers
        get() = KeyModifiers(ctrl = ctrlArmed, alt = altArmed)

    fun onModifierTap(modifier: KeyModifier): OneShotModifierState =
        when (modifier) {
            KeyModifier.CTRL -> copy(ctrlArmed = !ctrlArmed)
            KeyModifier.ALT -> copy(altArmed = !altArmed)
        }

    fun resolve(operation: CommandOperation): ModifierResolution =
        when (operation) {
            is CommandOperation.Emit ->
                ModifierResolution(
                    nextState = NONE,
                    commandToEmit =
                        operation.command.withAdditionalModifiers(armedModifiers),
                )
            CommandOperation.Cancelled,
            CommandOperation.NoOp,
            ->
                ModifierResolution(
                    nextState = this,
                    commandToEmit = null,
                )
        }

    companion object {
        val NONE = OneShotModifierState()
    }
}

data class ModifierResolution(
    val nextState: OneShotModifierState,
    val commandToEmit: KeyCommand?,
)

package com.zellij.keyboard.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OneShotModifierStateTest {
    @Test
    fun `each modifier tap toggles independently`() {
        val ctrl = OneShotModifierState.NONE.onModifierTap(KeyModifier.CTRL)
        assertEquals(OneShotModifierState(ctrlArmed = true), ctrl)
        assertEquals(
            OneShotModifierState.NONE,
            ctrl.onModifierTap(KeyModifier.CTRL),
        )

        val alt = OneShotModifierState.NONE.onModifierTap(KeyModifier.ALT)
        assertEquals(OneShotModifierState(altArmed = true), alt)
        assertEquals(
            OneShotModifierState.NONE,
            alt.onModifierTap(KeyModifier.ALT),
        )
    }

    @Test
    fun `ctrl and alt may be armed together in either order`() {
        val ctrlThenAlt =
            OneShotModifierState.NONE
                .onModifierTap(KeyModifier.CTRL)
                .onModifierTap(KeyModifier.ALT)
        val altThenCtrl =
            OneShotModifierState.NONE
                .onModifierTap(KeyModifier.ALT)
                .onModifierTap(KeyModifier.CTRL)

        val both = OneShotModifierState(ctrlArmed = true, altArmed = true)
        assertEquals(both, ctrlThenAlt)
        assertEquals(both, altThenCtrl)
        assertEquals(KeyModifiers(ctrl = true, alt = true), both.armedModifiers)
    }

    @Test
    fun `ordinary key consumes and clears both armed modifiers`() {
        val state = OneShotModifierState(ctrlArmed = true, altArmed = true)
        val ordinaryKey = KeyCommand(Key.Character('a'))

        val resolution = state.resolve(CommandOperation.Emit(ordinaryKey))

        assertEquals(OneShotModifierState.NONE, resolution.nextState)
        assertEquals(
            KeyCommand(
                key = Key.Character('a'),
                modifiers = KeyModifiers(ctrl = true, alt = true),
            ),
            resolution.commandToEmit,
        )
    }

    @Test
    fun `shortcut command merges armed modifiers and clears both`() {
        val state = OneShotModifierState(altArmed = true)
        val ctrlNextTab =
            KeyCommand(
                key = Key.Character('n'),
                modifiers = KeyModifiers.CTRL,
            )

        val resolution = state.resolve(CommandOperation.Emit(ctrlNextTab))

        assertEquals(OneShotModifierState.NONE, resolution.nextState)
        assertEquals(
            KeyCommand(
                key = Key.Character('n'),
                modifiers = KeyModifiers(ctrl = true, alt = true),
            ),
            resolution.commandToEmit,
        )
    }

    @Test
    fun `cancelled operation preserves armed modifiers and emits nothing`() {
        val state = OneShotModifierState(ctrlArmed = true, altArmed = true)

        val resolution = state.resolve(CommandOperation.Cancelled)

        assertEquals(state, resolution.nextState)
        assertNull(resolution.commandToEmit)
    }

    @Test
    fun `no-op preserves armed modifiers and emits nothing`() {
        val state = OneShotModifierState(ctrlArmed = true)

        val resolution = state.resolve(CommandOperation.NoOp)

        assertEquals(state, resolution.nextState)
        assertNull(resolution.commandToEmit)
    }

    @Test
    fun `emitted command clears state even when no modifier is armed`() {
        val command = KeyCommand(Key.Named.ENTER)

        val resolution =
            OneShotModifierState.NONE.resolve(CommandOperation.Emit(command))

        assertEquals(OneShotModifierState.NONE, resolution.nextState)
        assertEquals(command, resolution.commandToEmit)
    }
}

package com.zellij.keyboard.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TerminalKeyboardPlannerTest {
    @Test
    fun `letter layout uses familiar phone rows and compact terminal controls`() {
        val layout = TerminalKeyboardPlanner.layout(TerminalKeyboardState())

        assertEquals(TerminalKeyboardLayer.LETTERS, layout.layer)
        assertEquals(
            listOf(
                "letters_numbers",
                "letters_qwerty",
                "letters_home",
                "letters_bottom",
                "letters_controls",
            ),
            layout.rows.map(TerminalKeyboardRow::id),
        )
        assertEquals(
            listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
            layout.rows[0].keys.map(TerminalKeyModel::label),
        )
        assertEquals(
            listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p"),
            layout.rows[1].keys.map(TerminalKeyModel::label),
        )
        assertEquals(
            listOf("a", "s", "d", "f", "g", "h", "j", "k", "l"),
            layout.rows[2].keys.map(TerminalKeyModel::label),
        )
        assertEquals(
            listOf("Shift", "z", "x", "c", "v", "b", "n", "m", "⌫"),
            layout.rows[3].keys.map(TerminalKeyModel::label),
        )
        assertEquals(
            listOf("Esc", "Ctrl", "Alt", "Tab", "#+=", "Space", "Enter"),
            layout.rows[4].keys.map(TerminalKeyModel::label),
        )
    }

    @Test
    fun `symbol layout uses phone rows with paired punctuation and terminal controls`() {
        val layout =
            TerminalKeyboardPlanner.layout(
                TerminalKeyboardState(layer = TerminalKeyboardLayer.SYMBOLS),
            )

        assertEquals(
            listOf(
                "symbols_numbers",
                "symbols_primary",
                "symbols_pairs",
                "symbols_bottom",
                "symbols_controls",
            ),
            layout.rows.map(TerminalKeyboardRow::id),
        )
        assertEquals(
            listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
            layout.rows[0].keys.map(TerminalKeyModel::label),
        )
        assertEquals(
            listOf("!", "@", "#", "$", "%", "^", "&", "*", "(", ")"),
            layout.rows[1].keys.map(TerminalKeyModel::label),
        )
        assertEquals(
            listOf("`", "-", "=", "[", "]", "\\"),
            layout.rows[2].keys.map(TerminalKeyModel::label),
        )
        assertEquals(
            listOf("Shift", ";", "'", ",", ".", "/", "⌫"),
            layout.rows[3].keys.map(TerminalKeyModel::label),
        )
        assertEquals(
            listOf("Esc", "Ctrl", "Alt", "Tab", "ABC", "Space", "Enter"),
            layout.rows[4].keys.map(TerminalKeyModel::label),
        )
    }

    @Test
    fun `every rendered layout has stable unique key IDs`() {
        TerminalKeyboardLayer.entries.forEach { layer ->
            TerminalShiftState.entries.forEach { shift ->
                val layout =
                    TerminalKeyboardPlanner.layout(
                        TerminalKeyboardState(layer = layer, shift = shift),
                    )

                assertEquals(
                    layout.keys.size,
                    layout.keys.map(TerminalKeyModel::id).distinct().size,
                    "Duplicate key ID in $layer with $shift",
                )
            }
        }
    }

    @Test
    fun `complete supported characters modifiers and named keys remain reachable`() {
        val expectedCharacters =
            buildSet {
                addAll('a'..'z')
                addAll('A'..'Z')
                addAll('0'..'9')
                addAll(
                    setOf(
                        '!',
                        '@',
                        '#',
                        '$',
                        '%',
                        '^',
                        '&',
                        '*',
                        '(',
                        ')',
                        '-',
                        '_',
                        '=',
                        '+',
                        '[',
                        '{',
                        ']',
                        '}',
                        '\\',
                        '|',
                        '`',
                        '~',
                        ';',
                        ':',
                        '\'',
                        '"',
                        ',',
                        '<',
                        '.',
                        '>',
                        '/',
                        '?',
                    ),
                )
            }
        val reachableCharacters =
            TerminalKeyboardLayer.entries
                .flatMap { layer ->
                    TerminalShiftState.entries.flatMap { shift ->
                        val state = TerminalKeyboardState(layer = layer, shift = shift)
                        TerminalKeyboardPlanner
                            .layout(state)
                            .keys
                            .mapNotNull { key ->
                                (press(state, key.id).commandToEmit?.key as? Key.Character)?.value
                            }
                    }
                }.toSet()

        assertEquals(expectedCharacters, reachableCharacters)

        val expectedNamedKeys =
            setOf(
                Key.Named.ESCAPE,
                Key.Named.TAB,
                Key.Named.ENTER,
                Key.Named.BACKSPACE,
                Key.Named.SPACE,
            )
        val expectedControls =
            setOf(
                TerminalKeyIds.SHIFT,
                TerminalKeyIds.LAYER,
                TerminalKeyIds.CTRL,
                TerminalKeyIds.ALT,
            )

        TerminalKeyboardLayer.entries.forEach { layer ->
            val state = TerminalKeyboardState(layer = layer)
            val layout = TerminalKeyboardPlanner.layout(state)
            val reachableNamedKeys =
                layout.keys
                    .mapNotNull { key ->
                        press(state, key.id).commandToEmit?.key as? Key.Named
                    }.toSet()

            assertEquals(expectedNamedKeys, reachableNamedKeys, "Named keys missing on $layer")
            assertTrue(
                layout.keys.map(TerminalKeyModel::id).containsAll(expectedControls),
                "Modifier or layer control missing on $layer",
            )
        }
    }

    @Test
    fun `fractional weights widen edge and terminal controls`() {
        val letters = TerminalKeyboardPlanner.layout(TerminalKeyboardState())
        val letterBottom = letters.rows.single { it.id == "letters_bottom" }
        val letterWidths =
            letterBottom.keys
                .filter { it.role == TerminalKeyRole.LETTER }
                .map(TerminalKeyModel::widthUnits)

        assertEquals(List(7) { 1f }, letterWidths)
        assertEquals(1.5f, requireKey(letters, TerminalKeyIds.SHIFT).widthUnits)
        assertEquals(1.5f, requireKey(letters, TerminalKeyIds.BACKSPACE).widthUnits)
        assertTrue(requireKey(letters, TerminalKeyIds.SHIFT).widthUnits > letterWidths.max())
        assertTrue(requireKey(letters, TerminalKeyIds.BACKSPACE).widthUnits > letterWidths.max())
        assertEquals(
            listOf(1f, 1.15f, 1f, 1f, 1.25f, 2.5f, 1.5f),
            letters.rows.single { it.id == "letters_controls" }
                .keys
                .map(TerminalKeyModel::widthUnits),
        )

        val symbols =
            TerminalKeyboardPlanner.layout(
                TerminalKeyboardState(layer = TerminalKeyboardLayer.SYMBOLS),
            )
        assertEquals(2.5f, requireKey(symbols, TerminalKeyIds.SHIFT).widthUnits)
        assertEquals(2.5f, requireKey(symbols, TerminalKeyIds.BACKSPACE).widthUnits)
    }

    @Test
    fun `portrait rows never exceed ten normal touch target units`() {
        TerminalKeyboardLayer.entries.forEach { layer ->
            val layout = TerminalKeyboardPlanner.layout(TerminalKeyboardState(layer = layer))

            layout.rows.forEach { row ->
                assertTrue(
                    row.keys.size <= 10,
                    "${row.id} has ${row.keys.size} touch targets",
                )
                assertTrue(
                    row.keys.sumOf { it.widthUnits.toDouble() } <= 10.0,
                    "${row.id} exceeds ten normal-width units",
                )
            }
        }
    }

    @Test
    fun `key width must be positive and finite`() {
        val key = TerminalKeyboardPlanner.layout(TerminalKeyboardState()).keys.first()

        listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)
            .forEach { invalidWidth ->
                assertFailsWith<IllegalArgumentException> {
                    key.copy(widthUnits = invalidWidth)
                }
            }
    }

    @Test
    fun `rendered metadata is stable descriptive and adapter friendly`() {
        val state =
            TerminalKeyboardState(
                shift = TerminalShiftState.ONE_SHOT,
                modifiers = OneShotModifierState(ctrlArmed = true, altArmed = true),
            )
        val layout = TerminalKeyboardPlanner.layout(state)

        assertEquals("Letter A", requireKey(layout, "letter_a").accessibilityDescription)
        assertEquals("!", requireKey(layout, "digit_1").label)
        assertEquals(
            "Exclamation mark",
            requireKey(layout, "digit_1").accessibilityDescription,
        )
        assertTrue(requireKey(layout, TerminalKeyIds.SHIFT).isActive)
        assertTrue(requireKey(layout, TerminalKeyIds.CTRL).isActive)
        assertTrue(requireKey(layout, TerminalKeyIds.ALT).isActive)
        assertFalse(requireKey(layout, TerminalKeyIds.LAYER).isActive)
        assertEquals(2.5f, requireKey(layout, TerminalKeyIds.SPACE).widthUnits)
        assertEquals(TerminalKeyRole.BACKSPACE, requireKey(layout, TerminalKeyIds.BACKSPACE).role)
    }

    @Test
    fun `shift toggles as a UI-only change then emits uppercase and clears`() {
        val shiftPlan = press(TerminalKeyboardState(), TerminalKeyIds.SHIFT)

        assertEquals(
            TerminalKeyboardEffect.StateChanged(TerminalKeyboardUiChange.ShiftToggled),
            shiftPlan.effect,
        )
        assertEquals(TerminalShiftState.ONE_SHOT, shiftPlan.nextState.shift)
        assertNull(shiftPlan.commandToEmit)

        val characterPlan = press(shiftPlan.nextState, id("letter_a"))
        assertEquals(KeyCommand(Key.Character('A')), characterPlan.commandToEmit)
        assertEquals(TerminalShiftState.OFF, characterPlan.nextState.shift)
    }

    @Test
    fun `letter digits resolve their shifted variants`() {
        val shifted = TerminalKeyboardState(shift = TerminalShiftState.ONE_SHOT)

        assertEquals(
            KeyCommand(Key.Character('!')),
            press(shifted, id("digit_1")).commandToEmit,
        )
    }

    @Test
    fun `symbol layer shift resolves alternate symbol pairs`() {
        val normal = TerminalKeyboardState(layer = TerminalKeyboardLayer.SYMBOLS)
        val shifted = normal.copy(shift = TerminalShiftState.ONE_SHOT)

        assertEquals(
            KeyCommand(Key.Character('-')),
            press(normal, id("symbol_minus")).commandToEmit,
        )
        assertEquals(
            KeyCommand(Key.Character('_')),
            press(shifted, id("symbol_minus")).commandToEmit,
        )
        assertEquals(
            KeyCommand(Key.Character('|')),
            press(shifted, id("symbol_backslash")).commandToEmit,
        )
        assertEquals(
            KeyCommand(Key.Character('~')),
            press(shifted, id("symbol_grave")).commandToEmit,
        )
    }

    @Test
    fun `layer transition preserves shift and modifiers until symbol emission`() {
        val armed =
            TerminalKeyboardState(
                shift = TerminalShiftState.ONE_SHOT,
                modifiers = OneShotModifierState(ctrlArmed = true, altArmed = true),
            )

        val layerPlan = press(armed, TerminalKeyIds.LAYER)
        assertEquals(
            TerminalKeyboardEffect.StateChanged(TerminalKeyboardUiChange.LayerToggled),
            layerPlan.effect,
        )
        assertEquals(
            armed.copy(layer = TerminalKeyboardLayer.SYMBOLS),
            layerPlan.nextState,
        )

        val emitPlan = press(layerPlan.nextState, id("symbol_left_bracket"))
        assertEquals(
            KeyCommand(
                key = Key.Character('{'),
                modifiers = KeyModifiers(ctrl = true, alt = true),
            ),
            emitPlan.commandToEmit,
        )
        assertEquals(TerminalShiftState.OFF, emitPlan.nextState.shift)
        assertEquals(OneShotModifierState.NONE, emitPlan.nextState.modifiers)
    }

    @Test
    fun `ctrl and alt arm independently and merge into next ordinary key`() {
        val ctrlPlan = press(TerminalKeyboardState(), TerminalKeyIds.CTRL)
        assertEquals(
            TerminalKeyboardEffect.StateChanged(
                TerminalKeyboardUiChange.ModifierToggled(KeyModifier.CTRL),
            ),
            ctrlPlan.effect,
        )
        assertEquals(OneShotModifierState(ctrlArmed = true), ctrlPlan.nextState.modifiers)

        val altPlan = press(ctrlPlan.nextState, TerminalKeyIds.ALT)
        assertEquals(
            OneShotModifierState(ctrlArmed = true, altArmed = true),
            altPlan.nextState.modifiers,
        )

        val emitPlan = press(altPlan.nextState, id("letter_x"))
        assertEquals(
            KeyCommand(
                key = Key.Character('x'),
                modifiers = KeyModifiers(ctrl = true, alt = true),
            ),
            emitPlan.commandToEmit,
        )
        assertEquals(OneShotModifierState.NONE, emitPlan.nextState.modifiers)
    }

    @Test
    fun `named terminal commands merge and clear both modifiers`() {
        val armed =
            TerminalKeyboardState(
                modifiers = OneShotModifierState(ctrlArmed = true, altArmed = true),
            )
        val commands =
            mapOf(
                TerminalKeyIds.ESCAPE to Key.Named.ESCAPE,
                TerminalKeyIds.TAB to Key.Named.TAB,
                TerminalKeyIds.ENTER to Key.Named.ENTER,
                TerminalKeyIds.BACKSPACE to Key.Named.BACKSPACE,
                TerminalKeyIds.SPACE to Key.Named.SPACE,
            )

        commands.forEach { (keyId, namedKey) ->
            val plan = press(armed, keyId)
            assertEquals(
                KeyCommand(
                    key = namedKey,
                    modifiers = KeyModifiers(ctrl = true, alt = true),
                ),
                plan.commandToEmit,
                "Unexpected command for $keyId",
            )
            assertEquals(OneShotModifierState.NONE, plan.nextState.modifiers)
        }
    }

    @Test
    fun `named command preserves one-shot shift because it selects no character variant`() {
        val shifted = TerminalKeyboardState(shift = TerminalShiftState.ONE_SHOT)

        val plan = press(shifted, TerminalKeyIds.ENTER)

        assertEquals(KeyCommand(Key.Named.ENTER), plan.commandToEmit)
        assertEquals(TerminalShiftState.ONE_SHOT, plan.nextState.shift)
    }

    @Test
    fun `cancelled input preserves layer shift and armed modifiers`() {
        val state =
            TerminalKeyboardState(
                layer = TerminalKeyboardLayer.SYMBOLS,
                shift = TerminalShiftState.ONE_SHOT,
                modifiers = OneShotModifierState(ctrlArmed = true, altArmed = true),
            )

        val plan = TerminalKeyboardPlanner.plan(state, TerminalKeyboardInput.Cancelled)

        assertEquals(state, plan.nextState)
        assertEquals(TerminalKeyboardEffect.Cancelled, plan.effect)
        assertNull(plan.commandToEmit)
    }

    @Test
    fun `explicit no-op and unknown key preserve every state field`() {
        val state =
            TerminalKeyboardState(
                layer = TerminalKeyboardLayer.SYMBOLS,
                shift = TerminalShiftState.ONE_SHOT,
                modifiers = OneShotModifierState(altArmed = true),
            )

        val noOp = TerminalKeyboardPlanner.plan(state, TerminalKeyboardInput.NoOp)
        val unknown = press(state, id("missing_key"))

        assertEquals(state, noOp.nextState)
        assertEquals(TerminalKeyboardEffect.NoOp, noOp.effect)
        assertEquals(state, unknown.nextState)
        assertEquals(TerminalKeyboardEffect.NoOp, unknown.effect)
        assertNull(noOp.commandToEmit)
        assertNull(unknown.commandToEmit)
    }

    @Test
    fun `modifier can be disarmed without disturbing layer or shift`() {
        val state =
            TerminalKeyboardState(
                layer = TerminalKeyboardLayer.SYMBOLS,
                shift = TerminalShiftState.ONE_SHOT,
                modifiers = OneShotModifierState(ctrlArmed = true),
            )

        val plan = press(state, TerminalKeyIds.CTRL)

        assertEquals(state.copy(modifiers = OneShotModifierState.NONE), plan.nextState)
        assertNull(plan.commandToEmit)
    }

    private fun press(
        state: TerminalKeyboardState,
        keyId: TerminalKeyId,
    ): TerminalKeyboardPlan =
        TerminalKeyboardPlanner.plan(
            state,
            TerminalKeyboardInput.KeyPress(keyId),
        )

    private fun id(value: String): TerminalKeyId = TerminalKeyId(value)

    private fun requireKey(
        layout: TerminalKeyboardLayout,
        id: String,
    ): TerminalKeyModel = requireKey(layout, TerminalKeyId(id))

    private fun requireKey(
        layout: TerminalKeyboardLayout,
        id: TerminalKeyId,
    ): TerminalKeyModel = assertNotNull(layout.key(id), "Missing key $id")
}

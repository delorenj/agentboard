package com.zellij.keyboard.core

/**
 * Pure Kotlin terminal layout renderer and key-command planner.
 *
 * A platform adapter renders [layout], sends key IDs back through [plan], stores
 * the returned state, and emits a platform key event only for [TerminalKeyboardEffect.Emit].
 */
object TerminalKeyboardPlanner {
    private sealed interface KeyAction {
        data class Character(
            val normal: Char,
            val shifted: Char,
        ) : KeyAction

        data class NamedCommand(val key: Key.Named) : KeyAction

        data class ToggleModifier(val modifier: KeyModifier) : KeyAction

        data object ToggleShift : KeyAction

        data object ToggleLayer : KeyAction
    }

    private data class KeyDefinition(
        val id: TerminalKeyId,
        val role: TerminalKeyRole,
        val action: KeyAction,
        val widthUnits: Int = 1,
    )

    private data class RowDefinition(
        val id: String,
        val keys: List<KeyDefinition>,
    )

    private val letterRows: List<RowDefinition> =
        listOf(
            row(
                "letters_numbers",
                named(TerminalKeyIds.ESCAPE, TerminalKeyRole.ESCAPE, Key.Named.ESCAPE, 2),
                pair("digit_1", TerminalKeyRole.DIGIT, '1', '!'),
                pair("digit_2", TerminalKeyRole.DIGIT, '2', '@'),
                pair("digit_3", TerminalKeyRole.DIGIT, '3', '#'),
                pair("digit_4", TerminalKeyRole.DIGIT, '4', '$'),
                pair("digit_5", TerminalKeyRole.DIGIT, '5', '%'),
                pair("digit_6", TerminalKeyRole.DIGIT, '6', '^'),
                pair("digit_7", TerminalKeyRole.DIGIT, '7', '&'),
                pair("digit_8", TerminalKeyRole.DIGIT, '8', '*'),
                pair("digit_9", TerminalKeyRole.DIGIT, '9', '('),
                pair("digit_0", TerminalKeyRole.DIGIT, '0', ')'),
                pair("punctuation_minus", TerminalKeyRole.PUNCTUATION, '-', '_'),
                pair("punctuation_equals", TerminalKeyRole.PUNCTUATION, '=', '+'),
                named(
                    TerminalKeyIds.BACKSPACE,
                    TerminalKeyRole.BACKSPACE,
                    Key.Named.BACKSPACE,
                    2,
                ),
            ),
            row(
                "letters_qwerty",
                named(TerminalKeyIds.TAB, TerminalKeyRole.TAB, Key.Named.TAB, 2),
                letter('q'),
                letter('w'),
                letter('e'),
                letter('r'),
                letter('t'),
                letter('y'),
                letter('u'),
                letter('i'),
                letter('o'),
                letter('p'),
                pair("punctuation_left_bracket", TerminalKeyRole.PUNCTUATION, '[', '{'),
                pair("punctuation_right_bracket", TerminalKeyRole.PUNCTUATION, ']', '}'),
                pair("punctuation_backslash", TerminalKeyRole.PUNCTUATION, '\\', '|'),
            ),
            row(
                "letters_home",
                modifier(TerminalKeyIds.CTRL, KeyModifier.CTRL, 2),
                modifier(TerminalKeyIds.ALT, KeyModifier.ALT, 2),
                letter('a'),
                letter('s'),
                letter('d'),
                letter('f'),
                letter('g'),
                letter('h'),
                letter('j'),
                letter('k'),
                letter('l'),
                pair("punctuation_semicolon", TerminalKeyRole.PUNCTUATION, ';', ':'),
                pair("punctuation_quote", TerminalKeyRole.PUNCTUATION, '\'', '"'),
                named(TerminalKeyIds.ENTER, TerminalKeyRole.ENTER, Key.Named.ENTER, 2),
            ),
            row(
                "letters_bottom",
                shift(),
                letter('z'),
                letter('x'),
                letter('c'),
                letter('v'),
                letter('b'),
                letter('n'),
                letter('m'),
                pair("punctuation_comma", TerminalKeyRole.PUNCTUATION, ',', '<'),
                pair("punctuation_period", TerminalKeyRole.PUNCTUATION, '.', '>'),
                pair("punctuation_slash", TerminalKeyRole.PUNCTUATION, '/', '?'),
                layer(),
            ),
            row(
                "letters_space",
                named(TerminalKeyIds.SPACE, TerminalKeyRole.SPACE, Key.Named.SPACE, 12),
            ),
        )

    private val symbolRows: List<RowDefinition> =
        listOf(
            row(
                "symbols_primary",
                named(TerminalKeyIds.ESCAPE, TerminalKeyRole.ESCAPE, Key.Named.ESCAPE, 2),
                fixed("symbol_exclamation", '!'),
                fixed("symbol_at", '@'),
                fixed("symbol_hash", '#'),
                fixed("symbol_dollar", '$'),
                fixed("symbol_percent", '%'),
                fixed("symbol_caret", '^'),
                fixed("symbol_ampersand", '&'),
                fixed("symbol_asterisk", '*'),
                fixed("symbol_left_parenthesis", '('),
                fixed("symbol_right_parenthesis", ')'),
                named(
                    TerminalKeyIds.BACKSPACE,
                    TerminalKeyRole.BACKSPACE,
                    Key.Named.BACKSPACE,
                    2,
                ),
            ),
            row(
                "symbols_pairs",
                named(TerminalKeyIds.TAB, TerminalKeyRole.TAB, Key.Named.TAB, 2),
                pair("symbol_minus", TerminalKeyRole.PUNCTUATION, '-', '_'),
                pair("symbol_equals", TerminalKeyRole.PUNCTUATION, '=', '+'),
                pair("symbol_left_bracket", TerminalKeyRole.PUNCTUATION, '[', '{'),
                pair("symbol_right_bracket", TerminalKeyRole.PUNCTUATION, ']', '}'),
                pair("symbol_backslash", TerminalKeyRole.PUNCTUATION, '\\', '|'),
                pair("symbol_grave", TerminalKeyRole.PUNCTUATION, '`', '~'),
                pair("symbol_semicolon", TerminalKeyRole.PUNCTUATION, ';', ':'),
                pair("symbol_quote", TerminalKeyRole.PUNCTUATION, '\'', '"'),
                pair("symbol_comma", TerminalKeyRole.PUNCTUATION, ',', '<'),
                pair("symbol_period", TerminalKeyRole.PUNCTUATION, '.', '>'),
                pair("symbol_slash", TerminalKeyRole.PUNCTUATION, '/', '?'),
            ),
            row(
                "symbols_numbers",
                modifier(TerminalKeyIds.CTRL, KeyModifier.CTRL, 2),
                modifier(TerminalKeyIds.ALT, KeyModifier.ALT, 2),
                fixed("symbol_digit_1", '1', TerminalKeyRole.DIGIT),
                fixed("symbol_digit_2", '2', TerminalKeyRole.DIGIT),
                fixed("symbol_digit_3", '3', TerminalKeyRole.DIGIT),
                fixed("symbol_digit_4", '4', TerminalKeyRole.DIGIT),
                fixed("symbol_digit_5", '5', TerminalKeyRole.DIGIT),
                fixed("symbol_digit_6", '6', TerminalKeyRole.DIGIT),
                fixed("symbol_digit_7", '7', TerminalKeyRole.DIGIT),
                fixed("symbol_digit_8", '8', TerminalKeyRole.DIGIT),
                fixed("symbol_digit_9", '9', TerminalKeyRole.DIGIT),
                fixed("symbol_digit_0", '0', TerminalKeyRole.DIGIT),
                named(TerminalKeyIds.ENTER, TerminalKeyRole.ENTER, Key.Named.ENTER, 2),
            ),
            row(
                "symbols_controls",
                shift(),
                layer(),
            ),
            row(
                "symbols_space",
                named(TerminalKeyIds.SPACE, TerminalKeyRole.SPACE, Key.Named.SPACE, 12),
            ),
        )

    fun layout(state: TerminalKeyboardState): TerminalKeyboardLayout {
        val definitions =
            when (state.layer) {
                TerminalKeyboardLayer.LETTERS -> letterRows
                TerminalKeyboardLayer.SYMBOLS -> symbolRows
            }

        return TerminalKeyboardLayout(
            layer = state.layer,
            shift = state.shift,
            rows =
                definitions.map { row ->
                    TerminalKeyboardRow(
                        id = row.id,
                        keys = row.keys.map { it.render(state) },
                    )
                },
        )
    }

    fun plan(
        state: TerminalKeyboardState,
        input: TerminalKeyboardInput,
    ): TerminalKeyboardPlan =
        when (input) {
            is TerminalKeyboardInput.KeyPress -> planKeyPress(state, input.keyId)
            TerminalKeyboardInput.Cancelled ->
                preserveNonEmission(
                    state = state,
                    operation = CommandOperation.Cancelled,
                    effect = TerminalKeyboardEffect.Cancelled,
                )
            TerminalKeyboardInput.NoOp ->
                preserveNonEmission(
                    state = state,
                    operation = CommandOperation.NoOp,
                    effect = TerminalKeyboardEffect.NoOp,
                )
        }

    private fun planKeyPress(
        state: TerminalKeyboardState,
        keyId: TerminalKeyId,
    ): TerminalKeyboardPlan {
        val definition =
            definitionsFor(state.layer)
                .asSequence()
                .flatMap { it.keys.asSequence() }
                .firstOrNull { it.id == keyId }
                ?: return preserveNonEmission(
                    state = state,
                    operation = CommandOperation.NoOp,
                    effect = TerminalKeyboardEffect.NoOp,
                )

        return when (val action = definition.action) {
            is KeyAction.Character -> emitCharacter(state, action)
            is KeyAction.NamedCommand -> emitNamedCommand(state, action.key)
            is KeyAction.ToggleModifier ->
                TerminalKeyboardPlan(
                    nextState =
                        state.copy(
                            modifiers = state.modifiers.onModifierTap(action.modifier),
                        ),
                    effect =
                        TerminalKeyboardEffect.StateChanged(
                            TerminalKeyboardUiChange.ModifierToggled(action.modifier),
                        ),
                )
            KeyAction.ToggleShift ->
                TerminalKeyboardPlan(
                    nextState =
                        state.copy(
                            shift =
                                when (state.shift) {
                                    TerminalShiftState.OFF -> TerminalShiftState.ONE_SHOT
                                    TerminalShiftState.ONE_SHOT -> TerminalShiftState.OFF
                                },
                        ),
                    effect =
                        TerminalKeyboardEffect.StateChanged(
                            TerminalKeyboardUiChange.ShiftToggled,
                        ),
                )
            KeyAction.ToggleLayer ->
                TerminalKeyboardPlan(
                    nextState =
                        state.copy(
                            layer =
                                when (state.layer) {
                                    TerminalKeyboardLayer.LETTERS ->
                                        TerminalKeyboardLayer.SYMBOLS
                                    TerminalKeyboardLayer.SYMBOLS ->
                                        TerminalKeyboardLayer.LETTERS
                                },
                        ),
                    effect =
                        TerminalKeyboardEffect.StateChanged(
                            TerminalKeyboardUiChange.LayerToggled,
                        ),
                )
        }
    }

    private fun emitCharacter(
        state: TerminalKeyboardState,
        action: KeyAction.Character,
    ): TerminalKeyboardPlan {
        val character =
            when (state.shift) {
                TerminalShiftState.OFF -> action.normal
                TerminalShiftState.ONE_SHOT -> action.shifted
            }
        val resolution =
            state.modifiers.resolve(
                CommandOperation.Emit(KeyCommand(Key.Character(character))),
            )
        return TerminalKeyboardPlan(
            nextState =
                state.copy(
                    shift = TerminalShiftState.OFF,
                    modifiers = resolution.nextState,
                ),
            effect = TerminalKeyboardEffect.Emit(requireNotNull(resolution.commandToEmit)),
        )
    }

    private fun emitNamedCommand(
        state: TerminalKeyboardState,
        key: Key.Named,
    ): TerminalKeyboardPlan {
        val resolution =
            state.modifiers.resolve(
                CommandOperation.Emit(KeyCommand(key)),
            )
        return TerminalKeyboardPlan(
            nextState = state.copy(modifiers = resolution.nextState),
            effect = TerminalKeyboardEffect.Emit(requireNotNull(resolution.commandToEmit)),
        )
    }

    private fun preserveNonEmission(
        state: TerminalKeyboardState,
        operation: CommandOperation,
        effect: TerminalKeyboardEffect,
    ): TerminalKeyboardPlan {
        val resolution = state.modifiers.resolve(operation)
        check(resolution.commandToEmit == null)
        return TerminalKeyboardPlan(
            nextState = state.copy(modifiers = resolution.nextState),
            effect = effect,
        )
    }

    private fun definitionsFor(layer: TerminalKeyboardLayer): List<RowDefinition> =
        when (layer) {
            TerminalKeyboardLayer.LETTERS -> letterRows
            TerminalKeyboardLayer.SYMBOLS -> symbolRows
        }

    private fun KeyDefinition.render(state: TerminalKeyboardState): TerminalKeyModel {
        val character = action as? KeyAction.Character
        val selectedCharacter =
            character?.let {
                when (state.shift) {
                    TerminalShiftState.OFF -> it.normal
                    TerminalShiftState.ONE_SHOT -> it.shifted
                }
            }

        return TerminalKeyModel(
            id = id,
            label =
                selectedCharacter?.toString()
                    ?: when (id) {
                        TerminalKeyIds.ESCAPE -> "Esc"
                        TerminalKeyIds.TAB -> "Tab"
                        TerminalKeyIds.ENTER -> "Enter"
                        TerminalKeyIds.BACKSPACE -> "⌫"
                        TerminalKeyIds.SPACE -> "Space"
                        TerminalKeyIds.SHIFT -> "Shift"
                        TerminalKeyIds.LAYER ->
                            if (state.layer == TerminalKeyboardLayer.LETTERS) "#+=" else "ABC"
                        TerminalKeyIds.CTRL -> "Ctrl"
                        TerminalKeyIds.ALT -> "Alt"
                        else -> error("missing label for $id")
                    },
            accessibilityDescription =
                selectedCharacter?.let(::spokenCharacter)
                    ?: when (id) {
                        TerminalKeyIds.ESCAPE -> "Escape"
                        TerminalKeyIds.TAB -> "Tab"
                        TerminalKeyIds.ENTER -> "Enter"
                        TerminalKeyIds.BACKSPACE -> "Backspace"
                        TerminalKeyIds.SPACE -> "Space"
                        TerminalKeyIds.SHIFT ->
                            if (state.shift == TerminalShiftState.ONE_SHOT) {
                                "Shift on"
                            } else {
                                "Shift off"
                            }
                        TerminalKeyIds.LAYER ->
                            if (state.layer == TerminalKeyboardLayer.LETTERS) {
                                "Show symbols"
                            } else {
                                "Show letters"
                            }
                        TerminalKeyIds.CTRL ->
                            if (state.modifiers.ctrlArmed) "Control armed" else "Control"
                        TerminalKeyIds.ALT ->
                            if (state.modifiers.altArmed) "Alt armed" else "Alt"
                        else -> error("missing accessibility description for $id")
                    },
            role = role,
            widthUnits = widthUnits,
            isActive =
                when (id) {
                    TerminalKeyIds.SHIFT -> state.shift == TerminalShiftState.ONE_SHOT
                    TerminalKeyIds.LAYER -> state.layer == TerminalKeyboardLayer.SYMBOLS
                    TerminalKeyIds.CTRL -> state.modifiers.ctrlArmed
                    TerminalKeyIds.ALT -> state.modifiers.altArmed
                    else -> false
                },
        )
    }

    private fun spokenCharacter(character: Char): String =
        when (character) {
            'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm',
            'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z',
            'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M',
            'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z',
            -> "Letter ${character.uppercaseChar()}"
            '0', '1', '2', '3', '4', '5', '6', '7', '8', '9' -> "Digit $character"
            '!' -> "Exclamation mark"
            '@' -> "At sign"
            '#' -> "Number sign"
            '$' -> "Dollar sign"
            '%' -> "Percent sign"
            '^' -> "Caret"
            '&' -> "Ampersand"
            '*' -> "Asterisk"
            '(' -> "Left parenthesis"
            ')' -> "Right parenthesis"
            '-' -> "Minus"
            '_' -> "Underscore"
            '=' -> "Equals"
            '+' -> "Plus"
            '[' -> "Left bracket"
            '{' -> "Left brace"
            ']' -> "Right bracket"
            '}' -> "Right brace"
            '\\' -> "Backslash"
            '|' -> "Vertical bar"
            '`' -> "Grave accent"
            '~' -> "Tilde"
            ';' -> "Semicolon"
            ':' -> "Colon"
            '\'' -> "Apostrophe"
            '"' -> "Quotation mark"
            ',' -> "Comma"
            '<' -> "Less than"
            '.' -> "Period"
            '>' -> "Greater than"
            '/' -> "Slash"
            '?' -> "Question mark"
            else -> "Character $character"
        }

    private fun row(
        id: String,
        vararg keys: KeyDefinition,
    ): RowDefinition = RowDefinition(id, keys.toList())

    private fun letter(character: Char): KeyDefinition =
        pair(
            id = "letter_$character",
            role = TerminalKeyRole.LETTER,
            normal = character,
            shifted = character.uppercaseChar(),
        )

    private fun fixed(
        id: String,
        character: Char,
        role: TerminalKeyRole = TerminalKeyRole.PUNCTUATION,
    ): KeyDefinition = pair(id, role, character, character)

    private fun pair(
        id: String,
        role: TerminalKeyRole,
        normal: Char,
        shifted: Char,
    ): KeyDefinition =
        KeyDefinition(
            id = TerminalKeyId(id),
            role = role,
            action = KeyAction.Character(normal, shifted),
        )

    private fun named(
        id: TerminalKeyId,
        role: TerminalKeyRole,
        key: Key.Named,
        widthUnits: Int,
    ): KeyDefinition =
        KeyDefinition(
            id = id,
            role = role,
            action = KeyAction.NamedCommand(key),
            widthUnits = widthUnits,
        )

    private fun modifier(
        id: TerminalKeyId,
        modifier: KeyModifier,
        widthUnits: Int,
    ): KeyDefinition =
        KeyDefinition(
            id = id,
            role = TerminalKeyRole.MODIFIER,
            action = KeyAction.ToggleModifier(modifier),
            widthUnits = widthUnits,
        )

    private fun shift(): KeyDefinition =
        KeyDefinition(
            id = TerminalKeyIds.SHIFT,
            role = TerminalKeyRole.SHIFT,
            action = KeyAction.ToggleShift,
            widthUnits = 3,
        )

    private fun layer(): KeyDefinition =
        KeyDefinition(
            id = TerminalKeyIds.LAYER,
            role = TerminalKeyRole.LAYER,
            action = KeyAction.ToggleLayer,
            widthUnits = 3,
        )
}

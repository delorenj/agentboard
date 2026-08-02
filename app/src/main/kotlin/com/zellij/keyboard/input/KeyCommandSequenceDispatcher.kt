package com.zellij.keyboard.input

import com.zellij.keyboard.core.KeyCommand

sealed interface KeyCommandSequenceResult {
    data class Completed(val commands: List<KeyCommand>) : KeyCommandSequenceResult

    data class Aborted(
        val failedCommandIndex: Int,
        val emissionResult: KeyEventEmissionResult,
    ) : KeyCommandSequenceResult
}

/** Sends command keys in order and stops immediately when any key is not fully accepted. */
object KeyCommandSequenceDispatcher {
    fun dispatch(
        commands: List<KeyCommand>,
        emit: (KeyCommand) -> KeyEventEmissionResult,
    ): KeyCommandSequenceResult {
        commands.forEachIndexed { index, command ->
            val result = emit(command)
            val accepted =
                result is KeyEventEmissionResult.Completed && result.allAccepted
            if (!accepted) {
                return KeyCommandSequenceResult.Aborted(index, result)
            }
        }
        return KeyCommandSequenceResult.Completed(commands.toList())
    }
}

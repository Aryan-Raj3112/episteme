package com.aryan.reader.shared

data class LocalTtsInterruptionState(
    val resumeWhenInterruptionEnds: Boolean = false,
)

sealed interface LocalTtsInterruptionEvent {
    data class Began(val playbackWasActive: Boolean) : LocalTtsInterruptionEvent
    data class Ended(val systemAllowsResume: Boolean) : LocalTtsInterruptionEvent
    data class OutputBecameNoisy(val playbackWasActive: Boolean) : LocalTtsInterruptionEvent
}

enum class LocalTtsInterruptionAction {
    NONE,
    PAUSE,
    RESUME,
}

data class LocalTtsInterruptionTransition(
    val state: LocalTtsInterruptionState,
    val action: LocalTtsInterruptionAction,
)

fun LocalTtsInterruptionState.reduce(
    event: LocalTtsInterruptionEvent,
): LocalTtsInterruptionTransition = when (event) {
    is LocalTtsInterruptionEvent.Began -> LocalTtsInterruptionTransition(
        state = copy(resumeWhenInterruptionEnds = event.playbackWasActive),
        action = if (event.playbackWasActive) {
            LocalTtsInterruptionAction.PAUSE
        } else {
            LocalTtsInterruptionAction.NONE
        },
    )
    is LocalTtsInterruptionEvent.Ended -> LocalTtsInterruptionTransition(
        state = LocalTtsInterruptionState(),
        action = if (resumeWhenInterruptionEnds && event.systemAllowsResume) {
            LocalTtsInterruptionAction.RESUME
        } else {
            LocalTtsInterruptionAction.NONE
        },
    )
    is LocalTtsInterruptionEvent.OutputBecameNoisy -> LocalTtsInterruptionTransition(
        state = LocalTtsInterruptionState(),
        action = if (event.playbackWasActive) {
            LocalTtsInterruptionAction.PAUSE
        } else {
            LocalTtsInterruptionAction.NONE
        },
    )
}

package com.aryan.reader.shared.pdf

enum class SharedPdfAnnotationSessionPhase { EMPTY, LOADING, READY, FAILED }

data class SharedPdfAnnotationSessionState(
    val bookId: String? = null,
    val phase: SharedPdfAnnotationSessionPhase = SharedPdfAnnotationSessionPhase.EMPTY,
    val inkCount: Int = 0,
    val textBoxCount: Int = 0,
    val highlightCount: Int = 0,
    val error: String? = null,
) {
    fun canUseFor(activeBookId: String?): Boolean =
        activeBookId != null && phase == SharedPdfAnnotationSessionPhase.READY && bookId == activeBookId
}

sealed interface SharedPdfAnnotationSessionAction {
    data object Reset : SharedPdfAnnotationSessionAction
    data class LoadStarted(val bookId: String) : SharedPdfAnnotationSessionAction
    data class LoadCompleted(
        val bookId: String,
        val inkCount: Int,
        val textBoxCount: Int,
        val highlightCount: Int,
    ) : SharedPdfAnnotationSessionAction
    data class LoadFailed(val bookId: String, val error: String?) : SharedPdfAnnotationSessionAction
}

fun SharedPdfAnnotationSessionState.reduce(
    action: SharedPdfAnnotationSessionAction,
): SharedPdfAnnotationSessionState = when (action) {
    SharedPdfAnnotationSessionAction.Reset -> SharedPdfAnnotationSessionState()
    is SharedPdfAnnotationSessionAction.LoadStarted -> action.bookId.trim().takeIf(String::isNotBlank)
        ?.let { SharedPdfAnnotationSessionState(bookId = it, phase = SharedPdfAnnotationSessionPhase.LOADING) }
        ?: SharedPdfAnnotationSessionState()
    is SharedPdfAnnotationSessionAction.LoadCompleted -> if (
        phase == SharedPdfAnnotationSessionPhase.LOADING && bookId == action.bookId
    ) {
        copy(
            phase = SharedPdfAnnotationSessionPhase.READY,
            inkCount = action.inkCount.coerceAtLeast(0),
            textBoxCount = action.textBoxCount.coerceAtLeast(0),
            highlightCount = action.highlightCount.coerceAtLeast(0),
            error = null,
        )
    } else {
        this
    }
    is SharedPdfAnnotationSessionAction.LoadFailed -> if (
        phase == SharedPdfAnnotationSessionPhase.LOADING && bookId == action.bookId
    ) {
        copy(phase = SharedPdfAnnotationSessionPhase.FAILED, error = action.error)
    } else {
        this
    }
}

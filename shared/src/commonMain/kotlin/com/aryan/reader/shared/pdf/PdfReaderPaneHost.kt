package com.aryan.reader.shared.pdf

/**
 * Stable identity for one mounted PDF reader pane.
 *
 * A book can be mounted more than once over its lifetime (for example, after a
 * split pane replaces its document).  [sessionId] lets platform callbacks
 * distinguish those mounts even when the book id is unchanged.  The default
 * session id is intentionally stable for the legacy full-screen reader.
 */
data class SharedPdfReaderSessionKey(
    val bookId: String,
    val sessionId: Long = 0L,
) {
    val canonicalBookId: String
        get() = bookId.trim()

    val isValid: Boolean
        get() = canonicalBookId.isNotEmpty()

    fun matches(other: SharedPdfReaderSessionKey?): Boolean {
        return isValid && other?.isValid == true && sessionId == other.sessionId &&
            canonicalBookId == other.canonicalBookId
    }

    fun matches(bookId: String, sessionId: Long): Boolean {
        return isValid && this.sessionId == sessionId &&
            canonicalBookId == bookId.trim()
    }

    companion object {
        fun fullScreen(bookId: String): SharedPdfReaderSessionKey =
            SharedPdfReaderSessionKey(bookId = bookId.trim())
    }
}

/** Global resources which must not be controlled by an unfocused split pane. */
enum class SharedPdfReaderGlobalResource {
    SYSTEM_UI,
    KEEP_SCREEN_ON,
    KEYBOARD_COMMANDS,
    TTS,
    GLOBAL_MODAL,
    NATIVE_ACTION,
}

/**
 * Ownership supplied by the workspace host.
 *
 * The full-screen wrapper uses the default (focused and active) ownership, so
 * existing behavior is unchanged.  A split workspace should set focus and app
 * activity explicitly for each pane.
 */
data class SharedPdfReaderHostConfig(
    val sessionKey: SharedPdfReaderSessionKey,
    val isFocused: Boolean = true,
    val isAppActive: Boolean = true,
) {
    fun owns(resource: SharedPdfReaderGlobalResource): Boolean {
        // All resources represented here are process/window-global.  Keeping
        // the policy in one place prevents a secondary pane from racing the
        // focused pane as new global actions are added.
        return when (resource) {
            SharedPdfReaderGlobalResource.SYSTEM_UI,
            SharedPdfReaderGlobalResource.KEEP_SCREEN_ON,
            SharedPdfReaderGlobalResource.KEYBOARD_COMMANDS,
            SharedPdfReaderGlobalResource.TTS,
            SharedPdfReaderGlobalResource.GLOBAL_MODAL,
            SharedPdfReaderGlobalResource.NATIVE_ACTION -> sessionKey.isValid && isFocused && isAppActive
        }
    }

    fun acceptsCallback(callbackSession: SharedPdfReaderSessionKey?): Boolean {
        // Durable reader updates remain valid while the app is backgrounded;
        // only process-global resources are gated by app activity.
        return sessionKey.isValid && sessionKey.matches(callbackSession)
    }

    companion object {
        fun fullScreen(bookId: String): SharedPdfReaderHostConfig =
            SharedPdfReaderHostConfig(SharedPdfReaderSessionKey.fullScreen(bookId))
    }
}

/**
 * Durable and transient state associated with one reader mount.
 *
 * The durable reader reducer remains [SharedPdfReaderState].  Viewport and
 * transient fields are kept beside it so a future pane host can persist or
 * discard them deliberately instead of accidentally sharing global state.
 */
data class SharedPdfReaderHostState(
    val sessionKey: SharedPdfReaderSessionKey,
    val readerState: SharedPdfReaderState = SharedPdfReaderState(),
    val viewport: SharedPdfReaderViewport = SharedPdfReaderViewport(),
    val transient: SharedPdfReaderTransientState = SharedPdfReaderTransientState(),
    val revision: Long = 0L,
) {
    fun isCurrent(callbackSession: SharedPdfReaderSessionKey?): Boolean =
        sessionKey.isValid && sessionKey.matches(callbackSession)

    fun withReaderState(
        callbackSession: SharedPdfReaderSessionKey?,
        nextState: SharedPdfReaderState,
    ): SharedPdfReaderHostState? {
        return if (isCurrent(callbackSession)) {
            copy(readerState = nextState, revision = revision + 1L)
        } else {
            null
        }
    }

    fun withViewport(
        callbackSession: SharedPdfReaderSessionKey?,
        nextViewport: SharedPdfReaderViewport,
    ): SharedPdfReaderHostState? {
        return if (isCurrent(callbackSession)) {
            copy(viewport = nextViewport, revision = revision + 1L)
        } else {
            null
        }
    }
}

/** Reader-local state that should be reset when a pane's session changes. */
data class SharedPdfReaderTransientState(
    val searchQuery: String = "",
    val activeSearchResultIndex: Int = -1,
    val activeAnnotationId: String? = null,
    val pendingExternalLink: String? = null,
    val passwordDraft: String = "",
    val dialog: SharedPdfReaderTransientDialog? = null,
)

enum class SharedPdfReaderTransientDialog {
    PASSWORD,
    EXTERNAL_LINK,
    SHARE_FORMAT,
    FILE_INFORMATION,
    SETTINGS,
}

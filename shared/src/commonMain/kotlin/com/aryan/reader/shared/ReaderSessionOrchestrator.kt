package com.aryan.reader.shared

/** Portable intent to open a reader. [sourceToken] is resolved to a Uri/file handle only by the platform adapter. */
data class ReaderOpenCommand(
    val bookId: String,
    val fileType: FileType,
    val sourceToken: String,
    val restore: Boolean = false,
)

object ReaderSessionOrchestrator {
    fun start(current: AppReaderSessionState, command: ReaderOpenCommand): AppReaderSessionState =
        current.reduce(AppReaderSessionAction.OpenStarted(command.bookId, command.fileType))

    fun ready(current: AppReaderSessionState, bookId: String): AppReaderSessionState =
        current.reduce(AppReaderSessionAction.OpenReady(bookId))

    fun failed(
        current: AppReaderSessionState,
        bookId: String,
        message: String?,
        closeReader: Boolean = false,
    ): AppReaderSessionState = current.reduce(
        AppReaderSessionAction.OpenFailed(bookId, message, closeReader),
    )

    fun close(current: AppReaderSessionState): AppReaderSessionState =
        current.reduce(AppReaderSessionAction.Closed)
}

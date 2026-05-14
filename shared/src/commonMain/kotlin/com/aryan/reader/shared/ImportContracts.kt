package com.aryan.reader.shared

enum class SharedImportDecisionStatus {
    IMPORTABLE,
    DUPLICATE,
    UNSUPPORTED
}

data class SharedImportDecision(
    val file: ImportedBookFile,
    val id: String,
    val type: FileType,
    val status: SharedImportDecisionStatus
)

data class SharedImportPlan(
    val decisions: List<SharedImportDecision>,
    val importedBooks: List<BookItem>
) {
    val supportedFiles: List<ImportedBookFile>
        get() = decisions
            .filterNot { it.status == SharedImportDecisionStatus.UNSUPPORTED }
            .map { it.file }

    val importableFiles: List<ImportedBookFile>
        get() = supportedFiles

    val importedFiles: List<ImportedBookFile>
        get() = decisions
            .filter { it.status == SharedImportDecisionStatus.IMPORTABLE }
            .map { it.file }

    val duplicateFiles: List<ImportedBookFile>
        get() = decisions
            .filter { it.status == SharedImportDecisionStatus.DUPLICATE }
            .map { it.file }

    val unsupportedFiles: List<ImportedBookFile>
        get() = decisions
            .filter { it.status == SharedImportDecisionStatus.UNSUPPORTED }
            .map { it.file }

    val importedCount: Int get() = importedBooks.size
    val duplicateCount: Int get() = duplicateFiles.size
    val unsupportedCount: Int get() = unsupportedFiles.size
}

data class SharedImportOutcomeCounts(
    val addedCount: Int = 0,
    val duplicateCount: Int = 0,
    val unsupportedCount: Int = 0,
    val failedCount: Int = 0
)

data class SharedImportFeedback(
    val message: String,
    val isError: Boolean
)

object SharedImportPlanner {
    fun plan(
        files: List<ImportedBookFile>,
        existingBookIds: Set<String>,
        platform: ReaderPlatform,
        nowMillis: Long = currentTimestamp()
    ): SharedImportPlan {
        val seenIds = existingBookIds.toMutableSet()
        val decisions = files.map { file ->
            val id = stableImportId(file)
            val type = SharedFileCapabilities.fileTypeForName(file.name)
            val status = when {
                !SharedFileCapabilities.canOpen(type, platform) -> SharedImportDecisionStatus.UNSUPPORTED
                !seenIds.add(id) -> SharedImportDecisionStatus.DUPLICATE
                else -> SharedImportDecisionStatus.IMPORTABLE
            }
            SharedImportDecision(
                file = file,
                id = id,
                type = type,
                status = status
            )
        }
        val importedBooks = decisions.mapIndexedNotNull { index, decision ->
            if (decision.status != SharedImportDecisionStatus.IMPORTABLE) return@mapIndexedNotNull null
            val file = decision.file
            BookItem(
                id = decision.id,
                path = file.localPath ?: file.uriString,
                type = decision.type,
                displayName = file.name,
                timestamp = nowMillis + index,
                title = file.name.substringBeforeLast('.'),
                fileSize = file.size,
                sourceFolder = file.sourceFolder,
                isRecent = false
            )
        }
        return SharedImportPlan(decisions, importedBooks)
    }

    fun feedbackForCounts(
        counts: SharedImportOutcomeCounts,
        importedMessage: String,
        duplicateMessage: String,
        unsupportedMessage: String,
        failedMessage: String
    ): SharedImportFeedback {
        val message = when {
            counts.addedCount > 0 -> importedMessage
            counts.duplicateCount > 0 -> duplicateMessage
            counts.unsupportedCount > 0 -> unsupportedMessage
            else -> failedMessage
        }
        return SharedImportFeedback(
            message = message,
            isError = counts.addedCount == 0 && counts.duplicateCount == 0
        )
    }

    fun stableImportId(file: ImportedBookFile): String {
        return file.id ?: file.localPath ?: file.uriString ?: file.name
    }
}

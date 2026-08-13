package com.aryan.reader.shared.ios

import com.aryan.reader.shared.BookItem
import com.aryan.reader.shared.MobileReaderSessionRestoreAction
import com.aryan.reader.shared.MobileReaderSessionRestoreCandidate
import com.aryan.reader.shared.mobileReaderSessionRestoreAction
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDefaults

private const val IosLastOpenBookIdDefaultsKey = "reader_ios_last_open_book_id_v1"
private const val IosLastOpenFileTypeDefaultsKey = "reader_ios_last_open_file_type_v1"

internal fun loadIosReaderSessionBook(books: Collection<BookItem>): BookItem? {
    val defaults = NSUserDefaults.standardUserDefaults
    val bookId = defaults.stringForKey(IosLastOpenBookIdDefaultsKey)
    val fileTypeName = defaults.stringForKey(IosLastOpenFileTypeDefaultsKey)
    val book = books.firstOrNull { it.id == bookId }
    val action = mobileReaderSessionRestoreAction(
        persistedBookId = bookId,
        persistedFileTypeName = fileTypeName,
        candidate = book?.let { candidate ->
            MobileReaderSessionRestoreCandidate(
                bookId = candidate.id,
                fileType = candidate.type,
                isAvailable = candidate.isAvailable,
                hasReadableLocation = candidate.path
                    ?.let(NSFileManager.defaultManager::fileExistsAtPath) == true,
            )
        },
    )
    return when (action) {
        MobileReaderSessionRestoreAction.NONE -> null
        MobileReaderSessionRestoreAction.CLEAR_PERSISTED_SESSION -> {
            clearIosReaderSession()
            null
        }
        MobileReaderSessionRestoreAction.RESTORE -> book
    }
}

internal fun persistIosReaderSession(book: BookItem) {
    val defaults = NSUserDefaults.standardUserDefaults
    defaults.setObject(book.id, forKey = IosLastOpenBookIdDefaultsKey)
    defaults.setObject(book.type.name, forKey = IosLastOpenFileTypeDefaultsKey)
}

internal fun clearIosReaderSession() {
    val defaults = NSUserDefaults.standardUserDefaults
    defaults.removeObjectForKey(IosLastOpenBookIdDefaultsKey)
    defaults.removeObjectForKey(IosLastOpenFileTypeDefaultsKey)
}

package com.aryan.reader.shared.ui

import com.aryan.reader.shared.pdf.SharedPdfReaderState
import com.aryan.reader.shared.pdf.SharedPdfRichTextController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal fun CoroutineScope.saveAndCloseSharedMobilePdfReader(
    richTextController: SharedPdfRichTextController,
    readerState: SharedPdfReaderState,
    currentRichTextJson: () -> String,
    onReaderStateChange: (SharedPdfReaderState) -> Unit,
    onClose: () -> Unit,
) {
    launch {
        richTextController.saveImmediate()
        onReaderStateChange(readerState.copy(richTextDocumentJson = currentRichTextJson()))
        onClose()
    }
}

package com.aryan.reader.desktop

import com.aryan.reader.shared.FileType
import com.aryan.reader.shared.reader.SharedEpubBook
import com.aryan.reader.shared.reader.SharedJvmBookLoader
import java.io.File

object DesktopEpubLoader {
    fun load(file: File): SharedEpubBook {
        return SharedJvmBookLoader.load(file, FileType.EPUB)
    }
}

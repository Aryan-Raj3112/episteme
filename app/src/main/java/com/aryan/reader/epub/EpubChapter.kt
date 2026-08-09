package com.aryan.reader.epub

import com.aryan.reader.shared.reader.MobileEpubChapter
import com.aryan.reader.shared.reader.plainTextCharacterCount as sharedPlainTextCharacterCount

typealias EpubChapter = MobileEpubChapter

fun EpubChapter.plainTextCharacterCount(): Int =
    sharedPlainTextCharacterCount()

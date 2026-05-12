package com.aryan.reader.shared.reader

internal actual val SharedReaderDiagnosticsEnabled: Boolean =
    System.getProperty(SharedReaderDiagnosticsProperty)
        ?.trim()
        ?.equals("true", ignoreCase = true) == true

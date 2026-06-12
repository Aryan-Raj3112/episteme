package com.aryan.reader

import com.aryan.reader.shared.detectFontVariant
import com.aryan.reader.shared.familyFilenameSignature
import com.aryan.reader.shared.supportsVariableWeightAxis

const val ReaderFontDiagnosticsTag = "ReaderFontDiag"

fun readerFontDiagnosticSummary(nameWithoutExtension: String): String {
    val variant = nameWithoutExtension.detectFontVariant()
    return "name='$nameWithoutExtension' " +
        "signature='${nameWithoutExtension.familyFilenameSignature()}' " +
        "variant=$variant " +
        "variableWght=${nameWithoutExtension.supportsVariableWeightAxis()}"
}

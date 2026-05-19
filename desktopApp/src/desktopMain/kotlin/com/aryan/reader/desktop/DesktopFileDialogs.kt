package com.aryan.reader.desktop

import com.aryan.reader.shared.FileType
import com.aryan.reader.shared.ImportedBookFile
import com.aryan.reader.shared.ReaderPlatform
import com.aryan.reader.shared.SharedFileCapabilities
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import javax.swing.JFileChooser

internal val DesktopReadableFileTypes = SharedFileCapabilities.readableTypesFor(ReaderPlatform.DESKTOP)
internal val DesktopSyncableFileTypes = SharedFileCapabilities.syncableTypesFor(ReaderPlatform.DESKTOP)
internal val DesktopBookFileTypes = DesktopReadableFileTypes
private val DesktopBookFileDialogPattern = SharedFileCapabilities.all
    .filter { it.type in DesktopBookFileTypes }
    .flatMap { capability -> capability.extensions.map { extension -> "*.$extension" } }
    .joinToString(";")

internal fun desktopBookFileTypesForDialog(): Set<FileType> = DesktopBookFileTypes

internal fun chooseFiles(): List<ImportedBookFile> {
    val dialog = FileDialog(null as Frame?, desktopDialogString("desktop_import_books", "Import books"), FileDialog.LOAD).apply {
        isMultipleMode = true
        isVisible = true
    }
    return dialog.files.orEmpty().map { it.toDesktopImportedBookFile() }
}

internal fun chooseBookFile(): File? {
    val dialog = FileDialog(null as Frame?, desktopDialogString("desktop_open_book", "Open Book"), FileDialog.LOAD).apply {
        file = DesktopBookFileDialogPattern
        isVisible = true
    }
    val directory = dialog.directory ?: return null
    val file = dialog.file ?: return null
    return File(directory, file)
}

internal fun choosePdfFile(): File? {
    val dialog = FileDialog(null as Frame?, desktopDialogString("desktop_open_pdf", "Open PDF"), FileDialog.LOAD).apply {
        file = "*.pdf"
        isVisible = true
    }
    val directory = dialog.directory ?: return null
    val file = dialog.file ?: return null
    return File(directory, file)
}

internal fun chooseFontFile(): File? {
    val dialog = FileDialog(null as Frame?, desktopDialogString("desktop_choose_font", "Choose font"), FileDialog.LOAD).apply {
        file = "*.ttf;*.otf;*.woff2"
        isVisible = true
    }
    val directory = dialog.directory ?: return null
    val file = dialog.file ?: return null
    return File(directory, file)
}

internal fun chooseReaderTextureFile(): File? {
    val dialog = FileDialog(null as Frame?, desktopDialogString("desktop_choose_reader_texture", "Choose reader texture"), FileDialog.LOAD).apply {
        file = "*.png;*.jpg;*.jpeg;*.webp;*.gif;*.bmp"
        isVisible = true
    }
    val directory = dialog.directory ?: return null
    val file = dialog.file ?: return null
    return File(directory, file)
}

internal fun chooseSaveImageFile(defaultFileName: String): File? {
    val dialog = FileDialog(null as Frame?, desktopDialogString("desktop_save_image", "Save image"), FileDialog.SAVE).apply {
        file = defaultFileName
        isVisible = true
    }
    val directory = dialog.directory ?: return null
    val file = dialog.file ?: return null
    return File(directory, file)
}

internal fun chooseFolder(): File? {
    val chooser = JFileChooser().apply {
        dialogTitle = desktopDialogString("desktop_import_folder", "Import folder")
        fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        isAcceptAllFileFilterUsed = false
    }
    return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
        chooser.selectedFile
    } else {
        null
    }
}

private fun desktopDialogString(name: String, fallback: String): String {
    return loadDesktopStringResolver().string(name, fallback)
}

internal fun ImportedBookFile.desktopFileType(): FileType {
    return SharedFileCapabilities.fileTypeForName(name)
}

internal fun File.toDesktopImportedBookFile(sourceFolder: String? = null): ImportedBookFile {
    return ImportedBookFile(
        name = name,
        uriString = null,
        localPath = absolutePath,
        size = length(),
        sourceFolder = sourceFolder
    )
}

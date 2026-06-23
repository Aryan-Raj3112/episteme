package com.aryan.reader.desktop

import com.aryan.reader.shared.FileType
import com.aryan.reader.shared.ImportedBookFile
import com.aryan.reader.shared.ReaderPlatform
import com.aryan.reader.shared.SharedFileCapabilities
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

internal val DesktopReadableFileTypes = SharedFileCapabilities.readableTypesFor(ReaderPlatform.DESKTOP)
internal val DesktopSyncableFileTypes = SharedFileCapabilities.syncableTypesFor(ReaderPlatform.DESKTOP)
internal val DesktopBookFileTypes = DesktopReadableFileTypes
private val DesktopBookFileDialogExtensions = SharedFileCapabilities.all
    .filter { it.type in DesktopBookFileTypes }
    .flatMap { capability -> capability.extensions }
    .distinct()
private val DesktopBookFileDialogPattern = DesktopBookFileDialogExtensions
    .joinToString(";") { extension -> "*.$extension" }

internal fun desktopBookFileTypesForDialog(): Set<FileType> = DesktopBookFileTypes

internal fun chooseFiles(platform: DesktopPlatform = currentDesktopPlatform()): List<ImportedBookFile> {
    val files = if (platform.shouldUseSwingFileChooser()) {
        chooseFilesWithSwing(
            title = desktopDialogString("desktop_import_books", "Import books"),
            extensions = DesktopBookFileDialogExtensions,
            multiple = true
        )
    } else {
        val dialog = FileDialog(null as Frame?, desktopDialogString("desktop_import_books", "Import books"), FileDialog.LOAD).apply {
            file = DesktopBookFileDialogPattern
            isMultipleMode = true
            isVisible = true
        }
        dialog.files.orEmpty().toList()
    }
    return files.map { it.toDesktopImportedBookFile() }
}

internal fun chooseBookFile(platform: DesktopPlatform = currentDesktopPlatform()): File? {
    if (platform.shouldUseSwingFileChooser()) {
        return chooseFilesWithSwing(
            title = desktopDialogString("desktop_open_book", "Open Book"),
            extensions = DesktopBookFileDialogExtensions,
            multiple = false
        ).firstOrNull()
    }
    val dialog = FileDialog(null as Frame?, desktopDialogString("desktop_open_book", "Open Book"), FileDialog.LOAD).apply {
        file = DesktopBookFileDialogPattern
        isVisible = true
    }
    val directory = dialog.directory ?: return null
    val file = dialog.file ?: return null
    return File(directory, file)
}

internal fun choosePdfFile(platform: DesktopPlatform = currentDesktopPlatform()): File? {
    if (platform.shouldUseSwingFileChooser()) {
        return chooseFilesWithSwing(
            title = desktopDialogString("desktop_open_pdf", "Open PDF"),
            extensions = listOf("pdf"),
            multiple = false
        ).firstOrNull()
    }
    val dialog = FileDialog(null as Frame?, desktopDialogString("desktop_open_pdf", "Open PDF"), FileDialog.LOAD).apply {
        file = "*.pdf"
        isVisible = true
    }
    val directory = dialog.directory ?: return null
    val file = dialog.file ?: return null
    return File(directory, file)
}

internal fun chooseFontFile(platform: DesktopPlatform = currentDesktopPlatform()): File? {
    if (platform.shouldUseSwingFileChooser()) {
        return chooseFilesWithSwing(
            title = desktopDialogString("desktop_choose_font", "Choose font"),
            extensions = listOf("ttf", "otf", "woff2"),
            multiple = false
        ).firstOrNull()
    }
    val dialog = FileDialog(null as Frame?, desktopDialogString("desktop_choose_font", "Choose font"), FileDialog.LOAD).apply {
        file = "*.ttf;*.otf;*.woff2"
        isVisible = true
    }
    val directory = dialog.directory ?: return null
    val file = dialog.file ?: return null
    return File(directory, file)
}


internal fun chooseCoverImageFile(platform: DesktopPlatform = currentDesktopPlatform()): File? {
    val extensions = listOf("png", "jpg", "jpeg", "webp", "gif", "bmp")
    if (platform.shouldUseSwingFileChooser()) {
        return chooseFilesWithSwing(
            title = desktopDialogString("action_change_cover", "Change cover"),
            extensions = extensions,
            multiple = false
        ).firstOrNull()
    }
    val dialog = FileDialog(null as Frame?, desktopDialogString("action_change_cover", "Change cover"), FileDialog.LOAD).apply {
        file = "*.png;*.jpg;*.jpeg;*.webp;*.gif;*.bmp"
        isVisible = true
    }
    val directory = dialog.directory ?: return null
    val file = dialog.file ?: return null
    return File(directory, file)
}

internal fun chooseReaderTextureFile(platform: DesktopPlatform = currentDesktopPlatform()): File? {
    if (platform.shouldUseSwingFileChooser()) {
        return chooseFilesWithSwing(
            title = desktopDialogString("desktop_choose_reader_texture", "Choose reader texture"),
            extensions = listOf("png", "jpg", "jpeg", "webp", "gif", "bmp"),
            multiple = false
        ).firstOrNull()
    }
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

internal fun chooseSaveAnnotationExportFile(defaultFileName: String): File? {
    val dialog = FileDialog(null as Frame?, desktopDialogString("action_export_annotations", "Export annotations"), FileDialog.SAVE).apply {
        file = defaultFileName
        isVisible = true
    }
    val directory = dialog.directory ?: return null
    val file = dialog.file ?: return null
    return File(directory, file)
}
internal fun chooseSaveBookFile(defaultFileName: String): File? {
    val dialog = FileDialog(null as Frame?, desktopDialogString("action_save_copy_to_device", "Save copy to device"), FileDialog.SAVE).apply {
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

internal fun DesktopPlatform.shouldUseSwingFileChooser(): Boolean = isLinux

private fun chooseFilesWithSwing(
    title: String,
    extensions: List<String>,
    multiple: Boolean
): List<File> {
    val chooser = JFileChooser().apply {
        dialogTitle = title
        fileSelectionMode = JFileChooser.FILES_ONLY
        isMultiSelectionEnabled = multiple
        if (extensions.isNotEmpty()) {
            fileFilter = FileNameExtensionFilter(
                extensions.joinToString(separator = ", ") { extension -> ".$extension" },
                *extensions.toTypedArray()
            )
            isAcceptAllFileFilterUsed = false
        }
    }
    return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
        if (multiple) chooser.selectedFiles.orEmpty().toList() else listOfNotNull(chooser.selectedFile)
    } else {
        emptyList()
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

package com.aryan.reader.desktop

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.aryan.reader.shared.ImportedBookFile
import com.aryan.reader.shared.ReaderPlatform
import com.aryan.reader.shared.SharedFileCapabilities
import com.aryan.reader.shared.ui.readerQuantityString
import com.aryan.reader.shared.ui.readerString
import java.awt.Component
import java.awt.Container
import java.awt.EventQueue
import java.awt.datatransfer.DataFlavor
import java.awt.dnd.DnDConstants
import java.awt.dnd.DropTarget
import java.awt.dnd.DropTargetAdapter
import java.awt.dnd.DropTargetDragEvent
import java.awt.dnd.DropTargetDropEvent
import java.awt.dnd.DropTargetEvent
import java.io.File

internal data class DesktopDropImportState(
    val active: Boolean = false,
    val supportedCount: Int = 0,
    val totalFileCount: Int = 0,
    val hasFilePayload: Boolean = false
)

@Composable
internal fun DesktopFileDropTarget(
    window: Component?,
    onFilesDropped: (List<ImportedBookFile>) -> Unit,
    onDragStateChange: (DesktopDropImportState) -> Unit
) {
    val onFilesDroppedState = rememberUpdatedState(onFilesDropped)
    val onDragStateChangeState = rememberUpdatedState(onDragStateChange)

    DisposableEffect(window) {
        if (window == null) {
            onDispose { }
        } else {
            val installedTargets = mutableListOf<InstalledDropTarget>()
            var disposed = false
            var lastDragState = DesktopDropImportState()

            fun publishDragState(state: DesktopDropImportState) {
                if (state == lastDragState) return
                lastDragState = state
                onDragStateChangeState.value(state)
            }

            val listener = object : DropTargetAdapter() {
                override fun dragEnter(event: DropTargetDragEvent) {
                    handleDrag(event)
                }

                override fun dragOver(event: DropTargetDragEvent) {
                    handleDrag(event)
                }

                override fun dragExit(event: DropTargetEvent) {
                    publishDragState(DesktopDropImportState())
                }

                override fun drop(event: DropTargetDropEvent) {
                    if (!event.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                        event.rejectDrop()
                        publishDragState(DesktopDropImportState())
                        return
                    }
                    event.acceptDrop(DnDConstants.ACTION_COPY)
                    val files = event.transferable.localDraggedFiles().filter { it.isFile }
                    if (files.isEmpty()) {
                        event.dropComplete(false)
                        publishDragState(DesktopDropImportState())
                        return
                    }

                    onFilesDroppedState.value(files.map { it.toDesktopImportedBookFile() })
                    event.dropComplete(true)
                    publishDragState(DesktopDropImportState())
                }

                private fun handleDrag(event: DropTargetDragEvent) {
                    val hasFilePayload = event.isDataFlavorSupported(DataFlavor.javaFileListFlavor)
                    publishDragState(
                        DesktopDropImportState(
                            active = true,
                            hasFilePayload = hasFilePayload
                        )
                    )
                    if (hasFilePayload) {
                        event.acceptDrag(DnDConstants.ACTION_COPY)
                    } else {
                        event.rejectDrag()
                    }
                }
            }
            window.installDropTargets(listener, installedTargets)
            EventQueue.invokeLater {
                if (!disposed) {
                    window.installDropTargets(listener, installedTargets)
                }
            }

            onDispose {
                disposed = true
                installedTargets.forEach { installed ->
                    runCatching { installed.dropTarget.removeDropTargetListener(listener) }
                    installed.component.dropTarget = installed.previous
                }
                publishDragState(DesktopDropImportState())
            }
        }
    }
}

private data class InstalledDropTarget(
    val component: Component,
    val previous: DropTarget?,
    val dropTarget: DropTarget
)

private fun Component.installDropTargets(
    listener: DropTargetAdapter,
    installedTargets: MutableList<InstalledDropTarget>
) {
    collectDropTargetComponents()
        .distinct()
        .filterNot { component -> installedTargets.any { it.component == component } }
        .forEach { component ->
            val previous = component.dropTarget
            val target = DropTarget(component, DnDConstants.ACTION_COPY, listener, true)
            installedTargets += InstalledDropTarget(component, previous, target)
        }
}

private fun Component.collectDropTargetComponents(): List<Component> {
    val collected = mutableListOf<Component>()

    fun visit(component: Component) {
        collected += component
        if (component is Container) {
            component.components.forEach(::visit)
        }
    }

    visit(this)
    return collected
}

@Composable
internal fun DesktopDropImportOverlay(state: DesktopDropImportState) {
    if (!state.active) return

    val hasSupportedFiles = state.supportedCount > 0
    val title = when {
        hasSupportedFiles -> readerQuantityString(
            "desktop_drop_import_file_count",
            state.supportedCount,
            "Drop to import %1\$d file",
            "Drop to import %1\$d files",
            state.supportedCount
        )
        state.hasFilePayload -> readerString("desktop_drop_supported_files_to_import", "Drop supported files to import")
        else -> readerString("desktop_drop_files_to_import", "Drop files to import")
    }
    val body = if (hasSupportedFiles) {
        val skipped = state.totalFileCount - state.supportedCount
        if (skipped > 0) {
            readerQuantityString(
                "desktop_unsupported_import_file_count",
                skipped,
                "%1\$d unsupported file will be skipped.",
                "%1\$d unsupported files will be skipped.",
                skipped
            )
        } else {
            readerString("desktop_release_add_library", "Release to add to your library.")
        }
    } else {
        SharedFileCapabilities.supportedFormatsLabel(ReaderPlatform.DESKTOP)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(20f)
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.36f)),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = 8.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f))
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 30.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

private fun java.awt.datatransfer.Transferable.localDraggedFiles(): List<File> {
    if (!isDataFlavorSupported(DataFlavor.javaFileListFlavor)) return emptyList()
    return runCatching {
        @Suppress("UNCHECKED_CAST")
        (getTransferData(DataFlavor.javaFileListFlavor) as? List<*>)
            .orEmpty()
            .filterIsInstance<File>()
    }.getOrDefault(emptyList())
}

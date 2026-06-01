package com.aryan.reader.desktop

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.aryan.reader.shared.pdf.PdfPageBounds
import com.aryan.reader.shared.pdf.SharedPdfHighlighterPalette
import com.aryan.reader.shared.ui.SharedHsvColorPickerDialog
import com.aryan.reader.shared.ui.SharedSelectionMenuRect
import com.aryan.reader.shared.ui.SharedSelectionMenuSize
import com.aryan.reader.shared.ui.SharedSelectionMenuViewport
import com.aryan.reader.shared.ui.readerString
import com.aryan.reader.shared.ui.sharedSelectionMenuPlacement
import kotlin.math.roundToInt

internal data class DesktopPdfTextSelection(
    val text: String,
    val lineBounds: List<PdfPageBounds>,
    val startIndex: Int,
    val endIndex: Int
)

private data class DesktopPdfSelectionCanvasBounds(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val centerX: Float get() = (left + right) / 2f
}

private fun DesktopPdfTextSelection.canvasBounds(canvasSize: IntSize): DesktopPdfSelectionCanvasBounds? {
    val validBounds = lineBounds.filter { it.right > it.left && it.bottom > it.top }
    if (validBounds.isEmpty() || canvasSize.width <= 0 || canvasSize.height <= 0) return null
    return DesktopPdfSelectionCanvasBounds(
        left = validBounds.minOf { it.left } * canvasSize.width,
        top = validBounds.minOf { it.top } * canvasSize.height,
        right = validBounds.maxOf { it.right } * canvasSize.width,
        bottom = validBounds.maxOf { it.bottom } * canvasSize.height
    )
}

internal fun DesktopPdfTextSelection.menuAnchor(
    canvasSize: IntSize,
    fallback: Offset?
): Offset {
    val bounds = canvasBounds(canvasSize) ?: return fallback ?: Offset.Zero
    return Offset(x = bounds.centerX, y = bounds.top)
}

private fun DesktopPdfTextSelection.startHandleOffset(canvasSize: IntSize): Offset? {
    if (canvasSize.width <= 0 || canvasSize.height <= 0) return null
    val first = lineBounds.firstOrNull { it.right > it.left && it.bottom > it.top } ?: return null
    return Offset(
        x = first.left * canvasSize.width,
        y = first.bottom * canvasSize.height
    )
}

private fun DesktopPdfTextSelection.endHandleOffset(canvasSize: IntSize): Offset? {
    if (canvasSize.width <= 0 || canvasSize.height <= 0) return null
    val last = lineBounds.lastOrNull { it.right > it.left && it.bottom > it.top } ?: return null
    return Offset(
        x = last.right * canvasSize.width,
        y = last.bottom * canvasSize.height
    )
}

internal fun DesktopPdfTextSelection.handleAt(
    point: Offset,
    canvasSize: IntSize
): DesktopPdfSelectionHandle? {
    val start = startHandleOffset(canvasSize)
    val end = endHandleOffset(canvasSize)

    fun Offset.containsHandlePoint(): Boolean {
        val halfWidth = DesktopPdfSelectionHandleTouchWidthPx / 2f
        return point.x in (x - halfWidth)..(x + halfWidth) &&
            point.y in (y - DesktopPdfSelectionHandleTouchTopPx)..(y + DesktopPdfSelectionHandleTouchBottomPx)
    }

    return when {
        start != null && start.containsHandlePoint() -> DesktopPdfSelectionHandle.START
        end != null && end.containsHandlePoint() -> DesktopPdfSelectionHandle.END
        else -> null
    }
}

@Composable
internal fun PdfSearchHighlightOverlay(
    bounds: List<PdfPageBounds>,
    canvasSize: IntSize,
    color: Color
) {
    if (bounds.isEmpty() || canvasSize.width <= 0 || canvasSize.height <= 0) return
    Canvas(Modifier.fillMaxSize()) {
        bounds.forEach { rect ->
            drawRect(
                color = color,
                topLeft = Offset(rect.left * canvasSize.width, rect.top * canvasSize.height),
                size = androidx.compose.ui.geometry.Size(
                    (rect.right - rect.left) * canvasSize.width,
                    (rect.bottom - rect.top) * canvasSize.height
                )
            )
        }
    }
}

@Composable
internal fun PdfTextSelectionOverlay(
    selection: DesktopPdfTextSelection?,
    canvasSize: IntSize
) {
    val bounds = selection?.lineBounds.orEmpty()
    if (bounds.isEmpty()) return
    Canvas(Modifier.fillMaxSize()) {
        bounds.forEach { rect ->
            drawRect(
                color = Color(0x663B82F6),
                topLeft = Offset(rect.left * canvasSize.width, rect.top * canvasSize.height),
                size = androidx.compose.ui.geometry.Size(
                    (rect.right - rect.left) * canvasSize.width,
                    (rect.bottom - rect.top) * canvasSize.height
                )
            )
        }
    }
}

@Composable
internal fun PdfTextSelectionHandles(
    selection: DesktopPdfTextSelection?,
    canvasSize: IntSize,
    activeHandle: DesktopPdfSelectionHandle?
) {
    selection ?: return
    if (canvasSize.width <= 0 || canvasSize.height <= 0) return
    val density = LocalDensity.current
    val handleSize = 24.dp
    val handleWidthPx = with(density) { handleSize.toPx() }
    val start = selection.startHandleOffset(canvasSize)
    val end = selection.endHandleOffset(canvasSize)
    val handleColor = MaterialTheme.colorScheme.primary

    fun Modifier.handleOffset(position: Offset): Modifier = offset {
        IntOffset(
            x = (position.x - handleWidthPx / 2f).roundToInt(),
            y = position.y.roundToInt()
        )
    }

    Box(Modifier.fillMaxSize()) {
        start?.let { position ->
            Icon(
                imageVector = DesktopPdfSelectionMenuIcons.Teardrop,
                contentDescription = readerString("desktop_selection_start_handle", "Selection start handle"),
                tint = handleColor.copy(alpha = if (activeHandle == DesktopPdfSelectionHandle.END) 0.72f else 1f),
                modifier = Modifier
                    .handleOffset(position)
                    .size(handleSize)
                    .graphicsLayer {
                        rotationZ = 30f
                        transformOrigin = TransformOrigin(0.5f, 0f)
                    }
            )
        }
        end?.let { position ->
            Icon(
                imageVector = DesktopPdfSelectionMenuIcons.Teardrop,
                contentDescription = readerString("desktop_selection_end_handle", "Selection end handle"),
                tint = handleColor.copy(alpha = if (activeHandle == DesktopPdfSelectionHandle.START) 0.72f else 1f),
                modifier = Modifier
                    .handleOffset(position)
                    .size(handleSize)
                    .graphicsLayer {
                        rotationZ = -30f
                        transformOrigin = TransformOrigin(0.5f, 0f)
                    }
            )
        }
    }
}

private object DesktopPdfSelectionMenuIcons {
    val Copy = vector(
        name = "DesktopPdfSelectionCopy",
        pathData = "M360,720Q327,720 303.5,696.5Q280,673 280,640L280,160Q280,127 303.5,103.5Q327,80 360,80L720,80Q753,80 776.5,103.5Q800,127 800,160L800,640Q800,673 776.5,696.5Q753,720 720,720L360,720ZM360,640L720,640Q720,640 720,640Q720,640 720,640L720,160Q720,160 720,160Q720,160 720,160L360,160Q360,160 360,160Q360,160 360,160L360,640Q360,640 360,640Q360,640 360,640ZM200,880Q167,880 143.5,856.5Q120,833 120,800L120,240L200,240L200,800Q200,800 200,800Q200,800 200,800L640,800L640,880L200,880ZM360,640Q360,640 360,640Q360,640 360,640L360,160Q360,160 360,160Q360,160 360,160L360,160Q360,160 360,160Q360,160 360,160L360,640Q360,640 360,640Q360,640 360,640Z"
    )
    val Dictionary = vector(
        name = "DesktopPdfSelectionDictionary",
        pathData = "M160,569L205,569L228,503L332,503L356,569L400,569L303,311L257,311L160,569ZM241,466L279,359L281,359L319,466L241,466ZM560,396L560,328Q593,314 627.5,307Q662,300 700,300Q726,300 751,304Q776,308 800,314L800,378Q776,369 751.5,364.5Q727,360 700,360Q662,360 627,369.5Q592,379 560,396ZM560,616L560,548Q593,534 627.5,527Q662,520 700,520Q726,520 751,524Q776,528 800,534L800,598Q776,589 751.5,584.5Q727,580 700,580Q662,580 627,589Q592,598 560,616ZM560,506L560,438Q593,424 627.5,417Q662,410 700,410Q726,410 751,414Q776,418 800,424L800,488Q776,479 751.5,474.5Q727,470 700,470Q662,470 627,479.5Q592,489 560,506ZM260,640Q307,640 351.5,650.5Q396,661 440,682L440,288Q399,264 353,252Q307,240 260,240Q224,240 188.5,247Q153,254 120,268Q120,268 120,268Q120,268 120,268L120,664Q120,664 120,664Q120,664 120,664Q155,652 189.5,646Q224,640 260,640ZM520,682Q564,661 608.5,650.5Q653,640 700,640Q736,640 770.5,646Q805,652 840,664Q840,664 840,664Q840,664 840,664L840,268Q840,268 840,268Q840,268 840,268Q807,254 771.5,247Q736,240 700,240Q653,240 607,252Q561,264 520,288L520,682ZM480,800Q432,762 376,741Q320,720 260,720Q218,720 177.5,731Q137,742 100,762Q79,773 59.5,761Q40,749 40,726L40,244Q40,233 45.5,223Q51,213 62,208Q108,184 158,172Q208,160 260,160Q318,160 373.5,175Q429,190 480,220Q531,190 586.5,175Q642,160 700,160Q752,160 802,172Q852,184 898,208Q909,213 914.5,223Q920,233 920,244L920,726Q920,749 900.5,761Q881,773 860,762Q823,742 782.5,731Q742,720 700,720Q640,720 584,741Q528,762 480,800ZM280,461Q280,461 280,461Q280,461 280,461Q280,461 280,461Q280,461 280,461L280,461Q280,461 280,461Q280,461 280,461Q280,461 280,461Q280,461 280,461Q280,461 280,461Q280,461 280,461L280,461Q280,461 280,461Q280,461 280,461Z"
    )
    val Search = vector(
        name = "DesktopPdfSelectionSearch",
        pathData = "M784,840L532,588Q502,612 463,626Q424,640 380,640Q271,640 195.5,564.5Q120,489 120,380Q120,271 195.5,195.5Q271,120 380,120Q489,120 564.5,195.5Q640,271 640,380Q640,424 626,463Q612,502 588,532L840,784L784,840ZM380,560Q455,560 507.5,507.5Q560,455 560,380Q560,305 507.5,252.5Q455,200 380,200Q305,200 252.5,252.5Q200,305 200,380Q200,455 252.5,507.5Q305,560 380,560Z"
    )
    val Teardrop = vector(
        name = "DesktopPdfSelectionTeardrop",
        pathData = "M480,860Q347,860 253.5,768Q160,676 160,544Q160,481 184.5,423.5Q209,366 254,322L480,100L706,322Q751,366 775.5,423.5Q800,481 800,544Q800,676 706.5,768Q613,860 480,860Z"
    )

    private fun vector(name: String, pathData: String): ImageVector {
        return ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 960f,
            viewportHeight = 960f
        ).apply {
            addPath(
                pathData = PathParser().parsePathString(pathData).toNodes(),
                fill = SolidColor(Color.Black)
            )
        }.build()
    }
}

internal enum class DesktopPdfSelectionHandle {
    START,
    END
}

@Composable
internal fun PdfSelectionMenu(
    selection: DesktopPdfTextSelection?,
    menuOffset: Offset?,
    canvasSize: IntSize,
    highlighterPalette: List<Int> = SharedPdfHighlighterPalette.defaultColors,
    onHighlighterPaletteChange: (SharedPdfHighlighterPalette) -> Unit,
    onCopy: () -> Unit,
    onHighlight: (Int) -> Unit,
    onSearch: () -> Unit,
    onDefine: () -> Unit,
    onSpeak: () -> Unit,
    showDefine: Boolean,
    showSpeak: Boolean,
    showSearch: Boolean,
    onClear: () -> Unit
) {
    selection ?: return
    val anchor = menuOffset ?: return
    val selectionBounds = selection.canvasBounds(canvasSize)
    val paletteColors = remember(highlighterPalette) {
        SharedPdfHighlighterPalette(highlighterPalette).sanitized().colors
    }
    val density = LocalDensity.current
    var editingHighlighterSlot by remember(selection.startIndex, selection.endIndex, paletteColors) {
        mutableStateOf<Int?>(null)
    }
    var editingHighlighterDraftColors by remember(selection.startIndex, selection.endIndex, paletteColors) {
        mutableStateOf<List<Int>>(emptyList())
    }
    val actions = buildList {
        add(PdfSelectionMenuAction(readerString("action_copy", "Copy"), DesktopPdfSelectionMenuIcons.Copy, onCopy))
        if (showDefine) add(PdfSelectionMenuAction(readerString("action_define", "Define"), DesktopPdfSelectionMenuIcons.Dictionary, onDefine))
        if (showSpeak) add(PdfSelectionMenuAction(readerString("label_speak", "Speak"), Icons.AutoMirrored.Filled.VolumeUp, onSpeak))
        if (showSearch) add(PdfSelectionMenuAction(readerString("action_search", "Search"), DesktopPdfSelectionMenuIcons.Search, onSearch))
        add(PdfSelectionMenuAction(readerString("action_clear", "Clear"), Icons.Default.Close, onClear, isDestructive = true))
    }

    fun highlighterDraftColors(): List<Int> {
        return editingHighlighterDraftColors.ifEmpty { paletteColors }
    }

    fun updateHighlighterDraft(slotIndex: Int, color: Color): List<Int> {
        val nextColors = highlighterDraftColors().toMutableList()
        if (slotIndex in nextColors.indices) {
            nextColors[slotIndex] = color.copy(alpha = SharedPdfHighlighterPalette.DefaultAlpha / 255f).toArgb()
            editingHighlighterDraftColors = nextColors
        }
        return nextColors
    }

    fun openHighlighterEditor(slotIndex: Int) {
        if (editingHighlighterSlot == null) {
            editingHighlighterDraftColors = paletteColors
        }
        editingHighlighterSlot = slotIndex
    }

    val actionRowCount = ((actions.size + 2) / 3).coerceAtLeast(1)
    val popupWidthPx = with(density) { PdfSelectionMenuWidth.toPx() }
    val estimatedHeightPx = with(density) {
        PdfSelectionMenuPaletteHeight.toPx() +
            (actionRowCount * PdfSelectionMenuActionRowHeight.toPx())
    }
    val placement = sharedSelectionMenuPlacement(
        viewport = SharedSelectionMenuViewport(canvasSize.width, canvasSize.height),
        popup = SharedSelectionMenuSize(
            width = popupWidthPx.roundToInt(),
            height = estimatedHeightPx.roundToInt()
        ),
        selection = if (selectionBounds != null) {
            SharedSelectionMenuRect(
                left = selectionBounds.left,
                top = selectionBounds.top,
                right = selectionBounds.right,
                bottom = selectionBounds.bottom
            )
        } else {
            SharedSelectionMenuRect(
                left = anchor.x,
                top = anchor.y,
                right = anchor.x,
                bottom = anchor.y
            )
        },
        marginPx = with(density) { PdfSelectionMenuMargin.toPx() },
        gapPx = with(density) { PdfSelectionMenuAnchorGap.toPx() }
    )
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopStart) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp,
            shadowElevation = 10.dp,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.offset {
                IntOffset(placement.x, placement.y)
            }
        ) {
            Column(
                modifier = Modifier
                    .widthIn(min = 180.dp, max = 220.dp)
                    .padding(bottom = 6.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    paletteColors.forEach { colorArgb ->
                        Surface(
                            modifier = Modifier
                                .padding(horizontal = 4.dp)
                                .size(28.dp)
                                .clickable { onHighlight(colorArgb) },
                            color = Color(colorArgb),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.28f)),
                            content = {}
                        )
                    }
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .size(28.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                Brush.sweepGradient(
                                    listOf(
                                        Color.Red,
                                        Color.Yellow,
                                        Color.Green,
                                        Color.Cyan,
                                        Color.Blue,
                                        Color.Magenta,
                                        Color.Red
                                    )
                                )
                            )
                            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.32f), RoundedCornerShape(16.dp))
                            .clickable { openHighlighterEditor(0) }
                    )
                }
                HorizontalDivider()
                actions.chunked(3).forEach { rowActions ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp, vertical = 3.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        rowActions.forEach { action ->
                            val tint = if (action.isDestructive) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                            Column(
                                modifier = Modifier
                                    .width(58.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { action.onClick() }
                                    .padding(vertical = 6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = action.icon,
                                    contentDescription = action.label,
                                    tint = tint,
                                    modifier = Modifier.size(22.dp)
                                )
                                Text(
                                    action.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = tint,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        repeat(3 - rowActions.size) {
                            Spacer(modifier = Modifier.width(58.dp))
                        }
                    }
                }
            }
        }
    }
    editingHighlighterSlot?.let { requestedSlot ->
        val draftColors = highlighterDraftColors()
        val safeDraftColors = draftColors.ifEmpty { SharedPdfHighlighterPalette.defaultColors }
        val slot = requestedSlot.coerceIn(0, safeDraftColors.lastIndex)
        val initialColor = remember(slot) { Color(safeDraftColors[slot]).copy(alpha = 1f) }
        SharedHsvColorPickerDialog(
            initialColor = initialColor,
            title = readerString("desktop_highlight_color_format", "Highlight color %1\$d", slot + 1),
            onDismiss = { editingHighlighterSlot = null },
            onSave = { color ->
                val nextColors = updateHighlighterDraft(slot, color)
                onHighlighterPaletteChange(
                    SharedPdfHighlighterPalette(nextColors).sanitized()
                )
                editingHighlighterSlot = null
            },
            resetColor = Color(SharedPdfHighlighterPalette.defaultColors.getOrElse(slot) {
                SharedPdfHighlighterPalette.defaultColors.first()
            }).copy(alpha = 1f),
            stateKey = slot,
            onLiveColorChange = { color ->
                updateHighlighterDraft(slot, color)
            }
        ) { liveColor ->
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    highlighterDraftColors().forEachIndexed { index, argb ->
                        val color = if (index == slot) liveColor else Color(argb).copy(alpha = 1f)
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(21.dp))
                                .background(color)
                                .border(
                                    width = if (index == slot) 3.dp else 1.dp,
                                    color = if (index == slot) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                                    },
                                    shape = RoundedCornerShape(21.dp)
                                )
                                .clickable { openHighlighterEditor(index) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${index + 1}",
                                color = if (color.luminance() > 0.5f) Color.Black else Color.White,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class PdfSelectionMenuAction(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
    val isDestructive: Boolean = false
)

private val PdfSelectionMenuWidth = 220.dp
private val PdfSelectionMenuPaletteHeight = 54.dp
private val PdfSelectionMenuActionRowHeight = 66.dp
private val PdfSelectionMenuAnchorGap = 16.dp
private val PdfSelectionMenuMargin = 6.dp
private const val DesktopPdfSelectionHandleTouchWidthPx = 44f
private const val DesktopPdfSelectionHandleTouchTopPx = 8f
private const val DesktopPdfSelectionHandleTouchBottomPx = 40f

package com.aryan.reader.shared.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aryan.reader.shared.ReaderTheme
import kotlin.math.max
import kotlin.math.min

enum class SharedReaderThemeColorTarget { BACKGROUND, TEXT }

data class SharedReaderThemeBuilderLabels(
    val customTexturedDefault: String,
    val customSolidDefault: String,
    val newTheme: String,
    val editTheme: String,
    val themeName: String,
    val previewQuote: String,
    val previewAuthor: String,
    val lowContrastWarning: String,
    val pageColor: String,
    val textColor: String,
    val cancel: String,
    val save: String,
)

@Composable
fun SharedReaderThemeBuilder(
    initialTheme: ReaderTheme?,
    isTexturedMode: Boolean,
    globalTextureAlpha: Float,
    defaultTextureId: String?,
    labels: SharedReaderThemeBuilderLabels,
    newThemeId: () -> String,
    onSave: (ReaderTheme) -> Unit,
    onCancel: () -> Unit,
    texturePreview: @Composable (textureId: String, alpha: Float, modifier: Modifier) -> Unit,
    texturePickerContent: @Composable (
        selectedTextureId: String?,
        onTextureSelected: (String?) -> Unit,
    ) -> Unit,
    colorPickerContent: @Composable (
        target: SharedReaderThemeColorTarget,
        initialColor: Color,
        backgroundColor: Color,
        textColor: Color,
        onDismiss: () -> Unit,
        onColorChanged: (Color) -> Unit,
    ) -> Unit,
) {
    val defaultName = if (isTexturedMode) labels.customTexturedDefault else labels.customSolidDefault
    var name by remember(initialTheme?.id, isTexturedMode, defaultName) {
        mutableStateOf(initialTheme?.name ?: defaultName)
    }
    var backgroundColor by remember(initialTheme?.id) {
        mutableStateOf(initialTheme?.backgroundColor ?: Color(0xFFF5F5F5))
    }
    var textColor by remember(initialTheme?.id) {
        mutableStateOf(initialTheme?.textColor ?: Color(0xFF111111))
    }
    var textureId by remember(initialTheme?.id, isTexturedMode) {
        mutableStateOf(initialTheme?.textureId ?: defaultTextureId)
    }
    var editingColorTarget by remember { mutableStateOf<SharedReaderThemeColorTarget?>(null) }
    val contrast = readerThemeContrastRatio(backgroundColor, textColor)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.85f)
            .padding(16.dp),
    ) {
        Text(
            if (initialTheme == null) labels.newTheme else labels.editTheme,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(16.dp))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(labels.themeName) },
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                singleLine = true,
            )
            Surface(
                modifier = Modifier.fillMaxWidth().height(120.dp).padding(vertical = 8.dp),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                color = backgroundColor,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Box(Modifier.fillMaxSize()) {
                    if (isTexturedMode && textureId != null) {
                        texturePreview(textureId.orEmpty(), globalTextureAlpha, Modifier.fillMaxSize())
                    }
                    Column(Modifier.padding(16.dp).fillMaxWidth()) {
                        Text(labels.previewQuote, color = textColor, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            labels.previewAuthor,
                            color = textColor,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.End,
                        )
                    }
                }
            }
            AnimatedVisibility(contrast < 4.5f) {
                Text(
                    labels.lowContrastWarning,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                SharedReaderThemeColorSwatch(
                    labels.pageColor,
                    backgroundColor,
                    { editingColorTarget = SharedReaderThemeColorTarget.BACKGROUND },
                    Modifier.weight(1f),
                )
                SharedReaderThemeColorSwatch(
                    labels.textColor,
                    textColor,
                    { editingColorTarget = SharedReaderThemeColorTarget.TEXT },
                    Modifier.weight(1f),
                )
            }
            if (isTexturedMode) {
                Spacer(Modifier.height(24.dp))
                texturePickerContent(textureId) { textureId = it }
            }
            Spacer(Modifier.height(16.dp))
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 16.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onCancel) { Text(labels.cancel, color = MaterialTheme.colorScheme.primary) }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = {
                    onSave(
                        ReaderTheme(
                            id = initialTheme?.id ?: newThemeId(),
                            name = name,
                            backgroundColor = backgroundColor,
                            textColor = textColor,
                            isDark = backgroundColor.luminance() < 0.5f,
                            textureId = if (isTexturedMode) textureId else null,
                            isCustom = true,
                        ),
                    )
                },
            ) {
                Text(labels.save, color = MaterialTheme.colorScheme.onPrimary)
            }
        }
    }

    editingColorTarget?.let { target ->
        colorPickerContent(
            target,
            if (target == SharedReaderThemeColorTarget.BACKGROUND) backgroundColor else textColor,
            backgroundColor,
            textColor,
            { editingColorTarget = null },
            { newColor ->
                if (target == SharedReaderThemeColorTarget.BACKGROUND) backgroundColor = newColor else textColor = newColor
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SharedReaderThemeColorSwatch(
    label: String,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(bottom = 8.dp))
        Surface(
            onClick = onClick,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
            color = color,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) {}
    }
}

private fun readerThemeContrastRatio(first: Color, second: Color): Float {
    val high = max(first.luminance(), second.luminance())
    val low = min(first.luminance(), second.luminance())
    return (high + 0.05f) / (low + 0.05f)
}

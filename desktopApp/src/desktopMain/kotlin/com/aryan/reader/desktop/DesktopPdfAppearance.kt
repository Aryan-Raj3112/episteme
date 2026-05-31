package com.aryan.reader.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.aryan.reader.shared.BuiltInPdfReaderThemes
import com.aryan.reader.shared.PdfDisplayMode
import com.aryan.reader.shared.ReaderTheme
import com.aryan.reader.shared.reader.ReaderSettings

internal enum class DesktopPdfInspectorTab(val title: String) {
    APPEARANCE("Appearance"),
    APP_THEME("App theme"),
    VISUAL("Visual"),
    MARKUP("Markup"),
    TTS("TTS")
}

internal data class DesktopPdfThemeStyle(
    val theme: ReaderTheme,
    val viewerBackgroundColor: Color,
    val pageBackgroundColor: Color,
    val colorFilter: ColorFilter?,
    val textureBitmap: ImageBitmap?,
    val textureAlpha: Float,
    val textureBlendMode: BlendMode
)

@Composable
internal fun DesktopPdfThemedPageImage(
    bitmap: ImageBitmap,
    contentDescription: String,
    themeStyle: DesktopPdfThemeStyle,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.background(themeStyle.pageBackgroundColor)) {
        val textureBitmap = themeStyle.textureBitmap
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .semantics { this.contentDescription = contentDescription }
        ) {
            drawImage(
                image = bitmap,
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(bitmap.width, bitmap.height),
                dstOffset = IntOffset.Zero,
                dstSize = IntSize(
                    size.width.toInt().coerceAtLeast(1),
                    size.height.toInt().coerceAtLeast(1)
                ),
                colorFilter = themeStyle.colorFilter,
                filterQuality = FilterQuality.High
            )
            if (textureBitmap != null && themeStyle.textureAlpha > 0f) {
                drawRect(
                    brush = ShaderBrush(ImageShader(textureBitmap, TileMode.Repeated, TileMode.Repeated)),
                    size = size,
                    blendMode = themeStyle.textureBlendMode,
                    alpha = themeStyle.textureAlpha
                )
            }
        }
    }
}

internal fun ReaderSettings?.toDesktopPdfReaderSettings(): ReaderSettings {
    val defaults = DesktopDefaultPdfReaderSettings
    val settings = this ?: defaults
    val themeId = settings.themeId
    val hasPdfTheme = BuiltInPdfReaderThemes.any { it.id == themeId }
    val hasCustomColors = settings.backgroundColorArgb != null && settings.textColorArgb != null
    return settings.copy(
        themeId = when {
            themeId == null -> "no_theme"
            hasPdfTheme || hasCustomColors -> themeId
            else -> "no_theme"
        }
    )
}

internal fun ReaderSettings.toDesktopPdfThemeStyle(displayMode: PdfDisplayMode): DesktopPdfThemeStyle {
    val theme = toDesktopPdfTheme()
    val pageBackground = desktopPdfPageBackgroundColor(theme, displayMode)
    val isDarkTexture = theme.isDark || theme.id == "reverse"
    return DesktopPdfThemeStyle(
        theme = theme,
        viewerBackgroundColor = pageBackground,
        pageBackgroundColor = pageBackground,
        colorFilter = theme.toDesktopPdfColorFilter(),
        textureBitmap = DesktopReaderTextures.imageBitmapFor(textureId),
        textureAlpha = if (textureId == null) 0f else textureAlpha.coerceIn(0f, 1f),
        textureBlendMode = if (isDarkTexture) BlendMode.Screen else BlendMode.Multiply
    )
}

private fun ReaderSettings.toDesktopPdfTheme(): ReaderTheme {
    BuiltInPdfReaderThemes.firstOrNull { it.id == themeId }?.let { return it }
    val background = backgroundColorArgb?.toComposeColor()
    val text = textColorArgb?.toComposeColor()
    return if (background != null && text != null) {
        ReaderTheme(
            id = themeId ?: "desktop_pdf_custom",
            name = "Custom",
            backgroundColor = background,
            textColor = text,
            isDark = darkMode,
            textureId = textureId,
            isCustom = true
        )
    } else {
        BuiltInPdfReaderThemes.first()
    }
}

private fun ReaderTheme.toDesktopPdfColorFilter(): ColorFilter? {
    return when (id) {
        "no_theme", "system" -> null
        "reverse" -> {
            val colorMatrix = floatArrayOf(
                -1f, 0f, 0f, 0f, 255f,
                0f, -1f, 0f, 0f, 255f,
                0f, 0f, -1f, 0f, 255f,
                0f, 0f, 0f, 1f, 0f
            )
            ColorFilter.colorMatrix(ColorMatrix(colorMatrix))
        }
        else -> {
            if (!backgroundColor.isSpecified || !textColor.isSpecified) return null
            val bgR = backgroundColor.red * 255f
            val bgG = backgroundColor.green * 255f
            val bgB = backgroundColor.blue * 255f
            val fgR = textColor.red * 255f
            val fgG = textColor.green * 255f
            val fgB = textColor.blue * 255f
            val dr = (bgR - fgR) / 255f
            val dg = (bgG - fgG) / 255f
            val db = (bgB - fgB) / 255f
            val lumR = 0.2126f
            val lumG = 0.7152f
            val lumB = 0.0722f
            val colorMatrix = floatArrayOf(
                dr * lumR, dr * lumG, dr * lumB, 0f, fgR,
                dg * lumR, dg * lumG, dg * lumB, 0f, fgG,
                db * lumR, db * lumG, db * lumB, 0f, fgB,
                0f, 0f, 0f, 1f, 0f
            )
            ColorFilter.colorMatrix(ColorMatrix(colorMatrix))
        }
    }
}

@Composable
internal fun DesktopPdfInspectorSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        content()
    }
}

@Composable
internal fun DesktopPdfVisualOptionSwitch(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private fun Long.toComposeColor(): Color {
    return Color(this and 0xFFFFFFFFL)
}

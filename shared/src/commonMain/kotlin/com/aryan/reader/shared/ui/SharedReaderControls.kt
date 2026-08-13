package com.aryan.reader.shared.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.FilterChip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aryan.reader.shared.BuiltInReaderThemes
import com.aryan.reader.shared.CustomFontItem
import com.aryan.reader.shared.fontFaceSummary
import com.aryan.reader.shared.groupByFamily
import com.aryan.reader.shared.hasVariableWeightFace
import com.aryan.reader.shared.ReaderAiByokSettings
import com.aryan.reader.shared.ReaderExtrasState
import com.aryan.reader.shared.ReaderAction
import com.aryan.reader.shared.ReaderTexture
import com.aryan.reader.shared.ReaderTextureFilePrefix
import com.aryan.reader.shared.ReaderTheme
import com.aryan.reader.shared.ReaderTool
import com.aryan.reader.shared.ReaderToolbarPreferences
import com.aryan.reader.shared.ReaderTtsReplacementPreferences
import com.aryan.reader.shared.readerTextureDisplayName
import com.aryan.reader.shared.resetReaderFormatSettings
import com.aryan.reader.shared.sanitizeCustomReaderThemes
import com.aryan.reader.shared.shouldShowPageWidthFormatControl
import com.aryan.reader.shared.toReaderSettings
import com.aryan.reader.shared.withHorizontalReaderMargin
import com.aryan.reader.shared.withVerticalReaderMargin
import com.aryan.reader.shared.reader.ReaderSessionState
import com.aryan.reader.shared.reader.ReaderSettings
import com.aryan.reader.shared.reader.SharedReaderTextAlign
import kotlin.math.roundToInt

@Composable
internal fun SharedReaderControlPanel(
    session: ReaderSessionState,
    toolbarPreferences: ReaderToolbarPreferences,
    appThemeControls: (@Composable () -> Unit)?,
    onPickCustomFont: (() -> String?)?,
    customFonts: List<CustomFontItem>,
    extrasState: ReaderExtrasState,
    aiByokSettings: ReaderAiByokSettings,
    cloudTtsControlsAvailable: Boolean,
    onCloudTtsClearCache: () -> Unit,
    onCloudTtsVoiceChange: (String) -> Unit,
    ttsReplacementPreferences: ReaderTtsReplacementPreferences,
    ttsReplacementBookId: String,
    onTtsReplacementPreferencesChange: (ReaderTtsReplacementPreferences) -> Unit,
    customReaderThemes: List<ReaderTheme>,
    onCustomReaderThemesChange: (List<ReaderTheme>) -> Unit,
    readerCustomTextureIds: List<String>,
    readerTexturePreviewContent: (@Composable (String, Modifier) -> Unit)?,
    onImportReaderTexture: ((ReaderSettings) -> ReaderSettings?)?,
    onReaderAction: (ReaderAction) -> Unit
) {
    val sections = toolbarPreferences.availableReaderControlSections(
        cloudTtsControlsAvailable = cloudTtsControlsAvailable,
        appThemeControlsAvailable = appThemeControls != null
    )
    if (sections.isEmpty()) return
    val defaultSection = sections.first()
    var selectedSection by remember(sections) { mutableStateOf(defaultSection) }
    LaunchedEffect(sections) {
        if (selectedSection !in sections) {
            selectedSection = defaultSection
        }
    }
    val activeSection = selectedSection.takeIf { it in sections } ?: defaultSection

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        SharedReaderControlSectionTabs(
            sections = sections,
            activeSection = activeSection,
            onSectionSelected = { selectedSection = it }
        )
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(top = 12.dp),
            verticalArrangement = Arrangement.spacedBy(SharedUiTokens.contentGap)
        ) {
            item {
                when (activeSection) {
                    ReaderControlSection.APP_THEME -> appThemeControls?.invoke()

                    ReaderControlSection.FORMAT -> SharedReaderFormatControls(
                        settings = session.reader.settings,
                        onPickCustomFont = onPickCustomFont,
                        customFonts = customFonts,
                        onReaderAction = onReaderAction
                    )

                    ReaderControlSection.THEME -> SharedReaderThemeControls(
                        settings = session.reader.settings,
                        customTextureIds = readerCustomTextureIds,
                        onImportTexture = onImportReaderTexture,
                        customThemes = customReaderThemes,
                        onCustomThemesChange = onCustomReaderThemesChange,
                        texturePreviewContent = readerTexturePreviewContent,
                        onSettingsChange = { onReaderAction(ReaderAction.SettingsChanged(it)) }
                    )

                    ReaderControlSection.VISUAL -> SharedReaderVisualOptionsControls(
                        settings = session.reader.settings,
                        onReaderAction = onReaderAction
                    )

                    ReaderControlSection.TTS -> SharedReaderTtsControls(
                        extrasState = extrasState,
                        aiByokSettings = aiByokSettings,
                        toolbarPreferences = toolbarPreferences,
                        cloudTtsControlsAvailable = cloudTtsControlsAvailable,
                        onCloudTtsClearCache = onCloudTtsClearCache,
                        onCloudTtsVoiceChange = onCloudTtsVoiceChange,
                        ttsReplacementPreferences = ttsReplacementPreferences,
                        ttsReplacementBookId = ttsReplacementBookId,
                        onTtsReplacementPreferencesChange = onTtsReplacementPreferencesChange
                    )

                }
            }
        }
    }
}

@Composable
internal fun SharedReaderControlSectionTabs(
    sections: List<ReaderControlSection>,
    activeSection: ReaderControlSection,
    onSectionSelected: (ReaderControlSection) -> Unit
) {
    val activeIndex = sections.indexOf(activeSection).coerceAtLeast(0)
    ScrollableTabRow(
        selectedTabIndex = activeIndex,
        edgePadding = 0.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        sections.forEach { section ->
            Tab(
                selected = activeSection == section,
                onClick = { onSectionSelected(section) },
                icon = {
                    Icon(
                        section.icon(),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                },
                text = {
                    Text(
                        section.localizedTitle(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            )
        }
    }
}

internal enum class ReaderControlSection {
    APP_THEME,
    FORMAT,
    THEME,
    VISUAL,
    TTS
}

internal fun ReaderControlSection.icon(): ImageVector {
    return when (this) {
        ReaderControlSection.APP_THEME -> Icons.Default.Palette
        ReaderControlSection.FORMAT -> Icons.Default.TextFields
        ReaderControlSection.THEME -> Icons.Default.Palette
        ReaderControlSection.VISUAL -> Icons.Default.Tune
        ReaderControlSection.TTS -> Icons.AutoMirrored.Filled.VolumeUp
    }
}

@Composable
internal fun ReaderControlSection.localizedTitle(): String {
    return when (this) {
        ReaderControlSection.APP_THEME -> readerString("app_theme_title", "App theme")
        ReaderControlSection.FORMAT -> readerString("desktop_typography", "Typography")
        ReaderControlSection.THEME -> readerString("reading_themes", "Reading Themes")
        ReaderControlSection.VISUAL -> readerString("visual_options_title", "Visual")
        ReaderControlSection.TTS -> readerString("menu_tts_settings", "TTS")
    }
}

internal fun ReaderToolbarPreferences.availableReaderControlSections(
    cloudTtsControlsAvailable: Boolean,
    appThemeControlsAvailable: Boolean = false
): List<ReaderControlSection> {
    return buildList {
        if (isVisible(ReaderTool.FORMAT)) add(ReaderControlSection.FORMAT)
        if (isVisible(ReaderTool.THEME)) add(ReaderControlSection.THEME)
        if (appThemeControlsAvailable) add(ReaderControlSection.APP_THEME)
        if (isVisible(ReaderTool.VISUAL_OPTIONS) || isVisible(ReaderTool.READING_MODE)) add(ReaderControlSection.VISUAL)
        if (
            (cloudTtsControlsAvailable && (
                isVisible(ReaderTool.TTS_CONTROLS) ||
                    isVisible(ReaderTool.TTS_SETTINGS)
                )) ||
            isVisible(ReaderTool.TTS_REPLACEMENTS)
        ) {
            add(ReaderControlSection.TTS)
        }
    }
}

@Composable
fun SharedReaderFormatControls(
    settings: ReaderSettings,
    onPickCustomFont: (() -> String?)?,
    customFonts: List<CustomFontItem>,
    onReaderAction: (ReaderAction) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(
                onClick = {
                    onReaderAction(ReaderAction.SettingsChanged(settings.resetReaderFormatSettings()))
                }
            ) {
                Text(readerString("action_reset", "Reset"))
            }
        }

        SharedReaderPanelSection(readerString("section_font_alignment", "Font & Alignment")) {
                val customFontName = settings.customFontPath
                    ?.substringAfterLast('/')
                    ?.substringAfterLast('\\')
                    ?.takeIf { it.isNotBlank() }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        modifier = Modifier
                            .width(42.dp)
                            .height(42.dp)
                            .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Aa", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(customFontName ?: settings.fontFamily, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(readerString("select_font", "Font"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(
                        enabled = onPickCustomFont != null,
                        onClick = {
                            onPickCustomFont?.invoke()?.takeIf { it.isNotBlank() }?.let { path ->
                                onReaderAction(
                                    ReaderAction.SettingsChanged(
                                        settings.copy(
                                            fontFamily = path.substringAfterLast('/').substringAfterLast('\\'),
                                            customFontPath = path
                                        )
                                    )
                                )
                            }
                        }
                    ) {
                        Text(readerString("action_choose", "Choose"))
                    }
                }

                SharedReaderChoiceRow {
                    val fontFamilies = listOf(
                        "Default" to readerString("label_default", "Default"),
                        "Serif" to readerString("font_serif", "Serif"),
                        "Sans" to readerString("font_sans", "Sans"),
                        "Mono" to readerString("font_mono", "Mono")
                    )
                    fontFamilies.forEach { (family, label) ->
                        FilterChip(
                            selected = settings.customFontPath == null && settings.fontFamily == family,
                            onClick = {
                                onReaderAction(
                                    ReaderAction.SettingsChanged(settings.copy(fontFamily = family, customFontPath = null))
                                )
                            },
                            label = { Text(label) }
                        )
                    }
                    if (settings.customFontPath != null) {
                        TextButton(
                            onClick = {
                                onReaderAction(
                                    ReaderAction.SettingsChanged(settings.copy(fontFamily = "Default", customFontPath = null))
                                )
                            }
                        ) {
                            Text(readerString("action_clear", "Clear"))
                        }
                    }
                }

                val activeCustomFontFamilies = customFonts.filterNot { it.isDeleted }.groupByFamily()
                if (activeCustomFontFamilies.isNotEmpty()) {
                    Text(
                        readerString("desktop_imported_fonts", "Imported fonts"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    SharedReaderChoiceRow {
                        activeCustomFontFamilies.forEach { family ->
                            val isSelected = family.variants.any { it.font.path == settings.customFontPath }
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    val baseFont = family.variants.firstOrNull { it.variant?.weight == FontWeight.Normal && it.variant?.style == androidx.compose.ui.text.font.FontStyle.Normal }?.font ?: family.variants.first().font
                                    onReaderAction(
                                        ReaderAction.SettingsChanged(
                                            settings.copy(
                                                fontFamily = family.familyName,
                                                customFontPath = baseFont.path
                                            )
                                        )
                                    )
                                },
                                label = {
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text(family.familyName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        val variantsStr = buildString {
                                            append(family.fontFaceSummary())
                                            if (family.hasVariableWeightFace()) append(" - Variable weight")
                                        }
                                        if (variantsStr.isNotBlank()) {
                                            Text(
                                                "($variantsStr)",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                            )
                        }
                    }
                }

                SharedReaderChoiceRow {
                    FilterChip(
                        selected = settings.textAlign == SharedReaderTextAlign.START,
                        onClick = {
                            onReaderAction(ReaderAction.SettingsChanged(settings.copy(textAlign = SharedReaderTextAlign.START)))
                        },
                        label = { Text(readerString("label_default", "Default")) }
                    )
                    FilterChip(
                        selected = settings.textAlign == SharedReaderTextAlign.LEFT,
                        onClick = {
                            onReaderAction(ReaderAction.SettingsChanged(settings.copy(textAlign = SharedReaderTextAlign.LEFT)))
                        },
                        label = { Text(readerString("label_left", "Left")) }
                    )
                    FilterChip(
                        selected = settings.textAlign == SharedReaderTextAlign.RIGHT,
                        onClick = {
                            onReaderAction(ReaderAction.SettingsChanged(settings.copy(textAlign = SharedReaderTextAlign.RIGHT)))
                        },
                        label = { Text(readerString("label_right", "Right")) }
                    )
                    FilterChip(
                        selected = settings.textAlign == SharedReaderTextAlign.JUSTIFY,
                        onClick = {
                            onReaderAction(ReaderAction.SettingsChanged(settings.copy(textAlign = SharedReaderTextAlign.JUSTIFY)))
                        },
                        label = { Text(readerString("label_justify", "Justify")) }
                    )
                    FilterChip(
                        selected = settings.textAlign == SharedReaderTextAlign.CENTER,
                        onClick = {
                            onReaderAction(ReaderAction.SettingsChanged(settings.copy(textAlign = SharedReaderTextAlign.CENTER)))
                        },
                        label = { Text(readerString("desktop_align_center", "Center")) }
                    )
                }
            }

            SharedReaderPanelSection(readerString("desktop_layout_spacing", "Layout & Spacing")) {
                SharedReaderSettingSlider(
                    label = readerString("label_font_size", "Font size"),
                    value = settings.fontSize.toFloat(),
                    onValueChange = { value ->
                        onReaderAction(ReaderAction.SettingsChanged(settings.copy(fontSize = value.roundToInt())))
                    },
                    valueRange = 14f..30f,
                    valueLabel = settings.fontSize.toString(),
                    stepSize = 1f,
                    formatValue = { it.roundToInt().toString() }
                )
                SharedReaderSettingSlider(
                    label = readerString("label_line_height", "Line height"),
                    value = settings.lineSpacing,
                    onValueChange = { value ->
                        onReaderAction(ReaderAction.SettingsChanged(settings.copy(lineSpacing = value)))
                    },
                    valueRange = 1.1f..2.1f,
                    valueLabel = "${settings.lineSpacing.formatTwoDecimals()}x",
                    formatValue = { "${it.formatTwoDecimals()}x" }
                )
                SharedReaderSettingSlider(
                    label = readerString("label_paragraph_gap", "Paragraph gap"),
                    value = settings.paragraphSpacing,
                    onValueChange = { value ->
                        onReaderAction(ReaderAction.SettingsChanged(settings.copy(paragraphSpacing = value)))
                    },
                    valueRange = 0.5f..2.5f,
                    valueLabel = "${settings.paragraphSpacing.formatTwoDecimals()}x",
                    formatValue = { "${it.formatTwoDecimals()}x" }
                )
                SharedReaderSettingSlider(
                    label = readerString("label_image_size", "Image size"),
                    value = settings.imageScale,
                    onValueChange = { value ->
                        onReaderAction(ReaderAction.SettingsChanged(settings.copy(imageScale = value)))
                    },
                    valueRange = 0.5f..2.0f,
                    valueLabel = "${settings.imageScale.formatTwoDecimals()}x",
                    formatValue = { "${it.formatTwoDecimals()}x" }
                )
                SharedReaderSettingSlider(
                    label = readerString("label_horizontal_margin", "Horizontal margin"),
                    value = settings.resolvedHorizontalMargin.toFloat(),
                    onValueChange = { value ->
                        onReaderAction(
                            ReaderAction.SettingsChanged(
                                settings.withHorizontalReaderMargin(value.roundToInt())
                            )
                        )
                    },
                    valueRange = 0f..160f,
                    valueLabel = settings.resolvedHorizontalMargin.toString(),
                    stepSize = 4f,
                    formatValue = { it.roundToInt().toString() }
                )
                SharedReaderSettingSlider(
                    label = readerString("label_vertical_margin", "Vertical margin"),
                    value = settings.resolvedVerticalMargin.toFloat(),
                    onValueChange = { value ->
                        onReaderAction(
                            ReaderAction.SettingsChanged(
                                settings.withVerticalReaderMargin(value.roundToInt())
                            )
                        )
                    },
                    valueRange = 0f..160f,
                    valueLabel = settings.resolvedVerticalMargin.toString(),
                    stepSize = 4f,
                    formatValue = { it.roundToInt().toString() }
                )
                if (settings.shouldShowPageWidthFormatControl()) {
                    SharedReaderSettingSlider(
                        label = readerString("desktop_page_width", "Page width"),
                        value = settings.pageWidth.toFloat(),
                        onValueChange = { value ->
                            onReaderAction(ReaderAction.SettingsChanged(settings.copy(pageWidth = value.roundToInt())))
                        },
                        valueRange = 520f..1100f,
                        valueLabel = settings.pageWidth.toString(),
                        stepSize = 20f,
                        formatValue = { it.roundToInt().toString() }
                    )
                }
        }
    }
}

@Composable
fun SharedReaderThemeControls(
    settings: ReaderSettings,
    builtInThemes: List<ReaderTheme> = BuiltInReaderThemes,
    customThemes: List<ReaderTheme> = emptyList(),
    onCustomThemesChange: ((List<ReaderTheme>) -> Unit)? = null,
    customTextureIds: List<String> = emptyList(),
    onImportTexture: ((ReaderSettings) -> ReaderSettings?)? = null,
    texturePreviewContent: (@Composable (String, Modifier) -> Unit)? = null,
    onSettingsChange: (ReaderSettings) -> Unit
) {
    val activeCustomThemes = remember(customThemes) { customThemes.sanitizeCustomReaderThemes() }
    val allThemes = remember(builtInThemes, activeCustomThemes) { builtInThemes + activeCustomThemes }
    val selectedTheme = allThemes.firstOrNull { it.id == settings.themeId }
    var textured by remember(settings.themeId, settings.textureId, builtInThemes, activeCustomThemes) {
        mutableStateOf((selectedTheme?.textureId ?: settings.textureId) != null)
    }
    var editingColorTarget by remember { mutableStateOf<ReaderThemeColorTarget?>(null) }
    var themeBuilderState by remember { mutableStateOf<ReaderCustomThemeBuilderState?>(null) }
    val activeBuiltInThemes = builtInThemes.filter { (it.textureId != null) == textured }
    val activeSavedThemes = activeCustomThemes.filter { (it.textureId != null) == textured }
    val visibleCustomTextureIds = remember(customTextureIds, settings.textureId) {
        buildList {
            addAll(customTextureIds.distinct())
            settings.textureId
                ?.takeIf { it.startsWith(ReaderTextureFilePrefix) && it !in this }
                ?.let(::add)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        SharedReaderPanelSection(readerString("reading_themes", "Reading Themes")) {
            SharedReaderChoiceRow {
                FilterChip(
                    selected = !textured,
                    onClick = { textured = false },
                    label = { Text(readerString("desktop_solid", "Solid")) }
                )
                FilterChip(
                    selected = textured,
                    onClick = { textured = true },
                    label = { Text(readerString("theme_textured", "Textured")) }
                )
            }
            activeBuiltInThemes.chunked(3).forEach { rowThemes ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    rowThemes.forEach { theme ->
                        SharedReaderThemeChoice(
                            theme = theme,
                            selected = settings.themeId == theme.id || (settings.themeId == null && theme.id == "system"),
                            onSelected = { onSettingsChange(theme.toReaderSettings(settings)) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    repeat(3 - rowThemes.size) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
            SharedReaderCustomThemeSection(
                activeThemes = activeSavedThemes,
                currentThemeId = settings.themeId,
                canEditThemes = onCustomThemesChange != null,
                onCreateTheme = {
                    themeBuilderState = ReaderCustomThemeBuilderState(
                        initialTheme = null,
                        isTextured = textured
                    )
                },
                onThemeSelected = { theme -> onSettingsChange(theme.toReaderSettings(settings)) },
                onThemeEdit = { theme ->
                    themeBuilderState = ReaderCustomThemeBuilderState(
                        initialTheme = theme,
                        isTextured = theme.textureId != null
                    )
                },
                onThemeDelete = { theme ->
                    val updated = activeCustomThemes.filterNot { it.id == theme.id }.sanitizeCustomReaderThemes()
                    onCustomThemesChange?.invoke(updated)
                    if (settings.themeId == theme.id) {
                        builtInThemes.firstOrNull()?.let { fallback ->
                            onSettingsChange(fallback.toReaderSettings(settings))
                        }
                    }
                }
            )
        }

        SharedReaderPanelSection(readerString("desktop_custom_colors", "Custom colors")) {
            val backgroundColor = settings.readerBackgroundColor(allThemes)
            val textColor = settings.readerTextColor(allThemes)
            Surface(
                modifier = Modifier.fillMaxWidth().height(76.dp),
                color = backgroundColor,
                contentColor = textColor,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(readerString("desktop_custom_theme_preview", "Custom theme preview"), fontWeight = FontWeight.SemiBold, maxLines = 1)
                    Text(readerString("desktop_page_and_text_colors", "Page and text colors"), style = MaterialTheme.typography.bodySmall, maxLines = 1)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                SharedReaderThemeColorButton(
                    label = readerString("desktop_page", "Page"),
                    color = backgroundColor,
                    onClick = { editingColorTarget = ReaderThemeColorTarget.BACKGROUND },
                    modifier = Modifier.weight(1f)
                )
                SharedReaderThemeColorButton(
                    label = readerString("content_desc_text", "Text"),
                    color = textColor,
                    onClick = { editingColorTarget = ReaderThemeColorTarget.TEXT },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        if (textured) {
            SharedReaderPanelSection(readerString("theme_texture", "Texture")) {
                val textureChoices = buildList {
                    add(SharedReaderTextureChoiceModel(textureId = null, label = readerString("label_none", "None")))
                    if (onImportTexture != null) {
                        add(
                            SharedReaderTextureChoiceModel(
                                textureId = null,
                                label = readerString("action_import", "Import"),
                                icon = Icons.Default.Add,
                                isImportAction = true
                            )
                        )
                    }
                    ReaderTexture.entries.forEach { texture ->
                        add(SharedReaderTextureChoiceModel(textureId = texture.id, label = texture.displayName))
                    }
                    visibleCustomTextureIds.forEach { textureId ->
                        add(SharedReaderTextureChoiceModel(textureId = textureId, label = readerTextureDisplayName(textureId)))
                    }
                }
                textureChoices.chunked(3).forEach { rowChoices ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        rowChoices.forEach { choice ->
                            SharedReaderTextureChoice(
                                choice = choice,
                                selected = !choice.isImportAction && settings.textureId == choice.textureId,
                                texturePreviewContent = texturePreviewContent,
                                onSelected = {
                                    if (choice.isImportAction) {
                                        onImportTexture?.invoke(settings)?.let(onSettingsChange)
                                    } else {
                                        onSettingsChange(settings.copy(textureId = choice.textureId))
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        repeat(3 - rowChoices.size) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
                if (settings.textureId != null) {
                    SharedReaderSettingSlider(
                        label = readerString("desktop_texture_strength", "Texture strength"),
                        value = settings.textureAlpha.coerceIn(0f, 1f),
                        onValueChange = { value ->
                            onSettingsChange(settings.copy(textureAlpha = value))
                        },
                        valueRange = 0f..1f,
                        valueLabel = "${(settings.textureAlpha.coerceIn(0f, 1f) * 100).roundToInt()}%",
                        stepSize = 0.01f,
                        formatValue = { "${(it.coerceIn(0f, 1f) * 100).roundToInt()}%" }
                    )
                }
            }
        }
    }

    editingColorTarget?.let { target ->
        val backgroundColor = settings.readerBackgroundColor(allThemes)
        val textColor = settings.readerTextColor(allThemes)
        val initialColor = when (target) {
            ReaderThemeColorTarget.BACKGROUND -> backgroundColor
            ReaderThemeColorTarget.TEXT -> textColor
        }
        SharedHsvColorPickerDialog(
            initialColor = initialColor,
            title = target.localizedTitle(),
            onDismiss = { editingColorTarget = null },
            onSave = { color ->
                val nextBackground = if (target == ReaderThemeColorTarget.BACKGROUND) color else backgroundColor
                val nextText = if (target == ReaderThemeColorTarget.TEXT) color else textColor
                onSettingsChange(
                    settings.copy(
                        themeId = ReaderCustomThemeId,
                        darkMode = nextBackground.luminance() < 0.45f,
                        backgroundColorArgb = nextBackground.toArgb().toLong(),
                        textColorArgb = nextText.toArgb().toLong()
                    )
                )
                editingColorTarget = null
            }
        ) { color ->
            val previewBackground = if (target == ReaderThemeColorTarget.BACKGROUND) color else backgroundColor
            val previewText = if (target == ReaderThemeColorTarget.TEXT) color else textColor
            Surface(
                modifier = Modifier.fillMaxWidth().height(64.dp),
                shape = RoundedCornerShape(10.dp),
                color = previewBackground,
                contentColor = previewText,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(readerString("theme_color_live_preview", "Live preview"), fontWeight = FontWeight.Bold)
                    Text(readerString("desktop_page_and_text_colors", "Page and text colors"), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }

    themeBuilderState?.let { builderState ->
        SharedReaderCustomThemeDialog(
            initialTheme = builderState.initialTheme,
            isTexturedMode = builderState.isTextured,
            customThemes = activeCustomThemes,
            customTextureIds = visibleCustomTextureIds,
            onImportTexture = onImportTexture,
            texturePreviewContent = texturePreviewContent,
            onDismiss = { themeBuilderState = null },
            onSave = { theme ->
                val updated = if (builderState.initialTheme != null) {
                    activeCustomThemes.map { if (it.id == theme.id) theme else it }
                } else {
                    activeCustomThemes + theme.copy(
                        id = nextReaderCustomThemeId(activeCustomThemes),
                        isCustom = true
                    )
                }.sanitizeCustomReaderThemes()
                val savedTheme = updated.firstOrNull { it.id == theme.id } ?: updated.lastOrNull() ?: theme
                onCustomThemesChange?.invoke(updated)
                onSettingsChange(savedTheme.toReaderSettings(settings))
                themeBuilderState = null
            }
        )
    }
}

internal data class SharedReaderTextureChoiceModel(
    val textureId: String?,
    val label: String,
    val icon: ImageVector? = null,
    val isImportAction: Boolean = false
)

@Composable
internal fun SharedReaderTextureChoice(
    choice: SharedReaderTextureChoiceModel,
    selected: Boolean,
    texturePreviewContent: (@Composable (String, Modifier) -> Unit)?,
    onSelected: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onSelected,
        modifier = modifier.height(104.dp),
        shape = RoundedCornerShape(10.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(7.dp)),
                contentAlignment = Alignment.Center
            ) {
                when {
                    choice.textureId != null && texturePreviewContent != null -> {
                        texturePreviewContent(choice.textureId, Modifier.fillMaxSize())
                    }
                    choice.textureId != null -> {
                        SharedReaderTextureFallbackPreview(choice.textureId, Modifier.fillMaxSize())
                    }
                    else -> {
                        val previewColor = if (choice.isImportAction) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(previewColor),
                            contentAlignment = Alignment.Center
                        ) {
                            choice.icon?.let { icon ->
                                Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
                            }
                        }
                    }
                }
                if (selected) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .size(22.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }
            Text(
                choice.label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
internal fun SharedReaderTextureFallbackPreview(
    textureId: String,
    modifier: Modifier = Modifier
) {
    val texture = ReaderTexture.entries.firstOrNull { it.id == textureId }
    val baseColor = when (texture) {
        ReaderTexture.NATURAL_BLACK,
        ReaderTexture.GREY_WASH,
        ReaderTexture.CLASSY_FABRIC,
        ReaderTexture.SLATE -> Color(0xFF2C2C2C)
        ReaderTexture.RETINA_WOOD,
        ReaderTexture.LIGHT_VENEER -> Color(0xFFF0D4AD)
        ReaderTexture.CANVAS -> Color(0xFFE9E2D2)
        ReaderTexture.EINK -> Color(0xFFF3F3EE)
        ReaderTexture.RETRO_INTRO -> Color(0xFFF5DFB6)
        else -> Color(0xFFF7F1E5)
    }
    val accentColor = if (baseColor.luminance() > 0.5f) {
        Color.Black.copy(alpha = 0.12f)
    } else {
        Color.White.copy(alpha = 0.14f)
    }
    Box(
        modifier = modifier.background(
            Brush.linearGradient(
                listOf(
                    baseColor,
                    baseColor.copy(alpha = 0.84f),
                    accentColor
                )
            )
        ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "Aa",
            color = if (baseColor.luminance() > 0.5f) Color(0xFF24231F) else Color(0xFFEDE7DA),
            fontWeight = FontWeight.Bold
        )
    }
}

internal const val ReaderCustomThemeId = "custom_reader"

internal data class ReaderCustomThemeBuilderState(
    val initialTheme: ReaderTheme?,
    val isTextured: Boolean
)

@Composable
internal fun SharedReaderCustomThemeSection(
    activeThemes: List<ReaderTheme>,
    currentThemeId: String?,
    canEditThemes: Boolean,
    onCreateTheme: () -> Unit,
    onThemeSelected: (ReaderTheme) -> Unit,
    onThemeEdit: (ReaderTheme) -> Unit,
    onThemeDelete: (ReaderTheme) -> Unit
) {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            readerString("theme_my_themes", "My themes"),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f)
        )
        if (canEditThemes) {
            IconButton(onClick = onCreateTheme, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Add, contentDescription = readerString("theme_new", "New"))
            }
        }
    }
    if (activeThemes.isEmpty()) {
        Text(
            readerString("theme_no_custom", "No custom themes yet"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    } else {
        activeThemes.chunked(3).forEach { rowThemes ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                rowThemes.forEach { theme ->
                    SharedReaderThemeChoice(
                        theme = theme,
                        selected = currentThemeId == theme.id,
                        onSelected = { onThemeSelected(theme) },
                        onEdit = if (canEditThemes) ({ onThemeEdit(theme) }) else null,
                        onDelete = if (canEditThemes) ({ onThemeDelete(theme) }) else null,
                        modifier = Modifier.weight(1f)
                    )
                }
                repeat(3 - rowThemes.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
internal fun SharedReaderCustomThemeDialog(
    initialTheme: ReaderTheme?,
    isTexturedMode: Boolean,
    customThemes: List<ReaderTheme>,
    customTextureIds: List<String>,
    onImportTexture: ((ReaderSettings) -> ReaderSettings?)?,
    texturePreviewContent: (@Composable (String, Modifier) -> Unit)?,
    onDismiss: () -> Unit,
    onSave: (ReaderTheme) -> Unit
) {
    val defaultName = if (isTexturedMode) {
        readerString("theme_custom_textured_default", "Custom textured")
    } else {
        readerString("theme_custom_solid_default", "Custom solid")
    }
    val dialogCustomTextureIds = remember(customTextureIds, initialTheme?.textureId) {
        buildList {
            addAll(customTextureIds.distinct())
            initialTheme?.textureId
                ?.takeIf { it.startsWith(ReaderTextureFilePrefix) && it !in this }
                ?.let(::add)
        }
    }
    var name by remember(initialTheme?.id, isTexturedMode) { mutableStateOf(initialTheme?.name ?: defaultName) }
    var backgroundColor by remember(initialTheme?.id) { mutableStateOf(initialTheme?.backgroundColor ?: Color(0xFFF5F5F5)) }
    var textColor by remember(initialTheme?.id) { mutableStateOf(initialTheme?.textColor ?: Color(0xFF111111)) }
    var textureId by remember(initialTheme?.id, dialogCustomTextureIds) {
        mutableStateOf(initialTheme?.textureId ?: dialogCustomTextureIds.firstOrNull())
    }
    var editingColorTarget by remember { mutableStateOf<ReaderThemeColorTarget?>(null) }
    val contrast = readerChromeThemeContrastRatio(backgroundColor, textColor)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (initialTheme == null) {
                    readerString("theme_new", "New theme")
                } else {
                    readerString("theme_edit", "Edit theme")
                }
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                SharedStableOutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(readerString("theme_name", "Theme name")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    selectionKey = initialTheme?.id ?: defaultName
                )
                Surface(
                    modifier = Modifier.fillMaxWidth().height(116.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = backgroundColor,
                    contentColor = textColor,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Box(Modifier.fillMaxSize()) {
                        if (isTexturedMode && textureId != null) {
                            if (texturePreviewContent != null) {
                                texturePreviewContent(textureId.orEmpty(), Modifier.fillMaxSize())
                            } else {
                                SharedReaderTextureFallbackPreview(textureId.orEmpty(), Modifier.fillMaxSize())
                            }
                            Box(Modifier.fillMaxSize().background(backgroundColor.copy(alpha = 0.54f)))
                        }
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(readerString("theme_preview_quote", "Reading should feel easy."), fontWeight = FontWeight.SemiBold)
                            Text(readerString("theme_preview_author", "Theme preview"), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                if (contrast < 4.5f) {
                    Text(
                        readerString("theme_low_contrast_warning", "Low contrast may be hard to read."),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    SharedReaderThemeColorButton(
                        label = readerString("theme_page_color", "Page color"),
                        color = backgroundColor,
                        onClick = { editingColorTarget = ReaderThemeColorTarget.BACKGROUND },
                        modifier = Modifier.weight(1f)
                    )
                    SharedReaderThemeColorButton(
                        label = readerString("theme_text_color", "Text color"),
                        color = textColor,
                        onClick = { editingColorTarget = ReaderThemeColorTarget.TEXT },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (isTexturedMode) {
                    Text(
                        readerString("theme_select_custom_texture", "Select texture"),
                        style = MaterialTheme.typography.labelMedium
                    )
                    val textureChoices = buildList {
                        if (onImportTexture != null) {
                            add(
                                SharedReaderTextureChoiceModel(
                                    textureId = null,
                                    label = readerString("action_import", "Import"),
                                    icon = Icons.Default.Add,
                                    isImportAction = true
                                )
                            )
                        }
                        ReaderTexture.entries.forEach { texture ->
                            add(SharedReaderTextureChoiceModel(textureId = texture.id, label = texture.displayName))
                        }
                        dialogCustomTextureIds.forEach { importedTextureId ->
                            add(
                                SharedReaderTextureChoiceModel(
                                    textureId = importedTextureId,
                                    label = readerTextureDisplayName(importedTextureId)
                                )
                            )
                        }
                    }
                    textureChoices.chunked(3).forEach { rowChoices ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                            rowChoices.forEach { choice ->
                                SharedReaderTextureChoice(
                                    choice = choice,
                                    selected = !choice.isImportAction && textureId == choice.textureId,
                                    texturePreviewContent = texturePreviewContent,
                                    onSelected = {
                                        if (choice.isImportAction) {
                                            val imported = onImportTexture?.invoke(
                                                ReaderSettings(
                                                    textureId = textureId,
                                                    backgroundColorArgb = backgroundColor.toArgb().toLong(),
                                                    textColorArgb = textColor.toArgb().toLong()
                                                )
                                            )
                                            textureId = imported?.textureId ?: textureId
                                        } else {
                                            textureId = choice.textureId
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            repeat(3 - rowChoices.size) {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = {
                    onSave(
                        ReaderTheme(
                            id = initialTheme?.id ?: nextReaderCustomThemeId(customThemes),
                            name = name.trim().ifBlank { defaultName },
                            backgroundColor = backgroundColor,
                            textColor = textColor,
                            isDark = backgroundColor.luminance() < 0.5f,
                            textureId = if (isTexturedMode) textureId else null,
                            isCustom = true
                        )
                    )
                }
            ) {
                Text(readerString("action_save", "Save"))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(readerString("action_cancel", "Cancel"))
            }
        }
    )

    editingColorTarget?.let { target ->
        SharedHsvColorPickerDialog(
            initialColor = if (target == ReaderThemeColorTarget.BACKGROUND) backgroundColor else textColor,
            title = target.localizedTitle(),
            onDismiss = { editingColorTarget = null },
            onSave = { color ->
                if (target == ReaderThemeColorTarget.BACKGROUND) {
                    backgroundColor = color
                } else {
                    textColor = color
                }
                editingColorTarget = null
            }
        )
    }
}

internal fun nextReaderCustomThemeId(customThemes: List<ReaderTheme>): String {
    val usedIds = customThemes.mapTo(mutableSetOf()) { it.id }
    var index = customThemes.size + 1
    while ("reader_theme_$index" in usedIds) {
        index += 1
    }
    return "reader_theme_$index"
}

internal fun readerChromeThemeContrastRatio(color1: Color, color2: Color): Float {
    val l1 = maxOf(color1.luminance(), color2.luminance())
    val l2 = minOf(color1.luminance(), color2.luminance())
    return (l1 + 0.05f) / (l2 + 0.05f)
}

internal enum class ReaderThemeColorTarget {
    BACKGROUND,
    TEXT
}

@Composable
internal fun ReaderThemeColorTarget.localizedTitle(): String {
    return when (this) {
        ReaderThemeColorTarget.BACKGROUND -> readerString("theme_page_color", "Page color")
        ReaderThemeColorTarget.TEXT -> readerString("theme_text_color", "Text color")
    }
}

@Composable
internal fun SharedReaderThemeColorButton(
    label: String,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(54.dp),
        shape = RoundedCornerShape(10.dp),
        color = color,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Box(modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp), contentAlignment = Alignment.CenterStart) {
            Text(
                label,
                color = if (color.luminance() > 0.5f) Color.Black else Color.White,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

internal fun ReaderSettings.readerBackgroundColor(themes: List<ReaderTheme>): Color {
    return backgroundColorArgb?.toComposeColor()
        ?: themes.firstOrNull { it.id == themeId }?.backgroundColor?.takeIf { it.isSpecified }
        ?: if (darkMode) Color(0xFF171A17) else Color(0xFFFFFCF5)
}

internal fun ReaderSettings.readerTextColor(themes: List<ReaderTheme>): Color {
    return textColorArgb?.toComposeColor()
        ?: themes.firstOrNull { it.id == themeId }?.textColor?.takeIf { it.isSpecified }
        ?: if (darkMode) Color(0xFFE7E3D8) else Color(0xFF24231F)
}

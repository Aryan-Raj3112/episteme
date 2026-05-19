package com.aryan.reader.desktop

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aryan.reader.shared.ui.readerString
import java.io.File
import java.util.Properties

internal data class DesktopLanguageSettings(
    val languageTag: String? = null
)

internal data class DesktopLanguageOption(
    val languageTag: String?,
    val labelKey: String,
    val fallbackLabel: String
) {
    val normalizedTag: String? = normalizeDesktopLanguageTag(languageTag)
}

internal val DesktopLanguageOptions = listOf(
    DesktopLanguageOption(null, "language_system_default", "System default"),
    DesktopLanguageOption("en", "language_english_default", "English (Default)"),
    DesktopLanguageOption("ar", "language_arabic", "Arabic"),
    DesktopLanguageOption("de", "language_german", "German"),
    DesktopLanguageOption("tr", "language_turkish", "Turkish"),
    DesktopLanguageOption("fr", "language_french", "French"),
    DesktopLanguageOption("ru", "language_russian", "Russian"),
    DesktopLanguageOption("be", "language_belarusian", "Belarusian"),
    DesktopLanguageOption("es", "language_spanish", "Spanish"),
    DesktopLanguageOption("pt-BR", "language_portuguese_brazilian", "Portuguese (Brazil)"),
    DesktopLanguageOption("it", "language_italian", "Italian"),
    DesktopLanguageOption("pl", "language_polish", "Polish"),
    DesktopLanguageOption("vi", "language_vietnamese", "Vietnamese"),
    DesktopLanguageOption("ja", "language_japanese", "Japanese"),
    DesktopLanguageOption("ko", "language_korean", "Korean"),
    DesktopLanguageOption("hi", "language_hindi", "Hindi"),
    DesktopLanguageOption("zh-CN", "language_chinese_simplified", "Chinese, Simplified"),
    DesktopLanguageOption("nl", "language_dutch", "Dutch"),
    DesktopLanguageOption("uk", "language_ukrainian", "Ukrainian"),
    DesktopLanguageOption("id", "language_indonesian", "Indonesian")
)

internal fun selectedDesktopLanguageOption(languageTag: String?): DesktopLanguageOption {
    val normalized = normalizeDesktopLanguageTag(languageTag)
    return DesktopLanguageOptions.firstOrNull { it.normalizedTag == normalized }
        ?: DesktopLanguageOptions.first()
}

internal class DesktopLanguageSettingsStore(
    private val settingsFile: File = File(desktopUserConfigRoot(), "language.properties")
) {
    fun load(): DesktopLanguageSettings {
        if (!settingsFile.isFile) return DesktopLanguageSettings()
        val properties = Properties()
        return runCatching {
            settingsFile.inputStream().use(properties::load)
            DesktopLanguageSettings(
                languageTag = normalizeDesktopLanguageTag(properties.getProperty(LanguageTag))
            )
        }.getOrDefault(DesktopLanguageSettings())
    }

    fun save(settings: DesktopLanguageSettings) {
        settingsFile.parentFile?.mkdirs()
        val properties = Properties().apply {
            normalizeDesktopLanguageTag(settings.languageTag)?.let { languageTag ->
                setProperty(LanguageTag, languageTag)
            }
        }
        settingsFile.outputStream().use { output ->
            properties.store(output, "Episteme desktop language")
        }
    }

    private companion object {
        const val LanguageTag = "languageTag"
    }
}

@Composable
internal fun DesktopLanguageDialog(
    selectedLanguageTag: String?,
    onLanguageSelected: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    val selectedOption = selectedDesktopLanguageOption(selectedLanguageTag)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(readerString("options_language", "Language"), fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 520.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(DesktopLanguageOptions, key = { it.languageTag ?: "system" }) { option ->
                    val selected = option.normalizedTag == selectedOption.normalizedTag
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = if (selected) {
                            BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                        } else {
                            null
                        },
                        onClick = {
                            onLanguageSelected(option.normalizedTag)
                            onDismiss()
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            RadioButton(
                                selected = selected,
                                onClick = {
                                    onLanguageSelected(option.normalizedTag)
                                    onDismiss()
                                }
                            )
                            Column(Modifier.weight(1f)) {
                                Text(readerString(option.labelKey, option.fallbackLabel))
                            }
                        }
                    }
                    if (option != DesktopLanguageOptions.last()) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(readerString("action_cancel", "Cancel"))
            }
        }
    )
}

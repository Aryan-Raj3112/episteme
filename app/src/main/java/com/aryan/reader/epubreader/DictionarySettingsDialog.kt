package com.aryan.reader.epubreader

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.aryan.reader.R
import com.aryan.reader.areReaderAiFeaturesEnabled
import com.aryan.reader.readerModalMaxHeightDp
import com.aryan.reader.shared.ui.SharedDictionarySettingsLabels
import com.aryan.reader.shared.ui.SharedDictionarySettingsSheet
import com.aryan.reader.shared.ui.SharedExternalAppOption

@Suppress("KotlinConstantConditions")
@Composable
fun DictionarySettingsDialog(
    isVisible: Boolean,
    onDismiss: () -> Unit,
    isProUser: Boolean,
    useOnlineDictionary: Boolean,
    onToggleOnlineDictionary: (Boolean) -> Unit,
    selectedDictionaryPackageName: String?,
    onSelectDictionaryPackage: (String) -> Unit,
    selectedTranslatePackageName: String?,
    onSelectTranslatePackage: (String) -> Unit,
    selectedSearchPackageName: String?,
    onSelectSearchPackage: (String) -> Unit
) {
    if (!isVisible) return

    val context = LocalContext.current
    var dictionaryApps by remember { mutableStateOf<List<ExternalDictionaryApp>>(emptyList()) }
    var searchApps by remember { mutableStateOf<List<ExternalDictionaryApp>>(emptyList()) }
    val configuration = LocalConfiguration.current
    val maxSheetHeight = readerModalMaxHeightDp(configuration.screenHeightDp).dp

    LaunchedEffect(Unit) {
        dictionaryApps = ExternalDictionaryHelper.getAvailableDictionaries(context)
        searchApps = ExternalDictionaryHelper.getAvailableSearchApps(context)
    }

    val allAppsByPackage = remember(dictionaryApps, searchApps) {
        (dictionaryApps + searchApps).associateBy { it.packageName }
    }
    SharedDictionarySettingsSheet(
        isVisible = true,
        aiFeaturesEnabled = areReaderAiFeaturesEnabled(context),
        useOnlineDictionary = useOnlineDictionary,
        onToggleOnlineDictionary = onToggleOnlineDictionary,
        dictionaryApps = dictionaryApps.map { SharedExternalAppOption(it.packageName, it.label, it.icon != null) },
        searchApps = searchApps.map { SharedExternalAppOption(it.packageName, it.label, it.icon != null) },
        selectedDictionaryPackageName = selectedDictionaryPackageName,
        onSelectDictionaryPackage = onSelectDictionaryPackage,
        selectedTranslatePackageName = selectedTranslatePackageName,
        onSelectTranslatePackage = onSelectTranslatePackage,
        selectedSearchPackageName = selectedSearchPackageName,
        onSelectSearchPackage = onSelectSearchPackage,
        maxSheetHeight = maxSheetHeight,
        labels = SharedDictionarySettingsLabels(
            title = stringResource(R.string.dict_lookup_settings),
            dictionaryEngine = stringResource(R.string.dict_dictionary_engine),
            smartAi = stringResource(R.string.dict_smart_ai),
            externalApp = stringResource(R.string.dict_external_app),
            aiDescription = stringResource(R.string.dict_ai_description),
            externalDescription = stringResource(R.string.dict_external_description),
            fallbackApp = stringResource(R.string.dict_fallback_app),
            dictionaryApp = stringResource(R.string.dict_dictionary_app),
            dictionary = stringResource(R.string.tooltip_dictionary),
            translate = stringResource(R.string.dict_translate),
            translateDescription = stringResource(R.string.dict_translate_description),
            search = stringResource(R.string.tooltip_search),
            searchDescription = stringResource(R.string.dict_search_app_description),
            selectApp = stringResource(R.string.dict_select_app),
            none = stringResource(R.string.dict_none),
            selected = stringResource(R.string.content_desc_selected)
        ),
        appIcon = { packageName ->
            allAppsByPackage[packageName]?.icon?.let { icon ->
                Image(bitmap = icon.toBitmap().asImageBitmap(), contentDescription = null, modifier = Modifier.size(24.dp))
            }
        },
        onDismiss = onDismiss
    )
}

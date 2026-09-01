package com.aryan.reader.epubreader

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontFamily
import com.aryan.reader.BuildConfig
import com.aryan.reader.isByokCloudTtsAvailable
import com.aryan.reader.loadEpubRightToLeftPagination
import com.aryan.reader.loadReaderScreenOrientationMode
import com.aryan.reader.paginatedreader.Locator
import com.aryan.reader.shared.AiDefinitionResult
import com.aryan.reader.shared.ReaderLocator as SharedReaderLocator
import com.aryan.reader.tts.TtsPlaybackManager
import com.aryan.reader.tts.loadTtsMode
import kotlinx.coroutines.Job

/**
 * Typed holder for the EPUB reader's typography/format settings.
 *
 * Extracted from [EpubReaderHost] so the giant composable method stays under the
 * ART verifier limits that caused `VerifyError` on device. Behavior mirrors the
 * original inline `remember` block: a new instance is created whenever
 * [EpubReaderHost] recomposes with a different [isLocal] (or book), which reloads
 * the persisted settings exactly like the previous `remember(initialFormatSettings)`
 * keyed states did.
 */
internal class EpubReaderFormatState(
    private val context: Context,
    bookId: String,
    isLocal: Boolean,
) {
    private val initialFormatSettings: FormatSettings = loadFormatSettings(context, bookId, isLocal)

    var currentFontSizeEm by mutableFloatStateOf(initialFormatSettings.fontSize)
    var currentLineHeight by mutableFloatStateOf(initialFormatSettings.lineHeight)
    var currentParagraphGap by mutableFloatStateOf(initialFormatSettings.paragraphGap)
    var currentImageSize by mutableFloatStateOf(initialFormatSettings.imageSize)
    var currentHorizontalMargin by mutableFloatStateOf(initialFormatSettings.horizontalMargin)
    var currentVerticalMargin by mutableFloatStateOf(initialFormatSettings.verticalMargin)
    var currentFontWeight by mutableIntStateOf(initialFormatSettings.fontWeight)
    var currentLetterSpacing by mutableFloatStateOf(initialFormatSettings.letterSpacing)
    var currentTextAlign by mutableStateOf(initialFormatSettings.textAlign)
    var currentFontFamily by mutableStateOf(initialFormatSettings.font)
    var currentCustomFontPath by mutableStateOf(initialFormatSettings.customPath)

    val activeFontFamily: FontFamily by derivedStateOf {
        getComposeFontFamily(
            font = currentFontFamily,
            customFontPath = currentCustomFontPath,
            assetManager = context.assets
        )
    }
}

@Composable
internal fun rememberEpubReaderFormatState(
    context: Context,
    bookId: String,
    isLocal: Boolean,
): EpubReaderFormatState = remember(isLocal, bookId) { EpubReaderFormatState(context, bookId, isLocal) }

/**
 * Typed holder for the EPUB reader's auto-scroll speed settings (global vs
 * per-book local profiles). Extracted from [EpubReaderHost] for the same
 * verifier-size reasons as [EpubReaderFormatState]; the update clamping rules
 * are unchanged from the original inline lambdas.
 */
internal class EpubReaderAutoScrollSpeedState(
    private val context: Context,
    private val bookId: String,
) {
    var isAutoScrollLocal by mutableStateOf(loadAutoScrollLocalMode(context, bookId))

    private val initialSettings: Triple<Float, Float, Float> =
        loadAutoScrollLocalSettings(context, bookId) ?: Triple(
            loadAutoScrollSpeed(context),
            loadAutoScrollMinSpeed(context),
            loadAutoScrollMaxSpeed(context)
        )

    var autoScrollSpeed by mutableFloatStateOf(initialSettings.first)
    var autoScrollMinSpeed by mutableFloatStateOf(initialSettings.second)
    var autoScrollMaxSpeed by mutableFloatStateOf(initialSettings.third)

    fun onToggleAutoScrollMode(newIsLocal: Boolean) {
        isAutoScrollLocal = newIsLocal
        saveAutoScrollLocalMode(context, bookId, newIsLocal)

        if (newIsLocal) {
            val existingLocal = loadAutoScrollLocalSettings(context, bookId)
            if (existingLocal == null) {
                saveAutoScrollLocalSettings(context, bookId, autoScrollSpeed, autoScrollMinSpeed, autoScrollMaxSpeed)
            } else {
                autoScrollSpeed = existingLocal.first
                autoScrollMinSpeed = existingLocal.second
                autoScrollMaxSpeed = existingLocal.third
            }
        } else {
            autoScrollSpeed = loadAutoScrollSpeed(context)
            autoScrollMinSpeed = loadAutoScrollMinSpeed(context)
            autoScrollMaxSpeed = loadAutoScrollMaxSpeed(context)
        }
    }

    fun updateSpeed(newSpeed: Float) {
        autoScrollSpeed = newSpeed
        if (isAutoScrollLocal) {
            saveAutoScrollLocalSettings(context, bookId, newSpeed, autoScrollMinSpeed, autoScrollMaxSpeed)
        } else {
            saveAutoScrollSpeed(context, newSpeed)
        }
    }

    fun updateMinSpeed(newMin: Float) {
        autoScrollMinSpeed = newMin
        if (isAutoScrollLocal) {
            var currentMax = autoScrollMaxSpeed
            var currentSpeed = autoScrollSpeed

            if (currentMax < newMin) {
                currentMax = newMin
                autoScrollMaxSpeed = newMin
            }
            if (currentSpeed < newMin) {
                currentSpeed = newMin
                autoScrollSpeed = newMin
            } else if (currentSpeed > currentMax) {
                currentSpeed = currentMax
                autoScrollSpeed = currentMax
            }

            saveAutoScrollLocalSettings(context, bookId, currentSpeed, newMin, currentMax)
        } else {
            saveAutoScrollMinSpeed(context, newMin)
        }
    }

    fun updateMaxSpeed(newMax: Float) {
        autoScrollMaxSpeed = newMax
        if (isAutoScrollLocal) {
            var currentMin = autoScrollMinSpeed
            var currentSpeed = autoScrollSpeed

            if (currentMin > newMax) {
                currentMin = newMax
                autoScrollMinSpeed = newMax
            }
            if (currentSpeed > newMax) {
                currentSpeed = newMax
                autoScrollSpeed = newMax
            } else if (currentSpeed < currentMin) {
                currentSpeed = currentMin
                autoScrollSpeed = currentMin
            }

            saveAutoScrollLocalSettings(context, bookId, currentSpeed, currentMin, newMax)
        } else {
            saveAutoScrollMaxSpeed(context, newMax)
        }
    }
}

@Composable
internal fun rememberEpubReaderAutoScrollSpeedState(
    context: Context,
    bookId: String,
): EpubReaderAutoScrollSpeedState = remember(bookId) { EpubReaderAutoScrollSpeedState(context, bookId) }

/**
 * Typed holder for dictionary/AI lookup and toolbar-customization preferences.
 * Extracted from [EpubReaderHost] for ART verifier-size reasons; behavior matches
 * the original inline `remember { mutableStateOf(load...) }` blocks.
 */
internal class EpubReaderDictionaryToolsState(context: Context) {
    var showAiDefinitionPopup by mutableStateOf(false)
    var selectedTextForAi by mutableStateOf<String?>(null)
    var aiDefinitionResult by mutableStateOf<AiDefinitionResult?>(null)
    var isAiDefinitionLoading by mutableStateOf(false)

    var showDictionarySettingsSheet by mutableStateOf(false)

    var useOnlineDictionary by mutableStateOf(loadUseOnlineDict(context))
    var selectedDictPackage by mutableStateOf(loadExternalDictPackage(context))
    var selectedTranslatePackage by mutableStateOf(loadExternalTranslatePackage(context))
    var selectedSearchPackage by mutableStateOf(loadExternalSearchPackage(context))

    var hiddenTools by mutableStateOf(loadHiddenTools(context))
    var toolOrder by mutableStateOf(loadToolOrder(context))
    var bottomTools by mutableStateOf(loadBottomTools(context))
    var showCustomizeToolsSheet by mutableStateOf(false)
}

@Composable
internal fun rememberEpubReaderDictionaryToolsState(
    context: Context,
): EpubReaderDictionaryToolsState = remember { EpubReaderDictionaryToolsState(context) }

/**
 * Typed holder for the EPUB reader's persisted reading preferences (system bars,
 * page info, orientation, pagination direction, pull-to-turn, volume scroll,
 * tap-to-turn, realistic page turns and TTS mode). Extracted from
 * [EpubReaderHost] for ART verifier-size reasons.
 */
internal class EpubReaderReadingPrefsState(private val context: Context) {
    var systemUiMode by mutableStateOf(loadSystemUiMode(context))
    var pageInfoMode by mutableStateOf(loadPageInfoMode(context))
    var pageInfoPosition by mutableStateOf(loadPageInfoPosition(context))
    var screenOrientationMode by mutableStateOf(loadReaderScreenOrientationMode(context))
    var rightToLeftPagination by mutableStateOf(loadEpubRightToLeftPagination(context))
    var showScreenOrientationSheet by mutableStateOf(false)
    var pullToTurnEnabled by mutableStateOf(loadPullToTurn(context))
    var pullToTurnMultiplier by mutableFloatStateOf(loadPullToTurnMultiplier(context))
    var hideImages by mutableStateOf(loadHideImages(context))
    var showVisualOptionsSheet by mutableStateOf(false)
    var volumeScrollEnabled by mutableStateOf(loadVolumeScrollSetting(context))
    var tapToNavigateEnabled by mutableStateOf(loadTapToNavigateSetting(context))
    var isPageTurnAnimationEnabled by mutableStateOf(loadPageTurnAnimationSetting(context))
    var currentTtsMode by mutableStateOf(
        loadTtsMode(context).let {
            if (BuildConfig.FLAVOR == "oss" && !isByokCloudTtsAvailable(context)) TtsPlaybackManager.TtsMode.BASE else it
        }
    )
}

@Composable
internal fun rememberEpubReaderReadingPrefsState(
    context: Context,
): EpubReaderReadingPrefsState = remember { EpubReaderReadingPrefsState(context) }

/**
 * Typed holder for the EPUB reader's transient navigation/UI flags (slider scrub,
 * note & footnote flows, jump-history navigation anchors, dialog visibility).
 * Extracted from [EpubReaderHost] for ART verifier-size reasons. Only key-less
 * `remember { mutableStateOf }` states live here, so reset semantics are unchanged.
 */
internal class EpubReaderNavigationState {
    var sliderCurrentPage by mutableFloatStateOf(0f)
    var isFastScrubbing by mutableStateOf(false)
    var scrubDebounceJob by mutableStateOf<Job?>(null)
    var pendingSliderJumpOrigin by mutableStateOf<SharedReaderLocator?>(null)
    var sliderJumpGeneration by mutableIntStateOf(0)
    var volumeScrollFocusDebounceJob by mutableStateOf<Job?>(null)
    var sliderStartPage by mutableIntStateOf(0)

    var pendingNoteForNewHighlight by mutableStateOf(false)
    var highlightToNoteCfi by mutableStateOf<String?>(null)
    var activeFootnoteHtml by mutableStateOf<String?>(null)
    var showJustifyWarningDialog by mutableStateOf(false)
    var isNavigatingByToc by mutableStateOf(false)

    var chunkTargetOverride by mutableStateOf<Int?>(null)

    var isNavigatingToPosition by mutableStateOf(false)
    var isSeamlessTransitioning by mutableStateOf(false)
    var showInsufficientCreditsDialog by mutableStateOf(false)
    var showFileInfoDialog by mutableStateOf(false)

    var chapterToLoadOnSwitch by mutableStateOf<Int?>(null)
    var paginatedReconfigurationAnchor by mutableStateOf<Locator?>(null)
    var isPaginatedReconfigurationRestoring by mutableStateOf(false)
    var activeFragmentId by mutableStateOf<String?>(null)

    var showFormatAdjustmentBars by mutableStateOf(false)
}

@Composable
internal fun rememberEpubReaderNavigationState(): EpubReaderNavigationState = remember { EpubReaderNavigationState() }

/**
 * Typed holder for the native vertical reader's one-shot scroll request channels
 * (page / locator / progress / delta requests with their epoch counters).
 * Extracted from [EpubReaderHost] for ART verifier-size reasons.
 */
internal class EpubReaderVerticalScrollRequests {
    var nativeVerticalScrollRequest by mutableStateOf<Int?>(null)
    var nativeVerticalLocatorScrollRequest by mutableStateOf<Locator?>(null)
    var nativeVerticalLocatorScrollRequestId by mutableLongStateOf(0L)
    var nativeVerticalLocatorScrollKeepVisible by mutableStateOf(false)
    var nativeVerticalProgressScrollRequest by mutableStateOf<Float?>(null)
    var nativeVerticalProgressScrollRequestId by mutableLongStateOf(0L)
    var nativeVerticalScrollDeltaRequest by mutableStateOf<Float?>(null)
    var nativeVerticalScrollDeltaRequestId by mutableLongStateOf(0L)
    var nativeVerticalScrollDeltaAnimated by mutableStateOf(true)
}

@Composable
internal fun rememberEpubReaderVerticalScrollRequests(): EpubReaderVerticalScrollRequests =
    remember { EpubReaderVerticalScrollRequests() }

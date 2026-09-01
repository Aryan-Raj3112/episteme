/*
 * Episteme Reader - A native Android document reader.
 * Copyright (C) 2026 Episteme
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 * mail: epistemereader@gmail.com
 */
package com.aryan.reader

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.webkit.WebView
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.aryan.reader.data.PlatformFeaturesRepository
import com.aryan.reader.ui.theme.AppTheme
import kotlinx.coroutines.launch
import timber.log.Timber
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import com.aryan.reader.tts.ACTION_OPEN_TTS_SESSION
import com.aryan.reader.tts.EXTRA_TTS_BOOK_ID
import com.aryan.reader.tts.EXTRA_TTS_CHAPTER_INDEX
import com.aryan.reader.tts.EXTRA_TTS_PAGE_INDEX
import com.aryan.reader.tts.EXTRA_TTS_SOURCE_CFI
import com.aryan.reader.tts.EXTRA_TTS_START_OFFSET
import com.aryan.reader.tts.toMobileTtsHandoffRequest
import com.aryan.reader.shared.ExternalDocumentIntakeRequest
import com.aryan.reader.shared.ExternalDocumentOpenMode

@UnstableApi
open class MainActivity : AppCompatActivity() {

    private val viewModel: MainViewModel by viewModels()
    private lateinit var platformFeaturesRepository: PlatformFeaturesRepository
    private val isTemporaryExternalOpen: Boolean
        get() = intent?.getBooleanExtra(EXTRA_TEMPORARY_EXTERNAL_OPEN, false) == true

    private val updateLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode != RESULT_OK) {
            Timber.e("Update flow failed! Result code: ${result.resultCode}")
        }
    }

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        platformFeaturesRepository = PlatformFeaturesRepository(this)

        lifecycleScope.launch {
            viewModel.reviewRequestEvent.collect {
                platformFeaturesRepository.requestReview(this@MainActivity)
            }
        }

        lifecycleScope.launch {
            viewModel.temporaryExternalOpenFinished.collect {
                if (isTemporaryExternalOpen) {
                    finishAndRemoveTask()
                }
            }
        }

        if (savedInstanceState == null) {
            handleIntent(intent)
        }

        lifecycleScope.launch {
            platformFeaturesRepository.checkForUpdates(this@MainActivity, updateLauncher)
        }

        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val customFonts by viewModel.customFonts.collectAsStateWithLifecycle()

            ScreenCaptureProtectionEffect(enabled = uiState.isScreenCaptureProtectionEnabled)

            val darkTheme = when (uiState.appThemeMode) {
                AppThemeMode.LIGHT -> false
                AppThemeMode.DARK -> true
                AppThemeMode.SYSTEM -> isSystemInDarkTheme()
            }

            val textDimFactor = if (darkTheme) uiState.appTextDimFactorDark else uiState.appTextDimFactorLight
            val appFontFamily = remember(uiState.appFontPreference, customFonts) {
                uiState.appFontPreference.toAndroidAppFontFamily(customFonts)
            }

            AppTheme(
                darkTheme = darkTheme,
                dynamicColor = uiState.appSeedColor == null,
                seedColor = uiState.appSeedColor,
                contrastLevel = uiState.appContrastOption.value,
                textDimFactor = textDimFactor,
                appFontFamily = appFontFamily
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val windowSizeClass = calculateWindowSizeClass(this)
                    val navController = rememberNavController()
                    AppNavigation(
                        navController = navController,
                        windowSizeClass = windowSizeClass,
                        viewModel = viewModel
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val ttsRequest = intent?.toMobileTtsHandoffRequest()
        if (ttsRequest != null) {
            val target = ttsRequest.ttsTarget ?: return
            Timber.d("Received TTS notification intent for bookId=${target.bookId}")
            if (target.isAudiobookListening) {
                viewModel.openAudiobookTtsNotificationTarget(bookId = target.bookId)
                return
            }
            viewModel.openTtsNotificationTarget(
                bookId = target.bookId,
                sourceCfi = target.sourceCfi,
                startOffset = target.startOffset,
                chapterIndex = target.chapterIndex,
                pageIndex = target.pageIndex,
            )
            return
        }

        val sourceIntent = intent ?: return
        if (sourceIntent.action == Intent.ACTION_VIEW) {
            val request = ExternalDocumentIntentMapper.map(sourceIntent, this)?.request
            if (request != null) {
                handleExternalDocumentRequest(request)
                return
            }

            // Preserve the established VIEW behavior for providers whose
            // metadata cannot be normalized by the shared capability model.
            val uri = sourceIntent.data ?: return
            Timber.d("Received VIEW intent with unclassified URI; using direct fallback: $uri")
            viewModel.onFileSelected(
                uri,
                isFromRecent = false,
                isExternalIntent = true,
                isTemporaryExternalIntent = isTemporaryExternalOpen,
            )
            return
        }

        if (sourceIntent.action == Intent.ACTION_SEND || sourceIntent.action == Intent.ACTION_SEND_MULTIPLE) {
            val request = ExternalDocumentIntentMapper.map(sourceIntent, this)?.request ?: return
            handleExternalDocumentRequest(request)
        }
    }

    private fun handleExternalDocumentRequest(
        request: ExternalDocumentIntakeRequest,
    ) {
        val uris = request.documents.map { Uri.parse(it.uri) }
        if (uris.isEmpty()) return

        if (request.openMode == ExternalDocumentOpenMode.OPEN_SINGLE) {
            Timber.d("Received single external document handoff with URI: ${uris.single()}")
            viewModel.onFileSelected(
                uris.single(),
                isFromRecent = false,
                isExternalIntent = true,
                isTemporaryExternalIntent = isTemporaryExternalOpen,
            )
        } else {
            Timber.d("Received external document batch handoff with ${uris.size} URIs")
            // The existing batch importer intentionally imports in order
            // and does not auto-open an arbitrary item.
            viewModel.onFilesSelected(uris)
        }
    }
}

@Composable
private fun MainActivity.ScreenCaptureProtectionEffect(enabled: Boolean) {
    DisposableEffect(enabled) {
        if (enabled) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }

        onDispose {
            if (enabled) {
                window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
        }
    }
}

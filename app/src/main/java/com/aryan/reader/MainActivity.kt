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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.aryan.reader.data.PlatformFeaturesRepository
import com.aryan.reader.ui.theme.AppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
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
        // Splash stays until the library DB emits (first projected library
        // available) or a 3s timeout, whichever comes first. Removes the
        // empty-Home flash at the cost of a slightly longer splash on slow DBs.
        val splash = installSplashScreen()
        splash.setKeepOnScreenCondition { !viewModel.startupGatePassed.value }
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        platformFeaturesRepository = PlatformFeaturesRepository(this)
        DebugFpsStore.init(applicationContext)

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

        // Play update IPC stays off the critical path: first paint happens
        // first, then the update check runs. Users see the update dialog a
        // few seconds later instead of racing startup.
        lifecycleScope.launch(Dispatchers.IO) {
            delay(3000)
            runCatching {
                platformFeaturesRepository.checkForUpdates(this@MainActivity, updateLauncher)
            }
        }

        setContent {
            // Window metrics, nav controller, and the link handler live in
            // this outer scope so uiState recompositions below never redo
            // platform work. Everything that reads uiState sits in
            // MainContent, which restarts independently.
            val windowSizeClass = rememberSafeWindowSizeClass(this@MainActivity)
            val navController = rememberNavController()
            // Every screen opens links through this handler: Custom Tabs
            // with a browser fallback that copies the link instead of
            // crashing on devices with no browser installed.
            val safeUriHandler = remember { CustomTabUriHandler(this@MainActivity) }
            MainContent(
                windowSizeClass = windowSizeClass,
                navController = navController,
                safeUriHandler = safeUriHandler
            )
        }
    }

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    @Composable
    private fun MainContent(
        windowSizeClass: androidx.compose.material3.windowsizeclass.WindowSizeClass,
        navController: androidx.navigation.NavHostController,
        safeUriHandler: androidx.compose.ui.platform.UriHandler
    ) {
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()
        val customFonts by viewModel.customFonts.collectAsStateWithLifecycle()
        val debugFpsEnabled by DebugFpsStore.enabled.collectAsStateWithLifecycle()

        ScreenCaptureProtectionEffect(enabled = uiState.isScreenCaptureProtectionEnabled)

        val darkTheme = when (uiState.appThemeMode) {
            AppThemeMode.LIGHT -> false
            AppThemeMode.DARK -> true
            AppThemeMode.SYSTEM -> isSystemInDarkTheme()
        }

        val textDimFactor = if (darkTheme) uiState.appTextDimFactorDark else uiState.appTextDimFactorLight
        // Custom TTF/OTF parsing (Font(file)) does file IO and must not
        // run on the composition thread. Baseline fonts resolve
        // synchronously (no IO); custom file fonts load async with the
        // baseline as the initial value so first paint never blocks.
        // Users with a custom app font see the system font for ~1 frame.
        val baselineFontFamily = remember(uiState.appFontPreference) {
            val sanitized = uiState.appFontPreference.sanitized()
            if (sanitized.kind != AppFontPreferenceKind.CUSTOM) {
                uiState.appFontPreference.toAndroidAppFontFamily(emptyList())
            } else {
                null
            }
        }
        val appFontFamily by produceState(
            initialValue = baselineFontFamily,
            key1 = uiState.appFontPreference,
            key2 = customFonts
        ) {
            value = withContext(Dispatchers.IO) {
                runCatching {
                    uiState.appFontPreference.toAndroidAppFontFamily(customFonts)
                }.getOrNull() ?: baselineFontFamily
            }
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
                CompositionLocalProvider(LocalUriHandler provides safeUriHandler) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        AppNavigation(
                            navController = navController,
                            windowSizeClass = windowSizeClass,
                            viewModel = viewModel
                        )
                        // Debug-only global FPS meter: beneath the status
                        // bar, top-left, above every destination. Gated
                        // twice (here + inside) so release builds pay
                        // nothing and never compose the Choreographer loop.
                        if (BuildConfig.DEBUG && debugFpsEnabled) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize(),
                                contentAlignment = Alignment.TopStart
                            ) {
                                DebugFpsGlobalOverlay(enabled = true)
                            }
                        }
                    }
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
        // TTS handoff is extras-only (no IPC) so it stays synchronous.
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
        val action = sourceIntent.action ?: return
        if (action != Intent.ACTION_VIEW &&
            action != Intent.ACTION_SEND &&
            action != Intent.ACTION_SEND_MULTIPLE
        ) {
            return
        }
        // ContentResolver queries (getType + DISPLAY_NAME) can hit slow
        // providers, so mapping runs on IO. Home paints first; the
        // import/open completes async right after.
        lifecycleScope.launch(Dispatchers.IO) {
            val request = ExternalDocumentIntentMapper.map(sourceIntent, this@MainActivity)?.request
            if (request != null) {
                handleExternalDocumentRequest(request)
                return@launch
            }

            if (action == Intent.ACTION_VIEW) {
                // Preserve the established VIEW behavior for providers whose
                // metadata cannot be normalized by the shared capability model.
                val uri = sourceIntent.data ?: return@launch
                Timber.d("Received VIEW intent with unclassified URI; using direct fallback: $uri")
                withContext(Dispatchers.Main) {
                    viewModel.onFileSelected(
                        uri,
                        isFromRecent = false,
                        isExternalIntent = true,
                        isTemporaryExternalIntent = isTemporaryExternalOpen,
                    )
                }
            }
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

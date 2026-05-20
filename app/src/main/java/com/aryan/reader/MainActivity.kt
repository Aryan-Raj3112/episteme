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

@UnstableApi
class MainActivity : AppCompatActivity() {

    private val viewModel: MainViewModel by viewModels()
    private lateinit var platformFeaturesRepository: PlatformFeaturesRepository

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
        if (intent?.action == ACTION_OPEN_TTS_SESSION) {
            val bookId = intent.getStringExtra(EXTRA_TTS_BOOK_ID)
            if (!bookId.isNullOrBlank()) {
                Timber.d("Received TTS notification intent for bookId=$bookId")
                viewModel.openTtsNotificationTarget(
                    bookId = bookId,
                    sourceCfi = intent.getStringExtra(EXTRA_TTS_SOURCE_CFI),
                    startOffset = intent.getIntExtra(EXTRA_TTS_START_OFFSET, -1).takeIf { it >= 0 },
                    chapterIndex = intent.getIntExtra(EXTRA_TTS_CHAPTER_INDEX, -1).takeIf { it >= 0 },
                    pageIndex = intent.getIntExtra(EXTRA_TTS_PAGE_INDEX, -1).takeIf { it >= 0 }
                )
            }
            return
        }

        if (intent?.action == Intent.ACTION_VIEW && intent.data != null) {
            Timber.d("Received VIEW intent with URI: ${intent.data}")
            val uri = intent.data!!
            viewModel.onFileSelected(uri, isFromRecent = false, isExternalIntent = true)
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

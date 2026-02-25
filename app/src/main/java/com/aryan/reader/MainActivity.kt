/*
 * Episteme Reader - A native Android document reader.
 * Copyright (C) 2026 Episteme Authors
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
 * Electronic mail: epistemereader@gmail.com
 */
package com.aryan.reader

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.webkit.WebView
import androidx.activity.ComponentActivity
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
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.aryan.reader.data.PlatformFeaturesRepository // Import the new repo
import com.aryan.reader.ui.theme.AppTheme
import kotlinx.coroutines.launch
import timber.log.Timber

class MainActivity : ComponentActivity() {

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
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        platformFeaturesRepository = PlatformFeaturesRepository(this)

        lifecycleScope.launch {
            viewModel.reviewRequestEvent.collect {
                platformFeaturesRepository.requestReview(this@MainActivity)
            }
        }

        handleIntent(intent)

        lifecycleScope.launch {
            platformFeaturesRepository.checkForUpdates(this@MainActivity, updateLauncher)
        }

        setContent {
            AppTheme {
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
        WebView.setWebContentsDebuggingEnabled(true)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_VIEW && intent.data != null) {
            Timber.d("Received VIEW intent with URI: ${intent.data}")
            val uri = intent.data!!
            viewModel.onFileSelected(uri)
        }
    }
}
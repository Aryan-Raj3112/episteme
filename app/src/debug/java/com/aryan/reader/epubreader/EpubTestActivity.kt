package com.aryan.reader.epubreader

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import com.aryan.reader.RenderMode
import com.aryan.reader.epub.EpubBook
import kotlinx.serialization.json.Json

class EpubTestActivity : ComponentActivity() {
    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val bookJson = intent.getStringExtra("epubBookJson")
        val book = Json.decodeFromString<EpubBook>(bookJson!!)

        setContent {
            EpubReaderScreen(
                epubBook = book,
                renderMode = RenderMode.VERTICAL_SCROLL,
                initialLocator = null,
                initialCfi = null,
                initialBookmarksJson = null,
                isProUser = false,
                onNavigateBack = {},
                onSavePosition = { _, _, _ -> },
                onBookmarksChanged = {},
                onNavigateToPro = {},
                coverImagePath = null,
                onRenderModeChange = {},
                customFonts = TODO(),
                onImportFont = TODO()
            )
        }
    }
}
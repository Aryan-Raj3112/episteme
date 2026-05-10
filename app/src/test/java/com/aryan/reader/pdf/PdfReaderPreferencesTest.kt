package com.aryan.reader.pdf

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.aryan.reader.epubreader.SystemUiMode
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfReaderPreferencesTest {

    @Test
    fun `tool preferences load defaults and preserve saved order with unknowns removed`() {
        val prefs = InMemorySharedPreferences(
            PDF_TOOL_ORDER_KEY to "SEARCH,NO_SUCH_TOOL,TOC,SEARCH",
            PDF_BOTTOM_TOOLS_KEY to setOf(PdfReaderTool.SEARCH.name, PdfReaderTool.TOC.name),
            PDF_HIDDEN_TOOLS_KEY to setOf(PdfReaderTool.PRINT.name)
        )
        val context = contextWithPrefs(prefs)

        val order = loadPdfToolOrder(context)

        assertEquals(listOf(PdfReaderTool.SEARCH, PdfReaderTool.TOC), order.take(2))
        assertEquals(PdfReaderTool.entries.size, order.size)
        assertEquals(PdfReaderTool.entries.toSet(), order.toSet())
        assertEquals(setOf(PdfReaderTool.SEARCH.name, PdfReaderTool.TOC.name), loadPdfBottomTools(context))
        assertEquals(
            setOf(PdfReaderTool.PRINT.name, PdfReaderTool.SCREEN_ORIENTATION.name, PdfReaderTool.HIGHLIGHT_ALL.name),
            loadPdfHiddenTools(context)
        )
    }

    @Test
    fun `tool preferences save hidden bottom and explicit order`() {
        val prefs = InMemorySharedPreferences()
        val context = contextWithPrefs(prefs)

        savePdfHiddenTools(context, setOf(PdfReaderTool.PRINT.name, PdfReaderTool.SHARE.name))
        savePdfBottomTools(context, setOf(PdfReaderTool.SEARCH.name))
        savePdfToolOrder(context, listOf(PdfReaderTool.TOC, PdfReaderTool.SEARCH))

        assertEquals(setOf(PdfReaderTool.PRINT.name, PdfReaderTool.SHARE.name), loadPdfHiddenTools(context))
        assertFalse(PdfReaderTool.SCREEN_ORIENTATION.name in loadPdfHiddenTools(context))
        assertFalse(PdfReaderTool.HIGHLIGHT_ALL.name in loadPdfHiddenTools(context))
        assertEquals(setOf(PdfReaderTool.SEARCH.name), loadPdfBottomTools(context))
        assertEquals(listOf(PdfReaderTool.TOC, PdfReaderTool.SEARCH), loadPdfToolOrder(context).take(2))
    }

    @Test
    fun `reader mode and enum preferences default safely when saved values are invalid`() {
        val prefs = InMemorySharedPreferences(
            DISPLAY_MODE_KEY to "BROKEN",
            DOCK_LOCATION_KEY to "MISSING",
            DOCK_OFFSET_X_KEY to 12.5f,
            DOCK_OFFSET_Y_KEY to -7.25f,
            OCR_LANGUAGE_KEY to "UNKNOWN",
            PDF_SYSTEM_UI_MODE_KEY to Int.MIN_VALUE
        )
        val context = contextWithPrefs(prefs)

        assertEquals(DisplayMode.VERTICAL_SCROLL, loadDisplayMode(context))
        assertEquals(DockLocation.BOTTOM to Offset(12.5f, -7.25f), loadDockState(context))
        assertEquals(OcrLanguage.LATIN, loadOcrLanguage(context))
        assertEquals(SystemUiMode.SYNC, loadPdfSystemUiMode(context))
        assertFalse(hasUserSelectedOcrLanguage(context))
    }

    @Test
    fun `reader mode and enum preferences save and load selected values`() {
        val prefs = InMemorySharedPreferences()
        val context = contextWithPrefs(prefs)

        saveDisplayMode(context, DisplayMode.PAGINATION)
        saveDockState(context, DockLocation.FLOATING, Offset(3f, 4f))
        saveOcrLanguage(context, OcrLanguage.JAPANESE)
        savePdfSystemUiMode(context, SystemUiMode.HIDDEN)

        assertEquals(DisplayMode.PAGINATION, loadDisplayMode(context))
        assertEquals(DockLocation.FLOATING to Offset(3f, 4f), loadDockState(context))
        assertEquals(OcrLanguage.JAPANESE, loadOcrLanguage(context))
        assertTrue(hasUserSelectedOcrLanguage(context))
        assertEquals(SystemUiMode.HIDDEN, loadPdfSystemUiMode(context))
    }

    @Test
    fun `theme dictionary and simple boolean preferences round trip`() {
        val prefs = InMemorySharedPreferences()
        val context = contextWithPrefs(prefs)

        savePdfThemeId(context, "sepia")
        saveKeepScreenOn(context, true)
        saveUseOnlineDict(context, false)
        saveExternalDictPackage(context, "com.example.dict")
        saveExternalTranslatePackage(context, "com.example.translate")
        saveExternalSearchPackage(context, "com.example.search")
        savePdfMusicianMode(context, true)
        savePdfScrollLocked(context, "book/one", true)
        savePdfLockedState(context, "book/one", scale = 2.25f, offsetX = -10f, offsetY = 42f)
        saveStylusOnlyMode(context, true)
        savePdfDarkMode(context, true)

        assertEquals("sepia", loadPdfThemeId(context))
        assertTrue(loadKeepScreenOn(context))
        assertFalse(loadUseOnlineDict(context))
        assertEquals("com.example.dict", loadExternalDictPackage(context))
        assertEquals("com.example.translate", loadExternalTranslatePackage(context))
        assertEquals("com.example.search", loadExternalSearchPackage(context))
        assertTrue(loadPdfMusicianMode(context))
        assertTrue(loadPdfScrollLocked(context, "book/one"))
        assertEquals(Triple(2.25f, -10f, 42f), loadPdfLockedState(context, "book/one"))
        assertNull(loadPdfLockedState(context, "missing"))
        assertTrue(loadStylusOnlyMode(context))
        assertTrue(loadPdfDarkMode(context))
    }

    @Test
    fun `auto scroll global and per book preferences round trip with null local settings until speed exists`() {
        val prefs = InMemorySharedPreferences()
        val context = contextWithPrefs(prefs)

        assertNull(loadPdfAutoScrollLocalSettings(context, "book"))

        savePdfAutoScrollSpeed(context, 4.5f)
        savePdfAutoScrollMinSpeed(context, 0.25f)
        savePdfAutoScrollMaxSpeed(context, 8.75f)
        savePdfAutoScrollUseSlider(context, true)
        savePdfAutoScrollLocalMode(context, "book", true)
        savePdfAutoScrollLocalSettings(context, "book", speed = 5.5f, min = 0.5f, max = 9f)

        assertEquals(4.5f, loadPdfAutoScrollSpeed(context), 0.0001f)
        assertEquals(0.25f, loadPdfAutoScrollMinSpeed(context), 0.0001f)
        assertEquals(8.75f, loadPdfAutoScrollMaxSpeed(context), 0.0001f)
        assertTrue(loadPdfAutoScrollUseSlider(context))
        assertTrue(loadPdfAutoScrollLocalMode(context, "book"))
        assertEquals(Triple(5.5f, 0.5f, 9f), loadPdfAutoScrollLocalSettings(context, "book"))
    }

    @Test
    fun `custom highlight colors round trip while missing colors fall back to defaults`() {
        val prefs = InMemorySharedPreferences()
        val context = contextWithPrefs(prefs)

        saveCustomHighlightColors(
            context,
            mapOf(
                PdfHighlightColor.YELLOW to Color(0xFF010203),
                PdfHighlightColor.RED to Color(0xFF0A0B0C)
            )
        )

        val colors = loadCustomHighlightColors(context)

        assertEquals(Color(0xFF010203).toArgb(), colors.getValue(PdfHighlightColor.YELLOW).toArgb())
        assertEquals(Color(0xFF0A0B0C).toArgb(), colors.getValue(PdfHighlightColor.RED).toArgb())
        assertEquals(PdfHighlightColor.GREEN.color.toArgb(), colors.getValue(PdfHighlightColor.GREEN).toArgb())
    }

    private fun contextWithPrefs(prefs: SharedPreferences): Context {
        val context = mockk<Context>()
        every { context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE) } returns prefs
        return context
    }

    private class InMemorySharedPreferences(vararg initial: Pair<String, Any?>) : SharedPreferences {
        private val values = initial.toMap().toMutableMap()

        override fun getAll(): MutableMap<String, *> = values
        override fun getString(key: String?, defValue: String?): String? = values[key] as? String ?: defValue
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? {
            val value = values[key] as? Set<*> ?: return defValues
            return value.filterIsInstance<String>().toMutableSet()
        }
        override fun getInt(key: String?, defValue: Int): Int = values[key] as? Int ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = values[key] as? Long ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = values[key] as? Float ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = values[key] as? Boolean ?: defValue
        override fun contains(key: String?): Boolean = values.containsKey(key)
        override fun edit(): SharedPreferences.Editor = Editor()
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

        private inner class Editor : SharedPreferences.Editor {
            private val pending = mutableMapOf<String, Any?>()
            private var clearRequested = false

            override fun putString(key: String?, value: String?): SharedPreferences.Editor = applyPut(key, value)
            override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor =
                applyPut(key, values?.toSet())
            override fun putInt(key: String?, value: Int): SharedPreferences.Editor = applyPut(key, value)
            override fun putLong(key: String?, value: Long): SharedPreferences.Editor = applyPut(key, value)
            override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = applyPut(key, value)
            override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = applyPut(key, value)
            override fun remove(key: String?): SharedPreferences.Editor = applyPut(key, null)
            override fun clear(): SharedPreferences.Editor {
                clearRequested = true
                return this
            }
            override fun commit(): Boolean {
                flush()
                return true
            }
            override fun apply() = flush()

            private fun applyPut(key: String?, value: Any?): SharedPreferences.Editor {
                if (key != null) pending[key] = value
                return this
            }

            private fun flush() {
                if (clearRequested) values.clear()
                pending.forEach { (key, value) ->
                    if (value == null) values.remove(key) else values[key] = value
                }
            }
        }
    }
}

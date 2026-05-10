package com.aryan.reader

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderScreenOrientationTest {

    @Test
    fun `screen orientation defaults to follow system and falls back for invalid ids`() {
        val defaultContext = contextWithPrefs(InMemorySharedPreferences())
        val invalidContext = contextWithPrefs(
            InMemorySharedPreferences("reader_screen_orientation_mode" to Int.MIN_VALUE)
        )

        assertEquals(ReaderScreenOrientationMode.FOLLOW_SYSTEM, loadReaderScreenOrientationMode(defaultContext))
        assertEquals(ReaderScreenOrientationMode.FOLLOW_SYSTEM, loadReaderScreenOrientationMode(invalidContext))
    }

    @Test
    fun `screen orientation mode saves loads and maps to activity requested orientation`() {
        val context = contextWithPrefs(InMemorySharedPreferences())

        saveReaderScreenOrientationMode(context, ReaderScreenOrientationMode.LANDSCAPE)
        assertEquals(ReaderScreenOrientationMode.LANDSCAPE, loadReaderScreenOrientationMode(context))

        saveReaderScreenOrientationMode(context, ReaderScreenOrientationMode.PORTRAIT)
        assertEquals(ReaderScreenOrientationMode.PORTRAIT, loadReaderScreenOrientationMode(context))

        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED,
            ReaderScreenOrientationMode.FOLLOW_SYSTEM.toRequestedOrientation()
        )
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT,
            ReaderScreenOrientationMode.PORTRAIT.toRequestedOrientation()
        )
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE,
            ReaderScreenOrientationMode.LANDSCAPE.toRequestedOrientation()
        )
    }

    @Test
    fun `right to left pagination is separate for pdf and epub`() {
        val context = contextWithPrefs(InMemorySharedPreferences())

        assertEquals(false, loadPdfRightToLeftPagination(context))
        assertEquals(false, loadEpubRightToLeftPagination(context))

        savePdfRightToLeftPagination(context, true)
        assertEquals(true, loadPdfRightToLeftPagination(context))
        assertEquals(false, loadEpubRightToLeftPagination(context))

        saveEpubRightToLeftPagination(context, true)
        assertEquals(true, loadPdfRightToLeftPagination(context))
        assertEquals(true, loadEpubRightToLeftPagination(context))

        savePdfRightToLeftPagination(context, false)
        assertEquals(false, loadPdfRightToLeftPagination(context))
        assertEquals(true, loadEpubRightToLeftPagination(context))
    }

    private fun contextWithPrefs(prefs: SharedPreferences): Context {
        val context = mockk<Context>()
        every { context.getSharedPreferences("epub_reader_settings", Context.MODE_PRIVATE) } returns prefs
        every { context.getSharedPreferences("reader_prefs", Context.MODE_PRIVATE) } returns prefs
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

package com.aryan.reader

import com.aryan.reader.data.CustomFontEntity
import org.junit.Assert.assertNull
import org.junit.Test

class AppFontResolverTest {
    @Test
    fun `custom app font falls back to system when imported font is missing`() {
        val resolved = AppFontPreference.custom("missing")
            .toAndroidAppFontFamily(customFonts = emptyList())

        assertNull(resolved)
    }

    @Test
    fun `custom app font falls back to system when imported font file is gone`() {
        val resolved = AppFontPreference.custom("font")
            .toAndroidAppFontFamily(
                customFonts = listOf(
                    CustomFontEntity(
                        id = "font",
                        displayName = "Missing",
                        fileName = "missing.ttf",
                        fileExtension = "ttf",
                        path = "build/test-tmp/AppFontResolverTest/missing-${System.nanoTime()}.ttf",
                        timestamp = 1L
                    )
                )
            )

        assertNull(resolved)
    }
}

package com.aryan.reader.ui.theme

import android.content.res.Resources
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins the broken-firmware fallback: when the framework cannot serve dynamic
 * colors (S+ SDK check passed, resources still missing — the reported
 * `Resources$NotFoundException: Resource ID #0x106006c` startup crash), the
 * static scheme is used instead of crashing the launch.
 */
class DynamicSchemeFallbackTest {

    private val staticLight = lightColorScheme(primary = Color.Red)
    private val dynamicLight = darkColorScheme(primary = Color.Blue)

    @Test
    fun `missing framework dynamic colors fall back to static scheme`() {
        val resolved = resolveDynamicScheme(
            dynamic = { throw Resources.NotFoundException("Resource ID #0x106006c") },
            static = staticLight,
        )

        assertEquals(staticLight, resolved)
    }

    @Test
    fun `other dynamic resolution failures fall back to static scheme`() {
        val resolved = resolveDynamicScheme(
            dynamic = { throw IllegalStateException("vendor palette failure") },
            static = staticLight,
        )

        assertEquals(staticLight, resolved)
    }

    @Test
    fun `healthy dynamic resolution is passed through unchanged`() {
        val resolved = resolveDynamicScheme(
            dynamic = { dynamicLight },
            static = staticLight,
        )

        assertEquals(dynamicLight, resolved)
    }

    @Test(expected = OutOfMemoryError::class)
    fun `errors are never swallowed by the fallback`() {
        resolveDynamicScheme(
            dynamic = { throw OutOfMemoryError("exhausted") },
            static = staticLight,
        )
    }
}

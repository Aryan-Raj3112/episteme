package com.aryan.reader.shared.reader

import kotlin.test.Test
import kotlin.test.assertEquals

class SharedJvmUserDirectoriesTest {
    @Test
    fun `shared jvm cache root uses xdg cache on linux`() {
        val root = sharedJvmEpistemeCacheRoot(
            env = mapOf("XDG_CACHE_HOME" to "/tmp/reader-cache")::get,
            userHome = "/home/reader",
            osName = "Linux"
        )

        assertEquals("/tmp/reader-cache/episteme", root.portablePath())
    }

    @Test
    fun `shared jvm cache root preserves windows appdata location`() {
        val root = sharedJvmEpistemeCacheRoot(
            env = mapOf("APPDATA" to "C:/Users/reader/AppData/Roaming")::get,
            userHome = "C:/Users/reader",
            osName = "Windows 11"
        )

        assertEquals("C:/Users/reader/AppData/Roaming/Episteme", root.portablePath())
    }
}

private fun java.io.File.portablePath(): String {
    return path.replace('\\', '/')
}

package com.aryan.reader.desktop

import com.aryan.reader.shared.UserData
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopAuthStoreTest {
    @Test
    fun `save protects refresh tokens and load restores the account`() {
        val settingsFile = Files.createTempDirectory("reader-auth-store")
            .resolve("auth.properties")
            .toFile()
        val store = DesktopAuthStore(settingsFile, ReversibleSecretCodec)

        store.save(testSession())

        val raw = settingsFile.readText()
        assertFalse(raw.contains("firebase_refresh"))
        assertFalse(raw.contains("google_refresh"))
        assertTrue(raw.contains("firebaseRefreshTokenProtected="))
        assertTrue(raw.contains("googleRefreshTokenProtected="))

        val loaded = DesktopAuthStore(settingsFile, ReversibleSecretCodec).load()
        assertEquals("user-1", loaded?.user?.uid)
        assertEquals("reader@example.com", loaded?.user?.email)
        assertEquals("firebase_refresh", loaded?.refreshToken)
        assertEquals("google_refresh", loaded?.googleRefreshToken)
    }

    @Test
    fun `save fails without leaving a partial account file when secure storage is unavailable`() {
        val settingsFile = Files.createTempDirectory("reader-auth-store-unavailable")
            .resolve("auth.properties")
            .toFile()
        val store = DesktopAuthStore(settingsFile, ThrowingSecretCodec)

        assertFailsWith<IllegalStateException> {
            store.save(testSession())
        }
        assertFalse(settingsFile.exists())
    }

    private fun testSession(): DesktopAuthSession {
        return DesktopAuthSession(
            user = UserData(
                uid = "user-1",
                displayName = "Reader",
                photoUrl = null,
                email = "reader@example.com"
            ),
            idToken = "id_token",
            refreshToken = "firebase_refresh",
            expiresAtEpochMillis = 123L,
            googleAccessToken = "google_access",
            googleRefreshToken = "google_refresh",
            googleAccessTokenExpiresAtEpochMillis = 456L
        )
    }

    private object ReversibleSecretCodec : DesktopSecretCodec {
        override val isAvailable: Boolean = true

        override fun protect(value: String): String {
            return "test:" + value.reversed()
        }

        override fun unprotect(value: String): String {
            return value.removePrefix("test:").reversed()
        }
    }

    private object ThrowingSecretCodec : DesktopSecretCodec {
        override val isAvailable: Boolean = false

        override fun protect(value: String): String {
            throw IllegalStateException("Secure storage unavailable")
        }

        override fun unprotect(value: String): String = ""
    }
}

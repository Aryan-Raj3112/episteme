package com.aryan.reader.desktop

import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DesktopCloudConfigTest {
    @Test
    fun `packaged resource config can enable desktop Google sign in`() {
        val config = desktopCloudConfigFromProperties(
            resourceProperties = properties(
                "FIREBASE_WEB_API_KEY" to "firebase-key",
                "FIREBASE_PROJECT_ID" to "reader-project",
                "GOOGLE_OAUTH_CLIENT_ID" to "oauth-client",
                "GOOGLE_OAUTH_CLIENT_SECRET" to "oauth-secret"
            ),
            systemProperty = { null },
            environment = { null }
        )

        assertTrue(config.isAuthConfigured)
        assertEquals("firebase-key", config.firebaseWebApiKey)
        assertEquals("reader-project", config.firebaseProjectId)
        assertEquals("oauth-client", config.googleOAuthClientId)
        assertEquals("oauth-secret", config.googleOAuthClientSecret)
    }

    @Test
    fun `local desktop keys override packaged resource config`() {
        val config = desktopCloudConfigFromProperties(
            resourceProperties = properties(
                "FIREBASE_WEB_API_KEY" to "packaged-firebase-key",
                "FIREBASE_PROJECT_ID" to "packaged-project",
                "GOOGLE_OAUTH_CLIENT_ID" to "packaged-oauth-client",
                "GOOGLE_OAUTH_CLIENT_SECRET" to "packaged-oauth-secret"
            ),
            localProperties = properties(
                "DESKTOP_FIREBASE_WEB_API_KEY" to "local-firebase-key",
                "DESKTOP_FIREBASE_PROJECT_ID" to "local-project",
                "DESKTOP_GOOGLE_OAUTH_CLIENT_ID" to "local-oauth-client",
                "DESKTOP_GOOGLE_OAUTH_CLIENT_SECRET" to "local-oauth-secret"
            ),
            systemProperty = { null },
            environment = { null }
        )

        assertTrue(config.isAuthConfigured)
        assertEquals("local-firebase-key", config.firebaseWebApiKey)
        assertEquals("local-project", config.firebaseProjectId)
        assertEquals("local-oauth-client", config.googleOAuthClientId)
        assertEquals("local-oauth-secret", config.googleOAuthClientSecret)
    }
}

private fun properties(vararg values: Pair<String, String>): Properties {
    return Properties().apply {
        values.forEach { (key, value) -> setProperty(key, value) }
    }
}

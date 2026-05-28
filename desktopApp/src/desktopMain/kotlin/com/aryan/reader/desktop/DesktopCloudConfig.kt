package com.aryan.reader.desktop

import java.io.File
import java.util.Properties

internal data class DesktopCloudConfig(
    val aiWorkerUrl: String,
    val ttsWorkerUrl: String,
    val firebaseWebApiKey: String,
    val firebaseProjectId: String,
    val googleOAuthClientId: String,
    val googleOAuthClientSecret: String
) {
    val isAuthConfigured: Boolean
        get() = firebaseWebApiKey.isNotBlank() &&
            firebaseProjectId.isNotBlank() &&
            googleOAuthClientId.isNotBlank()

    val isAiWorkerConfigured: Boolean get() = aiWorkerUrl.isNotBlank()
    val isTtsWorkerConfigured: Boolean get() = ttsWorkerUrl.isNotBlank()
}

internal fun loadDesktopCloudConfig(): DesktopCloudConfig {
    return desktopCloudConfigFromProperties(
        resourceProperties = loadDesktopCloudResourceProperties(),
        localProperties = loadDesktopLocalProperties()
    )
}

private fun loadDesktopCloudResourceProperties(): Properties {
    return Properties().apply {
        val classLoader = DesktopCloudConfig::class.java.classLoader
        val stream = classLoader.getResourceAsStream("desktop-cloud.properties")
            ?: classLoader.getResourceAsStream("common/desktop-cloud.properties")
            ?: System.getProperty(ComposeApplicationResourcesDirProperty)
                ?.let(::File)
                ?.let { resourcesDir ->
                    listOf(
                        resourcesDir.resolve("desktop-cloud.properties"),
                        resourcesDir.resolve("common/desktop-cloud.properties")
                    ).firstOrNull { it.isFile }?.inputStream()
                }
        stream?.use { input -> load(input) }
    }
}

private fun loadDesktopLocalProperties(file: File = File("local.properties")): Properties {
    return Properties().apply {
        file.takeIf { it.isFile }
            ?.inputStream()
            ?.use { input -> load(input) }
    }
}

internal fun desktopCloudConfigFromProperties(
    resourceProperties: Properties,
    localProperties: Properties = Properties(),
    systemProperty: (String) -> String? = { key -> System.getProperty("episteme.desktop.$key") },
    environment: (String) -> String? = { key -> System.getenv(key) }
): DesktopCloudConfig {
    fun value(vararg keys: String): String {
        return keys.firstNotNullOfOrNull { key ->
            systemProperty(key)
                ?: environment("EPISTEME_DESKTOP_${key.uppercase()}")
                ?: environment(key)
                ?: localProperties.getProperty("DESKTOP_$key")
                ?: localProperties.getProperty(key)
                ?: resourceProperties.getProperty(key)
        }?.trim().orEmpty()
    }

    val aiWorkerUrl = value("AI_WORKER_URL").ifBlank {
        "https://reader-ai.aryanrajttps.workers.dev"
    }
    val ttsWorkerUrl = value("TTS_WORKER_URL")

    return DesktopCloudConfig(
        aiWorkerUrl = aiWorkerUrl,
        ttsWorkerUrl = ttsWorkerUrl,
        firebaseWebApiKey = value("FIREBASE_WEB_API_KEY", "GOOGLE_API_KEY"),
        firebaseProjectId = value("FIREBASE_PROJECT_ID").ifBlank { "reader-9fc469d7" },
        googleOAuthClientId = value("GOOGLE_OAUTH_CLIENT_ID", "GOOGLE_WEB_CLIENT_ID", "DEFAULT_WEB_CLIENT_ID"),
        googleOAuthClientSecret = value("GOOGLE_OAUTH_CLIENT_SECRET", "GOOGLE_WEB_CLIENT_SECRET", "DEFAULT_WEB_CLIENT_SECRET")
    )
}

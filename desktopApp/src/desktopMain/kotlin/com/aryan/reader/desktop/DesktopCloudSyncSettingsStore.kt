package com.aryan.reader.desktop

import java.io.File
import java.util.Properties
import java.util.UUID

internal data class DesktopCloudSyncSettings(
    val isSyncEnabled: Boolean = false,
    val isFolderSyncEnabled: Boolean = false
)

internal class DesktopCloudSyncSettingsStore(
    private val settingsFile: File = File(desktopUserConfigRoot(), "cloud-sync.properties")
) {
    fun load(): DesktopCloudSyncSettings {
        if (!settingsFile.isFile) return DesktopCloudSyncSettings()
        val properties = Properties()
        return runCatching {
            settingsFile.inputStream().use(properties::load)
            DesktopCloudSyncSettings(
                isSyncEnabled = properties.getProperty("syncEnabled", "false").toBooleanStrictOrNull() == true,
                isFolderSyncEnabled = properties.getProperty("folderSyncEnabled", "false").toBooleanStrictOrNull() == true
            )
        }.getOrDefault(DesktopCloudSyncSettings())
    }

    fun save(settings: DesktopCloudSyncSettings) {
        settingsFile.parentFile?.mkdirs()
        val properties = Properties().apply {
            setProperty("syncEnabled", settings.isSyncEnabled.toString())
            setProperty("folderSyncEnabled", settings.isFolderSyncEnabled.toString())
        }
        settingsFile.outputStream().use { output ->
            properties.store(output, "Episteme desktop cloud sync")
        }
    }
}

internal class DesktopInstallationIdStore(
    private val settingsFile: File = File(desktopUserConfigRoot(), "installation.properties")
) {
    fun getOrCreateId(): String {
        val properties = Properties()
        val existing = runCatching {
            if (!settingsFile.isFile) return@runCatching null
            settingsFile.inputStream().use(properties::load)
            properties.getProperty("installationId")?.takeIf { it.isNotBlank() }
        }.getOrNull()
        if (existing != null) return existing

        val generated = UUID.randomUUID().toString()
        settingsFile.parentFile?.mkdirs()
        settingsFile.outputStream().use { output ->
            Properties().apply {
                setProperty("installationId", generated)
            }.store(output, "Episteme desktop installation")
        }
        return generated
    }
}

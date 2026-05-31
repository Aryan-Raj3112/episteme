package com.aryan.reader.desktop

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Properties

internal data class DesktopAccountProfile(
    val isProUser: Boolean = false,
    val credits: Int = 0,
    val fetchedAtEpochMillis: Long = 0L
)

// Credits and Pro status are server-owned, so startup only trusts a recent snapshot.
internal const val DesktopAccountProfileCacheTtlMillis: Long = 30L * 60L * 1000L

internal fun DesktopAccountProfile.isFresh(
    nowEpochMillis: Long = System.currentTimeMillis(),
    ttlMillis: Long = DesktopAccountProfileCacheTtlMillis
): Boolean {
    if (fetchedAtEpochMillis <= 0L || ttlMillis <= 0L) return false
    val ageMillis = nowEpochMillis - fetchedAtEpochMillis
    return ageMillis in 0L..ttlMillis
}

internal class DesktopAccountProfileRepository(
    private val config: DesktopCloudConfig,
    private val store: DesktopAccountProfileStore = DesktopAccountProfileStore()
) {
    fun cachedProfile(
        uid: String,
        nowEpochMillis: Long = System.currentTimeMillis()
    ): DesktopAccountProfile? {
        return store.load(uid)?.takeIf { profile -> profile.isFresh(nowEpochMillis) }
    }

    fun saveFetchedProfile(uid: String, profile: DesktopAccountProfile) {
        store.save(uid, profile)
    }

    fun clearCachedProfiles() {
        store.clear()
    }

    suspend fun fetchProfile(uid: String, idToken: String): DesktopAccountProfile = withContext(Dispatchers.IO) {
        if (uid.isBlank() || idToken.isBlank()) return@withContext DesktopAccountProfile()
        val url = "https://firestore.googleapis.com/v1/projects/${urlEncode(config.firebaseProjectId)}/databases/(default)/documents/users/${urlEncode(uid)}"
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Authorization", "Bearer $idToken")
            setRequestProperty("Accept", "application/json")
            connectTimeout = 12_000
            readTimeout = 20_000
        }
        try {
            if (connection.responseCode == HttpURLConnection.HTTP_NOT_FOUND) {
                return@withContext DesktopAccountProfile(fetchedAtEpochMillis = System.currentTimeMillis())
            }
            val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (connection.responseCode !in 200..299) {
                throw IllegalStateException("Could not check account status: HTTP ${connection.responseCode}")
            }
            val fields = DesktopAccountJson.parseToJsonElement(text).jsonObject["fields"].jsonObjectOrNull()
            val profile = DesktopAccountProfile(
                isProUser = fields?.booleanField("isPro") == true,
                credits = fields?.numberField("credits")?.toInt() ?: 0,
                fetchedAtEpochMillis = System.currentTimeMillis()
            )
            profile
        } finally {
            connection.disconnect()
        }
    }
}

internal class DesktopAccountProfileStore(
    private val settingsFile: File = File(desktopUserConfigRoot(), "account_profile.properties")
) {
    fun load(uid: String): DesktopAccountProfile? {
        if (uid.isBlank() || !settingsFile.isFile) return null
        val properties = Properties()
        return runCatching {
            settingsFile.inputStream().use(properties::load)
            if (properties.getProperty("uid", "") != uid) return null
            DesktopAccountProfile(
                isProUser = properties.getProperty("isProUser", "false").toBooleanStrictOrNull() ?: false,
                credits = properties.getProperty("credits", "0").toIntOrNull() ?: 0,
                fetchedAtEpochMillis = properties.getProperty("fetchedAtEpochMillis", "0").toLongOrNull() ?: 0L
            )
        }.getOrNull()
    }

    fun save(uid: String, profile: DesktopAccountProfile) {
        if (uid.isBlank()) return
        val properties = Properties().apply {
            setProperty("uid", uid)
            setProperty("isProUser", profile.isProUser.toString())
            setProperty("credits", profile.credits.toString())
            setProperty("fetchedAtEpochMillis", profile.fetchedAtEpochMillis.toString())
        }
        settingsFile.storePropertiesAtomically(properties, "Episteme desktop account profile")
    }

    fun clear() {
        settingsFile.delete()
    }
}

private val DesktopAccountJson = Json { ignoreUnknownKeys = true }

private fun JsonObject?.booleanField(key: String): Boolean? {
    return this?.get(key)
        ?.jsonObjectOrNull()
        ?.get("booleanValue")
        ?.jsonPrimitive
        ?.contentOrNull
        ?.toBooleanStrictOrNull()
}

private fun JsonObject?.numberField(key: String): Double? {
    val field = this?.get(key)?.jsonObjectOrNull() ?: return null
    return field["integerValue"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()
        ?: field["doubleValue"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()
}

private fun JsonElement?.jsonObjectOrNull(): JsonObject? = this as? JsonObject

private fun urlEncode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())

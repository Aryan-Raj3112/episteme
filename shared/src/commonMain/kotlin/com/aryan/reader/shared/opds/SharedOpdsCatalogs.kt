package com.aryan.reader.shared.opds

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object SharedOpdsCatalogs {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    fun defaultCatalogs(idFactory: () -> String): List<OpdsCatalog> {
        return listOf(
            OpdsCatalog(
                id = idFactory(),
                title = "Project Gutenberg",
                url = "https://m.gutenberg.org/ebooks.opds/",
                isDefault = true
            ),
            OpdsCatalog(
                id = idFactory(),
                title = "Standard Ebooks",
                url = "https://standardebooks.org/feeds/opds",
                isDefault = true
            )
        )
    }

    fun decode(rawJson: String?): List<OpdsCatalog> {
        if (rawJson.isNullOrBlank()) return emptyList()
        return runCatching {
            json.parseToJsonElement(rawJson)
                .jsonArray
                .mapNotNull { it.asCatalogOrNull() }
        }.getOrDefault(emptyList())
    }

    fun decodeOrSeed(rawJson: String?, idFactory: () -> String): List<OpdsCatalog> {
        return decode(rawJson).ifEmpty { defaultCatalogs(idFactory) }
    }

    fun encode(catalogs: List<OpdsCatalog>): String {
        val array = JsonArray(catalogs.map { it.toJsonObject() })
        return json.encodeToString(JsonElement.serializer(), array)
    }

    fun addCatalog(
        catalogs: List<OpdsCatalog>,
        title: String,
        url: String,
        username: String?,
        password: String?,
        idFactory: () -> String
    ): List<OpdsCatalog> {
        val normalizedTitle = title.trim()
        val normalizedUrl = url.trim()
        if (normalizedTitle.isBlank() || normalizedUrl.isBlank()) return catalogs
        return catalogs + OpdsCatalog(
            id = idFactory(),
            title = normalizedTitle,
            url = normalizedUrl,
            username = username.normalizedCredential(),
            password = password.normalizedCredential()
        )
    }

    fun updateCatalog(
        catalogs: List<OpdsCatalog>,
        id: String,
        title: String,
        url: String,
        username: String?,
        password: String?
    ): List<OpdsCatalog> {
        return catalogs.map { catalog ->
            if (catalog.id != id || catalog.isDefault) {
                catalog
            } else {
                catalog.copy(
                    title = title.trim(),
                    url = url.trim(),
                    username = username.normalizedCredential(),
                    password = password.normalizedCredential()
                )
            }
        }
    }

    fun removeCatalog(catalogs: List<OpdsCatalog>, id: String): List<OpdsCatalog> {
        val catalog = catalogs.firstOrNull { it.id == id }
        if (catalog?.isDefault == true) return catalogs
        return catalogs.filterNot { it.id == id }
    }

    private fun JsonElement.asCatalogOrNull(): OpdsCatalog? {
        val obj = runCatching { jsonObject }.getOrNull() ?: return null
        val id = obj.string("id") ?: return null
        val title = obj.string("title") ?: return null
        val url = obj.string("url") ?: return null
        return OpdsCatalog(
            id = id,
            title = title,
            url = url,
            isDefault = obj.boolean("isDefault") ?: false,
            username = obj.string("username").normalizedCredential(),
            password = obj.string("password").normalizedCredential()
        )
    }

    private fun OpdsCatalog.toJsonObject(): JsonObject {
        return JsonObject(
            buildMap {
                put("id", JsonPrimitive(id))
                put("title", JsonPrimitive(title))
                put("url", JsonPrimitive(url))
                put("isDefault", JsonPrimitive(isDefault))
                put("username", username?.let(::JsonPrimitive) ?: JsonNull)
                put("password", password?.let(::JsonPrimitive) ?: JsonNull)
            }
        )
    }

    private fun JsonObject.string(name: String): String? {
        val value = this[name]?.takeUnless { it is JsonNull } ?: return null
        return runCatching { value.jsonPrimitive.contentOrNull }.getOrNull()
    }

    private fun JsonObject.boolean(name: String): Boolean? {
        return runCatching { this[name]?.jsonPrimitive?.booleanOrNull }.getOrNull()
    }

    private fun String?.normalizedCredential(): String? {
        return this?.trim()?.takeIf { it.isNotBlank() }
    }
}

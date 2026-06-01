package com.aryan.reader.desktop

import com.aryan.reader.shared.FileType
import com.aryan.reader.shared.SharedFileCapabilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.io.SequenceInputStream
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Duration
import java.time.Instant
import java.util.Collections
import java.util.UUID

internal data class DesktopCloudBookMetadata(
    val bookId: String = "",
    val title: String? = null,
    val author: String? = null,
    val displayName: String = "",
    val type: String = "",
    val lastPositionCfi: String? = null,
    val lastChapterIndex: Int? = null,
    val locatorBlockIndex: Int? = null,
    val locatorCharOffset: Int? = null,
    val lastPage: Int? = null,
    val progressPercentage: Float? = null,
    val isRecent: Boolean = true,
    val isDeleted: Boolean = false,
    val lastModifiedTimestamp: Long = 0L,
    val readingPositionModifiedTimestamp: Long = 0L,
    val annotationModifiedTimestamp: Long = 0L,
    val bookmarksJson: String? = null,
    val hasAnnotations: Boolean = false,
    val fileContentModifiedTimestamp: Long = 0L,
    val customName: String? = null,
    val highlightsJson: String? = null,
    val seriesName: String? = null,
    val seriesIndex: Double? = null,
    val description: String? = null,
    val originalTitle: String? = null,
    val originalAuthor: String? = null,
    val originalSeriesName: String? = null,
    val originalSeriesIndex: Double? = null,
    val originalDescription: String? = null
)

internal data class DesktopCloudShelfMetadata(
    val name: String = "",
    val bookIds: List<String> = emptyList(),
    val lastModifiedTimestamp: Long = 0L,
    val isDeleted: Boolean = false
)

internal data class DesktopCloudFontMetadata(
    val id: String = "",
    val displayName: String = "",
    val fileName: String = "",
    val fileExtension: String = "",
    val timestamp: Long = 0L,
    val isDeleted: Boolean = false
)

internal data class DesktopDriveFile(
    val id: String,
    val name: String,
    val modifiedTimeMillis: Long = 0L
)

internal class DesktopFirestoreRepository(
    private val config: DesktopCloudConfig,
    private val client: HttpClient = defaultDesktopCloudHttpClient()
) {
    suspend fun getAllBooks(userId: String, idToken: String): List<DesktopCloudBookMetadata> =
        firestoreCollection(userId, "books", idToken).mapNotNull { document ->
            document.fields?.toBookMetadata(document.id)
        }

    suspend fun getBookMetadata(userId: String, bookId: String, idToken: String): DesktopCloudBookMetadata? =
        firestoreDocument(userId, "books", bookId, idToken)?.let { document ->
            document.fields?.toBookMetadata(document.id)
        }

    suspend fun syncBookMetadata(
        userId: String,
        book: DesktopCloudBookMetadata,
        originDeviceId: String,
        idToken: String
    ) {
        val fields = book.toFirestoreFields() + ("originDeviceId" to firestoreString(originDeviceId))
        writeFirestoreDocument(userId, "books", book.bookId, fields, idToken)
    }

    suspend fun getAllShelves(userId: String, idToken: String): List<DesktopCloudShelfMetadata> =
        firestoreCollection(userId, "shelves", idToken).mapNotNull { document ->
            document.fields?.toShelfMetadata(document.id)
        }

    suspend fun syncShelf(
        userId: String,
        shelf: DesktopCloudShelfMetadata,
        originDeviceId: String,
        idToken: String
    ) {
        val fields = shelf.toFirestoreFields() + ("originDeviceId" to firestoreString(originDeviceId))
        writeFirestoreDocument(userId, "shelves", shelf.name, fields, idToken)
    }

    suspend fun getAllFonts(userId: String, idToken: String): List<DesktopCloudFontMetadata> =
        firestoreCollection(userId, "fonts", idToken).mapNotNull { document ->
            document.fields?.toFontMetadata(document.id)
        }

    suspend fun syncFontMetadata(userId: String, font: DesktopCloudFontMetadata, idToken: String) {
        writeFirestoreDocument(userId, "fonts", font.id, font.toFirestoreFields(), idToken)
    }

    suspend fun deleteFontMetadata(userId: String, fontId: String, idToken: String) {
        deleteFirestoreDocument(userId, "fonts", fontId, idToken)
    }

    suspend fun deleteAllUserFirestoreData(userId: String, idToken: String) {
        listOf("books", "shelves", "fonts").forEach { collection ->
            firestoreCollection(userId, collection, idToken).forEach { document ->
                deleteFirestoreDocument(userId, collection, document.id, idToken)
            }
        }
    }

    private suspend fun firestoreCollection(
        userId: String,
        collection: String,
        idToken: String
    ): List<DesktopFirestoreDocument> = withContext(Dispatchers.IO) {
        val response = sendFirestore(
            request = HttpRequest.newBuilder(firestoreCollectionUri(userId, collection))
                .GET()
                .build(),
            idToken = idToken,
            allowNotFound = true
        ) ?: return@withContext emptyList()
        val root = DesktopCloudJson.parseToJsonElement(response).jsonObject
        root["documents"]?.jsonArrayOrNull().orEmpty().mapNotNull { element ->
            val document = element.jsonObjectOrNull() ?: return@mapNotNull null
            DesktopFirestoreDocument(
                id = document.string("name")?.substringAfterLast('/').orEmpty(),
                fields = document["fields"]?.jsonObjectOrNull()
            )
        }
    }

    private suspend fun firestoreDocument(
        userId: String,
        collection: String,
        documentId: String,
        idToken: String
    ): DesktopFirestoreDocument? = withContext(Dispatchers.IO) {
        val response = sendFirestore(
            request = HttpRequest.newBuilder(firestoreDocumentUri(userId, collection, documentId))
                .GET()
                .build(),
            idToken = idToken,
            allowNotFound = true
        ) ?: return@withContext null
        val document = DesktopCloudJson.parseToJsonElement(response).jsonObject
        DesktopFirestoreDocument(
            id = document.string("name")?.substringAfterLast('/').orEmpty(),
            fields = document["fields"]?.jsonObjectOrNull()
        )
    }

    private suspend fun writeFirestoreDocument(
        userId: String,
        collection: String,
        documentId: String,
        fields: Map<String, JsonElement>,
        idToken: String
    ) = withContext(Dispatchers.IO) {
        val body = JsonObject(mapOf("fields" to JsonObject(fields)))
        sendFirestore(
            request = HttpRequest.newBuilder(firestoreDocumentUri(userId, collection, documentId))
                .header("Content-Type", "application/json; charset=UTF-8")
                .method("PATCH", HttpRequest.BodyPublishers.ofString(DesktopCloudJson.encodeToString(JsonElement.serializer(), body)))
                .build(),
            idToken = idToken
        )
        Unit
    }

    private suspend fun deleteFirestoreDocument(
        userId: String,
        collection: String,
        documentId: String,
        idToken: String
    ) = withContext(Dispatchers.IO) {
        sendFirestore(
            request = HttpRequest.newBuilder(firestoreDocumentUri(userId, collection, documentId))
                .DELETE()
                .build(),
            idToken = idToken,
            allowNotFound = true
        )
        Unit
    }

    private fun firestoreCollectionUri(userId: String, collection: String): URI {
        return URI.create("${firestoreBaseUrl()}/users/${pathEncode(userId)}/$collection")
    }

    private fun firestoreDocumentUri(userId: String, collection: String, documentId: String): URI {
        return URI.create("${firestoreCollectionUri(userId, collection)}/${pathEncode(documentId)}")
    }

    private fun firestoreBaseUrl(): String {
        return "https://firestore.googleapis.com/v1/projects/${pathEncode(config.firebaseProjectId)}/databases/(default)/documents"
    }

    private fun sendFirestore(
        request: HttpRequest,
        idToken: String,
        allowNotFound: Boolean = false
    ): String? {
        val authed = HttpRequest.newBuilder(request.uri())
            .timeout(Duration.ofSeconds(30))
            .copyMethodAndBodyFrom(request)
            .header("Authorization", "Bearer $idToken")
            .header("Accept", "application/json")
            .apply {
                request.headers().map().forEach { (name, values) ->
                    values.forEach { value -> header(name, value) }
                }
            }
            .build()
        val response = client.send(authed, HttpResponse.BodyHandlers.ofString(Charsets.UTF_8))
        if (allowNotFound && response.statusCode() == 404) return null
        if (response.statusCode() !in 200..299) {
            throw IllegalStateException("Firestore HTTP ${response.statusCode()}: ${response.body().take(240)}")
        }
        return response.body()
    }
}

internal class DesktopGoogleDriveRepository(
    private val client: HttpClient = defaultDesktopCloudHttpClient()
) {
    suspend fun getFiles(accessToken: String): List<DesktopDriveFile> = withContext(Dispatchers.IO) {
        listFiles(accessToken = accessToken, query = null)
    }

    suspend fun getFileByName(accessToken: String, fileName: String): DesktopDriveFile? = withContext(Dispatchers.IO) {
        listFiles(accessToken, "name = '${driveQueryStringValue(fileName)}' and trashed = false").firstOrNull()
    }

    suspend fun uploadFont(accessToken: String, fileName: String, file: File, extension: String): DesktopDriveFile? =
        uploadNamedFile(
            accessToken = accessToken,
            fileName = fileName,
            file = file,
            contentType = when (extension.lowercase()) {
                "ttf" -> "font/ttf"
                "otf" -> "font/otf"
                "woff2" -> "font/woff2"
                else -> "application/octet-stream"
            }
        )

    suspend fun uploadFile(accessToken: String, bookId: String, file: File, type: FileType): DesktopDriveFile? {
        val extension = SharedFileCapabilities.primaryExtensionFor(type) ?: return null
        val mimeType = SharedFileCapabilities.mimeTypeFor(type) ?: return null
        return uploadNamedFile(
            accessToken = accessToken,
            fileName = "$bookId.$extension",
            file = file,
            contentType = mimeType
        )
    }

    suspend fun uploadAnnotationFile(accessToken: String, bookId: String, file: File): DesktopDriveFile? {
        return uploadNamedFile(
            accessToken = accessToken,
            fileName = desktopCloudAnnotationDriveFileName(bookId),
            file = file,
            contentType = "application/json"
        )
    }

    suspend fun downloadAnnotationFile(accessToken: String, bookId: String, destination: File): Boolean {
        val driveFile = getFileByName(accessToken, desktopCloudAnnotationDriveFileName(bookId))
            ?: return false
        return downloadFile(accessToken, driveFile.id, destination).also { downloaded ->
            if (downloaded && driveFile.modifiedTimeMillis > 0L) {
                destination.setLastModified(driveFile.modifiedTimeMillis)
            }
        }
    }

    suspend fun downloadFile(accessToken: String, fileId: String, destination: File): Boolean = withContext(Dispatchers.IO) {
        destination.parentFile?.mkdirs()
        val temp = File(destination.parentFile ?: File("."), "${destination.name}.${System.nanoTime()}.tmp")
        val request = HttpRequest.newBuilder(
            URI.create("https://www.googleapis.com/drive/v3/files/${pathEncode(fileId)}?alt=media")
        )
            .timeout(Duration.ofMinutes(3))
            .header("Authorization", "Bearer $accessToken")
            .GET()
            .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofFile(temp.toPath()))
        if (response.statusCode() !in 200..299) {
            temp.delete()
            return@withContext false
        }
        Files.move(temp.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
        true
    }

    suspend fun deleteAllFiles(accessToken: String): Boolean = withContext(Dispatchers.IO) {
        getFiles(accessToken).forEach { file ->
            deleteDriveFile(accessToken, file.id)
        }
        true
    }

    suspend fun deleteDriveFile(accessToken: String, fileId: String): Boolean = withContext(Dispatchers.IO) {
        val request = HttpRequest.newBuilder(
            URI.create("https://www.googleapis.com/drive/v3/files/${pathEncode(fileId)}")
        )
            .timeout(Duration.ofSeconds(30))
            .header("Authorization", "Bearer $accessToken")
            .DELETE()
            .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString(Charsets.UTF_8))
        response.statusCode() in 200..299 || response.statusCode() == 404
    }

    private suspend fun uploadNamedFile(
        accessToken: String,
        fileName: String,
        file: File,
        contentType: String
    ): DesktopDriveFile? = withContext(Dispatchers.IO) {
        if (!file.isFile) return@withContext null
        val existingFiles = listFiles(accessToken, "name = '${driveQueryStringValue(fileName)}' and trashed = false")
        existingFiles.drop(1).forEach { duplicate -> deleteDriveFile(accessToken, duplicate.id) }
        val existingFileId = existingFiles.firstOrNull()?.id
        val boundary = "episteme_${UUID.randomUUID().toString().replace("-", "")}"
        val metadata = buildJsonObject {
            put("name", JsonPrimitive(fileName))
            if (existingFileId == null) {
                put("parents", JsonArray(listOf(JsonPrimitive("appDataFolder"))))
            }
        }
        val prefix = buildString {
            append("--")
            append(boundary)
            append("\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n")
            append(DesktopCloudJson.encodeToString(JsonElement.serializer(), metadata))
            append("\r\n--")
            append(boundary)
            append("\r\nContent-Type: ")
            append(contentType)
            append("\r\n\r\n")
        }.toByteArray(Charsets.UTF_8)
        val suffix = "\r\n--$boundary--\r\n".toByteArray(Charsets.UTF_8)
        val uploadUri = if (existingFileId == null) {
            URI.create("https://www.googleapis.com/upload/drive/v3/files?${query("uploadType" to "multipart", "fields" to "id,name,modifiedTime")}")
        } else {
            URI.create("https://www.googleapis.com/upload/drive/v3/files/${pathEncode(existingFileId)}?${query("uploadType" to "multipart", "fields" to "id,name,modifiedTime")}")
        }
        val request = HttpRequest.newBuilder(uploadUri)
            .timeout(Duration.ofMinutes(5))
            .header("Authorization", "Bearer $accessToken")
            .header("Content-Type", "multipart/related; boundary=$boundary")
            .method(
                if (existingFileId == null) "POST" else "PATCH",
                HttpRequest.BodyPublishers.ofInputStream {
                    sequenceInputStream(
                        ByteArrayInputStream(prefix),
                        file.inputStream(),
                        ByteArrayInputStream(suffix)
                    )
                }
            )
            .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString(Charsets.UTF_8))
        if (response.statusCode() !in 200..299) return@withContext null
        val root = DesktopCloudJson.parseToJsonElement(response.body()).jsonObject
        DesktopDriveFile(
            id = root.string("id").orEmpty(),
            name = root.string("name").orEmpty(),
            modifiedTimeMillis = parseDriveModifiedTimeMillis(root.string("modifiedTime"))
        )
    }

    private fun listFiles(accessToken: String, query: String?): List<DesktopDriveFile> {
        val params = buildList {
            add("spaces" to "appDataFolder")
            add("fields" to "files(id,name,modifiedTime)")
            if (!query.isNullOrBlank()) add("q" to query)
        }
        val request = HttpRequest.newBuilder(
            URI.create("https://www.googleapis.com/drive/v3/files?${query(params)}")
        )
            .timeout(Duration.ofSeconds(30))
            .header("Authorization", "Bearer $accessToken")
            .header("Accept", "application/json")
            .GET()
            .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString(Charsets.UTF_8))
        if (response.statusCode() !in 200..299) {
            throw IllegalStateException("Google Drive HTTP ${response.statusCode()}: ${response.body().take(240)}")
        }
        val root = DesktopCloudJson.parseToJsonElement(response.body()).jsonObject
        return root["files"]?.jsonArrayOrNull().orEmpty().mapNotNull { element ->
            val obj = element.jsonObjectOrNull() ?: return@mapNotNull null
            val id = obj.string("id") ?: return@mapNotNull null
            val name = obj.string("name") ?: return@mapNotNull null
            DesktopDriveFile(
                id = id,
                name = name,
                modifiedTimeMillis = parseDriveModifiedTimeMillis(obj.string("modifiedTime"))
            )
        }
    }
}

private fun parseDriveModifiedTimeMillis(value: String?): Long {
    if (value.isNullOrBlank()) return 0L
    return runCatching { Instant.parse(value).toEpochMilli() }.getOrDefault(0L)
}

private data class DesktopFirestoreDocument(
    val id: String,
    val fields: JsonObject?
)

private val DesktopCloudJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    prettyPrint = true
}

private fun defaultDesktopCloudHttpClient(): HttpClient {
    return HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(20))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()
}

private fun DesktopCloudBookMetadata.toFirestoreFields(): Map<String, JsonElement> = mapOf(
    "bookId" to firestoreString(bookId),
    "title" to firestoreNullableString(title),
    "author" to firestoreNullableString(author),
    "displayName" to firestoreString(displayName),
    "type" to firestoreString(type),
    "lastPositionCfi" to firestoreNullableString(lastPositionCfi),
    "lastChapterIndex" to firestoreNullableInt(lastChapterIndex),
    "locatorBlockIndex" to firestoreNullableInt(locatorBlockIndex),
    "locatorCharOffset" to firestoreNullableInt(locatorCharOffset),
    "lastPage" to firestoreNullableInt(lastPage),
    "progressPercentage" to firestoreNullableFloat(progressPercentage),
    "isRecent" to firestoreBoolean(isRecent),
    "isDeleted" to firestoreBoolean(isDeleted),
    "lastModifiedTimestamp" to firestoreLong(lastModifiedTimestamp),
    "readingPositionModifiedTimestamp" to firestoreLong(readingPositionModifiedTimestamp),
    "annotationModifiedTimestamp" to firestoreLong(annotationModifiedTimestamp),
    "bookmarksJson" to firestoreNullableString(bookmarksJson),
    "hasAnnotations" to firestoreBoolean(hasAnnotations),
    "fileContentModifiedTimestamp" to firestoreLong(fileContentModifiedTimestamp),
    "customName" to firestoreNullableString(customName),
    "highlightsJson" to firestoreNullableString(highlightsJson),
    "seriesName" to firestoreNullableString(seriesName),
    "seriesIndex" to firestoreNullableDouble(seriesIndex),
    "description" to firestoreNullableString(description),
    "originalTitle" to firestoreNullableString(originalTitle),
    "originalAuthor" to firestoreNullableString(originalAuthor),
    "originalSeriesName" to firestoreNullableString(originalSeriesName),
    "originalSeriesIndex" to firestoreNullableDouble(originalSeriesIndex),
    "originalDescription" to firestoreNullableString(originalDescription)
)

private fun DesktopCloudShelfMetadata.toFirestoreFields(): Map<String, JsonElement> = mapOf(
    "name" to firestoreString(name),
    "bookIds" to firestoreStringArray(bookIds),
    "lastModifiedTimestamp" to firestoreLong(lastModifiedTimestamp),
    "isDeleted" to firestoreBoolean(isDeleted)
)

private fun DesktopCloudFontMetadata.toFirestoreFields(): Map<String, JsonElement> = mapOf(
    "id" to firestoreString(id),
    "displayName" to firestoreString(displayName),
    "fileName" to firestoreString(fileName),
    "fileExtension" to firestoreString(fileExtension),
    "timestamp" to firestoreLong(timestamp),
    "isDeleted" to firestoreBoolean(isDeleted)
)

private fun JsonObject.toBookMetadata(documentId: String): DesktopCloudBookMetadata? {
    val bookId = stringField("bookId") ?: documentId.takeIf { it.isNotBlank() } ?: return null
    return DesktopCloudBookMetadata(
        bookId = bookId,
        title = stringField("title"),
        author = stringField("author"),
        displayName = stringField("displayName").orEmpty(),
        type = stringField("type").orEmpty(),
        lastPositionCfi = stringField("lastPositionCfi"),
        lastChapterIndex = intField("lastChapterIndex"),
        locatorBlockIndex = intField("locatorBlockIndex"),
        locatorCharOffset = intField("locatorCharOffset"),
        lastPage = intField("lastPage"),
        progressPercentage = doubleField("progressPercentage")?.toFloat(),
        isRecent = booleanField("isRecent") ?: true,
        isDeleted = booleanField("isDeleted") ?: false,
        lastModifiedTimestamp = longField("lastModifiedTimestamp"),
        readingPositionModifiedTimestamp = longField("readingPositionModifiedTimestamp"),
        annotationModifiedTimestamp = longField("annotationModifiedTimestamp"),
        bookmarksJson = stringField("bookmarksJson"),
        hasAnnotations = booleanField("hasAnnotations") ?: false,
        fileContentModifiedTimestamp = longField("fileContentModifiedTimestamp"),
        customName = stringField("customName"),
        highlightsJson = stringField("highlightsJson"),
        seriesName = stringField("seriesName"),
        seriesIndex = doubleField("seriesIndex"),
        description = stringField("description"),
        originalTitle = stringField("originalTitle"),
        originalAuthor = stringField("originalAuthor"),
        originalSeriesName = stringField("originalSeriesName"),
        originalSeriesIndex = doubleField("originalSeriesIndex"),
        originalDescription = stringField("originalDescription")
    )
}

private fun JsonObject.toShelfMetadata(documentId: String): DesktopCloudShelfMetadata? {
    val name = stringField("name") ?: documentId.takeIf { it.isNotBlank() } ?: return null
    return DesktopCloudShelfMetadata(
        name = name,
        bookIds = stringArrayField("bookIds"),
        lastModifiedTimestamp = longField("lastModifiedTimestamp"),
        isDeleted = booleanField("isDeleted") ?: false
    )
}

private fun JsonObject.toFontMetadata(documentId: String): DesktopCloudFontMetadata? {
    val id = stringField("id") ?: documentId.takeIf { it.isNotBlank() } ?: return null
    return DesktopCloudFontMetadata(
        id = id,
        displayName = stringField("displayName").orEmpty(),
        fileName = stringField("fileName").orEmpty(),
        fileExtension = stringField("fileExtension").orEmpty(),
        timestamp = longField("timestamp"),
        isDeleted = booleanField("isDeleted") ?: false
    )
}

private fun firestoreString(value: String): JsonElement = JsonObject(mapOf("stringValue" to JsonPrimitive(value)))

private fun firestoreNullableString(value: String?): JsonElement {
    return value?.let(::firestoreString) ?: firestoreNull()
}

private fun firestoreNullableInt(value: Int?): JsonElement {
    return value?.let { JsonObject(mapOf("integerValue" to JsonPrimitive(it.toString()))) } ?: firestoreNull()
}

private fun firestoreLong(value: Long): JsonElement = JsonObject(mapOf("integerValue" to JsonPrimitive(value.toString())))

private fun firestoreNullableFloat(value: Float?): JsonElement {
    return value?.let { JsonObject(mapOf("doubleValue" to JsonPrimitive(it.toDouble()))) } ?: firestoreNull()
}

private fun firestoreNullableDouble(value: Double?): JsonElement {
    return value?.let { JsonObject(mapOf("doubleValue" to JsonPrimitive(it))) } ?: firestoreNull()
}

private fun firestoreBoolean(value: Boolean): JsonElement = JsonObject(mapOf("booleanValue" to JsonPrimitive(value)))

private fun firestoreStringArray(values: List<String>): JsonElement {
    return JsonObject(
        mapOf(
            "arrayValue" to JsonObject(
                mapOf("values" to JsonArray(values.map(::firestoreString)))
            )
        )
    )
}

private fun firestoreNull(): JsonElement = JsonObject(mapOf("nullValue" to JsonPrimitive("NULL_VALUE")))

private fun JsonObject.stringField(key: String): String? {
    return field(key)?.get("stringValue")?.jsonPrimitive?.contentOrNull
}

private fun JsonObject.booleanField(key: String): Boolean? {
    return field(key)?.get("booleanValue")?.jsonPrimitive?.booleanOrNull
}

private fun JsonObject.longField(key: String): Long {
    val field = field(key) ?: return 0L
    return field["integerValue"]?.jsonPrimitive?.longOrNull
        ?: field["doubleValue"]?.jsonPrimitive?.doubleOrNull?.toLong()
        ?: 0L
}

private fun JsonObject.intField(key: String): Int? {
    val field = field(key) ?: return null
    return field["integerValue"]?.jsonPrimitive?.intOrNull
        ?: field["doubleValue"]?.jsonPrimitive?.doubleOrNull?.toInt()
}

private fun JsonObject.doubleField(key: String): Double? {
    val field = field(key) ?: return null
    return field["doubleValue"]?.jsonPrimitive?.doubleOrNull
        ?: field["integerValue"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()
}

private fun JsonObject.stringArrayField(key: String): List<String> {
    val values = field(key)
        ?.get("arrayValue")
        ?.jsonObjectOrNull()
        ?.get("values")
        ?.jsonArrayOrNull()
        .orEmpty()
    return values.mapNotNull { it.jsonObjectOrNull()?.get("stringValue")?.jsonPrimitive?.contentOrNull }
}

private fun JsonObject.field(key: String): JsonObject? = this[key]?.jsonObjectOrNull()

private fun JsonElement?.jsonObjectOrNull(): JsonObject? {
    if (this == null || this is JsonNull) return null
    return runCatching { jsonObject }.getOrNull()
}

private fun JsonElement?.jsonArrayOrNull(): JsonArray? {
    if (this == null || this is JsonNull) return null
    return runCatching { jsonArray }.getOrNull()
}

private fun JsonObject.string(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull

private fun query(vararg pairs: Pair<String, String>): String = query(pairs.asIterable())

private fun query(pairs: Iterable<Pair<String, String>>): String {
    return pairs.joinToString("&") { (key, value) -> "${formEncode(key)}=${formEncode(value)}" }
}

private fun formEncode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())

private fun pathEncode(value: String): String = formEncode(value).replace("+", "%20")

private fun driveQueryStringValue(value: String): String {
    return value.replace("\\", "\\\\").replace("'", "\\'")
}

private fun sequenceInputStream(vararg streams: InputStream): SequenceInputStream {
    return SequenceInputStream(Collections.enumeration(streams.toList()))
}

private fun HttpRequest.Builder.copyMethodAndBodyFrom(source: HttpRequest): HttpRequest.Builder {
    val publisher = source.bodyPublisher().orElse(HttpRequest.BodyPublishers.noBody())
    return method(source.method(), publisher)
}

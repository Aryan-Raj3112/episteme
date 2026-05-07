package com.aryan.reader.desktop

import com.aryan.reader.shared.opds.OpdsAcquisition
import com.aryan.reader.shared.opds.OpdsCatalog
import com.aryan.reader.shared.opds.OpdsEntry
import com.aryan.reader.shared.opds.OpdsFeed
import com.aryan.reader.shared.opds.SharedOpdsCatalogs
import com.aryan.reader.shared.opds.SharedOpdsDownloadNamer
import com.aryan.reader.shared.opds.SharedOpdsParser
import com.aryan.reader.shared.opds.SharedOpdsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.Authenticator
import java.net.PasswordAuthentication
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.UUID

internal class DesktopOpdsRepository(
    private val catalogFile: File = defaultCatalogFile(),
    private val idFactory: () -> String = { UUID.randomUUID().toString() }
) : SharedOpdsRepository {
    private val parser = SharedOpdsParser()

    override fun loadCatalogs(): List<OpdsCatalog> {
        val rawJson = catalogFile.takeIf { it.exists() }?.readText()
        val decodedCatalogs = SharedOpdsCatalogs.decode(rawJson)
        val catalogs = decodedCatalogs.ifEmpty { SharedOpdsCatalogs.defaultCatalogs(idFactory) }
        if (decodedCatalogs.isEmpty()) saveCatalogs(catalogs)
        return catalogs
    }

    override fun saveCatalogs(catalogs: List<OpdsCatalog>) {
        catalogFile.parentFile?.mkdirs()
        catalogFile.writeText(SharedOpdsCatalogs.encode(catalogs))
    }

    override suspend fun fetchFeed(url: String, username: String?, password: String?): Result<OpdsFeed> = withContext(Dispatchers.IO) {
        runCatching {
            val response = DesktopOpdsHttp.fetchString(url, username, password)
            if (response.statusCode !in 200..299) {
                error("HTTP ${response.statusCode}")
            }
            if (response.body.isBlank()) error("Empty response body")
            parser.parse(response.body, url)
        }
    }

    override suspend fun getSearchTemplate(openSearchUrl: String, username: String?, password: String?): String? = withContext(Dispatchers.IO) {
        runCatching {
            val response = DesktopOpdsHttp.fetchString(openSearchUrl, username, password)
            if (response.statusCode !in 200..299) return@withContext null
            parser.extractOpenSearchTemplate(response.body, openSearchUrl)
        }.getOrNull()
    }

    suspend fun downloadBook(
        entry: OpdsEntry,
        acquisition: OpdsAcquisition,
        catalog: OpdsCatalog?,
        onProgress: (Float?) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val response = DesktopOpdsHttp.fetchStream(acquisition.url, catalog?.username, catalog?.password)
        if (response.statusCode !in 200..299) {
            response.body.close()
            error("HTTP ${response.statusCode}")
        }

        val contentLength = response.headers.firstValueAsLong("content-length").orElse(-1L)
        val contentDisposition = response.headers.firstValue("content-disposition").orElse(null)
        val urlName = runCatching {
            URI(acquisition.url).path.substringAfterLast('/').takeIf { it.isNotBlank() }
        }.getOrNull()
        val extension = SharedOpdsDownloadNamer.resolveExtension(acquisition, contentDisposition, urlName)
        val target = uniqueDownloadFile(SharedOpdsDownloadNamer.safeFileStem(entry.title), extension)

        response.body.use { input ->
            target.outputStream().use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var totalRead = 0L
                var lastProgressAt = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    if (read > 0) {
                        output.write(buffer, 0, read)
                        totalRead += read
                        if (contentLength > 0) {
                            val now = System.currentTimeMillis()
                            if (now - lastProgressAt >= 200L) {
                                onProgress((totalRead.toFloat() / contentLength.toFloat()).coerceIn(0f, 1f))
                                lastProgressAt = now
                            }
                        }
                    }
                }
            }
        }
        onProgress(1f)
        target
    }

    fun catalogById(id: String?): OpdsCatalog? {
        if (id.isNullOrBlank()) return null
        return loadCatalogs().firstOrNull { it.id == id }
    }

    private fun uniqueDownloadFile(stem: String, extension: String): File {
        val dir = opdsDownloadsDir().apply { mkdirs() }
        var candidate = File(dir, "$stem$extension")
        var index = 1
        while (candidate.exists()) {
            candidate = File(dir, "${stem}_$index$extension")
            index += 1
        }
        return candidate
    }

    companion object {
        fun defaultCatalogFile(): File {
            return File(DesktopLibraryDatabase.defaultDatabaseFile().parentFile, "opds_catalogs.json")
        }

        fun opdsDownloadsDir(): File {
            return File(DesktopLibraryDatabase.defaultDatabaseFile().parentFile, "opds_downloads")
        }
    }
}

internal data class DesktopOpdsTextResponse(
    val statusCode: Int,
    val body: String
)

internal data class DesktopOpdsStreamResponse(
    val statusCode: Int,
    val headers: java.net.http.HttpHeaders,
    val body: java.io.InputStream
)

internal object DesktopOpdsHttp {
    fun fetchString(url: String, username: String?, password: String?): DesktopOpdsTextResponse {
        val request = request(url).build()
        val response = client(username, password).send(request, HttpResponse.BodyHandlers.ofString())
        return DesktopOpdsTextResponse(response.statusCode(), response.body().orEmpty())
    }

    fun fetchStream(url: String, username: String?, password: String?): DesktopOpdsStreamResponse {
        val request = request(url).build()
        val response = client(username, password).send(request, HttpResponse.BodyHandlers.ofInputStream())
        return DesktopOpdsStreamResponse(response.statusCode(), response.headers(), response.body())
    }

    fun fetchBytes(url: String, catalog: OpdsCatalog?): ByteArray {
        val request = request(url).build()
        val response = client(catalog?.username, catalog?.password).send(request, HttpResponse.BodyHandlers.ofByteArray())
        if (response.statusCode() !in 200..299) {
            error("HTTP ${response.statusCode()}")
        }
        return response.body()
    }

    private fun request(url: String): HttpRequest.Builder {
        return HttpRequest.newBuilder(URI(url.trim()))
            .timeout(Duration.ofSeconds(45))
            .header("User-Agent", "EpistemeReader/1.0 (Desktop)")
    }

    private fun client(username: String?, password: String?): HttpClient {
        val builder = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .followRedirects(HttpClient.Redirect.NORMAL)

        if (!username.isNullOrBlank() && !password.isNullOrBlank()) {
            builder.authenticator(
                object : Authenticator() {
                    override fun getPasswordAuthentication(): PasswordAuthentication {
                        return PasswordAuthentication(username, password.toCharArray())
                    }
                }
            )
        }

        return builder.build()
    }
}

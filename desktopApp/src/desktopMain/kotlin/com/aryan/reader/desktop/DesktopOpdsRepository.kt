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
import java.io.Closeable
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.MessageDigest
import java.time.Duration
import java.util.Base64
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
        val response = send(url, username, password, HttpResponse.BodyHandlers.ofString())
        return DesktopOpdsTextResponse(response.statusCode(), response.body().orEmpty())
    }

    fun fetchStream(url: String, username: String?, password: String?): DesktopOpdsStreamResponse {
        val response = send(url, username, password, HttpResponse.BodyHandlers.ofInputStream())
        return DesktopOpdsStreamResponse(response.statusCode(), response.headers(), response.body())
    }

    fun fetchBytes(url: String, catalog: OpdsCatalog?): ByteArray {
        val response = send(url, catalog?.username, catalog?.password, HttpResponse.BodyHandlers.ofByteArray())
        if (response.statusCode() !in 200..299) {
            error("HTTP ${response.statusCode()}")
        }
        return response.body()
    }

    private fun <T> send(
        url: String,
        username: String?,
        password: String?,
        bodyHandler: HttpResponse.BodyHandler<T>
    ): HttpResponse<T> {
        ensureNetworkAccess()
        val uri = URI(url.trim())
        val response = client().send(request(uri).build(), bodyHandler)
        val challenge = response.headers().firstValue("www-authenticate").orElse(null)
        val authorization = if (response.statusCode() == 401) {
            authorizationHeaderForChallenge(
                challenge = challenge,
                url = uri.toString(),
                username = username,
                password = password
            )
        } else {
            null
        }
        if (authorization == null) return response

        (response.body() as? Closeable)?.close()
        return client().send(
            request(uri)
                .header("Authorization", authorization)
                .build(),
            bodyHandler
        )
    }

    private fun request(uri: URI): HttpRequest.Builder {
        return HttpRequest.newBuilder(uri)
            .timeout(Duration.ofSeconds(45))
            .header("User-Agent", "EpistemeReader/1.0 (Desktop)")
    }

    private fun client(): HttpClient {
        return HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build()
    }

    private fun ensureNetworkAccess() {
        check(currentDesktopBuildProfile().featurePolicy.networkAccess) {
            "Network access is disabled in this desktop build."
        }
    }

    internal fun authorizationHeaderForChallenge(
        challenge: String?,
        url: String,
        username: String?,
        password: String?,
        method: String = "GET",
        cnonce: String = UUID.randomUUID().toString().replace("-", ""),
        nonceCount: String = "00000001"
    ): String? {
        if (challenge.isNullOrBlank() || username.isNullOrBlank() || password.isNullOrBlank()) return null
        return when {
            challenge.startsWith("Basic", ignoreCase = true) -> {
                val credentials = "$username:$password".toByteArray(Charsets.ISO_8859_1)
                "Basic ${Base64.getEncoder().encodeToString(credentials)}"
            }

            challenge.startsWith("Digest", ignoreCase = true) -> {
                val params = parseAuthParams(challenge)
                val realm = params["realm"].orEmpty()
                val nonce = params["nonce"] ?: return null
                val qop = params["qop"]
                    ?.split(',')
                    ?.map { it.trim().trim('"') }
                    ?.firstOrNull { it.equals("auth", ignoreCase = true) }
                val opaque = params["opaque"]
                val uri = URI(url)
                val requestUri = buildString {
                    append(uri.rawPath.takeIf { !it.isNullOrBlank() } ?: "/")
                    uri.rawQuery?.let { append('?').append(it) }
                }
                val ha1 = md5("$username:$realm:$password")
                val ha2 = md5("${method.uppercase()}:$requestUri")
                val responseHash = if (qop != null) {
                    md5("$ha1:$nonce:$nonceCount:$cnonce:$qop:$ha2")
                } else {
                    md5("$ha1:$nonce:$ha2")
                }

                buildString {
                    append("Digest username=\"${username.escapeAuthQuote()}\", ")
                    append("realm=\"${realm.escapeAuthQuote()}\", ")
                    append("nonce=\"${nonce.escapeAuthQuote()}\", ")
                    append("uri=\"${requestUri.escapeAuthQuote()}\", ")
                    append("response=\"$responseHash\"")
                    if (qop != null) {
                        append(", qop=$qop, nc=$nonceCount, cnonce=\"${cnonce.escapeAuthQuote()}\"")
                    }
                    if (opaque != null) {
                        append(", opaque=\"${opaque.escapeAuthQuote()}\"")
                    }
                }
            }

            else -> null
        }
    }

    private fun parseAuthParams(challenge: String): Map<String, String> {
        return Regex("""(\w+)=(?:"([^"]*)"|([^,\s]+))""")
            .findAll(challenge)
            .associate { match ->
                match.groupValues[1].lowercase() to (match.groupValues[2].ifBlank { match.groupValues[3] })
            }
    }

    private fun md5(input: String): String {
        val bytes = MessageDigest.getInstance("MD5").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun String.escapeAuthQuote(): String {
        return replace("\\", "\\\\").replace("\"", "\\\"")
    }
}

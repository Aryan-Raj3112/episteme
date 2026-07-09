package com.aryan.reader.shared.ios

import com.aryan.reader.shared.currentTimestamp
import com.aryan.reader.shared.opds.OpdsAcquisition
import com.aryan.reader.shared.opds.OpdsAuthor
import com.aryan.reader.shared.opds.OpdsCatalog
import com.aryan.reader.shared.opds.OpdsEntry
import com.aryan.reader.shared.opds.OpdsFacet
import com.aryan.reader.shared.opds.OpdsFeed
import com.aryan.reader.shared.opds.SharedOpdsCatalogs
import com.aryan.reader.shared.opds.SharedOpdsDownloadNamer
import com.aryan.reader.shared.opds.SharedOpdsRepository
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import platform.Foundation.NSData
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSError
import platform.Foundation.NSFileManager
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSMutableData
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSURLAuthenticationChallenge
import platform.Foundation.NSURLCredential
import platform.Foundation.NSURLCredentialPersistence
import platform.Foundation.NSURLRequestReloadIgnoringLocalCacheData
import platform.Foundation.NSURLResponse
import platform.Foundation.NSURLSession
import platform.Foundation.NSURLSessionAuthChallengePerformDefaultHandling
import platform.Foundation.NSURLSessionAuthChallengeUseCredential
import platform.Foundation.NSURLSessionConfiguration
import platform.Foundation.NSURLSessionDataDelegateProtocol
import platform.Foundation.NSURLSessionDataTask
import platform.Foundation.NSURLSessionResponseAllow
import platform.Foundation.NSURLSessionTask
import platform.Foundation.NSUserDefaults
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDomainMask
import platform.Foundation.HTTPMethod
import platform.Foundation.appendData
import platform.Foundation.dataTaskWithRequest
import platform.Foundation.dataWithLength
import platform.Foundation.create
import platform.Foundation.lastPathComponent
import platform.Foundation.setValue
import platform.Foundation.writeToURL
import platform.darwin.NSObject

internal class IosOpdsRepository : SharedOpdsRepository {
    private val parser = IosOpdsParser()
    private val httpClient = IosUrlSessionHttpClient()

    override fun loadCatalogs(): List<OpdsCatalog> {
        val encoded = NSUserDefaults.standardUserDefaults.stringForKey(KeyCatalogsJson)
        val decoded = SharedOpdsCatalogs.decode(encoded)
        val catalogs = decoded.ifEmpty {
            SharedOpdsCatalogs.defaultCatalogs { IosOpdsCatalogIds.next() }
        }.withUniqueIds()
        if (decoded.isEmpty() || catalogs != decoded) {
            saveCatalogs(catalogs)
        }
        return catalogs
    }

    override fun saveCatalogs(catalogs: List<OpdsCatalog>) {
        NSUserDefaults.standardUserDefaults.setObject(
            SharedOpdsCatalogs.encode(catalogs),
            forKey = KeyCatalogsJson
        )
    }

    override suspend fun fetchFeed(url: String, username: String?, password: String?): Result<OpdsFeed> {
        return runCatching {
            val response = fetch(url = url.trim(), username = username, password = password)
            if (response.statusCode !in 200..299) {
                error("HTTP ${response.statusCode}")
            }
            if (response.body.isBlank()) error("Empty response body")
            parser.parse(response.body, url)
        }
    }

    override suspend fun getSearchTemplate(openSearchUrl: String, username: String?, password: String?): String? {
        return runCatching {
            val response = fetch(openSearchUrl, username, password)
            if (response.statusCode !in 200..299) return@runCatching null
            parser.extractOpenSearchTemplate(response.body, openSearchUrl)
        }.getOrNull()
    }

    suspend fun downloadBook(
        entry: OpdsEntry,
        acquisition: OpdsAcquisition,
        username: String?,
        password: String?
    ): Result<IosDownloadedOpdsBook> {
        return runCatching {
            val response = fetch(acquisition.url, username, password)
            if (response.statusCode !in 200..299) error("HTTP ${response.statusCode}")
            val data = response.data ?: error("Empty response body")
            val extension = SharedOpdsDownloadNamer.resolveExtension(
                acquisition = acquisition,
                contentDisposition = response.headers["Content-Disposition"],
                urlPathSegment = acquisition.url.substringBefore('?').substringBefore('#').substringAfterLast('/')
            )
            val fileName = "opds_dl_${SharedOpdsDownloadNamer.safeFileStem(entry.title).take(50)}$extension"
            val fileUrl = documentsDirectoryUrl()
                ?: error("Could not access iOS Documents directory")
            val destination = fileUrl.URLByAppendingPathComponent(uniqueFileName(fileUrl, fileName))
                ?: error("Could not create destination URL")
            if (!data.writeToURL(destination, atomically = true)) {
                error("Could not save downloaded file")
            }
            IosDownloadedOpdsBook(
                name = destination.lastPathComponent ?: fileName,
                path = destination.path ?: acquisition.url
            )
        }
    }

    private suspend fun fetch(url: String, username: String?, password: String?): IosHttpResponse {
        val response = httpClient.fetch(url = url, username = username, password = password)
        return toResponse(response.data, response.response, response.error)
    }

    private fun toResponse(data: NSData?, response: NSURLResponse?, error: NSError?): IosHttpResponse {
        if (error != null) error(error.localizedDescription)
        val httpResponse = response as? NSHTTPURLResponse
        val body = data?.let { NSStringFromData(it) }.orEmpty()
        val headers = httpResponse?.allHeaderFields
            ?.mapNotNull { (key, value) -> (key as? String)?.let { it to value.toString() } }
            ?.toMap()
            .orEmpty()
        return IosHttpResponse(
            statusCode = httpResponse?.statusCode?.toInt() ?: 200,
            body = body,
            data = data,
            headers = headers
        )
    }

    private fun documentsDirectoryUrl(): NSURL? {
        return NSFileManager.defaultManager.URLsForDirectory(
            directory = NSDocumentDirectory,
            inDomains = NSUserDomainMask
        ).firstOrNull() as? NSURL
    }

    private fun uniqueFileName(directoryUrl: NSURL, preferredName: String): String {
        val stem = preferredName.substringBeforeLast('.', preferredName)
        val extension = preferredName.substringAfterLast('.', missingDelimiterValue = "")
            .takeIf { it.isNotBlank() }
            ?.let { ".$it" }
            .orEmpty()
        var candidate = preferredName
        var suffix = 1
        while (NSFileManager.defaultManager.fileExistsAtPath(directoryUrl.URLByAppendingPathComponent(candidate)?.path.orEmpty())) {
            candidate = "${stem}_${suffix++}$extension"
        }
        return candidate
    }

    private companion object {
        private const val KeyCatalogsJson = "reader_ios_opds_catalogs_json"
    }
}

internal object IosOpdsCatalogIds {
    private var counter = 0

    fun next(): String {
        counter += 1
        return "ios_catalog_${currentTimestamp()}_$counter"
    }
}

private fun List<OpdsCatalog>.withUniqueIds(): List<OpdsCatalog> {
    val seen = mutableSetOf<String>()
    return map { catalog ->
        if (seen.add(catalog.id)) {
            catalog
        } else {
            var nextId: String
            do {
                nextId = IosOpdsCatalogIds.next()
            } while (!seen.add(nextId))
            catalog.copy(id = nextId)
        }
    }
}

internal data class IosDownloadedOpdsBook(
    val name: String,
    val path: String
)

private data class IosHttpResponse(
    val statusCode: Int,
    val body: String,
    val data: NSData?,
    val headers: Map<String, String>
)

private data class IosUrlSessionResponse(
    val data: NSData?,
    val response: NSURLResponse?,
    val error: NSError?
)

private fun NSStringFromData(data: NSData): String {
    return platform.Foundation.NSString.create(data = data, encoding = NSUTF8StringEncoding)?.toString().orEmpty()
}

private class IosUrlSessionHttpClient {
    suspend fun fetch(url: String, username: String?, password: String?): IosUrlSessionResponse {
        val nsUrl = NSURL.URLWithString(url.trim()) ?: error("Invalid URL: $url")
        val request = NSMutableURLRequest.requestWithURL(
            URL = nsUrl,
            cachePolicy = NSURLRequestReloadIgnoringLocalCacheData,
            timeoutInterval = 45.0
        ).apply {
            HTTPMethod = "GET"
            setValue("EpistemeReader/1.0 (iOS)", forHTTPHeaderField = "User-Agent")
            basicAuthHeader(username, password)?.let { authHeader ->
                setValue(authHeader, forHTTPHeaderField = "Authorization")
            }
        }

        return suspendCancellableCoroutine { continuation ->
            val delegate = IosUrlSessionDelegate(
                username = username,
                password = password,
                onComplete = { result ->
                    if (continuation.isActive) {
                        continuation.resumeWith(result)
                    }
                }
            )
            val configuration = NSURLSessionConfiguration.defaultSessionConfiguration.apply {
                setTimeoutIntervalForRequest(45.0)
                setTimeoutIntervalForResource(90.0)
            }
            val session = NSURLSession.sessionWithConfiguration(
                configuration = configuration,
                delegate = delegate,
                delegateQueue = null
            )
            val task = session.dataTaskWithRequest(request)
            continuation.invokeOnCancellation {
                task.cancel()
                session.invalidateAndCancel()
            }
            task.resume()
        }
    }
}

private class IosUrlSessionDelegate(
    private val username: String?,
    private val password: String?,
    private val onComplete: (Result<IosUrlSessionResponse>) -> Unit
) : NSObject(), NSURLSessionDataDelegateProtocol {
    private var response: NSURLResponse? = null
    private val data = NSMutableData.dataWithLength(0u) ?: NSMutableData()
    private var completed = false

    override fun URLSession(
        session: NSURLSession,
        dataTask: NSURLSessionDataTask,
        didReceiveResponse: NSURLResponse,
        completionHandler: (Long) -> Unit
    ) {
        response = didReceiveResponse
        completionHandler(NSURLSessionResponseAllow)
    }

    override fun URLSession(
        session: NSURLSession,
        dataTask: NSURLSessionDataTask,
        didReceiveData: NSData
    ) {
        data.appendData(didReceiveData)
    }

    override fun URLSession(
        session: NSURLSession,
        task: NSURLSessionTask,
        didCompleteWithError: NSError?
    ) {
        if (completed) return
        completed = true
        onComplete(Result.success(IosUrlSessionResponse(data = data, response = response, error = didCompleteWithError)))
        session.finishTasksAndInvalidate()
    }

    override fun URLSession(
        session: NSURLSession,
        task: NSURLSessionTask,
        didReceiveChallenge: NSURLAuthenticationChallenge,
        completionHandler: (Long, NSURLCredential?) -> Unit
    ) {
        val user = username?.takeIf { it.isNotBlank() }
        val pass = password?.takeIf { it.isNotBlank() }
        if (user == null || pass == null) {
            completionHandler(NSURLSessionAuthChallengePerformDefaultHandling, null)
            return
        }
        val credential = NSURLCredential.create(
            user = user,
            password = pass,
            persistence = NSURLCredentialPersistence.NSURLCredentialPersistenceForSession
        )
        completionHandler(NSURLSessionAuthChallengeUseCredential, credential)
    }
}

private fun basicAuthHeader(username: String?, password: String?): String? {
    val user = username?.takeIf { it.isNotBlank() } ?: return null
    return "Basic ${base64Encode("$user:${password.orEmpty()}".encodeToByteArray())}"
}

private fun base64Encode(bytes: ByteArray): String {
    if (bytes.isEmpty()) return ""
    val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
    val output = StringBuilder(((bytes.size + 2) / 3) * 4)
    var index = 0
    while (index < bytes.size) {
        val first = bytes[index++].toInt() and 0xFF
        val second = if (index < bytes.size) bytes[index++].toInt() and 0xFF else -1
        val third = if (index < bytes.size) bytes[index++].toInt() and 0xFF else -1
        output.append(alphabet[first shr 2])
        output.append(alphabet[((first and 0x03) shl 4) or ((second.coerceAtLeast(0) and 0xF0) shr 4)])
        output.append(if (second >= 0) alphabet[((second and 0x0F) shl 2) or ((third.coerceAtLeast(0) and 0xC0) shr 6)] else '=')
        output.append(if (third >= 0) alphabet[third and 0x3F] else '=')
    }
    return output.toString()
}

private class IosOpdsParser {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun parse(bodyString: String, baseUrl: String): OpdsFeed {
        val trimmed = bodyString.trimStart()
        return if (trimmed.startsWith("{")) parseOpds2(trimmed, baseUrl) else parseOpds1(trimmed, baseUrl)
    }

    fun extractOpenSearchTemplate(bodyString: String, openSearchUrl: String): String? {
        return XmlElement.findAll(bodyString, "Url")
            .mapNotNull { url ->
                val type = url.attr("type").orEmpty()
                val template = url.attr("template")
                if (
                    !template.isNullOrBlank() &&
                    (type.contains("atom+xml", ignoreCase = true) || type.contains("opds+xml", ignoreCase = true))
                ) {
                    OpenSearchTemplateCandidate(
                        template = resolveUrl(openSearchUrl, template),
                        priority = openSearchTemplatePriority(type)
                    )
                } else {
                    null
                }
            }
            .maxByOrNull { it.priority }
            ?.template
    }

    private fun parseOpds2(jsonString: String, baseUrl: String): OpdsFeed {
        val root = json.parseToJsonElement(jsonString).jsonObject
        val metadata = root.obj("metadata")
        val title = metadata?.string("title") ?: "OPDS 2.0 Feed"
        var nextUrl: String? = null
        var searchUrl: String? = null
        val facets = mutableListOf<OpdsFacet>()

        root.array("links").forEach { link ->
            val href = link.string("href")
            if (!href.isNullOrBlank()) {
                val resolved = resolveUrl(baseUrl, href)
                val rels = link.rels()
                when {
                    "next" in rels -> nextUrl = resolved
                    "search" in rels -> searchUrl = resolved
                }
            }
        }

        root.array("facets").forEach { facetObj ->
            val group = facetObj.obj("metadata")?.string("title") ?: "Filter"
            facetObj.array("links").forEach { link ->
                val href = link.string("href")
                if (!href.isNullOrBlank()) {
                    facets += OpdsFacet(
                        title = link.string("title") ?: "Facet",
                        group = group,
                        url = resolveUrl(baseUrl, href),
                        isActive = link.obj("properties")?.boolean("active") ?: false
                    )
                }
            }
        }

        val entries = mutableListOf<OpdsEntry>()
        root.array("publications").forEach { entries += parseOpds2Publication(it, baseUrl) }
        root.array("navigation").forEach { entries += parseOpds2Navigation(it, baseUrl) }
        root.array("groups").forEach { group ->
            val groupTitle = group.obj("metadata")?.string("title").orEmpty()
            group.array("navigation").forEach { entries += parseOpds2Navigation(it, baseUrl) }
            group.array("publications").forEach { entries += parseOpds2Publication(it, baseUrl) }
            group.array("links").forEach { link ->
                val href = link.string("href")
                if (!href.isNullOrBlank()) {
                    entries += OpdsEntry(
                        id = href,
                        title = link.string("title") ?: groupTitle,
                        summary = null,
                        coverUrl = null,
                        navigationUrl = resolveUrl(baseUrl, href)
                    )
                }
            }
        }
        return OpdsFeed(title, entries, nextUrl, searchUrl, facets)
    }

    private fun parseOpds2Publication(pub: JsonObject, baseUrl: String): OpdsEntry {
        val metadata = pub.obj("metadata")
        val title = metadata?.string("title") ?: "Unknown Title"
        val id = metadata?.string("identifier") ?: pub.string("id") ?: "ios_opds_${title.hashCode()}"
        var coverUrl: String? = null
        var coverPriority = Int.MIN_VALUE

        pub.array("images").forEach { image ->
            val href = image.string("href")
            if (!href.isNullOrBlank()) {
                val priority = image.coverPriority()
                if (coverUrl == null || priority > coverPriority) {
                    coverUrl = resolveUrl(baseUrl, href)
                    coverPriority = priority
                }
            }
        }

        val acquisitions = mutableListOf<OpdsAcquisition>()
        var pseCount: Int? = null
        var pseUrlTemplate: String? = null
        pub.array("links").forEach { link ->
            val href = link.string("href")
            if (!href.isNullOrBlank()) {
                val rels = link.rels()
                val type = link.string("type").orEmpty()
                if (rels.any { it.equals(PseStreamRel, ignoreCase = true) }) {
                    pseUrlTemplate = resolveUrl(baseUrl, href)
                    pseCount = link.obj("properties")?.int("numberOfItems")?.takeIf { it > 0 }
                }
                val linkCoverPriority = coverPriority(rels, type)
                if (linkCoverPriority != null && linkCoverPriority > coverPriority) {
                    coverUrl = resolveUrl(baseUrl, href)
                    coverPriority = linkCoverPriority
                }
                if (rels.any { it.contains("acquisition", ignoreCase = true) } ||
                    (rels.any { it.equals("enclosure", ignoreCase = true) } && type.isDownloadableMediaType())
                ) {
                    acquisitions += OpdsAcquisition(resolveUrl(baseUrl, href), type)
                }
            }
        }

        val series = parseOpds2Series(metadata?.obj("belongsTo"))
        return OpdsEntry(
            id = id,
            title = title,
            summary = metadata?.string("description") ?: metadata?.string("summary"),
            authors = parseOpds2Authors(metadata?.get("author"), baseUrl),
            coverUrl = coverUrl,
            acquisitions = acquisitions,
            navigationUrl = null,
            publisher = metadata?.string("publisher"),
            published = metadata?.string("published"),
            language = metadata?.string("language"),
            series = series.first,
            seriesIndex = series.second,
            categories = parseOpds2Categories(metadata?.get("subject")),
            pseCount = pseCount,
            pseUrlTemplate = pseUrlTemplate
        )
    }

    private fun parseOpds2Navigation(nav: JsonObject, baseUrl: String): OpdsEntry {
        val href = nav.string("href")
        return OpdsEntry(
            id = href.orEmpty(),
            title = nav.string("title") ?: "Unknown",
            summary = nav.string("description"),
            coverUrl = null,
            navigationUrl = href?.takeIf { it.isNotBlank() }?.let { resolveUrl(baseUrl, it) }
        )
    }

    private fun parseOpds1(xmlString: String, baseUrl: String): OpdsFeed {
        val feedBody = XmlElement.firstBody(xmlString, "feed") ?: xmlString
        val title = XmlElement.firstText(feedBody, "title").ifBlank { "OPDS Feed" }
        var nextUrl: String? = null
        var searchUrl: String? = null
        val facets = mutableListOf<OpdsFacet>()

        XmlElement.findAll(feedBody, "link").forEach { link ->
            val rel = link.attr("rel")
            val href = link.attr("href")
            val linkTitle = link.attr("title")
            when {
                rel == "next" && href != null -> nextUrl = resolveUrl(baseUrl, href)
                rel == "search" && href != null -> searchUrl = resolveUrl(baseUrl, href)
                (rel == "facet" || rel == "http://opds-spec.org/facet") && href != null && linkTitle != null -> {
                    facets += OpdsFacet(
                        title = linkTitle,
                        group = link.attr("opds:facetGroup") ?: link.attr("facetGroup") ?: "Filter",
                        url = resolveUrl(baseUrl, href),
                        isActive = link.attr("opds:activeFacet") == "true" || link.attr("activeFacet") == "true"
                    )
                }
            }
        }

        val entries = XmlElement.findAll(feedBody, "entry").map { readOpds1Entry(it.body, baseUrl) }
        return OpdsFeed(title, entries, nextUrl, searchUrl, facets)
    }

    private fun readOpds1Entry(entry: String, baseUrl: String): OpdsEntry {
        val authors = XmlElement.findAll(entry, "author").map { author ->
            OpdsAuthor(
                name = XmlElement.firstText(author.body, "name"),
                url = XmlElement.firstText(author.body, "uri").takeIf { it.isNotBlank() }?.let { resolveUrl(baseUrl, it) }
            )
        }.filter { it.name.isNotBlank() }
        val categories = XmlElement.findAll(entry, "category").mapNotNull { it.attr("label") ?: it.attr("term") }
        val acquisitions = mutableListOf<OpdsAcquisition>()
        var coverUrl: String? = null
        var coverPriority = Int.MIN_VALUE
        var navigationUrl: String? = null
        var pseCount: Int? = null
        var pseUrlTemplate: String? = null

        XmlElement.findAll(entry, "link").forEach { link ->
            val href = link.attr("href").orEmpty()
            if (href.isBlank()) return@forEach
            val rels = link.attr("rel").orEmpty().relTokens()
            val type = link.attr("type").orEmpty()
            val absoluteUrl = resolveUrl(baseUrl, href)
            if (rels.any { it.equals(PseStreamRel, ignoreCase = true) }) {
                pseUrlTemplate = absoluteUrl
                pseCount = link.attr("pse:count")?.toIntOrNull() ?: link.attr("count")?.toIntOrNull()
            }
            val linkCoverPriority = coverPriority(rels, type)
            when {
                linkCoverPriority != null -> {
                    if (linkCoverPriority > coverPriority) {
                        coverUrl = absoluteUrl
                        coverPriority = linkCoverPriority
                    }
                }
                rels.any { it.contains("acquisition", ignoreCase = true) } ||
                    (rels.any { it.equals("enclosure", ignoreCase = true) } && type.isDownloadableMediaType()) -> {
                    acquisitions += OpdsAcquisition(absoluteUrl, type)
                }
                type.contains("profile=opds-catalog", ignoreCase = true) ||
                    type.contains("application/atom+xml", ignoreCase = true) ||
                    rels.any { it == "subsection" || it == "collection" || it == "start" } -> {
                    if (navigationUrl == null) navigationUrl = absoluteUrl
                }
            }
        }

        return OpdsEntry(
            id = XmlElement.firstText(entry, "id").ifBlank { XmlElement.firstText(entry, "title") },
            title = XmlElement.firstText(entry, "title").ifBlank { "Unknown Title" },
            summary = XmlElement.firstText(entry, "summary").ifBlank { XmlElement.firstText(entry, "content") }.takeIf { it.isNotBlank() },
            authors = authors,
            coverUrl = coverUrl,
            acquisitions = acquisitions,
            navigationUrl = navigationUrl,
            publisher = XmlElement.firstText(entry, "publisher").takeIf { it.isNotBlank() },
            published = XmlElement.firstText(entry, "published").ifBlank { XmlElement.firstText(entry, "updated") }.takeIf { it.isNotBlank() },
            language = XmlElement.firstText(entry, "language").takeIf { it.isNotBlank() },
            series = XmlElement.findAll(entry, "meta").firstOrNull {
                it.attr("property") == "calibre:series" || it.attr("name") == "calibre:series"
            }?.let { it.attr("content") ?: it.text },
            seriesIndex = XmlElement.findAll(entry, "meta").firstOrNull {
                it.attr("property") == "calibre:series_index" || it.attr("name") == "calibre:series_index"
            }?.let { it.attr("content") ?: it.text },
            categories = categories,
            pseCount = pseCount,
            pseUrlTemplate = pseUrlTemplate
        )
    }

    private fun parseOpds2Authors(authorElement: JsonElement?, baseUrl: String): List<OpdsAuthor> {
        return when (authorElement) {
            is JsonArray -> authorElement.mapNotNull { parseOpds2Author(it, baseUrl) }
            null -> emptyList()
            else -> listOfNotNull(parseOpds2Author(authorElement, baseUrl))
        }
    }

    private fun parseOpds2Author(authorElement: JsonElement, baseUrl: String): OpdsAuthor? {
        authorElement.primitiveString()?.let { return OpdsAuthor(it, null) }
        val obj = authorElement.asObjectOrNull() ?: return null
        val name = obj.string("name")?.takeIf { it.isNotBlank() } ?: return null
        val uri = obj.array("links").firstOrNull()?.string("href")?.let { resolveUrl(baseUrl, it) }
        return OpdsAuthor(name, uri)
    }

    private fun parseOpds2Categories(subjectElement: JsonElement?): List<String> {
        return when (subjectElement) {
            is JsonArray -> subjectElement.mapNotNull(::parseOpds2Category)
            null -> emptyList()
            else -> listOfNotNull(parseOpds2Category(subjectElement))
        }
    }

    private fun parseOpds2Category(subjectElement: JsonElement): String? {
        subjectElement.primitiveString()?.let { return it }
        return subjectElement.asObjectOrNull()?.string("name")?.takeIf { it.isNotBlank() }
    }

    private fun parseOpds2Series(belongsTo: JsonObject?): Pair<String?, String?> {
        val seriesElement = belongsTo?.get("series") ?: return null to null
        val first = if (seriesElement is JsonArray) seriesElement.firstOrNull() else seriesElement
        first?.primitiveString()?.let { return it to null }
        val seriesObj = first?.asObjectOrNull() ?: return null to null
        val name = seriesObj.string("name")
        val index = seriesObj.get("position")?.jsonPrimitive?.doubleOrNull?.toString()?.removeSuffix(".0")
        return name to index
    }

    private fun resolveUrl(baseUrl: String, href: String): String {
        if (href.startsWith("http://") || href.startsWith("https://") || href.startsWith("opds-pse://")) return href
        val baseMatch = Regex("""^(https?://[^/]+)(/.*)?$""").find(baseUrl) ?: return href
        val origin = baseMatch.groupValues[1]
        val path = baseMatch.groupValues.getOrNull(2).orEmpty()
        return when {
            href.startsWith("//") -> "https:$href"
            href.startsWith("/") -> origin + href
            else -> origin + path.substringBeforeLast('/', "/") + "/" + href
        }
            .replace("http://m.gutenberg.org", "https://m.gutenberg.org")
            .replace("http://www.gutenberg.org", "https://www.gutenberg.org")
    }

    private fun JsonObject.obj(name: String): JsonObject? = get(name)?.asObjectOrNull()
    private fun JsonObject.array(name: String): List<JsonObject> {
        return runCatching { get(name)?.jsonArray?.mapNotNull { it.asObjectOrNull() }.orEmpty() }.getOrDefault(emptyList())
    }
    private fun JsonObject.string(name: String): String? = runCatching { get(name)?.jsonPrimitive?.contentOrNull }.getOrNull()
    private fun JsonObject.boolean(name: String): Boolean? = runCatching { get(name)?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() }.getOrNull()
    private fun JsonObject.int(name: String): Int? = runCatching { get(name)?.jsonPrimitive?.intOrNull }.getOrNull()
    private fun JsonObject.coverPriority(): Int = coverPriority(rels(), string("type").orEmpty()) ?: ImageTypeCoverPriority
    private fun JsonObject.rels(): List<String> {
        val rel = get("rel") ?: return emptyList()
        rel.primitiveString()?.let { return it.relTokens() }
        return runCatching { rel.jsonArray.mapNotNull { it.primitiveString() } }.getOrDefault(emptyList()).flatMap { it.relTokens() }
    }
    private fun JsonElement.primitiveString(): String? = runCatching { jsonPrimitive.contentOrNull }.getOrNull()?.takeIf { it.isNotBlank() }
    private fun JsonElement.asObjectOrNull(): JsonObject? = runCatching { jsonObject }.getOrNull()

    private fun coverPriority(rels: List<String>, type: String): Int? {
        val normalized = rels.map { it.lowercase() }
        return when {
            normalized.any { it == "thumbnail" || it.endsWith("/thumbnail") || it.endsWith("/image/thumbnail") } -> ThumbnailCoverPriority
            normalized.any { it == "cover" || it.endsWith("/cover") || it.contains("/image") } -> GenericCoverPriority
            type.lowercase().substringBefore(';').trim().startsWith("image/") -> ImageTypeCoverPriority
            else -> null
        }
    }

    private fun openSearchTemplatePriority(type: String): Int {
        val normalized = type.lowercase()
        return when {
            normalized.contains("profile=opds-catalog") && normalized.contains("kind=acquisition") -> 4
            normalized.contains("profile=opds-catalog") -> 3
            normalized.contains("opds+xml") -> 2
            normalized.contains("atom+xml") -> 1
            else -> 0
        }
    }

    private fun String.relTokens(): List<String> = trim().split(Regex("""\s+""")).filter { it.isNotBlank() }
    private fun String.isDownloadableMediaType(): Boolean {
        val normalized = lowercase().substringBefore(';').trim()
        return normalized in DownloadableMediaTypes || DownloadableMediaTypeHints.any { normalized.contains(it) }
    }

    private data class OpenSearchTemplateCandidate(val template: String, val priority: Int)

    private companion object {
        private const val PseStreamRel = "http://vaemendis.net/opds-pse/stream"
        private const val ImageTypeCoverPriority = 1
        private const val GenericCoverPriority = 2
        private const val ThumbnailCoverPriority = 3
        private val DownloadableMediaTypes = setOf(
            "application/epub+zip",
            "application/kepub+zip",
            "application/pdf",
            "application/x-mobipocket-ebook",
            "application/vnd.amazon.ebook",
            "application/vnd.comicbook+zip",
            "application/vnd.comicbook-rar",
            "application/x-cbz",
            "application/x-cbr",
            "application/x-fictionbook+xml",
            "application/fb2+zip",
            "application/xhtml+xml",
            "text/html",
            "text/plain",
            "text/markdown",
            "text/x-markdown"
        )
        private val DownloadableMediaTypeHints = listOf("epub", "kepub", "mobipocket", "kindle", "azw", "fictionbook", "comicbook")
    }
}

private data class XmlElement(
    val name: String,
    val attributes: Map<String, String>,
    val body: String,
    val text: String
) {
    fun attr(name: String): String? {
        return attributes[name]
            ?: attributes.entries.firstOrNull { it.key.substringAfter(':').equals(name.substringAfter(':'), ignoreCase = true) }?.value
    }

    companion object {
        fun firstBody(xml: String, tag: String): String? = findAll(xml, tag).firstOrNull()?.body
        fun firstText(xml: String, tag: String): String = findAll(xml, tag).firstOrNull()?.text.orEmpty()

        fun findAll(xml: String, tag: String): List<XmlElement> {
            val tagName = Regex.escape(tag)
            val pattern = Regex(
                """<([A-Za-z0-9_:-]*:?$tagName)\b([^>]*)>(.*?)</\1>|<([A-Za-z0-9_:-]*:?$tagName)\b([^>]*)/>""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
            )
            return pattern.findAll(xml).map { match ->
                val name = match.groupValues[1].ifBlank { match.groupValues[4] }
                val attrs = match.groupValues[2].ifBlank { match.groupValues[5] }
                val body = match.groupValues[3]
                XmlElement(
                    name = name,
                    attributes = parseAttributes(attrs),
                    body = body,
                    text = body.stripXml().decodeXmlEntities().trim()
                )
            }.toList()
        }

        private fun parseAttributes(source: String): Map<String, String> {
            val attrs = linkedMapOf<String, String>()
            Regex("""([A-Za-z0-9_:-]+)\s*=\s*(['"])(.*?)\2""", RegexOption.DOT_MATCHES_ALL)
                .findAll(source)
                .forEach { match -> attrs[match.groupValues[1]] = match.groupValues[3].decodeXmlEntities() }
            return attrs
        }
    }
}

private fun String.stripXml(): String {
    return replace(Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("""</p\s*>""", RegexOption.IGNORE_CASE), "\n\n")
        .replace(Regex("""<[^>]+>"""), " ")
        .replace(Regex("""\s+"""), " ")
}

private fun String.decodeXmlEntities(): String {
    return replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
}

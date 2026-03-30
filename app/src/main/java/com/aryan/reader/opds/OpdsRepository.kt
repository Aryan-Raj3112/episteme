package com.aryan.reader.opds

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.security.MessageDigest
import java.util.UUID

class OpdsRepository(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("reader_opds_prefs", Context.MODE_PRIVATE)
    private val httpClient = OkHttpClient.Builder().build()
    private val parser = OpdsParser()

    companion object {
        private const val KEY_CATALOGS_JSON = "opds_catalogs_json"
    }

    fun getCatalogs(): List<OpdsCatalog> {
        val jsonString = prefs.getString(KEY_CATALOGS_JSON, null)
        val catalogs = mutableListOf<OpdsCatalog>()

        if (jsonString != null) {
            try {
                val jsonArray = JSONArray(jsonString)
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    catalogs.add(
                        OpdsCatalog(
                            id = obj.getString("id"),
                            title = obj.getString("title"),
                            url = obj.getString("url"),
                            isDefault = obj.optBoolean("isDefault", false),
                            username = obj.optString("username", "").takeIf { it.isNotBlank() },
                            password = obj.optString("password", "").takeIf { it.isNotBlank() }
                        )
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        if (catalogs.isEmpty()) {
            catalogs.add(OpdsCatalog(UUID.randomUUID().toString(), "Project Gutenberg", "https://m.gutenberg.org/ebooks.opds/", isDefault = true))
            saveCatalogs(catalogs)
        }

        return catalogs
    }

    private fun resolveUrl(baseUrl: String, href: String): String {
        return try {
            val resolved = java.net.URL(java.net.URL(baseUrl), href).toString()

            resolved.replace("http://m.gutenberg.org", "https://m.gutenberg.org")
                .replace("http://www.gutenberg.org", "https://www.gutenberg.org")
        } catch (_: Exception) {
            href
        }
    }

    suspend fun getSearchTemplate(openSearchUrl: String): String? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(openSearchUrl).build()
            val response = httpClient.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext null

            val parser = android.util.Xml.newPullParser()
            parser.setFeature(org.xmlpull.v1.XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(body.byteInputStream(), null)
            var eventType = parser.eventType

            while (eventType != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                if (eventType == org.xmlpull.v1.XmlPullParser.START_TAG && parser.name.equals("Url", ignoreCase = true)) {
                    val type = parser.getAttributeValue(null, "type")
                    if (type != null && (type.contains("atom+xml") || type.contains("opds+xml"))) {
                        val template = parser.getAttributeValue(null, "template")
                        if (template != null) {
                            val resolvedTemplate = resolveUrl(openSearchUrl, template)
                            Timber.tag("OpdsDebug").d("Resolved search template: $resolvedTemplate")
                            return@withContext resolvedTemplate
                        }
                    }
                }
                eventType = parser.next()
            }
            null
        } catch (e: Exception) {
            Timber.e(e, "Failed to fetch OpenSearch template")
            null
        }
    }

    fun addCatalog(title: String, url: String, username: String? = null, password: String? = null) {
        val current = getCatalogs().toMutableList()
        current.add(OpdsCatalog(UUID.randomUUID().toString(), title, url, username = username, password = password))
        saveCatalogs(current)
    }

    fun updateCatalog(id: String, title: String, url: String, username: String?, password: String?) {
        val current = getCatalogs().toMutableList()
        val index = current.indexOfFirst { it.id == id }
        if (index != -1 && !current[index].isDefault) {
            current[index] = current[index].copy(
                title = title.trim(),
                url = url.trim(),
                username = username?.trim().takeIf { !it.isNullOrBlank() },
                password = password?.trim().takeIf { !it.isNullOrBlank() }
            )
            saveCatalogs(current)
        }
    }

    fun removeCatalog(id: String) {
        val current = getCatalogs().toMutableList()
        val toRemove = current.find { it.id == id }
        if (toRemove?.isDefault == true) {
            return
        }
        current.removeAll { it.id == id }
        saveCatalogs(current)
    }

    private fun saveCatalogs(catalogs: List<OpdsCatalog>) {
        val jsonArray = JSONArray()
        catalogs.forEach { catalog ->
            val obj = JSONObject()
            obj.put("id", catalog.id)
            obj.put("title", catalog.title)
            obj.put("url", catalog.url)
            obj.put("isDefault", catalog.isDefault)
            if (catalog.username != null) obj.put("username", catalog.username)
            if (catalog.password != null) obj.put("password", catalog.password)
            jsonArray.put(obj)
        }
        prefs.edit { putString(KEY_CATALOGS_JSON, jsonArray.toString()) }
    }

    fun getAuthenticatedClient(username: String?, password: String?): OkHttpClient {
        return httpClient.newBuilder()
            .authenticator(OpdsAuthenticator(username, password))
            .build()
    }

    private class OpdsAuthenticator(private val user: String?, private val pass: String?) : okhttp3.Authenticator {
        private var cnonceCount = 0

        override fun authenticate(route: okhttp3.Route?, response: okhttp3.Response): Request? {
            if (user.isNullOrBlank() || pass.isNullOrBlank()) return null

            if (response.request.header("Authorization") != null) {
                return null
            }

            val wwwAuth = response.header("WWW-Authenticate") ?: return null

            if (wwwAuth.startsWith("Basic", ignoreCase = true)) {
                val credential = okhttp3.Credentials.basic(user, pass)
                return response.request.newBuilder().header("Authorization", credential).build()
            }

            if (wwwAuth.startsWith("Digest", ignoreCase = true)) {
                val realm = extractParam(wwwAuth, "realm") ?: ""
                val nonce = extractParam(wwwAuth, "nonce") ?: ""
                val qop = extractParam(wwwAuth, "qop")
                val opaque = extractParam(wwwAuth, "opaque")

                cnonceCount++
                val nc = String.format("%08x", cnonceCount)
                val cnonce = UUID.randomUUID().toString().replace("-", "")

                val url = response.request.url
                val uri = url.encodedPath + (if (url.encodedQuery != null) "?${url.encodedQuery}" else "")

                val ha1 = md5("$user:$realm:$pass")
                val ha2 = md5("${response.request.method}:$uri")

                val responseHash = if (qop != null) {
                    md5("$ha1:$nonce:$nc:$cnonce:$qop:$ha2")
                } else {
                    md5("$ha1:$nonce:$ha2")
                }

                val digestHeader = buildString {
                    append("Digest username=\"$user\", ")
                    append("realm=\"$realm\", ")
                    append("nonce=\"$nonce\", ")
                    append("uri=\"$uri\", ")
                    append("response=\"$responseHash\"")
                    if (qop != null) {
                        append(", qop=$qop, nc=$nc, cnonce=\"$cnonce\"")
                    }
                    if (opaque != null) {
                        append(", opaque=\"$opaque\"")
                    }
                }

                return response.request.newBuilder()
                    .header("Authorization", digestHeader)
                    .build()
            }

            return null
        }

        private fun extractParam(header: String, param: String): String? {
            val match = Regex("$param=\"([^\"]+)\"").find(header) ?: Regex("$param=([^,\\s]+)").find(header)
            return match?.groupValues?.get(1)
        }

        private fun md5(input: String): String {
            val bytes = MessageDigest.getInstance("MD5").digest(input.toByteArray())
            return bytes.joinToString("") { "%02x".format(it) }
        }
    }


    suspend fun fetchFeed(url: String, username: String? = null, password: String? = null): Result<OpdsFeed> = withContext(Dispatchers.IO) {
        Timber.tag("OpdsDebug").d("Starting fetch for URL: $url")
        try {
            val client = getAuthenticatedClient(username, password)

            val request = Request.Builder()
                .url(url.trim())
                .header("User-Agent", "EpistemeReader/1.0 (Android)")
                .build()

            Timber.tag("OpdsDebug").d("Executing network call...")
            val response = client.newCall(request).execute()

            Timber.tag("OpdsDebug").d("Response Code: ${response.code}")

            if (!response.isSuccessful) {
                val errorMsg = "HTTP ${response.code}: ${response.message}"
                Timber.tag("OpdsDebug").e("Fetch failed: $errorMsg")
                return@withContext Result.failure(Exception(errorMsg))
            }

            val bodyString = response.body?.string()
            if (bodyString.isNullOrBlank()) {
                return@withContext Result.failure(Exception("Empty response body"))
            }

            val inputStream = bodyString.byteInputStream()
            val feed = parser.parse(inputStream, url)

            Timber.tag("OpdsDebug").d("Parsing complete. Found ${feed.entries.size} entries.")
            Result.success(feed)
        } catch (e: Exception) {
            Timber.tag("OpdsDebug").e(e, "Exception during fetch/parse at URL: $url")
            Result.failure(e)
        }
    }
}
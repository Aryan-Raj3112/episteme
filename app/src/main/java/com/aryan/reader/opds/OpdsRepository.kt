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
                            isDefault = obj.optBoolean("isDefault", false)
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

    suspend fun getSearchTemplate(openSearchUrl: String): String? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(openSearchUrl).build()
            val response = httpClient.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext null

            // Parse the OpenSearch Description XML to find the actual template URL
            val parser = android.util.Xml.newPullParser()
            parser.setFeature(org.xmlpull.v1.XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(body.byteInputStream(), null)
            var eventType = parser.eventType

            while (eventType != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                if (eventType == org.xmlpull.v1.XmlPullParser.START_TAG && parser.name.equals("Url", ignoreCase = true)) {
                    val type = parser.getAttributeValue(null, "type")
                    if (type != null && (type.contains("atom+xml") || type.contains("opds+xml"))) {
                        val template = parser.getAttributeValue(null, "template")
                        if (template != null) return@withContext template
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

    fun addCatalog(title: String, url: String) {
        val current = getCatalogs().toMutableList()
        current.add(OpdsCatalog(UUID.randomUUID().toString(), title, url))
        saveCatalogs(current)
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
            jsonArray.put(obj)
        }
        prefs.edit { putString(KEY_CATALOGS_JSON, jsonArray.toString()) }
    }

    suspend fun fetchFeed(url: String): Result<OpdsFeed> = withContext(Dispatchers.IO) {
        Timber.tag("OpdsDebug").d("Starting fetch for URL: $url")
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "EpistemeReader/1.0 (Android)")
                .header("Accept", "application/atom+xml,application/xml,text/xml")
                .build()

            Timber.tag("OpdsDebug").d("Executing network call...")
            val response = httpClient.newCall(request).execute()

            Timber.tag("OpdsDebug").d("Response Code: ${response.code}")
            Timber.tag("OpdsDebug").d("Response Headers: ${response.headers}")

            if (!response.isSuccessful) {
                val errorMsg = "HTTP ${response.code}: ${response.message}"
                Timber.tag("OpdsDebug").e("Fetch failed: $errorMsg")
                return@withContext Result.failure(Exception(errorMsg))
            }

            val bodyString = response.body?.string()
            Timber.tag("OpdsDebug").d("Raw Body Sample (first 500 chars): ${bodyString?.take(500)}")

            if (bodyString.isNullOrBlank()) {
                Timber.tag("OpdsDebug").e("Response body is null or empty")
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
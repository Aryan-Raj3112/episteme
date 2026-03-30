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

                    var url = obj.getString("url")
                    if (url == "https://standardebooks.org/opds/all") {
                        url = "https://standardebooks.org/opds"
                    }

                    catalogs.add(
                        OpdsCatalog(
                            id = obj.getString("id"),
                            title = obj.getString("title"),
                            url = url
                        )
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        if (catalogs.isEmpty()) {
            val defaults = listOf(
                OpdsCatalog(UUID.randomUUID().toString(), "Project Gutenberg", "https://m.gutenberg.org/ebooks.opds/")
            )
            saveCatalogs(defaults)
            return defaults
        }

        return catalogs
    }

    fun addCatalog(title: String, url: String) {
        val current = getCatalogs().toMutableList()
        current.add(OpdsCatalog(UUID.randomUUID().toString(), title, url))
        saveCatalogs(current)
    }

    fun removeCatalog(id: String) {
        val current = getCatalogs().toMutableList()
        val toRemove = current.find { it.id == id }
        if (toRemove?.url?.contains("gutenberg.org") == true) {
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
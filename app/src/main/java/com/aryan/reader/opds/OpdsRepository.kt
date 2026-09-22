package com.aryan.reader.opds

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.aryan.reader.R
import com.aryan.reader.shared.opds.SharedOpdsCatalogs
import com.aryan.reader.shared.opds.SharedOpdsDownloadLocation
import com.aryan.reader.shared.opds.SharedOpdsDownloadLocationCodec
import com.aryan.reader.shared.opds.SharedOpdsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.util.UUID
import javax.net.ssl.SSLException

class OpdsRepository(context: Context) : SharedOpdsRepository {
    private val appContext: Context = context.applicationContext
    private val prefs: SharedPreferences = context.getSharedPreferences("reader_opds_prefs", Context.MODE_PRIVATE)
    private val parser = OpdsParser()

    companion object {
        private const val KEY_CATALOGS_JSON = "opds_catalogs_json"
        private const val KEY_DOWNLOAD_LOCATION_JSON = "opds_download_location_json"

        val sharedHttpClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .addInterceptor { chain ->
                    val originalRequest = chain.request()
                    val requestWithUserAgent = originalRequest.newBuilder()
                        .header("User-Agent", "EpistemeReader/1.0 (Android)")
                        .build()
                    chain.proceed(requestWithUserAgent)
                }
                .build()
        }

        /**
         * Whether [error] (or any nested cause) is a TLS trust failure, e.g. a
         * self-signed certificate or a private CA the system does not trust.
         * Surfaced separately so users get an actionable message instead of a
         * raw handshake exception.
         */
        fun isCertificateError(error: Throwable): Boolean {
            var cause: Throwable? = error
            while (cause != null) {
                if (cause is SSLException || cause is CertificateException) return true
                cause = cause.cause
            }
            return false
        }

        /**
         * Preemptive Basic credential for endpoints that must receive
         * Authorization on the first request (e.g. OPDS stream pages).
         * Only the username is required; some servers use token-style
         * setups with an empty password.
         */
        fun preemptiveBasicAuthHeader(username: String?, password: String?): String? {
            val user = username?.takeIf { it.isNotBlank() } ?: return null
            return okhttp3.Credentials.basic(user, password.orEmpty())
        }
    }

    private val httpClient = sharedHttpClient

    override fun loadCatalogs(): List<OpdsCatalog> {
        val jsonString = prefs.getString(KEY_CATALOGS_JSON, null)
        val decodedCatalogs = SharedOpdsCatalogs.decode(jsonString)
        val catalogs = decodedCatalogs.ifEmpty {
            SharedOpdsCatalogs.defaultCatalogs { UUID.randomUUID().toString() }
        }
        if (decodedCatalogs.isEmpty()) {
            saveCatalogs(catalogs)
        }
        return catalogs
    }

    fun getCatalogs(): List<OpdsCatalog> = loadCatalogs()

    override suspend fun getSearchTemplate(
        openSearchUrl: String,
        username: String?,
        password: String?
    ): String? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(openSearchUrl).build()
            val response = getAuthenticatedClient(username, password).newCall(request).execute()
            val body = response.body?.string() ?: return@withContext null
            parser.extractOpenSearchTemplate(body, openSearchUrl)
        } catch (e: Exception) {
            Timber.e(e, "Failed to fetch OpenSearch template")
            null
        }
    }

    fun addCatalog(title: String, url: String, username: String? = null, password: String? = null) {
        saveCatalogs(
            SharedOpdsCatalogs.addCatalog(
                catalogs = loadCatalogs(),
                title = title,
                url = url,
                username = username,
                password = password,
                idFactory = { UUID.randomUUID().toString() }
            )
        )
    }

    fun updateCatalog(id: String, title: String, url: String, username: String?, password: String?) {
        saveCatalogs(SharedOpdsCatalogs.updateCatalog(loadCatalogs(), id, title, url, username, password))
    }

    fun removeCatalog(id: String) {
        saveCatalogs(SharedOpdsCatalogs.removeCatalog(loadCatalogs(), id))
    }

    override fun saveCatalogs(catalogs: List<OpdsCatalog>) {
        prefs.edit { putString(KEY_CATALOGS_JSON, SharedOpdsCatalogs.encode(catalogs)) }
    }

    override fun loadOpdsDownloadLocation(): SharedOpdsDownloadLocation? {
        return SharedOpdsDownloadLocationCodec.decode(
            prefs.getString(KEY_DOWNLOAD_LOCATION_JSON, null)
        )
    }

    override fun saveOpdsDownloadLocation(location: SharedOpdsDownloadLocation?) {
        val encoded = SharedOpdsDownloadLocationCodec.encode(location)
        prefs.edit {
            if (encoded == null) {
                remove(KEY_DOWNLOAD_LOCATION_JSON)
            } else {
                putString(KEY_DOWNLOAD_LOCATION_JSON, encoded)
            }
        }
    }

    fun getAuthenticatedClient(username: String?, password: String?): OkHttpClient {
        return httpClient.newBuilder()
            .authenticator(OpdsAuthenticator(username, password))
            .build()
    }

    class OpdsAuthenticator(private val user: String?, private val pass: String?) : okhttp3.Authenticator {
        private var cnonceCount = 0

        override fun authenticate(route: okhttp3.Route?, response: okhttp3.Response): Request? {
            // Only the username is required: token-style setups use an empty
            // password, which is valid per RFC 7617 ("user:") and already
            // supported by preemptiveBasicAuthHeader for stream pages.
            if (user.isNullOrBlank()) return null

            if (response.request.header("Authorization") != null) {
                return null
            }

            val wwwAuth = response.header("WWW-Authenticate") ?: return null

            if (wwwAuth.startsWith("Basic", ignoreCase = true)) {
                val credential = okhttp3.Credentials.basic(user, pass.orEmpty())
                return response.request.newBuilder().header("Authorization", credential).build()
            }

            if (wwwAuth.startsWith("Digest", ignoreCase = true)) {
                val realm = extractParam(wwwAuth, "realm") ?: ""
                val nonce = extractParam(wwwAuth, "nonce") ?: ""
                val qop = selectAuthQop(extractParam(wwwAuth, "qop"))
                val opaque = extractParam(wwwAuth, "opaque")

                cnonceCount++
                val nc = String.format("%08x", cnonceCount)
                val cnonce = UUID.randomUUID().toString().replace("-", "")

                val url = response.request.url
                val uri = url.encodedPath + (if (url.encodedQuery != null) "?${url.encodedQuery}" else "")

                val ha1 = md5("$user:$realm:${pass.orEmpty()}")
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

        private fun selectAuthQop(value: String?): String? {
            return value
                ?.split(',')
                ?.map { it.trim().trim('"') }
                ?.firstOrNull { it.equals("auth", ignoreCase = true) }
        }

        private fun md5(input: String): String {
            val bytes = MessageDigest.getInstance("MD5").digest(input.toByteArray())
            return bytes.joinToString("") { "%02x".format(it) }
        }
    }


    override suspend fun fetchFeed(url: String, username: String?, password: String?): Result<OpdsFeed> = withContext(Dispatchers.IO) {
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

            val feed = parser.parse(bodyString, url)

            Timber.tag("OpdsDebug").d("Parsing complete. Found ${feed.entries.size} entries.")
            Result.success(feed)
        } catch (e: Exception) {
            Timber.tag("OpdsDebug").e(e, "Exception during fetch/parse at URL: $url")
            val failure = if (isCertificateError(e)) {
                Exception(appContext.getString(R.string.opds_error_certificate), e)
            } else {
                e
            }
            Result.failure(failure)
        }
    }
}

package com.aryan.reader.desktop

import com.aryan.reader.shared.UserData
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.Properties
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

internal data class DesktopAuthSession(
    val user: UserData,
    val idToken: String,
    val refreshToken: String,
    val expiresAtEpochMillis: Long,
    val googleAccessToken: String = "",
    val googleRefreshToken: String = "",
    val googleAccessTokenExpiresAtEpochMillis: Long = 0L
) {
    val isFresh: Boolean get() = idToken.isNotBlank() && expiresAtEpochMillis - System.currentTimeMillis() > 60_000L
    val isGoogleAccessTokenFresh: Boolean
        get() = googleAccessToken.isNotBlank() &&
            googleAccessTokenExpiresAtEpochMillis - System.currentTimeMillis() > 60_000L
}

internal class DesktopFirebaseAuthRepository(
    private val config: DesktopCloudConfig,
    private val store: DesktopAuthStore = DesktopAuthStore()
) {
    private var session: DesktopAuthSession? = store.load()

    fun currentSession(): DesktopAuthSession? = session

    suspend fun restoreSavedSession(): DesktopAuthSession? {
        val restored = session ?: store.load()?.also { session = it }
        return restored?.let { refreshSessionIfNeeded(it) }
    }

    suspend fun signIn(openUrl: (String) -> Unit): DesktopAuthSession {
        if (!config.isAuthConfigured) {
            throw IllegalStateException("Desktop Google sign-in is not configured.")
        }
        val oauthCode = requestGoogleOAuthCode(openUrl)
        val googleTokens = exchangeCodeForGoogleTokens(oauthCode.code, oauthCode.redirectUri, oauthCode.codeVerifier)
        val existingGoogleRefreshToken = session?.googleRefreshToken.orEmpty()
        val nextSession = signInWithFirebase(googleTokens.idToken).copy(
            googleAccessToken = googleTokens.accessToken,
            googleRefreshToken = googleTokens.refreshToken.ifBlank { existingGoogleRefreshToken },
            googleAccessTokenExpiresAtEpochMillis = googleTokens.expiresAtEpochMillis
        )
        session = nextSession
        persistSession(nextSession)
        return nextSession
    }

    fun signOut() {
        session = null
        store.clear()
    }

    suspend fun freshIdToken(): String? {
        val current = session ?: store.load()?.also { session = it } ?: return null
        return refreshSessionIfNeeded(current)?.idToken
    }

    suspend fun freshGoogleAccessToken(): String? {
        val current = session ?: store.load()?.also { session = it } ?: return null
        if (current.isGoogleAccessTokenFresh) return current.googleAccessToken
        if (current.googleRefreshToken.isBlank()) return null
        val refreshed = runCatching {
            refreshGoogleAccessToken(current)
        }.getOrNull() ?: return null
        session = refreshed
        persistSession(refreshed)
        return refreshed.googleAccessToken
    }

    private suspend fun refreshSessionIfNeeded(current: DesktopAuthSession): DesktopAuthSession? {
        if (current.isFresh) return current
        val refreshed = runCatching {
            refreshFirebaseSession(current)
        }.onFailure {
            signOut()
        }.getOrNull() ?: return null
        session = refreshed
        persistSession(refreshed)
        return refreshed
    }

    private suspend fun persistSession(session: DesktopAuthSession) {
        withContext(Dispatchers.IO) {
            store.save(session)
        }
    }

    private suspend fun requestGoogleOAuthCode(openUrl: (String) -> Unit): DesktopOAuthCode = withContext(Dispatchers.IO) {
        val codeVerifier = randomUrlToken(64)
        val state = UUID.randomUUID().toString()
        val server = HttpServer.create(InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0)
        val callback = CompletableFuture<Result<String>>()
        val redirectUri = "http://127.0.0.1:${server.address.port}/callback"
        server.executor = Executors.newSingleThreadExecutor()
        server.createContext("/callback") { exchange ->
            val params = exchange.requestURI.rawQuery.orEmpty().split("&")
                .mapNotNull { part ->
                    val key = part.substringBefore("=", "")
                    val value = part.substringAfter("=", "")
                    key.takeIf { it.isNotBlank() }?.let { it to java.net.URLDecoder.decode(value, Charsets.UTF_8.name()) }
                }
                .toMap()
            val (title, message, result) = if (params["state"] != state) {
                Triple(
                    "Google sign-in failed",
                    "Google sign-in could not be completed. Return to Episteme and try again.",
                    Result.failure(IllegalStateException("Google sign-in returned an invalid state."))
                )
            } else if (params["error"].isNullOrBlank().not()) {
                Triple(
                    "Google sign-in failed",
                    "Google sign-in was cancelled or failed. Return to Episteme and try again.",
                    Result.failure(IllegalStateException(params["error"] ?: "Google sign-in failed."))
                )
            } else {
                val code = params["code"].orEmpty()
                if (code.isBlank()) {
                    Triple(
                        "Google sign-in failed",
                        "Google sign-in could not be completed. Return to Episteme and try again.",
                        Result.failure(IllegalStateException("Google sign-in did not return an authorization code."))
                    )
                } else {
                    Triple(
                        "Google sign-in complete",
                        "Return to Episteme to continue.",
                        Result.success(code)
                    )
                }
            }
            val body = googleOAuthCallbackPage(title, message).toByteArray(Charsets.UTF_8)
            try {
                exchange.responseHeaders.add("Content-Type", "text/html; charset=UTF-8")
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            } finally {
                callback.complete(result)
            }
        }
        server.start()
        try {
            val authUrl = buildGoogleAuthUrl(
                redirectUri = redirectUri,
                codeVerifier = codeVerifier,
                state = state
            )
            openUrl(authUrl)
            val code = runCatching { callback.get(120, TimeUnit.SECONDS) }
                .getOrElse { throw IllegalStateException("Google sign-in timed out.") }
                .getOrThrow()
            DesktopOAuthCode(code = code, redirectUri = redirectUri, codeVerifier = codeVerifier)
        } finally {
            server.stop(0)
            (server.executor as? java.util.concurrent.ExecutorService)?.shutdownNow()
        }
    }

    private fun buildGoogleAuthUrl(
        redirectUri: String,
        codeVerifier: String,
        state: String
    ): String {
        val codeChallenge = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(MessageDigest.getInstance("SHA-256").digest(codeVerifier.toByteArray(Charsets.US_ASCII)))
        return "https://accounts.google.com/o/oauth2/v2/auth?" + formEncode(
            "client_id" to config.googleOAuthClientId,
            "redirect_uri" to redirectUri,
            "response_type" to "code",
            "scope" to DesktopGoogleOAuthScopes,
            "code_challenge" to codeChallenge,
            "code_challenge_method" to "S256",
            "state" to state,
            "access_type" to "offline",
            "prompt" to "consent select_account"
        )
    }

    private suspend fun exchangeCodeForGoogleTokens(
        code: String,
        redirectUri: String,
        codeVerifier: String
    ): DesktopGoogleTokens = withContext(Dispatchers.IO) {
        val tokenRequest = listOfNotNull(
            "client_id" to config.googleOAuthClientId,
            config.googleOAuthClientSecret.takeIf { it.isNotBlank() }?.let { "client_secret" to it },
            "code" to code,
            "code_verifier" to codeVerifier,
            "grant_type" to "authorization_code",
            "redirect_uri" to redirectUri
        )
        val response = postForm(
            url = "https://oauth2.googleapis.com/token",
            body = formEncode(tokenRequest)
        )
        val parsed = DesktopAuthJson.parseToJsonElement(response).jsonObject
        val idToken = parsed.string("id_token")
            ?: throw IllegalStateException(parsed.string("error_description") ?: "Google sign-in did not return an ID token.")
        val accessToken = parsed.string("access_token")
            ?: throw IllegalStateException(parsed.string("error_description") ?: "Google sign-in did not return a Drive access token.")
        DesktopGoogleTokens(
            idToken = idToken,
            accessToken = accessToken,
            refreshToken = parsed.string("refresh_token").orEmpty(),
            expiresAtEpochMillis = System.currentTimeMillis() + ((parsed.string("expires_in")?.toLongOrNull() ?: 3600L) * 1000L)
        )
    }

    private suspend fun signInWithFirebase(googleIdToken: String): DesktopAuthSession = withContext(Dispatchers.IO) {
        val payload = buildJsonObject {
            put("postBody", JsonPrimitive("id_token=${urlEncode(googleIdToken)}&providerId=google.com"))
            put("requestUri", JsonPrimitive("http://localhost"))
            put("returnIdpCredential", JsonPrimitive(true))
            put("returnSecureToken", JsonPrimitive(true))
        }.toString()
        val parsed = postJson(
            url = "https://identitytoolkit.googleapis.com/v1/accounts:signInWithIdp?key=${urlEncode(config.firebaseWebApiKey)}",
            body = payload
        ).let { DesktopAuthJson.parseToJsonElement(it).jsonObject }

        val idToken = parsed.string("idToken")
            ?: throw IllegalStateException(parsed.errorMessage() ?: "Firebase sign-in failed.")
        val refreshToken = parsed.string("refreshToken")
            ?: throw IllegalStateException("Firebase sign-in did not return a refresh token.")
        val expiresAt = System.currentTimeMillis() + ((parsed.string("expiresIn")?.toLongOrNull() ?: 3600L) * 1000L)
        val user = UserData(
            uid = parsed.string("localId").orEmpty(),
            displayName = parsed.string("displayName"),
            photoUrl = parsed.string("photoUrl"),
            email = parsed.string("email")
        )
        DesktopAuthSession(user = user, idToken = idToken, refreshToken = refreshToken, expiresAtEpochMillis = expiresAt)
    }

    private suspend fun refreshFirebaseSession(current: DesktopAuthSession): DesktopAuthSession = withContext(Dispatchers.IO) {
        val parsed = postForm(
            url = "https://securetoken.googleapis.com/v1/token?key=${urlEncode(config.firebaseWebApiKey)}",
            body = formEncode(
                "grant_type" to "refresh_token",
                "refresh_token" to current.refreshToken
            )
        ).let { DesktopAuthJson.parseToJsonElement(it).jsonObject }
        val idToken = parsed.string("id_token")
            ?: throw IllegalStateException(parsed.errorMessage() ?: "Could not refresh Google account session.")
        val refreshToken = parsed.string("refresh_token") ?: current.refreshToken
        val expiresAt = System.currentTimeMillis() + ((parsed.string("expires_in")?.toLongOrNull() ?: 3600L) * 1000L)
        current.copy(
            idToken = idToken,
            refreshToken = refreshToken,
            expiresAtEpochMillis = expiresAt
        )
    }

    private suspend fun refreshGoogleAccessToken(current: DesktopAuthSession): DesktopAuthSession = withContext(Dispatchers.IO) {
        val tokenRequest = listOfNotNull(
            "client_id" to config.googleOAuthClientId,
            config.googleOAuthClientSecret.takeIf { it.isNotBlank() }?.let { "client_secret" to it },
            "refresh_token" to current.googleRefreshToken,
            "grant_type" to "refresh_token"
        )
        val parsed = postForm(
            url = "https://oauth2.googleapis.com/token",
            body = formEncode(tokenRequest)
        ).let { DesktopAuthJson.parseToJsonElement(it).jsonObject }
        val accessToken = parsed.string("access_token")
            ?: throw IllegalStateException(parsed.string("error_description") ?: "Could not refresh Google Drive access.")
        val expiresAt = System.currentTimeMillis() + ((parsed.string("expires_in")?.toLongOrNull() ?: 3600L) * 1000L)
        current.copy(
            googleAccessToken = accessToken,
            googleAccessTokenExpiresAtEpochMillis = expiresAt
        )
    }

    private data class DesktopOAuthCode(
        val code: String,
        val redirectUri: String,
        val codeVerifier: String
    )

    private data class DesktopGoogleTokens(
        val idToken: String,
        val accessToken: String,
        val refreshToken: String,
        val expiresAtEpochMillis: Long
    )

    private fun googleOAuthCallbackPage(title: String, message: String): String {
        return """
            <!doctype html>
            <html lang="en">
            <head>
              <meta charset="utf-8">
              <meta name="viewport" content="width=device-width, initial-scale=1">
              <title>${title.escapeHtml()}</title>
              <style>
                body { font-family: system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; margin: 0; min-height: 100vh; display: grid; place-items: center; background: #f7f4ef; color: #1f1b16; }
                main { max-width: 34rem; padding: 2rem; text-align: center; }
                h1 { margin: 0 0 0.75rem; font-size: 1.75rem; }
                p { margin: 0; font-size: 1rem; line-height: 1.5; color: #5b5349; }
              </style>
            </head>
            <body>
              <main>
                <h1>${title.escapeHtml()}</h1>
                <p>${message.escapeHtml()}</p>
              </main>
            </body>
            </html>
        """.trimIndent()
    }
}

internal class DesktopAuthStore(
    private val settingsFile: File = File(desktopUserConfigRoot(), "auth.properties"),
    private val secretCodec: DesktopSecretCodec = DesktopSecretCodec.platform()
) {
    fun load(): DesktopAuthSession? {
        if (!settingsFile.isFile) return null
        val properties = Properties()
        return runCatching {
            settingsFile.inputStream().use(properties::load)
            val refreshTokenRef = properties.getProperty(RefreshTokenKey, "")
            val refreshToken = refreshTokenRef.takeIf { it.isNotBlank() }
                ?.let { secretCodec.unprotect(RefreshTokenKey, it) }
                .orEmpty()
            val googleRefreshTokenRef = properties.getProperty(GoogleRefreshTokenKey, "")
            val googleRefreshToken = googleRefreshTokenRef.takeIf { it.isNotBlank() }
                ?.let { secretCodec.unprotect(GoogleRefreshTokenKey, it) }
                .orEmpty()
            if (refreshToken.isBlank()) return null
            DesktopAuthSession(
                user = UserData(
                    uid = properties.getProperty("uid", ""),
                    displayName = properties.getProperty("displayName", "").takeIf { it.isNotBlank() },
                    photoUrl = properties.getProperty("photoUrl", "").takeIf { it.isNotBlank() },
                    email = properties.getProperty("email", "").takeIf { it.isNotBlank() }
                ),
                idToken = "",
                refreshToken = refreshToken,
                expiresAtEpochMillis = 0L,
                googleRefreshToken = googleRefreshToken
            )
        }.getOrNull()
    }

    fun save(session: DesktopAuthSession) {
        val protectedRefreshToken = protectRequired(RefreshTokenKey, session.refreshToken)
        val protectedGoogleRefreshToken = session.googleRefreshToken
            .takeIf { it.isNotBlank() }
            ?.let { protectRequired(GoogleRefreshTokenKey, it) }
        val properties = Properties().apply {
            setProperty("uid", session.user.uid)
            setProperty("displayName", session.user.displayName.orEmpty())
            setProperty("photoUrl", session.user.photoUrl.orEmpty())
            setProperty("email", session.user.email.orEmpty())
            setProperty(RefreshTokenKey, protectedRefreshToken)
            protectedGoogleRefreshToken?.let { setProperty(GoogleRefreshTokenKey, it) }
        }
        settingsFile.storePropertiesAtomically(properties, "Episteme desktop account")
    }

    fun clear() {
        secretCodec.delete(RefreshTokenKey)
        secretCodec.delete(GoogleRefreshTokenKey)
        settingsFile.delete()
    }

    private companion object {
        const val RefreshTokenKey = "firebaseRefreshTokenProtected"
        const val GoogleRefreshTokenKey = "googleRefreshTokenProtected"
    }

    private fun protectRequired(keyName: String, value: String): String {
        if (value.isBlank()) {
            throw IllegalArgumentException("Cannot save a desktop account without a refresh token.")
        }
        val protectedValue = secretCodec.protect(keyName, value)
        if (protectedValue.isBlank()) {
            throw IllegalStateException("Desktop secure key storage returned an empty value for $keyName.")
        }
        return protectedValue
    }
}

private val DesktopAuthJson = Json { ignoreUnknownKeys = true }
private const val DesktopGoogleOAuthScopes = "openid email profile https://www.googleapis.com/auth/drive.appdata"

private fun JsonObject.string(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull

private fun JsonObject.errorMessage(): String? {
    val error = this["error"].jsonObjectOrNull() ?: return null
    return error.string("message")
}

private fun randomUrlToken(length: Int): String {
    val bytes = ByteArray(length)
    SecureRandom().nextBytes(bytes)
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
}

private fun postForm(url: String, body: String): String {
    return postBody(url, body, "application/x-www-form-urlencoded")
}

private fun postJson(url: String, body: String): String {
    return postBody(url, body, "application/json; charset=UTF-8")
}

private fun postBody(url: String, body: String, contentType: String): String {
    val connection = (URL(url).openConnection() as HttpURLConnection).apply {
        requestMethod = "POST"
        setRequestProperty("Content-Type", contentType)
        setRequestProperty("Accept", "application/json")
        connectTimeout = 15_000
        readTimeout = 30_000
        doOutput = true
        doInput = true
    }
    return try {
        connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
        val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
        if (connection.responseCode !in 200..299) {
            val message = runCatching {
                DesktopAuthJson.parseToJsonElement(text).jsonObject.errorMessage()
            }.getOrNull()
            throw IllegalStateException(message ?: "HTTP ${connection.responseCode}: ${text.take(240)}")
        }
        text
    } finally {
        connection.disconnect()
    }
}

private fun formEncode(vararg pairs: Pair<String, String>): String {
    return formEncode(pairs.asIterable())
}

private fun formEncode(pairs: Iterable<Pair<String, String>>): String {
    return pairs.joinToString("&") { (key, value) -> "${urlEncode(key)}=${urlEncode(value)}" }
}

private fun urlEncode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())

private fun JsonElement?.jsonObjectOrNull(): JsonObject? = this as? JsonObject

private fun String.escapeHtml(): String = buildString(length) {
    this@escapeHtml.forEach { char ->
        when (char) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            '\'' -> append("&#39;")
            else -> append(char)
        }
    }
}

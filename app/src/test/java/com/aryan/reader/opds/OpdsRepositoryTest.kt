package com.aryan.reader.opds

import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class OpdsRepositoryTest {

    @Test
    fun `getCatalogs seeds default catalogs only once`() {
        val repository = repositoryWithFreshPrefs()

        val first = repository.getCatalogs()
        val second = repository.getCatalogs()

        assertEquals(2, first.size)
        assertEquals(first, second)
        assertTrue(first.all { it.isDefault })
        assertTrue(first.any { it.title == "Project Gutenberg" })
        assertTrue(first.any { it.title == "Standard Ebooks" })
    }

    @Test
    fun `add update and remove catalog preserve defaults and trim editable credentials`() {
        val repository = repositoryWithFreshPrefs()
        repository.getCatalogs()

        repository.addCatalog(" Custom ", " https://example.org/opds ", " user ", " pass ")
        val added = repository.getCatalogs().single { !it.isDefault }
        repository.updateCatalog(
            id = added.id,
            title = " Updated ",
            url = " https://example.org/new ",
            username = "   ",
            password = " token "
        )

        val updated = repository.getCatalogs().single { !it.isDefault }
        assertEquals("Updated", updated.title)
        assertEquals("https://example.org/new", updated.url)
        assertNull(updated.username)
        assertEquals("token", updated.password)

        val defaultId = repository.getCatalogs().first { it.isDefault }.id
        repository.removeCatalog(defaultId)
        assertEquals(3, repository.getCatalogs().size)

        repository.removeCatalog(updated.id)
        assertTrue(repository.getCatalogs().all { it.isDefault })
    }

    @Test
    fun `basic authenticator adds authorization once and ignores missing credentials`() {
        val request = Request.Builder().url("https://example.org/feed").build()
        val response = responseFor(request, "Basic realm=\"Catalog\"")

        val authenticated = OpdsRepository.OpdsAuthenticator("user", "pass")
            .authenticate(null, response)
        val missingCredentials = OpdsRepository.OpdsAuthenticator("", "pass")
            .authenticate(null, response)
        val alreadyAuthorized = OpdsRepository.OpdsAuthenticator("user", "pass")
            .authenticate(null, responseFor(request.newBuilder().header("Authorization", "old").build(), "Basic"))

        assertEquals("Basic dXNlcjpwYXNz", authenticated?.header("Authorization"))
        assertNull(missingCredentials)
        assertNull(alreadyAuthorized)
    }

    @Test
    fun `digest authenticator builds digest header with qop opaque and request uri`() {
        val request = Request.Builder()
            .url("https://example.org/catalog/feed?x=1")
            .build()
        val response = responseFor(
            request,
            "Digest realm=\"realm\", nonce=\"abc\", qop=\"auth\", opaque=\"opaque-token\""
        )

        val authenticated = OpdsRepository.OpdsAuthenticator("user", "pass")
            .authenticate(null, response)
        val header = authenticated?.header("Authorization").orEmpty()

        assertTrue(header.startsWith("Digest "))
        assertTrue(header.contains("""username="user""""))
        assertTrue(header.contains("""realm="realm""""))
        assertTrue(header.contains("""nonce="abc""""))
        assertTrue(header.contains("""uri="/catalog/feed?x=1""""))
        assertTrue(header.contains("qop=auth"))
        assertTrue(header.contains("nc=00000001"))
        assertTrue(header.contains("""cnonce=""""))
        assertTrue(header.contains("""opaque="opaque-token""""))
        assertNotNull(Regex("""response="[a-f0-9]{32}"""").find(header))
    }

    @Test
    fun `digest authenticator selects auth from qop list`() {
        val request = Request.Builder()
            .url("https://example.org/catalog/feed")
            .build()
        val response = responseFor(
            request,
            "Digest realm=\"realm\", nonce=\"abc\", qop=\"auth,auth-int\""
        )

        val authenticated = OpdsRepository.OpdsAuthenticator("user", "pass")
            .authenticate(null, response)
        val header = authenticated?.header("Authorization").orEmpty()

        assertTrue(header.contains("qop=auth"))
        assertTrue(!header.contains("auth,auth-int"))
    }

    @Test
    fun `authenticator ignores unsupported challenge`() {
        val request = Request.Builder().url("https://example.org/feed").build()

        assertNull(
            OpdsRepository.OpdsAuthenticator("user", "pass")
                .authenticate(null, responseFor(request, "Bearer realm=\"x\""))
        )
    }

    private fun repositoryWithFreshPrefs(): OpdsRepository {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("reader_opds_prefs", android.content.Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        return OpdsRepository(context)
    }

    private fun responseFor(request: Request, challenge: String): Response {
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(401)
            .message("Unauthorized")
            .header("WWW-Authenticate", challenge)
            .body("".toResponseBody(null))
            .build()
    }
}

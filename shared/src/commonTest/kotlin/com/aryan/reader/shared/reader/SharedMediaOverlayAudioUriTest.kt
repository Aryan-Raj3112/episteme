package com.aryan.reader.shared.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * How a media overlay addresses its own audio.
 *
 * Deliberately pure string handling, so the encoding that both platforms depend on is testable
 * without a device. The interesting cases are all round-trip ones: an entry path is opaque, it comes
 * from a publisher's SMIL document, and a single mangled character would open the wrong file — or,
 * worse, refuse to open any.
 */
class SharedMediaOverlayAudioUriTest {

    private fun roundTrip(path: String) =
        SharedMediaOverlayAudioUri.entryPathOf(SharedMediaOverlayAudioUri.uriFor(path))

    @Test
    fun `a plain path round trips`() {
        assertEquals("OEBPS/Audio/chapter1.mp3", roundTrip("OEBPS/Audio/chapter1.mp3"))
    }

    /**
     * Real books have spaces in media filenames, and a Uri path would otherwise split on them.
     */
    @Test
    fun `spaces round trip`() {
        assertEquals("OEBPS/Audio/chapter one.mp3", roundTrip("OEBPS/Audio/chapter one.mp3"))
    }

    /**
     * Non-ASCII names are the norm in a translated edition, and the encoding is over UTF-8 bytes —
     * encoding the characters instead would produce a Uri a media framework cannot parse back.
     */
    @Test
    fun `non-ascii names round trip`() {
        assertEquals("OEBPS/Audio/第一章.mp3", roundTrip("OEBPS/Audio/第一章.mp3"))
        assertEquals("Ünïcödé/chapter—1.mp3", roundTrip("Ünïcödé/chapter—1.mp3"))
    }

    @Test
    fun `characters with uri meaning are encoded`() {
        assertEquals("OEBPS%2FAudio%2F%23hash%3F.mp3", SharedMediaOverlayAudioUri.percentEncode("OEBPS/Audio/#hash?.mp3"))
        assertEquals("a%20b", SharedMediaOverlayAudioUri.percentEncode("a b"))
        // `+` is a space in a query string and literal in a path, which is exactly the kind of
        // ambiguity this encoding removes.
        assertEquals("%2B", SharedMediaOverlayAudioUri.percentEncode("+"))
    }

    /**
     * The separator is encoded too.
     *
     * Stricter than a path-segment encoder needs to be, and the reason is that an entry path is
     * opaque: keeping `/` literal would let anything downstream read it as a hierarchy and resolve a
     * `..` inside it against some other base.
     */
    @Test
    fun `slashes are encoded rather than left as path separators`() {
        assertEquals("OEBPS%2FAudio%2Fa.mp3", SharedMediaOverlayAudioUri.percentEncode("OEBPS/Audio/a.mp3"))
        assertEquals(SharedMediaOverlayAudioUri.scheme + ":///OEBPS%2FAudio%2Fa.mp3", SharedMediaOverlayAudioUri.uriFor("OEBPS/Audio/a.mp3"))
    }

    @Test
    fun `unreserved characters are left alone`() {
        val unreserved = "abcXYZ019-._~"
        assertEquals(unreserved, SharedMediaOverlayAudioUri.percentEncode(unreserved))
    }

    // --- foreign uris -----------------------------------------------------------------------

    /**
     * A `par` may legitimately point at a `file:` or `http:` URL. Those are handed to the ordinary
     * sources rather than rejected, so an unusual book plays instead of failing — which is why this
     * returns null rather than throwing.
     */
    @Test
    fun `a foreign uri is not treated as an archive entry`() {
        assertNull(SharedMediaOverlayAudioUri.entryPathOf("file:///tmp/a.mp3"))
        assertNull(SharedMediaOverlayAudioUri.entryPathOf("https://example.com/a.mp3"))
        assertNull(SharedMediaOverlayAudioUri.entryPathOf("content://media/1"))
    }

    @Test
    fun `an empty or bare scheme uri names nothing`() {
        assertNull(SharedMediaOverlayAudioUri.entryPathOf("${SharedMediaOverlayAudioUri.scheme}:///"))
        assertNull(SharedMediaOverlayAudioUri.entryPathOf("${SharedMediaOverlayAudioUri.scheme}://"))
        assertNull(SharedMediaOverlayAudioUri.entryPathOf(""))
    }

    /**
     * A malformed escape is passed through rather than dropped.
     *
     * It cannot come from our own encoder, so seeing one means the Uri came from somewhere else —
     * and silently deleting bytes there would turn a bad name into a *different* entry.
     */
    @Test
    fun `a malformed escape is preserved rather than silently dropped`() {
        assertEquals("a%zzb", SharedMediaOverlayAudioUri.percentDecode("a%zzb"))
        assertEquals("a%", SharedMediaOverlayAudioUri.percentDecode("a%"))
        assertEquals("a%4", SharedMediaOverlayAudioUri.percentDecode("a%4"))
    }

    @Test
    fun `escapes are case insensitive`() {
        assertEquals("a b", SharedMediaOverlayAudioUri.percentDecode("a%20b"))
        assertEquals("a b", SharedMediaOverlayAudioUri.percentDecode("a%20b".lowercase()))
        assertEquals("a/b", SharedMediaOverlayAudioUri.percentDecode("a%2Fb"))
        assertEquals("a/b", SharedMediaOverlayAudioUri.percentDecode("a%2fb"))
    }
}
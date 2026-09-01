package com.aryan.reader.data

import com.aryan.reader.shared.CloudBookTombstone
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidCloudBookDeleteOutboxTest {
    @Test
    fun `codec preserves shared tombstone fields and ignores malformed entries`() {
        val expected = listOf(CloudBookTombstone("book-1", "PDF", 42L))
        val encoded = CloudBookDeleteOutboxCodec.encode(expected)

        assertEquals(expected, CloudBookDeleteOutboxCodec.decode(encoded))
        assertTrue(CloudBookDeleteOutboxCodec.decode("not-json").isEmpty())
        assertTrue(CloudBookDeleteOutboxCodec.decodeResult("not-json") is CloudBookDeleteOutboxCodec.DecodeResult.Malformed)
        assertTrue(
            CloudBookDeleteOutboxCodec.decodeResult("[{\"deletedAt\":42}]") is
                CloudBookDeleteOutboxCodec.DecodeResult.Malformed,
        )
    }

    @Test
    fun `clear removes all durable deletion intents`() {
        val editor = mockk<SharedPreferences.Editor>(relaxed = true)
        val preferences = mockk<SharedPreferences>(relaxed = true)
        every { preferences.edit() } returns editor
        every { editor.remove(any()) } returns editor
        every { editor.commit() } returns true

        AndroidCloudBookDeleteOutbox(preferences).clear("user-a")

        verify { editor.remove(any()) }
        verify { editor.commit() }
    }

    @Test
    fun `remote failure leaves local state untouched`() = kotlinx.coroutines.test.runTest {
        val localEvents = mutableListOf<String>()

        val thrown = runCatching {
            executeRemoteFirstLocalDelete(
                local = "book-1",
                deleteRemote = { error("network failure") },
                markDeleted = { localEvents += "mark" },
                finalizeLocal = { localEvents += "finalize" },
            )
        }.exceptionOrNull()

        assertTrue(thrown is IllegalStateException)
        assertTrue(localEvents.isEmpty())
    }

    @Test
    fun `remote work completes before local mutation`() = kotlinx.coroutines.test.runTest {
        val events = mutableListOf<String>()

        executeRemoteFirstLocalDelete(
            local = "book-1",
            deleteRemote = {
                events += "drive"
                events += "firestore"
            },
            markDeleted = { events += "mark" },
            finalizeLocal = { events += "finalize" },
        )

        assertEquals(listOf("drive", "firestore", "mark", "finalize"), events)
    }
}

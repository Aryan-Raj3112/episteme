package com.aryan.reader.paginatedreader

import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.text.TextLayoutResult
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `onGloballyPositioned` re-registers every text block on every layout pass, and it passes the same
 * `TextLayoutResult` / `LayoutCoordinates` / `TextContentBlock` instances when nothing changed.
 * `SnapshotStateMap.put` compares the stored value with the incoming one (that `Intrinsics.areEqual`
 * frame is this check) and retaining/ignoring an unchanged registration still costs the deep
 * `equals` of `TextLayoutResult`/`ParagraphBlock` — measured as an ANR sampled inside
 * `SnapshotStateMap.put` (docs/crashlytics-triage.md#131).
 *
 * `ReactiveBlockMap` must therefore skip the delegate write when the registered triple is
 * unchanged, while still advancing `tick` so layer-phase consumers re-read live coordinates.
 */
class ReactiveBlockMapTest {

    private class CountingDelegate {
        val inner = mutableMapOf<String, Triple<TextLayoutResult, LayoutCoordinates, TextContentBlock>>()
        var puts = 0
        val map = object : MutableMap<String, Triple<TextLayoutResult, LayoutCoordinates, TextContentBlock>> by inner {
            override fun put(
                key: String,
                value: Triple<TextLayoutResult, LayoutCoordinates, TextContentBlock>,
            ): Triple<TextLayoutResult, LayoutCoordinates, TextContentBlock>? {
                puts++
                return inner.put(key, value)
            }
        }
    }

    @Test
    fun `re-registering the same layout does not write the delegate but still ticks`() {
        val delegate = CountingDelegate()
        val map = ReactiveBlockMap(delegate.map)
        val layout = mockk<TextLayoutResult>(relaxed = true)
        val coordinates = coordinates()
        val block = mockk<TextContentBlock>(relaxed = true)
        val first = Triple(layout, coordinates, block)

        map["cfi_1"] = first
        val tickAfterFirstPut = map.tick
        assertEquals(1, delegate.puts)

        // A new Triple instance with identical components: what onGloballyPositioned passes on the
        // next layout pass when nothing actually changed.
        val returned = map.put("cfi_1", Triple(layout, coordinates, block))

        assertSame(first, returned)
        assertSame(first, map["cfi_1"])
        assertEquals("an unchanged registration must not reach the delegate", 1, delegate.puts)
        assertTrue("tick must advance so consumers re-read live coordinates", map.tick > tickAfterFirstPut)
    }

    @Test
    fun `a different text layout is written`() {
        val delegate = CountingDelegate()
        val map = ReactiveBlockMap(delegate.map)
        val coordinates = coordinates()
        val block = mockk<TextContentBlock>(relaxed = true)
        map["cfi_1"] = Triple(mockk<TextLayoutResult>(relaxed = true), coordinates, block)

        val relaidOut = mockk<TextLayoutResult>(relaxed = true)
        map["cfi_1"] = Triple(relaidOut, coordinates, block)

        assertEquals(2, delegate.puts)
        assertSame(relaidOut, map["cfi_1"]!!.first)
    }

    @Test
    fun `different coordinates are written`() {
        val delegate = CountingDelegate()
        val map = ReactiveBlockMap(delegate.map)
        val layout = mockk<TextLayoutResult>(relaxed = true)
        val block = mockk<TextContentBlock>(relaxed = true)
        map["cfi_1"] = Triple(layout, coordinates(), block)

        val moved = coordinates()
        map["cfi_1"] = Triple(layout, moved, block)

        assertEquals(2, delegate.puts)
        assertSame(moved, map["cfi_1"]!!.second)
    }

    @Test
    fun `a different content block is written`() {
        val delegate = CountingDelegate()
        val map = ReactiveBlockMap(delegate.map)
        val layout = mockk<TextLayoutResult>(relaxed = true)
        val coordinates = coordinates()
        map["cfi_1"] = Triple(layout, coordinates, mockk<TextContentBlock>(relaxed = true))

        val reblocked = mockk<TextContentBlock>(relaxed = true)
        map["cfi_1"] = Triple(layout, coordinates, reblocked)

        assertEquals(2, delegate.puts)
        assertSame(reblocked, map["cfi_1"]!!.third)
    }

    @Test
    fun `every distinct key is stored`() {
        val delegate = CountingDelegate()
        val map = ReactiveBlockMap(delegate.map)
        val layout = mockk<TextLayoutResult>(relaxed = true)
        val coordinates = coordinates()
        val block = mockk<TextContentBlock>(relaxed = true)

        map["cfi_1"] = Triple(layout, coordinates, block)
        map["cfi_2"] = Triple(layout, coordinates, block)

        assertEquals(2, delegate.puts)
        assertEquals(setOf("cfi_1", "cfi_2"), map.keys)
    }

    @Test
    fun `repeated redundant registrations across many layout passes never write again`() {
        // A draw-time layout can dispatch on-positioned callbacks several times per traversal, and
        // this repeat arrived from `AndroidComposeView.dispatchDraw` -> `measureAndLayout` ->
        // `dispatchOnPositionedCallbacks` (docs/crashlytics-triage.md#131, repeat). The guard must
        // hold for every pass, not just the one after the write.
        val delegate = CountingDelegate()
        val map = ReactiveBlockMap(delegate.map)
        val layout = mockk<TextLayoutResult>(relaxed = true)
        val coordinates = coordinates()
        val block = mockk<TextContentBlock>(relaxed = true)

        map["cfi_1"] = Triple(layout, coordinates, block)
        val tickAfterFirstPut = map.tick
        repeat(5) { map["cfi_1"] = Triple(layout, coordinates, block) }

        assertEquals("only the first registration may reach the delegate", 1, delegate.puts)
        assertTrue("tick must advance on every pass", map.tick >= tickAfterFirstPut + 5)
    }

    private fun coordinates(): LayoutCoordinates = mockk(relaxed = true)
}

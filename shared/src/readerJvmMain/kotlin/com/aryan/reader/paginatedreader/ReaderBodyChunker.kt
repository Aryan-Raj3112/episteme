package com.aryan.reader.paginatedreader

import org.jsoup.nodes.Element
import org.jsoup.nodes.Node

/**
 * Chapter body → virtual chunk ranges, shared by every consumer.
 *
 * The WebView reader renders a chapter as N chunk containers, and a search hit
 * carries a chunk index so the reader can load exactly the chunk it needs.
 * Those two must be the same chunker: when the searcher grouped raw body
 * children and the reader descended into a lone wrapper container, every hit in
 * a wrapped chapter reported chunk 0 and navigation jumped to the chapter's
 * first match.
 */
data class SharedReaderBodyChunk(
    val nodes: List<Node>,
    val elementStartIndex: Int,
    val elementCount: Int,
)

/** Default grouping of top-level nodes per virtual chunk (Android benchmark). */
const val READER_BODY_CHUNK_NODE_COUNT = 20

/**
 * Standard Ebooks chapters wrap everything in a single container
 * (`<section id="...">`); chunking the wrapper itself collapses the whole
 * chapter into one 200000px+ chunk and defeats virtualization. Descend into
 * lone passthrough containers so figures and paragraphs chunk.
 */
fun sharedEffectiveReaderChunkNodes(
    bodyNodes: List<Node>,
    chunkSize: Int = READER_BODY_CHUNK_NODE_COUNT,
): List<Node> {
    val size = chunkSize.coerceAtLeast(1)
    var nodes = bodyNodes
    var guard = 0
    while (guard++ < 8) {
        val elements = nodes.filterIsInstance<Element>()
        if (elements.size != 1) break
        val container = elements.single()
        if (container.tagName().lowercase() !in READER_CHUNK_PASSTHROUGH_CONTAINERS) break
        val innerElements = container.childNodes().filterIsInstance<Element>()
        if (innerElements.size <= size) break
        nodes = container.childNodes().toList()
    }
    return nodes
}

/** Groups a chapter body into the exact chunk ranges the reader renders. */
fun sharedReaderBodyChunks(
    bodyNodes: List<Node>,
    chunkSize: Int = READER_BODY_CHUNK_NODE_COUNT,
): List<SharedReaderBodyChunk> {
    var elementStartIndex = 0
    return sharedEffectiveReaderChunkNodes(bodyNodes, chunkSize).chunked(chunkSize).map { nodes ->
        val elementCount = nodes.count { it is Element }
        SharedReaderBodyChunk(
            nodes = nodes,
            elementStartIndex = elementStartIndex,
            elementCount = elementCount,
        ).also { elementStartIndex += elementCount }
    }
}

/** The markup one chunk contributes to the reader document. */
fun SharedReaderBodyChunk.readerChunkHtml(): String = nodes.joinToString(separator = "\n") { it.outerHtml() }

private val READER_CHUNK_PASSTHROUGH_CONTAINERS = setOf("section", "div", "article", "main", "aside")

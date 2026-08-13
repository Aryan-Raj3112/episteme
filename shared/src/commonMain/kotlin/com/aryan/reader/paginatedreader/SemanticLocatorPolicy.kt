package com.aryan.reader.paginatedreader

data class ParsedSemanticCfi(
    val basePath: String,
    val charOffset: Int
)

fun parseSemanticCfi(cfi: String): ParsedSemanticCfi {
    val firstPoint = cfi.substringBefore('|')
    val separator = firstPoint.lastIndexOf(':')
    val basePath = if (separator > 0) firstPoint.substring(0, separator) else firstPoint
    val offset = if (separator > 0 && separator < firstPoint.lastIndex) {
        firstPoint.substring(separator + 1).toIntOrNull() ?: 0
    } else {
        0
    }
    return ParsedSemanticCfi(basePath, offset)
}

fun findBestMatchingSemanticBlock(blocks: List<SemanticBlock>, inputCfi: String): SemanticBlock? {
    val flattened = mutableListOf<SemanticBlock>()
    fun append(items: List<SemanticBlock>) {
        items.forEach { block ->
            flattened += block
            when (block) {
                is SemanticFlexContainer -> append(block.children)
                is SemanticTable -> block.rows.forEach { row -> row.forEach { cell -> append(cell.content) } }
                is SemanticList -> append(block.items)
                else -> Unit
            }
        }
    }
    append(blocks)
    return flattened.asSequence().filter { it.cfi != null }.map { block ->
        val blockCfi = block.cfi!!
        val prefixScore = if (inputCfi == blockCfi || inputCfi.startsWith("$blockCfi/")) blockCfi.length else 0
        var inputIndex = inputCfi.lastIndex
        var blockIndex = blockCfi.lastIndex
        var suffixScore = 0
        while (inputIndex >= 0 && blockIndex >= 0 && inputCfi[inputIndex] == blockCfi[blockIndex]) {
            suffixScore++
            inputIndex--
            blockIndex--
        }
        block to maxOf(prefixScore, suffixScore)
    }.maxByOrNull { it.second }?.first
}

fun findSemanticBlockByIndex(blocks: List<SemanticBlock>, targetBlockIndex: Int): SemanticBlock? {
    val queue = ArrayDeque(blocks)
    while (queue.isNotEmpty()) {
        val block = queue.removeFirst()
        if (block.blockIndex == targetBlockIndex) return block
        when (block) {
            is SemanticFlexContainer -> queue.addAll(block.children)
            is SemanticTable -> block.rows.forEach { row -> row.forEach { cell -> queue.addAll(cell.content) } }
            is SemanticList -> queue.addAll(block.items)
            else -> Unit
        }
    }
    return null
}

fun semanticCfiForBlock(block: SemanticBlock, absoluteCharOffset: Int): String? = block.cfi?.let { cfi ->
    val localOffset = when (block) {
        is SemanticTextBlock -> {
            val start = block.startCharOffsetInSource
            val end = start + block.text.length
            (if (absoluteCharOffset in start..end) absoluteCharOffset - start else absoluteCharOffset)
                .coerceIn(0, block.text.length)
        }
        else -> absoluteCharOffset.coerceAtLeast(0)
    }
    if (localOffset > 0) "$cfi:$localOffset" else cfi
}

fun estimateSemanticPageCount(blocks: List<SemanticBlock>): Int {
    var charCount = 0
    fun walk(block: SemanticBlock) {
        when (block) {
            is SemanticTextBlock -> charCount += block.text.length
            is SemanticFlexContainer -> block.children.forEach(::walk)
            is SemanticTable -> block.rows.forEach { row -> row.forEach { cell -> cell.content.forEach(::walk) } }
            is SemanticList -> block.items.forEach(::walk)
            is SemanticWrappingBlock -> block.paragraphsToWrap.forEach(::walk)
            else -> Unit
        }
    }
    blocks.forEach(::walk)
    return ((charCount + 2_499) / 2_500).coerceAtLeast(1)
}

fun semanticTextOffset(
    blocks: List<SemanticBlock>,
    targetBlockIndex: Int,
    targetCharOffset: Int
): Int? {
    var offset = 0
    fun traverse(items: List<SemanticBlock>): Boolean {
        items.forEach { block ->
            if (block.blockIndex == targetBlockIndex) {
                val absoluteOffset = (block as? SemanticTextBlock)?.let { textBlock ->
                    val start = textBlock.startCharOffsetInSource
                    val end = start + textBlock.text.length
                    targetCharOffset.takeIf { (start > 0 || offset == 0) && it in start..end }
                }
                offset = absoluteOffset ?: offset + targetCharOffset
                return true
            }
            if (block is SemanticTextBlock) offset += block.text.length + 1
            val children = when (block) {
                is SemanticFlexContainer -> block.children
                is SemanticTable -> block.rows.flatten().flatMap { it.content }
                is SemanticList -> block.items
                is SemanticWrappingBlock -> block.paragraphsToWrap
                else -> emptyList()
            }
            if (children.isNotEmpty() && traverse(children)) return true
        }
        return false
    }
    return if (traverse(blocks)) offset else null
}

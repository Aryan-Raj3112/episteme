@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.aryan.reader.shared.ios

import com.aryan.reader.shared.BookItem
import com.aryan.reader.shared.reader.SharedEpubArchive
import com.aryan.reader.shared.reader.SharedEpubBook
import com.aryan.reader.shared.reader.SharedEpubPackageLoader
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask
import platform.posix.memcpy
import platform.zlib.MAX_WBITS
import platform.zlib.Z_FINISH
import platform.zlib.Z_OK
import platform.zlib.Z_STREAM_END
import platform.zlib.inflate
import platform.zlib.inflateEnd
import platform.zlib.inflateInit2
import platform.zlib.z_stream

internal fun loadIosEpubBook(book: BookItem): SharedEpubBook {
    val path = book.path.resolveIosEpubSourcePath()
        ?: error("EPUB path is unavailable")
    val archive = IosZipEpubArchive(path)
    return SharedEpubPackageLoader.load(
        archive = archive,
        sourceId = book.id,
        fileName = book.displayName.ifBlank { path.substringAfterLast('/') }
    )
}

private class IosZipEpubArchive(path: String) : SharedEpubArchive {
    private val archiveBytes = path.readIosFileBytes()
    private val entries: Map<String, IosZipEntry> = parseZipEntries(archiveBytes)

    override val entryPaths: Set<String> = entries.values.mapTo(linkedSetOf()) { it.path }

    override fun readBytes(path: String): ByteArray? {
        val normalized = normalizeIosZipPath(path) ?: return null
        val entry = entries[normalized.lowercase()] ?: return null
        val localOffset = entry.localHeaderOffset.checkedInt("local header offset")
        archiveBytes.requireSignature(localOffset, ZipLocalHeaderSignature, "local file header")
        val fileNameLength = archiveBytes.u16(localOffset + 26)
        val extraLength = archiveBytes.u16(localOffset + 28)
        val dataOffsetLong = entry.localHeaderOffset + 30L + fileNameLength + extraLength
        val dataOffset = dataOffsetLong.checkedInt("entry data offset")
        val compressedSize = entry.compressedSize.checkedInt("compressed entry size")
        require(
            dataOffsetLong >= 0 &&
                dataOffsetLong + entry.compressedSize >= dataOffsetLong &&
                dataOffsetLong + entry.compressedSize <= archiveBytes.size.toLong()
        ) {
            "EPUB ZIP entry exceeds archive bounds: ${entry.path}"
        }
        val compressed = archiveBytes.copyOfRange(dataOffset, dataOffset + compressedSize)
        val output = when (entry.compressionMethod) {
            ZipMethodStored -> compressed
            ZipMethodDeflate -> inflateRawZipEntry(compressed, entry.uncompressedSize.checkedInt("uncompressed entry size"))
            else -> error("Unsupported EPUB ZIP compression method ${entry.compressionMethod}: ${entry.path}")
        }
        require(output.size.toLong() == entry.uncompressedSize) {
            "EPUB ZIP entry size mismatch: ${entry.path}"
        }
        require(output.crc32() == entry.crc32) {
            "EPUB ZIP entry checksum mismatch: ${entry.path}"
        }
        return output
    }

    override fun readText(path: String): String? {
        val bytes = readBytes(path) ?: return null
        return bytes.decodeEpubText()
    }
}

private data class IosZipEntry(
    val path: String,
    val compressionMethod: Int,
    val compressedSize: Long,
    val uncompressedSize: Long,
    val crc32: UInt,
    val localHeaderOffset: Long
)

private fun parseZipEntries(bytes: ByteArray): Map<String, IosZipEntry> {
    val eocdOffset = bytes.findZipEndOfCentralDirectory()
        ?: error("EPUB ZIP central directory was not found")
    val diskNumber = bytes.u16(eocdOffset + 4)
    val centralDisk = bytes.u16(eocdOffset + 6)
    require(diskNumber == 0 && centralDisk == 0) { "Multi-disk EPUB ZIP archives are not supported" }

    val entriesOnDisk = bytes.u16(eocdOffset + 8)
    var entryCount = bytes.u16(eocdOffset + 10).toLong()
    require(entriesOnDisk == 0xFFFF || entriesOnDisk.toLong() == entryCount) {
        "Multi-disk EPUB ZIP archives are not supported"
    }
    var centralSize = bytes.u32(eocdOffset + 12)
    var centralOffset = bytes.u32(eocdOffset + 16)
    if (entryCount == 0xFFFFL || centralSize == ZipUInt32Sentinel || centralOffset == ZipUInt32Sentinel) {
        val locatorOffset = eocdOffset - 20
        bytes.requireSignature(locatorOffset, Zip64LocatorSignature, "ZIP64 locator")
        require(bytes.u32(locatorOffset + 4) == 0L && bytes.u32(locatorOffset + 16) == 1L) {
            "Multi-disk ZIP64 EPUB archives are not supported"
        }
        val zip64Offset = bytes.u64(locatorOffset + 8).checkedInt("ZIP64 directory offset")
        bytes.requireSignature(zip64Offset, Zip64EndOfCentralDirectorySignature, "ZIP64 central directory")
        require(bytes.u32(zip64Offset + 16) == 0L && bytes.u32(zip64Offset + 20) == 0L) {
            "Multi-disk ZIP64 EPUB archives are not supported"
        }
        entryCount = bytes.u64(zip64Offset + 32)
        centralSize = bytes.u64(zip64Offset + 40)
        centralOffset = bytes.u64(zip64Offset + 48)
    }
    require(entryCount in 0..ZipMaximumEntries) { "EPUB ZIP has an unreasonable number of entries: $entryCount" }
    require(centralSize in 0..ZipMaximumCentralDirectoryBytes) { "EPUB ZIP central directory is too large" }
    require(centralOffset >= 0 && centralOffset + centralSize >= centralOffset && centralOffset + centralSize <= eocdOffset.toLong()) {
        "EPUB ZIP central directory exceeds archive bounds"
    }

    val output = linkedMapOf<String, IosZipEntry>()
    var totalUncompressedSize = 0L
    var cursor = centralOffset.checkedInt("central directory offset")
    repeat(entryCount.toInt()) {
        bytes.requireSignature(cursor, ZipCentralHeaderSignature, "central directory entry")
        val flags = bytes.u16(cursor + 8)
        require(flags and ZipEncryptedFlag == 0) { "Encrypted EPUB ZIP entries are not supported" }
        val method = bytes.u16(cursor + 10)
        val checksum = bytes.u32(cursor + 16).toUInt()
        var compressedSize = bytes.u32(cursor + 20)
        var uncompressedSize = bytes.u32(cursor + 24)
        val nameLength = bytes.u16(cursor + 28)
        val extraLength = bytes.u16(cursor + 30)
        val commentLength = bytes.u16(cursor + 32)
        var localOffset = bytes.u32(cursor + 42)
        val nameStart = cursor + 46
        val extraStart = nameStart + nameLength
        val entryEnd = extraStart + extraLength + commentLength
        require(nameStart >= 0 && entryEnd <= bytes.size) { "EPUB ZIP central entry exceeds archive bounds" }
        val rawName = bytes.copyOfRange(nameStart, nameStart + nameLength).decodeZipEntryName(flags)

        if (compressedSize == ZipUInt32Sentinel || uncompressedSize == ZipUInt32Sentinel || localOffset == ZipUInt32Sentinel) {
            val zip64 = bytes.parseZip64Extra(extraStart, extraLength)
            var valueIndex = 0
            if (uncompressedSize == ZipUInt32Sentinel) uncompressedSize = zip64.getOrElse(valueIndex++) { error("Missing ZIP64 uncompressed size") }
            if (compressedSize == ZipUInt32Sentinel) compressedSize = zip64.getOrElse(valueIndex++) { error("Missing ZIP64 compressed size") }
            if (localOffset == ZipUInt32Sentinel) localOffset = zip64.getOrElse(valueIndex) { error("Missing ZIP64 local offset") }
        }
        require(uncompressedSize <= ZipMaximumEntryBytes) { "EPUB ZIP entry is too large: $rawName" }
        require(totalUncompressedSize <= ZipMaximumTotalUncompressedBytes - uncompressedSize) {
            "EPUB ZIP expands beyond the supported size"
        }
        totalUncompressedSize += uncompressedSize
        val normalized = normalizeIosZipPath(rawName)
        if (normalized != null && !rawName.endsWith('/')) {
            output[normalized.lowercase()] = IosZipEntry(
                path = normalized,
                compressionMethod = method,
                compressedSize = compressedSize,
                uncompressedSize = uncompressedSize,
                crc32 = checksum,
                localHeaderOffset = localOffset
            )
        }
        cursor = entryEnd
    }
    require(output.isNotEmpty()) { "EPUB ZIP contains no files" }
    return output
}

private fun ByteArray.parseZip64Extra(offset: Int, length: Int): List<Long> {
    var cursor = offset
    val end = offset + length
    while (cursor + 4 <= end) {
        val headerId = u16(cursor)
        val dataSize = u16(cursor + 2)
        val dataStart = cursor + 4
        val dataEnd = dataStart + dataSize
        require(dataEnd <= end) { "Malformed EPUB ZIP extra field" }
        if (headerId == Zip64ExtraHeaderId) {
            val values = mutableListOf<Long>()
            var valueCursor = dataStart
            while (valueCursor + 8 <= dataEnd) {
                values += u64(valueCursor)
                valueCursor += 8
            }
            return values
        }
        cursor = dataEnd
    }
    return emptyList()
}

private fun inflateRawZipEntry(compressed: ByteArray, expectedSize: Int): ByteArray {
    if (expectedSize == 0) return ByteArray(0)
    require(compressed.isNotEmpty()) { "Compressed EPUB ZIP entry is empty" }
    val output = ByteArray(expectedSize)
    compressed.usePinned { inputPinned ->
        output.usePinned { outputPinned ->
            memScoped {
                val stream = alloc<z_stream>()
                stream.next_in = inputPinned.addressOf(0).reinterpret()
                stream.avail_in = compressed.size.convert()
                stream.total_in = 0uL
                stream.next_out = outputPinned.addressOf(0).reinterpret()
                stream.avail_out = output.size.convert()
                stream.total_out = 0uL
                stream.msg = null
                stream.state = null
                stream.zalloc = null
                stream.zfree = null
                stream.opaque = null
                stream.data_type = 0
                stream.adler = 0uL
                stream.reserved = 0uL
                val initialized = inflateInit2(stream.ptr, -MAX_WBITS)
                require(initialized == Z_OK) { "Could not initialize EPUB ZIP decompression: $initialized" }
                try {
                    val result = inflate(stream.ptr, Z_FINISH)
                    require(result == Z_STREAM_END) { "Could not decompress EPUB ZIP entry: $result" }
                    require(stream.total_out.toLong() == expectedSize.toLong()) { "EPUB ZIP decompression produced the wrong size" }
                } finally {
                    inflateEnd(stream.ptr)
                }
            }
        }
    }
    return output
}

private fun String?.resolveIosEpubSourcePath(): String? {
    val raw = this?.takeIf(String::isNotBlank) ?: return null
    if (NSFileManager.defaultManager.fileExistsAtPath(raw)) return raw
    val fileName = raw.substringAfterLast('/').takeIf(String::isNotBlank) ?: return null
    val appSupport = NSFileManager.defaultManager.URLsForDirectory(
        directory = NSApplicationSupportDirectory,
        inDomains = NSUserDomainMask
    ).firstOrNull() as? NSURL
    val importCandidate = appSupport?.URLByAppendingPathComponent("Imports", isDirectory = true)
        ?.URLByAppendingPathComponent(fileName, isDirectory = false)
        ?.path
    val documentsCandidate = (NSFileManager.defaultManager.URLsForDirectory(
        directory = NSDocumentDirectory,
        inDomains = NSUserDomainMask
    ).firstOrNull() as? NSURL)
        ?.URLByAppendingPathComponent(fileName, isDirectory = false)
        ?.path
    return listOfNotNull(importCandidate, documentsCandidate)
        .firstOrNull(NSFileManager.defaultManager::fileExistsAtPath)
}

private fun String.readIosFileBytes(): ByteArray {
    val data = NSFileManager.defaultManager.contentsAtPath(this) ?: error("Could not read EPUB file: $this")
    require(data.length <= ZipMaximumArchiveBytes.toULong()) { "EPUB file is too large" }
    val output = ByteArray(data.length.toInt())
    if (output.isNotEmpty()) {
        output.usePinned { pinned -> memcpy(pinned.addressOf(0), data.bytes, output.size.convert()) }
    }
    return output
}

private fun ByteArray.decodeEpubText(): String {
    if (size >= 2 && this[0] == 0xFF.toByte() && this[1] == 0xFE.toByte()) {
        return buildString((size - 2) / 2) {
            var index = 2
            while (index + 1 < size) {
                append(((this@decodeEpubText[index].toInt() and 0xFF) or ((this@decodeEpubText[index + 1].toInt() and 0xFF) shl 8)).toChar())
                index += 2
            }
        }
    }
    if (size >= 2 && this[0] == 0xFE.toByte() && this[1] == 0xFF.toByte()) {
        return buildString((size - 2) / 2) {
            var index = 2
            while (index + 1 < size) {
                append((((this@decodeEpubText[index].toInt() and 0xFF) shl 8) or (this@decodeEpubText[index + 1].toInt() and 0xFF)).toChar())
                index += 2
            }
        }
    }
    val utf8 = decodeToString().removePrefix("\uFEFF")
    if ('\uFFFD' !in utf8) return utf8
    return buildString(size) { this@decodeEpubText.forEach { append((it.toInt() and 0xFF).toChar()) } }
}

private fun ByteArray.decodeZipEntryName(flags: Int): String {
    val decoded = decodeToString()
    if ('\uFFFD' !in decoded || flags and ZipUtf8Flag != 0) return decoded
    return buildString(size) { this@decodeZipEntryName.forEach { append((it.toInt() and 0xFF).toChar()) } }
}

private fun normalizeIosZipPath(path: String): String? {
    val parts = ArrayDeque<String>()
    path.replace('\\', '/').split('/').forEach { part ->
        when (part) {
            "", "." -> Unit
            ".." -> if (parts.isEmpty()) return null else parts.removeLast()
            else -> {
                if ('\u0000' in part) return null
                parts.addLast(part)
            }
        }
    }
    return parts.joinToString("/").takeIf(String::isNotBlank)
}

private fun ByteArray.u16(offset: Int): Int {
    require(offset >= 0 && offset + 2 <= size) { "Unexpected end of EPUB ZIP" }
    return (this[offset].toInt() and 0xFF) or ((this[offset + 1].toInt() and 0xFF) shl 8)
}

private fun ByteArray.u32(offset: Int): Long {
    require(offset >= 0 && offset + 4 <= size) { "Unexpected end of EPUB ZIP" }
    return (u16(offset).toLong() or (u16(offset + 2).toLong() shl 16)) and 0xFFFF_FFFFL
}

private fun ByteArray.u64(offset: Int): Long {
    val low = u32(offset)
    val high = u32(offset + 4)
    require(high <= 0x7FFF_FFFFL) { "EPUB ZIP64 value exceeds supported range" }
    return low or (high shl 32)
}

private fun ByteArray.findZipEndOfCentralDirectory(): Int? {
    val minimum = (size - ZipMaximumCommentLength - 22).coerceAtLeast(0)
    for (offset in (size - 22).coerceAtLeast(0) downTo minimum) {
        if (
            u32(offset) == ZipEndOfCentralDirectorySignature &&
            offset + 22 + u16(offset + 20) == size
        ) {
            return offset
        }
    }
    return null
}

private fun ByteArray.requireSignature(offset: Int, signature: Long, label: String) {
    require(offset >= 0 && offset + 4 <= size && u32(offset) == signature) { "Malformed EPUB ZIP $label" }
}

private fun Long.checkedInt(label: String): Int {
    require(this in 0..Int.MAX_VALUE.toLong()) { "EPUB ZIP $label exceeds supported range" }
    return toInt()
}

private fun ByteArray.crc32(): UInt {
    var crc = 0xFFFF_FFFFu
    forEach { byte ->
        crc = crc xor (byte.toUInt() and 0xFFu)
        repeat(8) { crc = if (crc and 1u != 0u) (crc shr 1) xor 0xEDB8_8320u else crc shr 1 }
    }
    return crc xor 0xFFFF_FFFFu
}

private const val ZipLocalHeaderSignature = 0x04034B50L
private const val ZipCentralHeaderSignature = 0x02014B50L
private const val ZipEndOfCentralDirectorySignature = 0x06054B50L
private const val Zip64EndOfCentralDirectorySignature = 0x06064B50L
private const val Zip64LocatorSignature = 0x07064B50L
private const val ZipUInt32Sentinel = 0xFFFF_FFFFL
private const val Zip64ExtraHeaderId = 0x0001
private const val ZipMethodStored = 0
private const val ZipMethodDeflate = 8
private const val ZipEncryptedFlag = 0x0001
private const val ZipUtf8Flag = 0x0800
private const val ZipMaximumCommentLength = 65_535
private const val ZipMaximumEntries = 100_000L
private const val ZipMaximumCentralDirectoryBytes = 128L * 1024L * 1024L
private const val ZipMaximumEntryBytes = 512L * 1024L * 1024L
private const val ZipMaximumTotalUncompressedBytes = 1L * 1024L * 1024L * 1024L
private const val ZipMaximumArchiveBytes = 1L * 1024L * 1024L * 1024L

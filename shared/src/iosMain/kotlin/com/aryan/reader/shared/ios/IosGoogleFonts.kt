@file:OptIn(ExperimentalForeignApi::class)

package com.aryan.reader.shared.ios

import com.aryan.reader.shared.CustomFontItem
import com.aryan.reader.shared.currentTimestamp
import com.aryan.reader.shared.generated.resources.Res
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataWithContentsOfURL
import platform.Foundation.writeToURL

private const val IOS_GOOGLE_FONTS_DIRECTORY_NAME = "Fonts"

// An old Safari User-Agent forces Google to return the raw .ttf instead of .woff2.
private const val IOS_GOOGLE_FONTS_USER_AGENT =
    "Mozilla/5.0 (Macintosh; U; Intel Mac OS X 10_6_8; en-us) AppleWebKit/533.21.1 " +
        "(KHTML, like Gecko) Version/5.0.5 Safari/533.21.1"

private val IosGoogleFontsHttpClient = IosUrlSessionHttpClient()

private var iosGoogleFontNamesCache: List<String>? = null

internal suspend fun loadIosGoogleFontNames(): List<String> {
    iosGoogleFontNamesCache?.let { return it }
    val names = runCatching {
        val raw = Res.readBytes("files/google_fonts.json").decodeToString()
        Json.parseToJsonElement(raw).jsonArray.map { it.jsonPrimitive.content }
    }.getOrElse { emptyList() }
    iosGoogleFontNamesCache = names
    return names
}

internal suspend fun downloadIosGoogleFont(fontName: String): Result<CustomFontItem> {
    return runCatching {
        val cssUrl = "https://fonts.googleapis.com/css?family=${iosPercentEncode(fontName)}"
        val cssResponse = IosGoogleFontsHttpClient.fetch(
            url = cssUrl,
            username = null,
            password = null,
            headers = mapOf("User-Agent" to IOS_GOOGLE_FONTS_USER_AGENT),
        )
        if (cssResponse.error != null) error(cssResponse.error.localizedDescription)
        val statusCode = (cssResponse.response as? NSHTTPURLResponse)?.statusCode?.toInt() ?: 200
        if (statusCode != 200) error("Font '$fontName' not found on server.")
        val css = iosStringFromData(cssResponse.data) ?: error("Font '$fontName' not found on server.")

        val fontUrl = Regex("""url\((https://[^)]+)\)""")
            .find(css)
            ?.groupValues
            ?.get(1)
            ?: error("Could not parse download link for $fontName")
        val extension = fontUrl.substringAfterLast('.', "ttf").lowercase()
        if (extension != "ttf" && extension != "otf") {
            error("Unsupported format ($extension) returned for $fontName")
        }

        val data = NSData.dataWithContentsOfURL(
            NSURL.URLWithString(fontUrl) ?: error("Could not download font file for $fontName")
        ) ?: error("Could not download font file for $fontName")
        val directory = iosGoogleFontsDirectoryUrl()
            ?: error("Could not locate the fonts directory")
        val destination = iosUniqueFontDestination(directory, "$fontName.$extension")
        if (!data.writeToURL(destination, atomically = true)) {
            error("Could not save the downloaded font")
        }
        val path = destination.path ?: error("Could not save the downloaded font")
        CustomFontItem(
            id = "ios-font-${path.hashCode()}",
            displayName = fontName,
            fileName = "$fontName.$extension",
            fileExtension = extension,
            path = path,
            timestamp = currentTimestamp(),
        )
    }
}

private fun iosGoogleFontsDirectoryUrl(): NSURL? {
    val appSupport = NSFileManager.defaultManager.URLsForDirectory(
        directory = NSApplicationSupportDirectory,
        inDomains = NSUserDomainMask
    ).firstOrNull() as? NSURL ?: return null
    val directory = appSupport.URLByAppendingPathComponent(IOS_GOOGLE_FONTS_DIRECTORY_NAME, isDirectory = true)
        ?: return null
    NSFileManager.defaultManager.createDirectoryAtURL(
        directory,
        withIntermediateDirectories = true,
        attributes = null,
        error = null,
    )
    return directory
}

private fun iosUniqueFontDestination(directoryUrl: NSURL, preferredName: String): NSURL {
    var candidate = preferredName
    var suffix = 1
    while (NSFileManager.defaultManager.fileExistsAtPath(
            directoryUrl.URLByAppendingPathComponent(candidate)?.path.orEmpty()
        )
    ) {
        val stem = preferredName.substringBeforeLast('.', preferredName)
        val extension = preferredName.substringAfterLast('.', missingDelimiterValue = "")
        candidate = "${stem}_$suffix.$extension"
        suffix += 1
    }
    return directoryUrl.URLByAppendingPathComponent(candidate)!!
}

private fun iosStringFromData(data: NSData?): String? {
    return data?.let { NSString.create(data = it, encoding = NSUTF8StringEncoding)?.toString() }
}

private fun iosPercentEncode(value: String): String {
    val unreserved = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"
    val hexDigits = "0123456789ABCDEF"
    return buildString(value.length * 3) {
        value.encodeToByteArray().forEach { byte ->
            val char = byte.toInt().toChar()
            when {
                char in unreserved -> append(char)
                byte.toInt() == ' '.code -> append("%20")
                else -> {
                    append('%')
                    append(hexDigits[(byte.toInt() ushr 4) and 0xF])
                    append(hexDigits[byte.toInt() and 0xF])
                }
            }
        }
    }
}

package com.aryan.reader.shared.opds

/**
 * Shared policy for robust OPDS book downloads.
 *
 * Follows the HTTP Range standard (RFC 9110, Section 14.2):
 * resume with `Range: bytes=<offset>-`, accept `206 Partial Content`,
 * restart from zero when the server answers `200 OK` to a ranged request,
 * and use `If-Range` with a previously seen ETag so a changed
 * representation restarts instead of corrupting the file.
 *
 * Retry behaviour covers transient transport failures and retryable HTTP
 * statuses (408 / 429 / 500 / 502 / 503 / 504) with exponential backoff,
 * honoring `Retry-After` when the server sends one.
 */
object SharedOpdsResumableDownload {
    const val MaxAttempts = 5
    const val MaxRestarts = 2
    const val BaseDelayMs = 1_000L
    const val MaxDelayMs = 15_000L
    const val MaxRetryAfterMs = 60_000L

    fun rangeHeader(offset: Long): String {
        require(offset >= 0L) { "Range offset must be non-negative" }
        return "bytes=$offset-"
    }

    /**
     * Parses the total length from a `Content-Range` header such as
     * `bytes 0-1023/2048` or `bytes star-slash-2048`. Returns null when the
     * total is unknown (`*`) or the header is malformed.
     */
    fun parseContentRangeTotal(header: String?): Long? {
        if (header.isNullOrBlank()) return null
        val total = header.substringAfter('/', missingDelimiterValue = "").trim()
        if (total.isEmpty() || total == "*") return null
        return total.toLongOrNull()?.takeIf { it >= 0L }
    }

    /** Parses the first-byte position from `Content-Range: bytes <first>-<last>/<total>`. */
    fun parseContentRangeFirstByte(header: String?): Long? {
        if (header.isNullOrBlank()) return null
        val rangePart = header.substringBefore('/', missingDelimiterValue = "").trim()
        val dash = rangePart.indexOf('-')
        if (dash < 0) return null
        // Strip any unit prefix such as "bytes ".
        val first = rangePart.substring(dash.let { rangePart.lastIndexOf(' ', it) + 1 }, dash).trim()
        if (first.isEmpty() || first == "*") return null
        return first.toLongOrNull()?.takeIf { it >= 0L }
    }

    fun parseContentLength(header: String?): Long? {
        if (header.isNullOrBlank()) return null
        return header.trim().toLongOrNull()?.takeIf { it >= 0L }
    }

    /**
     * Resolves the full representation length for a download response.
     * For `206` the total comes from `Content-Range`, falling back to
     * `offset + Content-Length`. For `200` it is `Content-Length` directly.
     */
    fun resolveTotal(
        statusCode: Int,
        contentRange: String?,
        contentLength: Long?,
        offset: Long,
    ): Long? {
        if (statusCode == 206) {
            parseContentRangeTotal(contentRange)?.let { return it }
            if (contentLength != null && contentLength >= 0L) return offset + contentLength
            return null
        }
        if (statusCode in 200..299) {
            return contentLength?.takeIf { it >= 0L }
        }
        return null
    }

    fun isRetryableHttpStatus(code: Int): Boolean {
        return code == 408 || code == 429 ||
            code == 500 || code == 502 || code == 503 || code == 504
    }

    /**
     * Classifies transport failures by error domain/code.
     *
     * Covers Apple `NSURLErrorDomain` transient codes (notably -1005
     * "network connection was lost") and POSIX connection resets/timeouts.
     * Cancellation (-999) and TLS trust failures are never retryable.
     * Unknown errors are treated as retryable so a transient drop still
     * resumes; attempts remain bounded by [MaxAttempts].
     */
    fun isRetryableNetworkError(domain: String?, code: Long?): Boolean {
        if (domain == null && code == null) return true
        if (domain == "NSURLErrorDomain" || domain == "NSURLErrorDomainSwift") {
            return when (code) {
                -1001L, // Timed out
                -1003L, // Cannot find host (transient DNS)
                -1004L, // Cannot connect to host
                -1005L, // Network connection lost
                -1006L, // DNS lookup failed
                -1008L, // Resource unavailable
                -1009L, // Not connected to internet
                -1011L, // Bad server response
                -1020L, // Data not allowed (transient policy)
                -2000L, // Network load failed (WebKit transient)
                -> true
                else -> false
            }
        }
        if (domain == "NSPOSIXErrorDomain") {
            // ECONNRESET (54), ETIMEDOUT (60), ENETDOWN (50),
            // ENETUNREACH (51), ENETRESET (52), ECONNABORTED (53),
            // EHOSTDOWN (64), EHOSTUNREACH (65).
            return code in setOf(50L, 51L, 52L, 53L, 54L, 60L, 64L, 65L)
        }
        // Java / OkHttp style: generic IOExceptions without a clear
        // non-retryable signal are worth one bounded retry cycle.
        if (domain == "IOException" || domain == "InterruptedIOException" ||
            domain == "SocketTimeout" || domain == "ConnectException" ||
            domain == "UnknownHostException" || domain == "SSLException-Transient"
        ) {
            return true
        }
        return false
    }

    /** Parses a delta-seconds `Retry-After` header into milliseconds. HTTP dates are ignored. */
    fun parseRetryAfterMs(header: String?): Long? {
        if (header.isNullOrBlank()) return null
        val seconds = header.trim().toLongOrNull() ?: return null
        if (seconds < 0L) return null
        return (seconds * 1_000L).coerceAtMost(MaxRetryAfterMs)
    }

    /**
     * Backoff for the [failedAttempt]-th failure (1-based). Honors an
     * explicit `Retry-After` delay when present, otherwise exponential
     * 1s / 2s / 4s / 8s capped at [MaxDelayMs].
     */
    fun retryDelayMs(failedAttempt: Int, retryAfterMs: Long?): Long {
        if (retryAfterMs != null) return retryAfterMs.coerceIn(0L, MaxRetryAfterMs)
        val attempt = failedAttempt.coerceIn(1, MaxAttempts)
        var delay = BaseDelayMs
        repeat(attempt - 1) { delay = (delay * 2L).coerceAtMost(MaxDelayMs) }
        return delay.coerceAtMost(MaxDelayMs)
    }
}

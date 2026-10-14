package com.aryan.reader.shared.ios

import kotlin.concurrent.Volatile
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSFileManager
import platform.Foundation.NSLock
import platform.Foundation.NSURL

/**
 * Resolves a linked-folder book ref to a real filesystem path by way of the
 * security-scoped bookmark the picker handed us.
 *
 * The native side owns the bookmarks (they are `URL.BookmarkData` written into
 * `UserDefaults` by `ContentView.swift`), so Kotlin asks Swift for a resolved
 * URL. The Swift resolver holds the scope for the duration of the call, which
 * is enough for enumeration and hashing but *not* for a long-lived read: a PDF
 * keeps its fd open for the whole session and EPUB assets are re-read lazily
 * long after the opening call returned.
 *
 * That is what [IosFolderBookScope] is for. A plain function call cannot own a
 * scope, so anything holding a book open across a suspension must take a token
 * instead.
 */
internal fun interface IosFolderBookmarkResolver {
    /** Resolves [folderName] to a filesystem path, holding scope only for the call. */
    fun resolveFolderPath(folderName: String): String?
}

/**
 * Swift-owned scope driver, mirroring Android's indefinite SAF grant.
 *
 * Android's `takePersistableUriPermission` is process-wide and needs no
 * per-access wrapping. iOS security scope is per-URL and must be held during
 * every byte read: `resolveFolderPath` alone releases scope on return, so a
 * path it returns is dead by the time the caller reads through it on a real
 * device (File Provider / sandbox enforcement). The driver lets Swift — the
 * only side holding the bookmark-resolved `NSURL` — retain the URL and keep
 * `startAccessingSecurityScopedResource` balanced across an acquire/release
 * pair. Kotlin never synthesizes a scoped URL from a bare path.
 */
internal interface IosFolderScopeDriver {
    /** Acquires scope for [folderName] and returns its root path, or null. */
    fun acquire(folderName: String): String?

    /** Releases one acquire on [folderName]. */
    fun release(folderName: String)
}

internal object IosFolderBookScope {
    private val fallback = IosFolderBookmarkResolver { null }

    @Volatile
    private var installedResolver: IosFolderBookmarkResolver? = null

    @Volatile
    private var installedDriver: IosFolderScopeDriver? = null

    fun install(resolver: IosFolderBookmarkResolver) {
        installedResolver = resolver
    }

    fun installDriver(driver: IosFolderScopeDriver?) {
        installedDriver = driver
    }

    fun current(): IosFolderBookmarkResolver = installedResolver ?: fallback

    fun driver(): IosFolderScopeDriver? = installedDriver

    /**
     * Resolves a `ios-folder-book://` ref to a readable path, or null when the
     * folder is no longer reachable.
     */
    fun resolvePath(ref: String?): String? {
        val decoded = SharedIosBookSourceRef.decode(ref) ?: return null
        val folderPath = current().resolveFolderPath(decoded.folderName) ?: return null
        return decoded.resolveAgainst(folderPath)
    }
}

/**
 * A held security scope for one linked folder.
 *
 * `startAccessingSecurityScopedResource` is reference counted by the system and
 * must be balanced, so the token is idempotent: acquiring twice on the same
 * folder reuses the live scope, and the scope is released when the last holder
 * is closed. Long-lived readers should hold one of these for as long as they
 * touch the file.
 */
@OptIn(ExperimentalForeignApi::class)
internal class IosFolderScopeToken internal constructor(
    val folderName: String
) : AutoCloseable {
    internal var scopeURL: NSURL? = null
    internal var release: (() -> Unit)? = null

    /** The resolved folder root, valid only while this token is open. */
    val folderPath: String?
        get() = scopeURL?.path

    fun isHeld(): Boolean = scopeURL != null

    override fun close() {
        val onRelease = release
        release = null
        scopeURL = null
        onRelease?.invoke()
    }
}

@OptIn(ExperimentalForeignApi::class)
internal object IosFolderScopeRegistry {
    private val lock = NSLock()
    private val held = mutableMapOf<String, Pair<NSURL, Int>>()

    /**
     * Takes a scope on [folderName], or returns null when the folder cannot be
     * resolved. Callers must close the returned token.
     *
     * When a Swift [IosFolderScopeDriver] is installed, scope is held by Swift
     * on the bookmark-resolved `NSURL` — the only object that carries the
     * entitlement. The returned token's `folderPath` is valid until [close].
     * Without a driver (tests, simulator-local paths) it falls back to the
     * short resolver plus a locally-created URL, which is sufficient for
     * app-owned paths that need no entitlement.
     */
    fun acquire(folderName: String): IosFolderScopeToken? {
        IosFolderBookScope.driver()?.let { driver ->
            val folderPath = driver.acquire(folderName) ?: return null
            // Swift retains the scoped NSURL and refcounts; Kotlin only needs
            // to balance with release. Keep a file URL for path derivation so
            // folderPath stays available after close() clears state.
            val url = NSURL.fileURLWithPath(folderPath)
            return IosFolderScopeToken(folderName).also { token ->
                token.scopeURL = url
                token.release = { driver.release(folderName) }
            }
        }
        val folderPath = IosFolderBookScope.current().resolveFolderPath(folderName) ?: return null
        val url = NSURL.fileURLWithPath(folderPath)
        lock.lock()
        val existing = held[folderName]
        lock.unlock()
        if (existing != null) {
            // Another holder already has the scope; take a reference on it
            // rather than starting a second one.
            lock.lock()
            held[folderName] = existing.first to (existing.second + 1)
            lock.unlock()
        } else {
            if (!url.startAccessingSecurityScopedResource()) {
                return null
            }
            lock.lock()
            held[folderName] = url to 1
            lock.unlock()
        }
        return IosFolderScopeToken(folderName).also { token ->
            token.scopeURL = url
            token.release = { releaseOnce(folderName, url) }
        }
    }

    private fun releaseOnce(folderName: String, url: NSURL) {
        var shouldStop = false
        lock.lock()
        val entry = held[folderName]
        when {
            entry == null -> shouldStop = false
            entry.second <= 1 -> {
                held.remove(folderName)
                shouldStop = true
            }
            else -> held[folderName] = entry.first to (entry.second - 1)
        }
        lock.unlock()
        if (shouldStop) {
            url.stopAccessingSecurityScopedResource()
        }
    }

    /** Releases any scope still held for [folderName]; used when unlinking a folder. */
    fun releaseAll(folderName: String) {
        IosFolderBookScope.driver()?.release(folderName)
        lock.lock()
        val entry = held.remove(folderName)
        lock.unlock()
        entry?.first?.stopAccessingSecurityScopedResource()
    }
}

/**
 * Runs [block] with scope held for [folderName], mirroring Android's
 * grant-always-present SAF reads. The folder root path is only valid inside
 * [block]; returning it and reading later reintroduces the exact device-only
 * failure this exists to fix.
 */
internal fun <T> withIosFolderScope(folderName: String, block: (folderRoot: String) -> T): T? {
    IosFolderScopeRegistry.acquire(folderName)?.use { token ->
        val root = token.folderPath ?: return null
        return block(root)
    }
    return null
}

/** Suspend variant for PDF renders that open/close inside `withPdfium`. */
internal suspend fun <T> withIosFolderScopeSuspend(
    folderName: String,
    block: suspend (folderRoot: String) -> T,
): T? {
    IosFolderScopeRegistry.acquire(folderName)?.use { token ->
        val root = token.folderPath ?: return null
        return block(root)
    }
    return null
}

/**
 * Acquires scope for whatever [pathOrRef] points at (no-op for plain paths).
 * Caller must close the token. Null means plain path or unreachable ref.
 */
internal fun acquireScopeForBookPath(pathOrRef: String?): IosFolderScopeToken? {
    val decoded = SharedIosBookSourceRef.decode(pathOrRef) ?: return null
    return IosFolderScopeRegistry.acquire(decoded.folderName)
}

/**
 * Resolves [ref] and runs [block] with scope held for the whole read.
 * Returns null when the ref is not a provider ref or the folder is
 * unreachable — callers report "unavailable" and keep the row, never a guess.
 */
internal fun <T> withIosFolderBookPath(ref: String?, block: (resolvedPath: String) -> T): T? {
    val decoded = SharedIosBookSourceRef.decode(ref) ?: return null
    return withIosFolderScope(decoded.folderName) { root ->
        block(decoded.resolveAgainst(root))
    }
}

/**
 * Resolves either a provider ref (scoped) or a plain/app-managed path
 * (direct) and runs [block]. Plain paths need no scope.
 */
internal fun <T> withResolvedIosBookPath(pathOrRef: String?, block: (resolvedPath: String) -> T): T? {
    val value = pathOrRef?.trim()?.takeIf { it.isNotBlank() } ?: return null
    if (SharedIosBookSourceRef.isProviderRef(value)) {
        return withIosFolderBookPath(value, block)
    }
    val direct = value.resolveIosReadablePath() ?: return null
    return block(direct)
}

/** Scoped byte read for a provider ref; null when unreachable or unreadable. */
internal fun readIosFolderBookBytes(ref: String?): ByteArray? {
    return withIosFolderBookPath(ref) { resolved ->
        NSFileManager.defaultManager.contentsAtPath(resolved)?.toIosByteArray()
    }
}

/** Resolves a book ref or a plain path to something readable right now. */
internal fun resolveIosFolderBookPath(ref: String?): String? {
    if (!SharedIosBookSourceRef.isProviderRef(ref)) return null
    return IosFolderBookScope.resolvePath(ref)
        ?.takeIf { NSFileManager.defaultManager.fileExistsAtPath(it) }
}

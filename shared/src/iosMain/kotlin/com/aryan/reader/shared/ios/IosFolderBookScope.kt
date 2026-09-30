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

internal object IosFolderBookScope {
    private val fallback = IosFolderBookmarkResolver { null }

    @Volatile
    private var installedResolver: IosFolderBookmarkResolver? = null

    fun install(resolver: IosFolderBookmarkResolver) {
        installedResolver = resolver
    }

    fun current(): IosFolderBookmarkResolver = installedResolver ?: fallback

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
     */
    fun acquire(folderName: String): IosFolderScopeToken? {
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
        lock.lock()
        val entry = held.remove(folderName)
        lock.unlock()
        entry?.first?.stopAccessingSecurityScopedResource()
    }
}

/** Resolves a book ref or a plain path to something readable right now. */
internal fun resolveIosFolderBookPath(ref: String?): String? {
    if (!SharedIosBookSourceRef.isProviderRef(ref)) return null
    return IosFolderBookScope.resolvePath(ref)
        ?.takeIf { NSFileManager.defaultManager.fileExistsAtPath(it) }
}

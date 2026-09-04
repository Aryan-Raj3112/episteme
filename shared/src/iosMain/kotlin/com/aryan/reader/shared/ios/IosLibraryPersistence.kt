package com.aryan.reader.shared.ios

import com.aryan.reader.shared.SharedLibrarySnapshot
import com.aryan.reader.shared.SharedLibrarySnapshotJson
import com.aryan.reader.shared.SharedReaderScreenState
import com.aryan.reader.shared.migrateAndroidEpubFormatSettings
import com.aryan.reader.shared.toSharedMobileLibrarySnapshot
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import platform.Foundation.NSUserDefaults

private const val IosReaderPreferencesDefaultsKey = "reader_ios_reader_preferences_v1"
private const val IosLibrarySnapshotDefaultsKey = "reader_ios_library_snapshot_v1"

/** Debounce window for snapshot persistence; rapid state churn coalesces. */
private const val IosLibrarySnapshotPersistDelayMs = 500L

internal fun decodeIosLibrarySnapshotJson(encoded: String): SharedLibrarySnapshot? {
    return runCatching { SharedLibrarySnapshotJson.decodeOrEmpty(encoded) }
        .getOrNull()
        ?.takeIf { snapshot -> snapshot != SharedLibrarySnapshot() || encoded.isNotBlank() }
}

internal fun loadIosLibrarySnapshot(): SharedLibrarySnapshot {
    val defaults = NSUserDefaults.standardUserDefaults
    val encoded = defaults.stringForKey(IosLibrarySnapshotDefaultsKey)
        ?: defaults.stringForKey(IosReaderPreferencesDefaultsKey)

    if (encoded != null) {
        val decoded = runCatching { SharedLibrarySnapshotJson.decodeOrEmpty(encoded) }
            .getOrNull()
            ?.migrateAndroidEpubFormatSettings()
        if (decoded != null) {
            IosLibrarySnapshotFileStore.write(encodeStableIosSnapshot(decoded))
            return decoded
                .withResolvedIosBookPaths()
                .withResolvedIosAudiobookPaths()
        }
    }

    // Primary store missing or corrupt: recover from the durable file mirror
    // and repopulate defaults so the next read is fast.
    val recovered = IosLibrarySnapshotFileStore
        .readNewest(decode = ::decodeIosLibrarySnapshotJson)
        .let { it as? SharedLibrarySnapshot }
        ?.migrateAndroidEpubFormatSettings()
    if (recovered != null) {
        defaults.setObject(
            SharedLibrarySnapshotJson.encode(
                recovered
                    .withStableIosBookPaths()
                    .withStableIosAudiobookPaths()
            ),
            forKey = IosLibrarySnapshotDefaultsKey,
        )
    }
    return (recovered ?: SharedLibrarySnapshot())
        .withResolvedIosBookPaths()
        .withResolvedIosAudiobookPaths()
}

internal fun encodeStableIosSnapshot(snapshot: SharedLibrarySnapshot): String {
    return SharedLibrarySnapshotJson.encode(
        snapshot
            .migrateAndroidEpubFormatSettings()
            .withStableIosBookPaths()
            .withStableIosAudiobookPaths()
    )
}

/**
 * Coalesces rapid snapshot persistence into at most one write per debounce
 * window. State churn (selection changes, transient reader updates) used to
 * rewrite the full library JSON on every recomposition; writes are also
 * mirrored to the durable file store for corruption recovery.
 */
internal object IosLibrarySnapshotPersister {

    private var pendingJob: Job? = null
    private var pendingState: SharedReaderScreenState? = null

    fun schedule(scope: kotlinx.coroutines.CoroutineScope, state: SharedReaderScreenState) {
        pendingState = state
        if (pendingJob?.isActive == true) return
        pendingJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                delay(IosLibrarySnapshotPersistDelayMs)
            } finally {
                // Preserve the freshest state, not the state that started the delay.
                pendingState?.let { latest ->
                    pendingState = null
                    persistIosLibrarySnapshot(latest)
                }
            }
        }
    }
}

internal fun persistIosLibrarySnapshot(state: SharedReaderScreenState) {
    val encoded = SharedLibrarySnapshotJson.encode(
        state.toSharedMobileLibrarySnapshot()
            .migrateAndroidEpubFormatSettings()
            .withStableIosBookPaths()
            .withStableIosAudiobookPaths()
    )
    NSUserDefaults.standardUserDefaults.setObject(encoded, forKey = IosLibrarySnapshotDefaultsKey)
    IosLibrarySnapshotFileStore.write(encoded)
}

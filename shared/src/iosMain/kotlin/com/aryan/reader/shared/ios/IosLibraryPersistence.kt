package com.aryan.reader.shared.ios

import com.aryan.reader.shared.SharedLibrarySnapshot
import com.aryan.reader.shared.SharedLibrarySnapshotJson
import com.aryan.reader.shared.SharedReaderScreenState
import com.aryan.reader.shared.toSharedMobileLibrarySnapshot
import platform.Foundation.NSUserDefaults

private const val IosReaderPreferencesDefaultsKey = "reader_ios_reader_preferences_v1"
private const val IosLibrarySnapshotDefaultsKey = "reader_ios_library_snapshot_v1"

internal fun loadIosLibrarySnapshot(): SharedLibrarySnapshot {
    val defaults = NSUserDefaults.standardUserDefaults
    val encoded = defaults.stringForKey(IosLibrarySnapshotDefaultsKey)
        ?: defaults.stringForKey(IosReaderPreferencesDefaultsKey)
        ?: return SharedLibrarySnapshot()
    return SharedLibrarySnapshotJson.decodeOrEmpty(encoded)
        .withResolvedIosBookPaths()
        .withResolvedIosAudiobookPaths()
}

internal fun persistIosLibrarySnapshot(state: SharedReaderScreenState) {
    val encoded = SharedLibrarySnapshotJson.encode(
        state.toSharedMobileLibrarySnapshot()
            .withStableIosBookPaths()
            .withStableIosAudiobookPaths()
    )
    NSUserDefaults.standardUserDefaults.setObject(encoded, forKey = IosLibrarySnapshotDefaultsKey)
}

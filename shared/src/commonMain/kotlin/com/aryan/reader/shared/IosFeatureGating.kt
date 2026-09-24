package com.aryan.reader.shared

/**
 * Intentional temporary iOS launch scope.
 *
 * For now iOS hides Google sign-in, cloud sync (library + folder backup),
 * credits purchase, and cloud TTS from the UI. The underlying logic, data
 * models, sync executors, StoreKit products, and auth handlers are kept
 * intact so the features can be re-enabled later by flipping these flags
 * back to `true` and restoring the gated UI call sites in `ReaderIosApp`.
 *
 * Android remains the benchmark and is unaffected: these flags are only
 * read by iOS surfaces. Do not reuse them to gate Android behavior.
 */
object IosFeatureGating {
    /** Google sign-in / link buttons and Google-specific copy. Apple remains. */
    const val SHOW_GOOGLE_SIGN_IN = false

    /**
     * Library cloud sync. iOS ships this on the pure-CloudKit backend (Pro,
     * iCloud identity, no Google login), so the row is visible. The Drive/
     * Firestore library path stays dormant behind the native `useCloudKit
     * LibrarySync` flag; both backends share the same UI + shared merge.
     */
    const val SHOW_CLOUD_SYNC = true

    /**
     * When true, enabling library sync additionally requires a linked Google
     * account with Drive permission (Android's benchmark). iOS is false: the
     * CloudKit backend authenticates with iCloud, so only Pro gates it.
     */
    const val REQUIRES_GOOGLE_DRIVE_FOR_SYNC = false

    /**
     * Folder backup/mirror (the Drive cloud-folder protocol). Not yet ported
     * to CloudKit and it depends on the hidden Google/Drive auth, so it stays
     * hidden. Flipping this true restores the folder UI + Drive executor.
     */
    const val SHOW_DRIVE_FOLDER_SYNC = false

    /**
     * Destructive "clear cloud + local data" row. Its current implementation
     * deletes Drive + Firestore content, which is not the CloudKit silo, so it
     * stays hidden until a CloudKit zone reset exists.
     */
    const val SHOW_CLOUD_DATA_CLEAR = false

    /** Credits purchase cards and credits-balance upsell. Pro purchase remains. */
    const val SHOW_CREDITS_PURCHASE = false

    /** Cloud TTS model/voice/cache controls. Local/device TTS remains. */
    const val SHOW_CLOUD_TTS = false
}

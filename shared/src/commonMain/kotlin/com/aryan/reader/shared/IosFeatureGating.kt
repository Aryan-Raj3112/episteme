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

    /** Library sync toggle, folder backup/sync entry, and Drive status copy. */
    const val SHOW_CLOUD_SYNC = false

    /** Credits purchase cards and credits-balance upsell. Pro purchase remains. */
    const val SHOW_CREDITS_PURCHASE = false

    /** Cloud TTS model/voice/cache controls. Local/device TTS remains. */
    const val SHOW_CLOUD_TTS = false
}

package com.aryan.reader.shared

/**
 * Engine (Cloud / Device) selection for surfaces that follow the reader until the user
 * explicitly chooses otherwise.
 *
 * Android benchmark (`ListenTtsVoicePreferences.kt`): `loadListenTtsMode` reads Listen's own key
 * and, when the key is absent, falls back to the reader's mode; `saveListenTtsMode` writes it.
 * Android stores a `TtsMode` name and then runs it through `resolveTtsModeForCurrentBuild`, so a
 * cloud mode the current build cannot serve is downgraded rather than failing.
 *
 * Same three-valued store as [ReaderTtsVoiceOverride], for the same reason: the backing stores
 * (Android `SharedPreferences`, iOS `NSUserDefaults`) cannot tell an absent string from a stored
 * null, so "never chose" has to be encoded as a missing key rather than a sentinel value.
 *
 *  - key absent -> inherit the reader's live engine mode;
 *  - key present -> this surface's own pinned choice.
 *
 * Kept in commonMain as pure functions so both platforms resolve identically and the rule is
 * unit-testable without a device.
 */
object ReaderTtsEngineOverride {

    /** Pinned to the on-device speech engine. */
    const val DEVICE: String = "device"

    /** Pinned to a cloud speech backend. */
    const val CLOUD: String = "cloud"

    /**
     * Resolves which engine a surface should speak with.
     *
     * @param isOverrideStored whether this surface's backing store holds the key.
     * @param storedOverride the raw stored value; a blank or unrecognised value falls back to
     *   [DEVICE] rather than to whatever the reader is using, since a stored value means the
     *   user did make a choice and defaulting it to the reader's would silently undo it.
     * @param readerCloudModeEnabled the reader's live cloud mode, used while no override is stored.
     * @param cloudAvailable whether this build can serve cloud speech. Android applies the same
     *   downgrade in `resolveTtsModeForCurrentBuild`: a pinned cloud mode the build cannot honour
     *   becomes device speech instead of erroring.
     */
    fun resolveEngineMode(
        isOverrideStored: Boolean,
        storedOverride: String?,
        readerCloudModeEnabled: Boolean,
        cloudAvailable: Boolean = true,
    ): String {
        val pinned = if (!isOverrideStored) {
            if (readerCloudModeEnabled) CLOUD else DEVICE
        } else {
            storedOverride?.takeIf { it == CLOUD || it == DEVICE } ?: DEVICE
        }
        return if (pinned == CLOUD && !cloudAvailable) DEVICE else pinned
    }

    /**
     * Encodes an explicit choice for storage.
     *
     * Note the reader's current mode is deliberately *not* folded in here: writing "the reader's
     * mode" as an explicit value would turn an inherited choice into a pinned one and silently
     * detach Listen from the reader the first time the user touched this setting.
     */
    fun encodeEngineMode(cloudEnabled: Boolean): String = if (cloudEnabled) CLOUD else DEVICE

    /**
     * Whether this surface has made its own engine choice, independent of the reader's.
     *
     * Used to decide whether the Listen UI should present the choice as following the reader.
     */
    fun hasOwnEngineMode(isOverrideStored: Boolean): Boolean = isOverrideStored
}
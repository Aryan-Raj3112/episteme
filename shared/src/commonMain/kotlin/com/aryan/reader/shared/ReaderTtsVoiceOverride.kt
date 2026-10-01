package com.aryan.reader.shared

/**
 * Voice selection for surfaces that have their own voice but should follow the reader's until the
 * user explicitly chooses otherwise.
 *
 * Android benchmark (`ListenTtsVoicePreferences.kt`): Listen inherits the Reader engine and voice
 * until the user makes an explicit choice in the Listen voice settings, then stays pinned to it.
 * First-write-wins is implemented via a backing store's "key present" check, which means the value
 * space is three-valued:
 *
 *  - key absent -> inherit the live reader value;
 *  - key present and non-blank -> this surface's own choice;
 *  - key present and blank ([SYSTEM_DEFAULT]) -> an explicit "use the system default" choice.
 *
 * The blank sentinel exists because the backing stores (Android `SharedPreferences`, iOS
 * `NSUserDefaults`) cannot distinguish an absent string from a stored null, and "system default" is
 * a real, selectable state distinct from "whatever the reader picked".
 *
 * Kept in commonMain as pure functions so both platforms resolve identically and so the rules are
 * unit-testable without a device.
 */
object ReaderTtsVoiceOverride {

    /** Stored value meaning "explicitly use the platform system-default voice". */
    const val SYSTEM_DEFAULT: String = ""

    /**
     * Resolves the effective voice identifier.
     *
     * @param isOverrideStored whether this surface's backing store holds the key.
     * @param storedOverride the raw stored value; blank means [SYSTEM_DEFAULT].
     * @param inheritedVoiceIdentifier the live reader value, used while no override is stored.
     * @return the voice id to speak with, or null for the system default.
     */
    fun resolveVoiceIdentifier(
        isOverrideStored: Boolean,
        storedOverride: String?,
        inheritedVoiceIdentifier: String?
    ): String? {
        if (!isOverrideStored) return inheritedVoiceIdentifier
        val stored = storedOverride ?: return null
        return stored.takeIf { it.isNotEmpty() }
    }

    /**
     * Encodes an explicit choice for storage. A null identifier is stored as [SYSTEM_DEFAULT] so
     * that "user picked system default" is distinguishable from "user never chose".
     */
    fun encodeVoiceOverride(voiceIdentifier: String?): String = voiceIdentifier ?: SYSTEM_DEFAULT

    /**
     * Whether this surface has made its own voice choice, independent of the reader's.
     *
     * Any stored key counts, including [SYSTEM_DEFAULT]: the user explicitly picked the system
     * default, so the surface is deliberately *not* following the reader. The stored value itself
     * carries no extra information here, which is why it is not a parameter.
     */
    fun hasOwnVoice(isOverrideStored: Boolean): Boolean = isOverrideStored
}

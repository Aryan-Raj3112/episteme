package com.aryan.reader.shared

/**
 * What the app opens with on a cold start.
 *
 * The reader session is persisted either way: choosing [HOME] must not destroy the record of the
 * last book, so switching back to [OPEN_LAST_READING] resumes that book on the next launch instead
 * of resuming nothing. [OPEN_LAST_READING] is the default because it is what the app has always
 * done, and a startup that silently changes what the user sees would be the worse surprise.
 */
enum class MobileStartupBehavior(val persistedValue: String) {
    OPEN_LAST_READING("OPEN_LAST_READING"),
    HOME("HOME"),
    ;

    companion object {
        val DEFAULT: MobileStartupBehavior = OPEN_LAST_READING

        /** Unknown or missing persisted values fall back to [DEFAULT] rather than failing a launch. */
        fun fromPersisted(value: String?): MobileStartupBehavior =
            entries.firstOrNull { it.persistedValue == value } ?: DEFAULT
    }
}

/**
 * Whether a cold start should reopen the persisted reader session.
 *
 * Kept separate from the enum so the rule can be asserted without a host, and so the reader-side
 * restore path has one answer to ask instead of re-deriving it from persisted strings.
 */
fun mobileStartupBehaviorAllowsReaderRestore(behavior: MobileStartupBehavior): Boolean =
    behavior == MobileStartupBehavior.OPEN_LAST_READING

/**
 * The value preview shown on the settings row. Hosts resolve the two labels from their own string
 * table so the wording stays translated, but the choice of label stays shared.
 */
fun sharedStartupBehaviorValue(
    behavior: MobileStartupBehavior,
    openLastBookLabel: String,
    homeLabel: String,
): String = when (behavior) {
    MobileStartupBehavior.OPEN_LAST_READING -> openLastBookLabel
    MobileStartupBehavior.HOME -> homeLabel
}

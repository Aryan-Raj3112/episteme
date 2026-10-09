package com.aryan.reader.shared

/**
 * Result of persisting a provider API key from the AI Keys & Models screen.
 *
 * Saving used to be fire-and-forget: the host wrote the secret, reloaded the
 * settings, and redrew. When the write failed the field simply cleared and the
 * "Saved API keys" list still read "No key saved" — indistinguishable from the
 * user never having pressed Save. A key that is not stored must be *reported*,
 * because everything downstream (cloud TTS, AI features) then silently
 * degrades to the signed-in worker or to nothing at all.
 */
sealed interface AiKeySaveResult {
    data object Saved : AiKeySaveResult

    /** The key was blank or had no usable characters; nothing was written. */
    data class Invalid(val reason: AiKeySaveError) : AiKeySaveResult

    /** The secure store rejected or dropped the write. */
    data class Failed(val reason: AiKeySaveError) : AiKeySaveResult

    val isSaved: Boolean get() = this is Saved
}

enum class AiKeySaveError {
    /** Empty/whitespace input. */
    BLANK,

    /**
     * The key was rejected before it reached the network because it is
     * obviously not a usable secret for that provider (e.g. spaces or a
     * `Bearer ` prefix pasted along with the token).
     */
    MALFORMED,

    /** The platform secure store (iOS Keychain) refused the write. */
    KEYCHAIN_UNAVAILABLE,

    /**
     * The write reported success but reading the value back did not return it.
     * Catches partial failures (entitlement/ACL problems) that a write-only
     * check would miss.
     */
    VERIFY_FAILED,
}

/**
 * Validates a pasted API key before it is written anywhere.
 *
 * Only rejects input that cannot work: blank, or a value carrying whitespace
 * inside it. Provider key formats differ and are not validated against a
 * pattern — guessing a length or prefix would reject valid keys. A pasted
 * `Bearer ` prefix is stripped rather than rejected, since that is the most
 * common paste accident and the token after it is still usable.
 */
fun normalizeAiKeyEntry(raw: String): AiKeySaveResult {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return AiKeySaveResult.Invalid(AiKeySaveError.BLANK)
    val withoutScheme = trimmed.removePrefix("Bearer ").removePrefix("bearer ").trim()
    if (withoutScheme.isEmpty()) return AiKeySaveResult.Invalid(AiKeySaveError.BLANK)
    if (withoutScheme.any { it.isWhitespace() }) {
        return AiKeySaveResult.Invalid(AiKeySaveError.MALFORMED)
    }
    return AiKeySaveResult.Saved
}

/** The normalized secret to store, or null when the input is unusable. */
fun normalizedAiKeyEntry(raw: String): String? {
    if (normalizeAiKeyEntry(raw) !is AiKeySaveResult.Saved) return null
    return raw.trim().removePrefix("Bearer ").removePrefix("bearer ").trim()
}

/** Human-readable copy for a save failure, shown in the screen's error banner. */
fun aiKeySaveErrorMessage(error: AiKeySaveError): String = when (error) {
    AiKeySaveError.BLANK -> "Enter an API key first."
    AiKeySaveError.MALFORMED -> "That key contains spaces. Paste only the key, without quotes or extra words."
    AiKeySaveError.KEYCHAIN_UNAVAILABLE ->
        "Could not store the key in the device keychain. Check that the app is correctly signed and try again."
    AiKeySaveError.VERIFY_FAILED ->
        "The keychain did not confirm the save, so the key was not stored. Please try again."
}

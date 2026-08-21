package com.aryan.reader.shared

enum class ProEntitlementSource {
    LEGACY,
    GOOGLE_PLAY_LIFETIME,
    APP_STORE_LIFETIME,
}

data class StoreEntitlementSnapshot(
    val activeProSources: Set<ProEntitlementSource> = emptySet(),
    val legacyIsPro: Boolean = false,
    val credits: Int = 0,
) {
    val isPro: Boolean
        get() = legacyIsPro || activeProSources.isNotEmpty()

    val safeCredits: Int
        get() = credits.coerceAtLeast(0)
}

enum class IosPurchaseAvailability {
    SIGN_IN_REQUIRED,
    PRO_ALREADY_ACTIVE,
    AVAILABLE,
}

/** Purchases belong to the canonical Episteme account, not its login provider. */
fun iosPurchaseAvailability(
    isSignedIn: Boolean,
    isPro: Boolean,
    purchasingPro: Boolean,
): IosPurchaseAvailability = when {
    !isSignedIn -> IosPurchaseAvailability.SIGN_IN_REQUIRED
    purchasingPro && isPro -> IosPurchaseAvailability.PRO_ALREADY_ACTIVE
    else -> IosPurchaseAvailability.AVAILABLE
}


package com.aryan.reader.shared.ui

import com.aryan.reader.shared.AccountAuthProvider
import com.aryan.reader.shared.UserData

/**
 * Rows that may be exposed by the mobile account/global drawer.
 *
 * The Library Beta account drawer is intentionally smaller than the global
 * drawer: app appearance, custom fonts, and AI are owned by the local Library
 * drawer there. Keeping the row semantics in common code prevents the iOS
 * surface from accidentally growing a second copy of those actions.
 */
enum class MobileAppDrawerItem {
    SETTINGS,
    ABOUT,
    APP_THEME,
    FONTS,
    AI_SETTINGS,
    SUPPORT_PROJECT,
    HELP_FEEDBACK,
}

/**
 * Build-time edition identity, when the host actually knows it.
 *
 * This is deliberately nullable at the drawer boundary. StoreKit catalog
 * availability is a runtime service state and must never be treated as one of
 * these values.
 */
enum class MobileAppEdition {
    STANDARD,
    PRO,
    OPEN_SOURCE,
}

/**
 * Copy and state supplied by a host before rendering the shared account UI.
 *
 * Keeping identity, provider capabilities, and entitlement state together
 * prevents each platform drawer from growing a subtly different account
 * projection. The nullable copy fields let the host provide localized text;
 * the renderer still has Android-compatible fallbacks when a host does not.
 */
data class MobileAccountPresentation(
    val currentUser: UserData? = null,
    val providers: Set<AccountAuthProvider> = emptySet(),
    val supportedSignInProviders: Set<AccountAuthProvider> = setOf(AccountAuthProvider.GOOGLE),
    val isProUser: Boolean = false,
    val credits: Int = 0,
    val edition: MobileAppEdition? = null,
    val signInLabel: String? = null,
    val signedOutDescription: String? = null,
    val legalDisclosure: MobileAccountLegalDisclosure? = null,
) {
    val isSignedIn: Boolean
        get() = currentUser != null

    /** Clears account-owned state after an auth session ends. */
    fun resetAuthentication(): MobileAccountPresentation = copy(
        currentUser = null,
        providers = emptySet(),
        isProUser = false,
        credits = 0,
    )
}

/** Signed-out agreement copy shown next to the account action. */
data class MobileAccountLegalDisclosure(
    val text: String,
    val termsLabel: String? = null,
    val privacyLabel: String? = null,
)

/**
 * Stable fallback label for a host's supported sign-in providers. Hosts can
 * replace this with a localized string through [MobileAccountPresentation.signInLabel].
 */
fun mobileAccountSignInLabel(providers: Set<AccountAuthProvider>): String = when {
    AccountAuthProvider.APPLE in providers && AccountAuthProvider.GOOGLE in providers ->
        "Sign in with Apple or Google"
    AccountAuthProvider.APPLE in providers -> "Sign in with Apple"
    AccountAuthProvider.GOOGLE in providers -> "Sign in with Google"
    else -> "Sign in"
}

/**
 * Capability/variant inputs for the mobile account drawer.
 *
 * These flags describe which actions are supported by the current surface,
 * not whether StoreKit products happened to load. Edition identity is kept
 * separate because product availability is transient and is not an edition
 * signal.
 */
data class MobileAppDrawerCapabilities(
    val showAbout: Boolean = false,
    val showAppTheme: Boolean = true,
    val showFonts: Boolean = true,
    val showAiSettings: Boolean = true,
    val showSupportProject: Boolean = false,
    val showHelpFeedback: Boolean = true,
) {
    companion object {
        /** The full drawer used from Home and other global entry points. */
        val GLOBAL = MobileAppDrawerCapabilities()

        /** Android Unified Library's account drawer benchmark. */
        val UNIFIED_LIBRARY_ACCOUNT = MobileAppDrawerCapabilities(
            showAbout = true,
            showAppTheme = false,
            showFonts = false,
            showAiSettings = false,
            showSupportProject = true,
            showHelpFeedback = true,
        )
    }
}

data class MobileAppDrawerModel(
    val items: List<MobileAppDrawerItem>,
)

/**
 * Builds the stable Android-benchmark row order for a given drawer variant.
 * Optional rows are filtered here so platform renderers only map semantics to
 * their own localized labels, icons, and callbacks.
 */
fun mobileAppDrawerModel(
    capabilities: MobileAppDrawerCapabilities = MobileAppDrawerCapabilities.GLOBAL,
): MobileAppDrawerModel = MobileAppDrawerModel(
    items = buildList {
        add(MobileAppDrawerItem.SETTINGS)
        if (capabilities.showAbout) add(MobileAppDrawerItem.ABOUT)
        if (capabilities.showAppTheme) add(MobileAppDrawerItem.APP_THEME)
        if (capabilities.showFonts) add(MobileAppDrawerItem.FONTS)
        if (capabilities.showAiSettings) add(MobileAppDrawerItem.AI_SETTINGS)
        if (capabilities.showSupportProject) add(MobileAppDrawerItem.SUPPORT_PROJECT)
        if (capabilities.showHelpFeedback) add(MobileAppDrawerItem.HELP_FEEDBACK)
    },
)

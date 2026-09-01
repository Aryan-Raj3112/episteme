package com.aryan.reader.shared

enum class AccountAuthProvider {
    APPLE,
    GOOGLE,
}

data class AccountMergeSnapshot(
    val uid: String,
    val providers: Set<AccountAuthProvider>,
    val isPro: Boolean,
    val credits: Int,
    val hasCloudData: Boolean,
)

enum class AccountLinkResolution {
    LINK_PROVIDER,
    SWITCH_TO_EXISTING,
    REQUIRE_SERVER_MERGE,
}

data class AccountLinkPlan(
    val resolution: AccountLinkResolution,
    val destinationUid: String,
    val sourceUid: String? = null,
    val preservePro: Boolean = false,
    val creditsToTransfer: Int = 0,
)

/**
 * Plans provider linking without silently combining accounts by email.
 *
 * The account already owning the Google credential remains the destination because
 * Google Drive sync is tied to that Google account. Any valuable Apple-first state
 * requires a trusted server merge before the temporary source account is removed.
 */
fun planGoogleAccountLink(
    current: AccountMergeSnapshot,
    googleCredentialOwner: AccountMergeSnapshot?,
): AccountLinkPlan {
    if (googleCredentialOwner == null || googleCredentialOwner.uid == current.uid) {
        return AccountLinkPlan(
            resolution = AccountLinkResolution.LINK_PROVIDER,
            destinationUid = current.uid,
        )
    }
    val sourceHasValue = current.isPro || current.credits > 0 || current.hasCloudData
    return if (sourceHasValue) {
        AccountLinkPlan(
            resolution = AccountLinkResolution.REQUIRE_SERVER_MERGE,
            destinationUid = googleCredentialOwner.uid,
            sourceUid = current.uid,
            preservePro = current.isPro || googleCredentialOwner.isPro,
            creditsToTransfer = current.credits,
        )
    } else {
        AccountLinkPlan(
            resolution = AccountLinkResolution.SWITCH_TO_EXISTING,
            destinationUid = googleCredentialOwner.uid,
            sourceUid = current.uid,
            preservePro = googleCredentialOwner.isPro,
        )
    }
}

fun canEnableGoogleDriveSync(
    providers: Set<AccountAuthProvider>,
    hasGoogleDrivePermission: Boolean,
): Boolean = AccountAuthProvider.GOOGLE in providers && hasGoogleDrivePermission

/**
 * Cloud sync follows Android's paid-feature gate and additionally requires the
 * Google identity that owns the Drive app-data permission.
 *
 * Other Pro features are intentionally provider agnostic: an Apple-authenticated
 * Pro account remains eligible for them.
 */
fun canUseCloudSync(
    providers: Set<AccountAuthProvider>,
    hasGoogleDrivePermission: Boolean,
    isProUser: Boolean,
): Boolean = resolveCloudSyncSetupIntent(
    isProUser = isProUser,
    providers = providers,
    hasGoogleDrivePermission = hasGoogleDrivePermission,
) == CloudSyncSetupIntent.READY

fun canUseProFeature(
    isSignedIn: Boolean,
    isProUser: Boolean,
): Boolean = isSignedIn && isProUser

package com.aryan.reader.shared

/**
 * The next user-facing step required before cloud sync can be enabled.
 *
 * Google plus Google Drive authorization is deliberately required for sync on
 * every mobile platform. Apple authentication can still be used for the rest
 * of the account and Pro features.
 */
enum class CloudSyncSetupIntent {
    READY,
    NEEDS_PRO,
    NEEDS_GOOGLE_LINK,
    NEEDS_DRIVE_AUTH,
}

/** The platform destination for a cloud-sync setup intent. */
enum class CloudSyncSetupRoute {
    TOGGLE_SYNC,
    OPEN_PRO,
    OPEN_ACCOUNT,
    AUTHORIZE_GOOGLE_DRIVE,
}

fun resolveCloudSyncSetupIntent(
    isProUser: Boolean,
    providers: Set<AccountAuthProvider>,
    hasGoogleDrivePermission: Boolean,
): CloudSyncSetupIntent = when {
    !isProUser -> CloudSyncSetupIntent.NEEDS_PRO
    AccountAuthProvider.GOOGLE !in providers -> CloudSyncSetupIntent.NEEDS_GOOGLE_LINK
    !hasGoogleDrivePermission -> CloudSyncSetupIntent.NEEDS_DRIVE_AUTH
    else -> CloudSyncSetupIntent.READY
}

fun cloudSyncSetupRoute(intent: CloudSyncSetupIntent): CloudSyncSetupRoute = when (intent) {
    CloudSyncSetupIntent.READY -> CloudSyncSetupRoute.TOGGLE_SYNC
    CloudSyncSetupIntent.NEEDS_PRO -> CloudSyncSetupRoute.OPEN_PRO
    CloudSyncSetupIntent.NEEDS_GOOGLE_LINK -> CloudSyncSetupRoute.OPEN_ACCOUNT
    CloudSyncSetupIntent.NEEDS_DRIVE_AUTH -> CloudSyncSetupRoute.AUTHORIZE_GOOGLE_DRIVE
}

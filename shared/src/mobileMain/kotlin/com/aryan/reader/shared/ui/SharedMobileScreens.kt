package com.aryan.reader.shared.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Ai
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.Fonts
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Surface
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aryan.reader.shared.UserData

@Composable
fun SharedMobileAppDrawerContent(
    account: MobileAccountPresentation = MobileAccountPresentation(),
    accountAvatar: @Composable (UserData, Modifier) -> Unit = { _, modifier ->
        Icon(
            imageVector = Icons.Default.AccountCircle,
            contentDescription = readerString("content_desc_profile", "Profile"),
            modifier = modifier,
            tint = MaterialTheme.colorScheme.primary,
        )
    },
    isSyncEnabled: Boolean,
    isFolderSyncEnabled: Boolean,
    /**
     * True while a cloud pass is in flight; the Sync row shows a spinner in
     * place of the Switch. Defaults false so Android (which owns its sync
     * progress in `MainViewModel`) is unchanged.
     */
    isCloudSyncing: Boolean = false,
    onSignInClick: () -> Unit,
    onSignOutClick: () -> Unit,
    onSyncToggle: (Boolean) -> Unit,
    onFolderSyncToggle: (Boolean) -> Unit,
    onProClick: () -> Unit,
    onFontsClick: () -> Unit,
    onAiSettingsClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onAppThemeClick: () -> Unit,
    onFeedbackClick: () -> Unit,
    onPrivacyPolicyClick: () -> Unit,
    onTermsClick: () -> Unit,
    onLicensesClick: () -> Unit,
    drawerCapabilities: MobileAppDrawerCapabilities = MobileAppDrawerCapabilities.GLOBAL,
    onAboutClick: (() -> Unit)? = null,
    onSupportProjectClick: (() -> Unit)? = null,
    // Temporary iOS launch scope (see IosFeatureGating): iOS passes false to
    // hide cloud sync rows while keeping the sync logic. Defaults stay true
    // so Android behavior remains the benchmark.
    showSyncControls: Boolean = true,
    /**
     * Folder backup/mirror row visibility. Defaults to [showSyncControls] so
     * Android (which keeps both on) is unchanged; iOS can keep library sync on
     * while its Drive-based folder sync remains hidden.
     */
    showFolderSyncControls: Boolean = showSyncControls,
    // Wallet/spendable-balance badge next to Pro status. Defaults stay true.
    showCreditsBalance: Boolean = true,
    /**
     * Space above the sheet, for hosts whose display keeps a strip above the drawer (the
     * foldable iPhone lays its destinations out there). The sheet's own top system inset is
     * replaced rather than added to, so the first row sits at the top of the sheet instead
     * of a second, empty band below it. Zero on every other host, which keeps the framework
     * insets untouched.
     */
    topInset: Dp = 0.dp,
    modifier: Modifier = Modifier
) {
    ModalDrawerSheet(
        modifier = modifier.padding(top = topInset),
        windowInsets = if (topInset > 0.dp) {
            DrawerDefaults.windowInsets.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
        } else {
            DrawerDefaults.windowInsets
        },
    ) {
        val currentUser = account.currentUser
        val isProUser = account.isProUser
        val credits = account.credits
        val walletMicros = account.walletMicros
        val walletMigrated = account.walletMigrated
        val edition = account.edition
        val signInLabel = account.signInLabel ?: mobileAccountSignInLabel(account.supportedSignInProviders)
        val drawerItems = mobileAppDrawerModel(drawerCapabilities).items

        SharedDrawerLayout(
            header = {
                SharedDrawerHeader(
                    title = if (currentUser != null) {
                        currentUser.displayName
                            ?: currentUser.email
                            ?: readerString("desktop_signed_in", "Signed in")
                    } else {
                        readerString("account_signed_out_title", "Not signed in")
                    },
                    subtitle = if (currentUser != null) {
                        currentUser.email.takeIf { currentUser.displayName != null }
                    } else {
                        signInLabel
                    },
                    leading = {
                        if (currentUser != null) {
                            accountAvatar(currentUser, Modifier.size(44.dp))
                        } else {
                            Icon(
                                imageVector = Icons.Default.AccountCircle,
                                contentDescription = null,
                                modifier = Modifier.size(44.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                )
            },
            footer = {
                SharedDrawerLegalLinks(
                    onPrivacyPolicyClick = onPrivacyPolicyClick,
                    onTermsClick = onTermsClick,
                    onLicensesClick = onLicensesClick,
                )
            },
        ) {
            item { SharedDrawerSectionLabel(readerString("account_title", "Account")) }

            if (currentUser == null) {
                item {
                    SharedDrawerItem(
                        icon = { Icon(Icons.Default.AccountCircle, contentDescription = null) },
                        label = signInLabel,
                        onClick = onSignInClick,
                        modifier = Modifier.testTag("MobileDrawerSignIn"),
                    )
                }
                account.signedOutDescription?.let { description ->
                    item {
                        Text(
                            text = description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 28.dp, vertical = SharedSpacing.Xs),
                        )
                    }
                }
                account.legalDisclosure?.let { disclosure ->
                    item { SharedDrawerLegalDisclosure(disclosure, onTermsClick, onPrivacyPolicyClick) }
                }
            } else {
                item {
                    SharedDrawerItem(
                        icon = { Icon(Icons.Default.VerifiedUser, contentDescription = null) },
                        label = when {
                            edition == MobileAppEdition.STANDARD ->
                                readerString("drawer_standard_version", "Standard version")
                            isProUser -> readerString("drawer_pro_unlocked", "Episteme Pro and Credits")
                            else -> readerString("drawer_upgrade_pro", "Episteme Pro and Credits")
                        },
                        onClick = onProClick,
                        trailing = if (edition != MobileAppEdition.STANDARD && showCreditsBalance) {
                            {
                                Surface(
                                    color = MaterialTheme.colorScheme.tertiaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                    shape = CircleShape,
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(SharedSpacing.Xs),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.VerifiedUser,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp),
                                        )
                                        Text(
                                            text = com.aryan.reader.shared.spendableDisplayText(
                                                credits,
                                                walletMicros,
                                                walletMigrated,
                                            ),
                                            style = MaterialTheme.typography.labelMedium,
                                        )
                                    }
                                }
                            }
                        } else null,
                        modifier = Modifier.testTag("MobileDrawerPro"),
                    )
                }
                if (showSyncControls) {
                    item {
                        if (isProUser && !isCloudSyncing) {
                            SharedDrawerSwitchRow(
                                icon = { Icon(Icons.Default.Sync, contentDescription = null) },
                                label = readerString("drawer_sync_library", "Sync library"),
                                supporting = readerString("drawer_sync_desc", "Keep your library in the cloud."),
                                checked = isSyncEnabled,
                                onCheckedChange = onSyncToggle,
                                modifier = Modifier.testTag("MobileDrawerSync"),
                            )
                        } else {
                            SharedDrawerItem(
                                icon = { Icon(Icons.Default.Sync, contentDescription = null) },
                                label = readerString("drawer_sync_library", "Sync library"),
                                supporting = if (isCloudSyncing) {
                                    readerString("drawer_syncing", "Syncing…")
                                } else {
                                    readerString("content_desc_pro_feature", "Pro feature")
                                },
                                onClick = { if (isProUser) onSyncToggle(!isSyncEnabled) else onProClick() },
                                modifier = Modifier.testTag("MobileDrawerSync"),
                            )
                        }
                    }
                }
                if (isSyncEnabled && showFolderSyncControls) {
                    item {
                        SharedDrawerSwitchRow(
                            icon = { Icon(Icons.Default.FolderSpecial, contentDescription = null) },
                            label = readerString("drawer_backup_local_folders", "Backup local folders"),
                            supporting = readerString("drawer_backup_desc", "Keep folder metadata synced."),
                            checked = isFolderSyncEnabled,
                            onCheckedChange = onFolderSyncToggle,
                            modifier = Modifier.testTag("MobileDrawerFolderSync"),
                        )
                    }
                }
            }

            item { SharedDrawerGroupDivider() }
            item { SharedDrawerSectionLabel(readerString("unified_library_appearance", "Appearance")) }
            drawerItems.filter { it.isAppearance() }.forEach { item ->
                when (item) {
                    MobileAppDrawerItem.SETTINGS -> item {
                        SharedDrawerItem(
                            icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                            label = readerString("settings", "Settings"),
                            onClick = onSettingsClick,
                            modifier = Modifier.testTag("MobileDrawerSettings"),
                        )
                    }
                    MobileAppDrawerItem.APP_THEME -> item {
                        SharedDrawerItem(
                            icon = { Icon(Icons.Default.Palette, contentDescription = null) },
                            label = readerString("app_theme_title", "App theme"),
                            onClick = onAppThemeClick,
                            modifier = Modifier.testTag("MobileDrawerTheme"),
                        )
                    }
                    MobileAppDrawerItem.FONTS -> item {
                        SharedDrawerItem(
                            icon = { Icon(Icons.Default.Fonts, contentDescription = null) },
                            label = readerString("drawer_custom_fonts", "Custom fonts"),
                            onClick = onFontsClick,
                            modifier = Modifier.testTag("MobileDrawerFonts"),
                        )
                    }
                    MobileAppDrawerItem.AI_SETTINGS -> item {
                        SharedDrawerItem(
                            icon = { Icon(Icons.Default.Ai, contentDescription = null) },
                            label = readerString("ai_settings_title", "AI settings"),
                            onClick = onAiSettingsClick,
                            modifier = Modifier.testTag("MobileDrawerAiSettings"),
                        )
                    }
                    else -> Unit
                }
            }

            item { SharedDrawerGroupDivider() }
            item { SharedDrawerSectionLabel(readerString("drawer_section_support", "Support")) }
            drawerItems.filter { !it.isAppearance() }.forEach { item ->
                when (item) {
                    MobileAppDrawerItem.ABOUT -> onAboutClick?.let { onClick ->
                        item {
                            SharedDrawerItem(
                                icon = { Icon(Icons.Default.Info, contentDescription = null) },
                                label = readerString("about_title", "About"),
                                onClick = onClick,
                                modifier = Modifier.testTag("MobileDrawerAbout"),
                            )
                        }
                    }
                    MobileAppDrawerItem.SUPPORT_PROJECT -> onSupportProjectClick?.let { onClick ->
                        item {
                            SharedDrawerItem(
                                icon = { Icon(Icons.Outlined.FavoriteBorder, contentDescription = null) },
                                label = readerString("drawer_support_project", "Support project"),
                                onClick = onClick,
                                modifier = Modifier.testTag("MobileDrawerSupport"),
                            )
                        }
                    }
                    MobileAppDrawerItem.HELP_FEEDBACK -> item {
                        SharedDrawerItem(
                            icon = { Icon(Icons.Default.Feedback, contentDescription = null) },
                            label = readerString("drawer_help_feedback", "Help & Feedback"),
                            onClick = onFeedbackClick,
                            modifier = Modifier.testTag("MobileDrawerFeedback"),
                        )
                    }
                    else -> Unit
                }
            }

            if (currentUser != null) {
                item { SharedDrawerGroupDivider() }
                item {
                    SharedDrawerItem(
                        icon = {
                            Icon(
                                Icons.Default.Logout,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                            )
                        },
                        label = readerString("drawer_sign_out", "Sign out"),
                        onClick = onSignOutClick,
                        modifier = Modifier.testTag("MobileDrawerSignOut"),
                    )
                }
            }
        }
    }
}

/** Settings-shaped drawer rows sit under "Appearance"; everything else is support. */
private fun MobileAppDrawerItem.isAppearance(): Boolean = when (this) {
    MobileAppDrawerItem.SETTINGS,
    MobileAppDrawerItem.APP_THEME,
    MobileAppDrawerItem.FONTS,
    MobileAppDrawerItem.AI_SETTINGS -> true
    MobileAppDrawerItem.ABOUT,
    MobileAppDrawerItem.SUPPORT_PROJECT,
    MobileAppDrawerItem.HELP_FEEDBACK -> false
}

/** Terms/privacy inside a signed-out disclosure, styled like the rest of the drawer copy. */
@Composable
private fun SharedDrawerLegalDisclosure(
    disclosure: MobileAccountLegalDisclosure,
    onTermsClick: () -> Unit,
    onPrivacyPolicyClick: () -> Unit,
) {
    val linkColor = MaterialTheme.colorScheme.primary
    val annotated = buildAnnotatedString {
        append(disclosure.text)
        fun link(label: String?, tag: String) {
            val value = label?.takeIf(String::isNotEmpty) ?: return
            val start = disclosure.text.indexOf(value)
            if (start < 0) return
            addStringAnnotation(tag, value, start, start + value.length)
            addStyle(
                SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline),
                start,
                start + value.length,
            )
        }
        link(disclosure.termsLabel, "terms")
        link(disclosure.privacyLabel, "privacy")
    }
    @Suppress("DEPRECATION")
    ClickableText(
        text = annotated,
        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
        modifier = Modifier.padding(horizontal = 28.dp, vertical = SharedSpacing.Xs),
        onClick = { offset ->
            when {
                annotated.getStringAnnotations("terms", offset, offset).isNotEmpty() -> onTermsClick()
                annotated.getStringAnnotations("privacy", offset, offset).isNotEmpty() -> onPrivacyPolicyClick()
            }
        },
    )
}

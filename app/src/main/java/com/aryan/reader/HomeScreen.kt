/**
 * Shared Android chrome still owned by this file. The filename is a historical artifact: the
 * `HomeScreen` this was named for was retired when Library Beta replaced it, and is now gone
 * (see `docs/shared-parity-migration.md` C4). What remains is the set of composables that its
 * neighbours still call, so the name no longer describes the contents.
 *
 * Live callers, for anyone re-homing these:
 * - `SettingsScreen` — the dialogs, `DeviceManagementScreen`, `AppThemeBottomSheet`,
 *   `LanguageSelectionDialog`
 * - `UnifiedLibraryScreen` — `AppDrawerContent`
 * - `DebugFpsOverlay` — `FpsMonitor`
 *
 * Splitting this into per-concern files is mechanical and safe, but it is pure relocation with no
 * behavior change, so it is deliberately not done here: renaming churn is not worth a diff's worth
 * of review on its own. Do it when one of these symbols next needs to move for a real reason.
 */
@file:Suppress("DEPRECATION")

package com.aryan.reader

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Palette
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.navigation.NavHostController
import com.aryan.reader.data.RecentFileItem
import com.aryan.reader.shared.ui.SharedDrawerAccountFooter
import com.aryan.reader.shared.ui.SharedDrawerGroupDivider
import com.aryan.reader.shared.ui.SharedDrawerHeader
import com.aryan.reader.shared.ui.SharedDrawerItem
import com.aryan.reader.shared.ui.SharedDrawerLayout
import com.aryan.reader.shared.ui.readerString
import com.aryan.reader.shared.ui.SharedDrawerLegalLinks
import com.aryan.reader.shared.ui.SharedDrawerSectionLabel
import com.aryan.reader.shared.ui.SharedDrawerSwitchRow
import com.aryan.reader.shared.ui.SharedSpacing
import com.aryan.reader.shared.formatMicrosUsd
import com.aryan.reader.shared.ui.AppIcon
import com.aryan.reader.shared.ui.SharedMobileAppDestination
import com.aryan.reader.shared.ui.SharedMobileLanguageSelectionList
import com.aryan.reader.shared.ui.SharedAppThemeBottomSheet
import java.text.SimpleDateFormat
import java.util.Locale

internal fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RecentFileCard(
    item: RecentFileItem,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    isPinned: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    isDownloading: Boolean,
    usePdfFileNameAsDisplayName: Boolean = false,
) {
    val progressPercent = item.progressPercentage?.takeIf { it > 0f }?.coerceIn(0f, 100f)?.toInt()
    val authorText = item.author?.takeIf { it.isNotBlank() && !it.equals("Unknown", ignoreCase = true) } ?: " "
    com.aryan.reader.shared.ui.SharedAndroidHomeRecentCard(
        bookId = item.bookId,
        title = item.cardTitle(usePdfFileNameAsDisplayName),
        author = authorText,
        progressPercent = progressPercent,
        isAvailable = item.isAvailable,
        isDownloading = isDownloading,
        isSelected = isSelected,
        hasCustomCover = !item.coverImagePath.isNullOrBlank(),
        showStatusBadges = item.sourceFolderUri != null || item.isOpdsStream() || isPinned,
        unavailableDescription = stringResource(R.string.not_available_locally),
        selectedDescription = stringResource(R.string.content_desc_selected),
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier,
        cover = { coverModifier ->
                ThemedBookCover(
                    item = item,
                    contentDescription = item.displayName,
                    contentScale = ContentScale.Crop,
                    modifier = coverModifier,
                )
        },
        statusBadges = { badgeModifier ->
                    FileStatusBadges(
                        item = item,
                        isPinned = isPinned,
                        overlay = true,
                modifier = badgeModifier,
                    )
        },
        fileTypeBadge = { compact ->
                    FileTypeBadge(
                        type = item.type,
                        overlay = true,
                compact = compact,
                    )
        },
    )
}

@Composable
internal fun AppDrawerContent(
    uiState: ReaderScreenState,
    onSignInClick: () -> Unit,
    onSignOutClick: () -> Unit,
    onSyncToggle: (Boolean) -> Unit,
    onUpgradeClick: () -> Unit,
    onSyncUpsellClick: () -> Unit,
    onFontsClick: () -> Unit,
    onAiSettingsClick: () -> Unit,
    onSettingsClick: () -> Unit,
    navController: NavHostController,
    onFolderSyncSettingsClick: () -> Unit,
    onAboutClick: (() -> Unit)? = null,
    showFonts: Boolean = true,
    showAiSettings: Boolean = true,
    showSupportProject: Boolean = false,
    onAppThemeClick: (() -> Unit)? = null,
) {
    val isOss = BuildConfig.FLAVOR == "oss"
    val user = uiState.currentUser
    val uriHandler = LocalUriHandler.current

    ModalDrawerSheet {
        SharedDrawerLayout(
            header = {
                if (!isOss && user != null) {
                    SharedDrawerHeader(
                        title = user.displayName ?: user.email ?: stringResource(R.string.desktop_signed_in),
                        // Only repeat the address when it is not already the title.
                        subtitle = user.email.takeIf { user.displayName != null },
                        leading = {
                            AndroidAccountAvatar(
                                user = user,
                                modifier = Modifier.size(44.dp),
                                contentDescription = stringResource(R.string.content_desc_profile_picture),
                            )
                        },
                    )
                } else {
                    SharedDrawerHeader(
                        title = stringResource(if (isOss) R.string.app_name_oss else R.string.app_name),
                        subtitle = if (!isOss && user == null) {
                            stringResource(R.string.unified_library_drawer_title)
                        } else null,
                        leading = {
                            AppIcon(
                                contentDescription = stringResource(R.string.content_desc_app_icon),
                                size = 44.dp,
                                shape = RoundedCornerShape(14.dp),
                                plateColor = MaterialTheme.colorScheme.secondaryContainer,
                            )
                        },
                    )
                }
            },
            footer = {
                SharedDrawerLegalLinks(
                    onPrivacyPolicyClick = { uriHandler.openUri(PRIVACY_POLICY_URL) },
                    onTermsClick = { uriHandler.openUri(TERMS_URL) },
                    onLicensesClick = { uriHandler.openUri(LICENSES_URL) },
                )
            },
        ) {
            if (!isOss) {
                item { SharedDrawerSectionLabel(stringResource(R.string.account_title)) }
                if (user == null) {
                    item {
                        SharedDrawerItem(
                            icon = { Icon(Icons.Outlined.AccountCircle, contentDescription = null) },
                            label = stringResource(R.string.drawer_sign_in),
                            onClick = onSignInClick,
                            modifier = Modifier.testTag("MobileDrawerSignIn"),
                        )
                    }
                    item {
                        LegalText(
                            prefixText = stringResource(R.string.drawer_by_signing_in),
                            modifier = Modifier.padding(horizontal = 28.dp, vertical = SharedSpacing.Sm),
                            textAlign = TextAlign.Start,
                        )
                    }
                } else {
                    item {
                        SharedDrawerItem(
                            icon = { Icon(Icons.Default.VerifiedUser, contentDescription = null) },
                            label = if (uiState.isProUser) {
                                stringResource(R.string.drawer_pro_unlocked)
                            } else {
                                stringResource(R.string.drawer_upgrade_pro)
                            },
                            onClick = onUpgradeClick,
                            trailing = if (BuildConfig.FLAVOR == "pro") {
                                {
                                    Surface(
                                        color = MaterialTheme.colorScheme.tertiaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                        shape = CircleShape,
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.FormatListNumbered,
                                                contentDescription = null,
                                                modifier = Modifier.size(14.dp),
                                            )
                                            Text(
                                                text = safeStringResource(
                                                    R.string.wallet_balance,
                                                    formatMicrosUsd(uiState.walletMicros),
                                                ),
                                                style = MaterialTheme.typography.labelMedium,
                                            )
                                        }
                                    }
                                }
                            } else null,
                        )
                    }
                    // Sync is a setting, so it is a whole-row switch while Pro is on, and an upsell
                    // row while Pro is off: a disabled switch would swallow the way to fix it.
                    item {
                        if (uiState.isProUser) {
                            SharedDrawerSwitchRow(
                                icon = { Icon(Icons.Default.Sync, contentDescription = null) },
                                label = stringResource(R.string.drawer_sync_library),
                                supporting = if (uiState.isSyncEnabled) {
                                    stringResource(R.string.drawer_folder_sync_desc)
                                } else {
                                    stringResource(R.string.folder_sync_library_sync_off)
                                },
                                checked = uiState.isSyncEnabled,
                                onCheckedChange = onSyncToggle,
                                modifier = Modifier.testTag("MobileDrawerSync"),
                            )
                        } else {
                            SharedDrawerItem(
                                icon = { Icon(Icons.Default.Sync, contentDescription = null) },
                                label = stringResource(R.string.drawer_sync_library),
                                supporting = stringResource(R.string.content_desc_pro_feature),
                                onClick = onSyncUpsellClick,
                                modifier = Modifier.testTag("MobileDrawerSync"),
                            )
                        }
                    }
                    if (uiState.canUseCloudFolderSync()) {
                        item {
                            SharedDrawerItem(
                                icon = { Icon(Icons.Default.FolderSpecial, contentDescription = null) },
                                label = stringResource(R.string.drawer_folder_sync),
                                supporting = if (uiState.isSyncEnabled) {
                                    stringResource(R.string.drawer_folder_sync_desc)
                                } else {
                                    stringResource(R.string.folder_sync_library_sync_off)
                                },
                                onClick = onFolderSyncSettingsClick,
                                modifier = Modifier.testTag("MobileDrawerFolderSyncSettings"),
                            )
                        }
                    }
                }
            }

            // Settings, fonts, AI and theme are all app-level: "Appearance" would be a stretch for
            // Settings, and this drawer carries no reader appearance rows of its own.
            item { SharedDrawerSectionLabel(readerString("drawer_section_app", "App")) }
            onAppThemeClick?.let { onTheme ->
                item {
                    SharedDrawerItem(
                        icon = { Icon(Icons.Default.Palette, contentDescription = null) },
                        label = stringResource(R.string.app_theme_title),
                        onClick = onTheme,
                    )
                }
            }
            if (showFonts) {
                item {
                    SharedDrawerItem(
                        icon = { Icon(painterResource(R.drawable.fonts), contentDescription = null) },
                        label = stringResource(R.string.drawer_custom_fonts),
                        onClick = onFontsClick,
                    )
                }
            }
            if (showAiSettings && !BuildConfig.IS_OFFLINE) {
                item {
                    SharedDrawerItem(
                        icon = { Icon(painterResource(R.drawable.ai), contentDescription = null) },
                        label = stringResource(R.string.ai_settings_title),
                        onClick = onAiSettingsClick,
                    )
                }
            }
            item {
                SharedDrawerItem(
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    label = stringResource(R.string.settings),
                    onClick = onSettingsClick,
                )
            }

            item { SharedDrawerGroupDivider() }
            item { SharedDrawerSectionLabel(readerString("drawer_section_support", "Support")) }
            onAboutClick?.let { onClick ->
                item {
                    SharedDrawerItem(
                        icon = { Icon(Icons.Default.Info, contentDescription = null) },
                        label = stringResource(R.string.about_title),
                        onClick = onClick,
                    )
                }
            }
            if (isOss || showSupportProject) {
                item {
                    SharedDrawerItem(
                        icon = { Icon(Icons.Outlined.FavoriteBorder, contentDescription = null) },
                        label = stringResource(R.string.drawer_support_project),
                        onClick = { navController.navigateIfReady(SharedMobileAppDestination.SUPPORT_PROJECT) },
                    )
                }
            }
            item {
                SharedDrawerItem(
                    icon = { Icon(painterResource(R.drawable.feedback), contentDescription = null) },
                    label = stringResource(R.string.drawer_help_feedback),
                    onClick = { navController.navigateIfReady(SharedMobileAppDestination.FEEDBACK) },
                )
            }

            if (!isOss && user != null) {
                item { SharedDrawerGroupDivider() }
                item {
                    // Last row in the sheet, and the only destructive one.
                    NavigationDrawerItem(
                        icon = {
                            Icon(
                                painterResource(R.drawable.logout),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                            )
                        },
                        label = {
                            Text(
                                text = stringResource(R.string.drawer_sign_out),
                                color = MaterialTheme.colorScheme.error,
                            )
                        },
                        selected = false,
                        onClick = onSignOutClick,
                        modifier = Modifier.padding(horizontal = SharedSpacing.Md, vertical = SharedSpacing.Xxs),
                    )
                }
            }
        }
    }
}

@Composable
fun UpgradeDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.VerifiedUser, contentDescription = null) },
        title = { Text(stringResource(R.string.dialog_unlock_pro)) },
        text = { Text(stringResource(R.string.dialog_unlock_pro_desc)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.action_upgrade)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        })
}

@Composable
fun SignOutConfirmationDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_confirm_sign_out)) },
        text = { Text(stringResource(R.string.dialog_confirm_sign_out_desc)) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Text(stringResource(R.string.drawer_sign_out))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        })
}

@Composable
fun DeviceManagementScreen(
    devices: List<DeviceItem>, onRemoveDevice: (String) -> Unit, isReplacing: Boolean
) {
    val dateFormatter = remember {
        SimpleDateFormat("MMM d, yyyy 'at' h:mm a", Locale.getDefault())
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background.copy(alpha = 0.98f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = stringResource(R.string.device_limit_reached),
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.device_limit_reached_desc),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))

            if (isReplacing) {
                CircularProgressIndicator()
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(devices, key = { it.deviceId }) { device ->
                        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.PhoneAndroid, contentDescription = stringResource(R.string.content_desc_device))
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(device.deviceName, fontWeight = FontWeight.SemiBold)
                                    device.lastSeen?.let {
                                        Text(
                                            stringResource(R.string.last_seen, dateFormatter.format(it)),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                TextButton(onClick = { onRemoveDevice(device.deviceId) }) {
                                    Text(stringResource(R.string.action_remove))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ClearAllDataConfirmationDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Info, contentDescription = null) },
        title = { Text(stringResource(R.string.dialog_destructive_action)) },
        text = { Text(stringResource(R.string.dialog_destructive_action_desc)) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Text(stringResource(R.string.action_delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        })
}

@Deprecated("Use DebugFpsOverlay (global, Choreographer-based). Kept for binary compat only.")
@Composable
fun FpsMonitor(modifier: Modifier = Modifier) {
    if (!BuildConfig.DEBUG) return
    DebugFpsOverlay(modifier = modifier)
}

@Composable
fun DangerousFolderActionDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.error
            )
        },
        text = { Text(message) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text(stringResource(R.string.action_confirm_clear))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

@Composable
fun ExternalFileBehaviorDialog(
    currentBehavior: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.options_external_file_behavior)) },
        text = {
            Column(modifier = Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())) {
                val options = listOf(
                    Triple("ASK", R.string.external_file_behavior_ask, R.string.external_file_behavior_ask_desc),
                    Triple("KEEP", R.string.external_file_behavior_keep, R.string.external_file_behavior_keep_desc),
                    Triple("DELETE", R.string.external_file_behavior_delete, R.string.external_file_behavior_delete_desc),
                    Triple("TEMPORARY", R.string.external_file_behavior_temporary, R.string.external_file_behavior_temporary_desc)
                )
                options.forEach { (value, labelRes, descriptionRes) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(value); onDismiss() }
                            .padding(vertical = 12.dp)
                    ) {
                        RadioButton(selected = currentBehavior == value, onClick = null)
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(labelRes))
                            Text(
                                text = stringResource(descriptionRes),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

@Composable
fun StrictFilterConfirmationDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_strict_file_filter_title)) },
        text = { Text(stringResource(R.string.dialog_strict_file_filter_desc)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.action_enable)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}

/**
 * App theme bottom sheet.
 *
 * Rendering lives in shared (`SharedAppThemeBottomSheet`), which owns the mode
 * / contrast / text-dim / seed-color controls *and* the custom-theme create
 * dialog. This wrapper only reads Android's [ReaderScreenState] and forwards the
 * ViewModel mutators.
 */
@Composable
fun AppThemeBottomSheet(
    uiState: ReaderScreenState,
    onThemeModeChanged: (AppThemeMode) -> Unit,
    onContrastOptionChanged: (AppContrastOption) -> Unit,
    onTextDimFactorLightChanged: (Float) -> Unit,
    onTextDimFactorDarkChanged: (Float) -> Unit,
    onSeedColorChanged: (Color?) -> Unit,
    onCustomThemeAdded: (CustomAppTheme) -> Unit,
    onCustomThemeDeleted: (String) -> Unit,
    onDismiss: () -> Unit
) {
    SharedAppThemeBottomSheet(
        appThemeMode = uiState.appThemeMode,
        appContrastOption = uiState.appContrastOption,
        appTextDimFactorLight = uiState.appTextDimFactorLight,
        appTextDimFactorDark = uiState.appTextDimFactorDark,
        appSeedColor = uiState.appSeedColor,
        customAppThemes = uiState.customAppThemes,
        onThemeModeChanged = onThemeModeChanged,
        onContrastOptionChanged = onContrastOptionChanged,
        onTextDimFactorLightChanged = onTextDimFactorLightChanged,
        onTextDimFactorDarkChanged = onTextDimFactorDarkChanged,
        onSeedColorChanged = onSeedColorChanged,
        onCustomThemeAdded = onCustomThemeAdded,
        onCustomThemeDeleted = onCustomThemeDeleted,
        onDismiss = onDismiss
    )
}
@Composable
fun LanguageSelectionDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val currentLocales = AppCompatDelegate.getApplicationLocales()
    val currentTag = if (!currentLocales.isEmpty) currentLocales.get(0)?.toLanguageTag() else null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.options_language)) },
        text = {
            SharedMobileLanguageSelectionList(
                selectedTag = currentTag,
                onSelect = { tag ->
                    val locales = tag?.let { LocaleListCompat.forLanguageTags(it) }
                        ?: LocaleListCompat.getEmptyLocaleList()
                    AppCompatDelegate.setApplicationLocales(locales)
                    onDismiss()
                    context.findActivity()?.recreate()
                },
                maxListHeight = 360.dp
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

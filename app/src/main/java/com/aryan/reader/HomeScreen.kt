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

@Suppress("KotlinConstantConditions")
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
) {
    val isOss = BuildConfig.FLAVOR == "oss"

    ModalDrawerSheet {
        Column(modifier = Modifier.fillMaxHeight()) {
            if (!isOss) {
                if (uiState.currentUser != null) {
                    // Signed-in: Show user info at the top
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AndroidAccountAvatar(
                            user = uiState.currentUser,
                            modifier = Modifier.size(80.dp),
                            contentDescription = stringResource(R.string.content_desc_profile_picture),
                        )
                        uiState.currentUser.displayName?.let { name ->
                            Text(text = name, style = MaterialTheme.typography.titleMedium)
                        }
                        uiState.currentUser.email?.let { email ->
                            Text(text = email, style = MaterialTheme.typography.bodyMedium)
                        }
                        if (BuildConfig.FLAVOR == "pro") {
                            Surface(
                                color = MaterialTheme.colorScheme.tertiaryContainer,
                                shape = CircleShape,
                                modifier = Modifier.padding(top = 8.dp)
                            ) {
                                Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.FormatListNumbered, contentDescription = stringResource(R.string.credits_tab), modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onTertiaryContainer)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(safeStringResource(R.string.wallet_balance, formatMicrosUsd(uiState.walletMicros)), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onTertiaryContainer)
                                }
                            }
                        }
                    }
                } else {
                    // Signed-out: Show Sign In button at the top
                    Spacer(modifier = Modifier.height(8.dp))
                    NavigationDrawerItem(
                        icon = { Icon(Icons.Outlined.AccountCircle, contentDescription = null) },
                        label = { Text(stringResource(R.string.drawer_sign_in)) },
                        selected = false,
                        onClick = onSignInClick,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )

                    // LegalText
                    LegalText(
                        prefixText = stringResource(R.string.drawer_by_signing_in),
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                        textAlign = TextAlign.Start
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                Spacer(modifier = Modifier.height(16.dp))

                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.VerifiedUser, contentDescription = null) },
                    label = {
                        val text = if (uiState.isProUser) stringResource(R.string.drawer_pro_unlocked) else stringResource(R.string.drawer_upgrade_pro)
                        Text(text)
                    },
                    selected = false,
                    onClick = onUpgradeClick,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )

                // Sync Toggle Item
                if (uiState.currentUser != null) {
                    NavigationDrawerItem(
                        icon = { Icon(painterResource(id = R.drawable.sync), contentDescription = null) },
                        label = { Text(stringResource(R.string.drawer_sync_library)) },
                        badge = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (!uiState.isProUser) {
                                    Icon(
                                        imageVector = Icons.Default.VerifiedUser,
                                        contentDescription = stringResource(R.string.content_desc_pro_feature),
                                        modifier = Modifier.size(20.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                Switch(
                                    checked = uiState.isSyncEnabled, onCheckedChange = {
                                        if (uiState.isProUser) onSyncToggle(it) else onSyncUpsellClick()
                                    }, enabled = uiState.isProUser
                                )
                            }
                        }, selected = false, onClick = {
                            if (uiState.isProUser) {
                                onSyncToggle(!uiState.isSyncEnabled)
                            } else {
                                onSyncUpsellClick()
                            }
                        }, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }
                if (uiState.canUseCloudFolderSync()) {
                    NavigationDrawerItem(
                        icon = { Icon(imageVector = Icons.Default.FolderSpecial, contentDescription = null) },
                        label = {
                            Column {
                                Text(stringResource(R.string.drawer_folder_sync))
                                Text(
                                    if (uiState.isSyncEnabled) {
                                        stringResource(R.string.drawer_folder_sync_desc)
                                    } else {
                                        stringResource(R.string.folder_sync_library_sync_off)
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        badge = { Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null) },
                        selected = false,
                        onClick = onFolderSyncSettingsClick,
                        modifier = Modifier
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                            .testTag("MobileDrawerFolderSyncSettings")
                    )
                }
            } else {
                // OSS Header
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    AppIcon(
                        contentDescription = stringResource(R.string.content_desc_app_icon),
                        size = 64.dp,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = stringResource(R.string.app_name_oss), style = MaterialTheme.typography.titleMedium)
                }
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }

            NavigationDrawerItem(
                icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                label = { Text(stringResource(R.string.settings)) },
                selected = false,
                onClick = onSettingsClick,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )

            onAboutClick?.let { onClick ->
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Info, contentDescription = null) },
                    label = { Text(stringResource(R.string.about_title)) },
                    selected = false,
                    onClick = onClick,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }

            if (showFonts) {
                NavigationDrawerItem(
                    icon = { Icon(painterResource(id = R.drawable.fonts), contentDescription = null) },
                    label = { Text(stringResource(R.string.drawer_custom_fonts)) },
                    selected = false,
                    onClick = onFontsClick,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }

            if (showAiSettings && !BuildConfig.IS_OFFLINE) {
                NavigationDrawerItem(
                    icon = { Icon(painterResource(id = R.drawable.ai), contentDescription = null) },
                    label = { Text(stringResource(R.string.ai_settings_title)) },
                    selected = false,
                    onClick = onAiSettingsClick,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }

            if (isOss || showSupportProject) {
                NavigationDrawerItem(
                    icon = { Icon(Icons.Outlined.FavoriteBorder, contentDescription = null) },
                    label = { Text(stringResource(R.string.drawer_support_project)) },
                    selected = false,
                    onClick = { navController.navigateIfReady(SharedMobileAppDestination.SUPPORT_PROJECT) },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }

            NavigationDrawerItem(
                icon = { Icon(painterResource(id = R.drawable.feedback), contentDescription = null) },
                label = { Text(stringResource(R.string.drawer_help_feedback)) },
                selected = false,
                onClick = { navController.navigateIfReady(SharedMobileAppDestination.FEEDBACK) },
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )

            if (!isOss) {
                if (uiState.currentUser != null) {
                    NavigationDrawerItem(
                        icon = { Icon(painterResource(id = R.drawable.logout), contentDescription = null) },
                        label = { Text(stringResource(R.string.drawer_sign_out)) },
                        selected = false,
                        onClick = onSignOutClick,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // legal links
            run {
                val uriHandler = LocalUriHandler.current
                val baseStyle = MaterialTheme.typography.labelMedium
                var scaledTextStyle by remember { mutableStateOf(baseStyle) }

                Text(
                    text = stringResource(R.string.legal_footer_combined),
                    style = scaledTextStyle,
                    maxLines = 1,
                    softWrap = false,
                    onTextLayout = {
                        if (it.didOverflowWidth) {
                            scaledTextStyle =
                                scaledTextStyle.copy(fontSize = scaledTextStyle.fontSize * 0.95)
                        }
                    },
                    modifier = Modifier.height(0.dp)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp, start = 8.dp, end = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.legal_privacy_policy),
                        style = scaledTextStyle.copy(color = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.clickable { uriHandler.openUri(PRIVACY_POLICY_URL) },
                        softWrap = false
                    )
                    Text("  •  ", style = scaledTextStyle.copy(color = MaterialTheme.colorScheme.onSurfaceVariant))
                    Text(
                        text = stringResource(R.string.legal_terms_of_service),
                        style = scaledTextStyle.copy(color = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.clickable { uriHandler.openUri(TERMS_URL) },
                        softWrap = false
                    )
                    Text("  •  ", style = scaledTextStyle.copy(color = MaterialTheme.colorScheme.onSurfaceVariant))
                    Text(
                        text = stringResource(R.string.legal_licenses),
                        style = scaledTextStyle.copy(color = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.clickable { uriHandler.openUri(LICENSES_URL) },
                        softWrap = false
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

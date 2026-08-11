package com.aryan.reader.shared.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Ai
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.Fonts
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aryan.reader.shared.UserData

@Composable
fun SharedMobileAppDrawerContent(
    currentUser: UserData?,
    isProUser: Boolean,
    credits: Int,
    isSyncEnabled: Boolean,
    isFolderSyncEnabled: Boolean,
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
    isStandardEdition: Boolean = false,
    aiSettingsAvailable: Boolean = true,
    modifier: Modifier = Modifier
) {
    ModalDrawerSheet(modifier = modifier) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (currentUser != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AccountCircle,
                        contentDescription = "Profile",
                        modifier = Modifier.size(80.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = currentUser.displayName ?: currentUser.email ?: "Signed in",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    currentUser.email?.let { email ->
                        Text(
                            text = email,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        shape = CircleShape,
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Default.VerifiedUser, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text(
                                if (isStandardEdition) "Standard version" else "$credits credits",
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
            } else {
                Spacer(Modifier.height(8.dp))
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.AccountCircle, contentDescription = null) },
                    label = { Text("Sign in with Google") },
                    selected = false,
                    onClick = onSignInClick,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
                Text(
                    text = if (isStandardEdition) "Sync account and app settings." else "Sync account, Pro features, and credits.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 10.dp)
                )
            }

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))

            NavigationDrawerItem(
                icon = { Icon(Icons.Default.VerifiedUser, contentDescription = null) },
                label = {
                    Text(
                        when {
                            isStandardEdition -> "Standard version"
                            isProUser -> "Pro unlocked"
                            else -> "Upgrade to Pro"
                        }
                    )
                },
                selected = false,
                onClick = onProClick,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )

            NavigationDrawerItem(
                icon = { Icon(Icons.Default.Sync, contentDescription = null) },
                label = { Text("Sync library") },
                selected = false,
                onClick = { onSyncToggle(!isSyncEnabled) },
                badge = { Switch(checked = isSyncEnabled, onCheckedChange = onSyncToggle) },
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )

            if (isSyncEnabled) {
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.FolderSpecial, contentDescription = null) },
                    label = {
                        Column {
                            Text("Backup local folders")
                            Text(
                                "Keep folder metadata synced.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    selected = false,
                    onClick = { onFolderSyncToggle(!isFolderSyncEnabled) },
                    badge = { Switch(checked = isFolderSyncEnabled, onCheckedChange = onFolderSyncToggle) },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))

            NavigationDrawerItem(
                icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                label = { Text("Settings") },
                selected = false,
                onClick = onSettingsClick,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
            NavigationDrawerItem(
                icon = { Icon(Icons.Default.Palette, contentDescription = null) },
                label = { Text("App theme") },
                selected = false,
                onClick = onAppThemeClick,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
            NavigationDrawerItem(
                icon = { Icon(Icons.Default.Fonts, contentDescription = null) },
                label = { Text("Custom fonts") },
                selected = false,
                onClick = onFontsClick,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
            if (aiSettingsAvailable) {
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Ai, contentDescription = null) },
                    label = { Text("AI settings") },
                    selected = false,
                    onClick = onAiSettingsClick,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }
            NavigationDrawerItem(
                icon = { Icon(Icons.Default.Feedback, contentDescription = null) },
                label = { Text("Help & Feedback") },
                selected = false,
                onClick = onFeedbackClick,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )

            if (currentUser != null) {
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Logout, contentDescription = null) },
                    label = { Text("Sign out") },
                    selected = false,
                    onClick = onSignOutClick,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }

            Spacer(Modifier.weight(1f))
            val legalFooterBaseStyle = MaterialTheme.typography.labelMedium
            var legalFooterStyle by remember { mutableStateOf(legalFooterBaseStyle) }
            Text(
                text = readerString("legal_footer_combined", "Privacy Policy  •  Terms of Service  •  Licenses"),
                style = legalFooterStyle,
                maxLines = 1,
                softWrap = false,
                onTextLayout = { result ->
                    if (result.didOverflowWidth) {
                        legalFooterStyle = legalFooterStyle.copy(fontSize = legalFooterStyle.fontSize * 0.95)
                    }
                },
                modifier = Modifier.height(0.dp),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = readerString("legal_privacy_policy", "Privacy Policy"),
                    style = legalFooterStyle,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    modifier = Modifier.clickable(onClick = onPrivacyPolicyClick),
                )
                Text("  •  ", style = legalFooterStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    text = readerString("legal_terms_of_service", "Terms of Service"),
                    style = legalFooterStyle,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    modifier = Modifier.clickable(onClick = onTermsClick),
                )
                Text("  •  ", style = legalFooterStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    text = readerString("legal_licenses", "Licenses"),
                    style = legalFooterStyle,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    modifier = Modifier.clickable(onClick = onLicensesClick),
                )
            }
        }
    }
}

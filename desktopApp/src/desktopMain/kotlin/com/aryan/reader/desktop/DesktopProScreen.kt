package com.aryan.reader.desktop

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aryan.reader.shared.UserData
import com.aryan.reader.shared.ui.readerString

@Composable
internal fun DesktopProScreen(
    user: UserData?,
    isProUser: Boolean,
    credits: Int,
    authConfigured: Boolean,
    isBusy: Boolean,
    statusMessage: String?,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onRefresh: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(30.dp), tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f)) {
                Text(readerString("desktop_account_and_credits", "Account & credits"), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(
                    readerString("desktop_pro_sign_in_desc", "Sign in to check your account status on desktop."),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Default.Verified, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(readerString("desktop_account_overview", "Account overview"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                }
                if (user == null) {
                    Text(
                        if (authConfigured) {
                            readerString("desktop_no_google_account_connected", "No Google account is connected.")
                        } else {
                            readerString("desktop_google_sign_in_not_configured", "Google sign-in is not configured for this desktop build.")
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(onClick = onSignIn, enabled = authConfigured && !isBusy) {
                        if (isBusy) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.size(8.dp))
                        }
                        Text(readerString("drawer_sign_in", "Sign in with Google"))
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(user.displayName ?: user.email ?: readerString("desktop_signed_in", "Signed in"), style = MaterialTheme.typography.titleMedium)
                            user.email?.let {
                                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        OutlinedButton(onClick = onRefresh, enabled = !isBusy) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.size(8.dp))
                            Text(readerString("desktop_refresh", "Refresh"))
                        }
                        OutlinedButton(onClick = onSignOut, enabled = !isBusy) {
                            Text(readerString("drawer_sign_out", "Sign out"))
                        }
                    }
                }

                HorizontalDivider()

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    DesktopAccountValue(
                        label = readerString("desktop_plan", "Plan"),
                        value = if (isProUser) {
                            readerString("desktop_pro_unlocked_account", "Pro is unlocked for this account.")
                        } else {
                            readerString("desktop_pro_not_unlocked_account", "Pro is not unlocked for this account.")
                        },
                        modifier = Modifier.weight(1f)
                    )
                    DesktopAccountValue(
                        label = readerString("credits_tab", "Credits"),
                        value = readerString("desktop_credits_available_format", "%1\$d credits available", credits),
                        modifier = Modifier.weight(1f)
                    )
                }

                Text(
                    readerString(
                        "desktop_pro_purchase_android_desc",
                        "Pro and credits can only be purchased from the Android app. Desktop checks the same signed-in account and uses those credits for cloud TTS, summaries, recaps, and other paid AI features."
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                statusMessage?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun DesktopAccountValue(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

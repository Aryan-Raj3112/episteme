package com.aryan.reader.shared.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun SharedAndroidUnifiedTopBar(
    title: String?,
    showingShelf: Boolean,
    drawerDescription: String,
    backToShelvesDescription: String,
    onMenu: () -> Unit,
    onBackFromShelf: () -> Unit,
    onAccount: () -> Unit,
    accountAvatar: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(shadowElevation = 1.dp, modifier = modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().height(64.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showingShelf) {
                IconButton(onClick = onBackFromShelf) { Icon(Icons.AutoMirrored.Filled.ArrowBack, backToShelvesDescription) }
            } else {
                IconButton(onClick = onMenu) { Icon(Icons.Default.Menu, drawerDescription) }
            }
            if (title != null) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                Spacer(Modifier.weight(1f))
            }
            IconButton(onClick = onAccount, modifier = Modifier.testTag("UnifiedLibraryProfile")) { accountAvatar() }
        }
    }
}

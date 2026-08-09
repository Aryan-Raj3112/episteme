package com.aryan.reader.feedback

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aryan.reader.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupportProjectScreen(
    onNavigateBack: () -> Unit
) {
    val uriHandler = LocalUriHandler.current
    com.aryan.reader.shared.ui.SharedAndroidUtilityLinkScreen(
        strings = com.aryan.reader.shared.ui.SharedAndroidUtilityLinkStrings(
            title = stringResource(R.string.support_project_title),
            backDescription = stringResource(R.string.action_back),
            heading = stringResource(R.string.support_project_heading),
            description = stringResource(R.string.support_project_desc),
            firstTitle = stringResource(R.string.support_github_sponsor),
            firstDescription = stringResource(R.string.support_github_sponsor_desc),
            secondTitle = stringResource(R.string.support_patreon),
            secondDescription = stringResource(R.string.support_patreon_desc),
            openDescription = stringResource(R.string.action_open),
        ),
        onNavigateBack = onNavigateBack,
        onFirstClick = { uriHandler.openUri("https://github.com/sponsors/Aryan-Raj3112") },
        onSecondClick = { uriHandler.openUri("https://www.patreon.com/c/epistemereader") },
        heroIcon = {
            Icon(
                Icons.Outlined.FavoriteBorder,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        firstIcon = {
            Icon(
                painterResource(R.drawable.github),
                contentDescription = stringResource(R.string.support_github_sponsor),
                modifier = Modifier.size(28.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        secondIcon = {
            Icon(
                Icons.Outlined.FavoriteBorder,
                contentDescription = stringResource(R.string.support_patreon),
                modifier = Modifier.size(28.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        },
    )
}

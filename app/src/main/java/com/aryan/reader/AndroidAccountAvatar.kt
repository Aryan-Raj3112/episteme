package com.aryan.reader

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest

/**
 * Android account avatar used by every signed-in account surface.
 *
 * Keep the fallback visible while Coil is loading and after a failed remote
 * request.  AsyncImage otherwise renders an empty slot on error, which makes
 * a valid profile URL look indistinguishable from a missing one.
 */
@Composable
internal fun AndroidAccountAvatar(
    user: UserData,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val photoUrl = remember(user.uid, user.photoUrl) {
        normalizedAccountPhotoUrl(user.photoUrl)
    }
    var imageLoaded by remember(photoUrl) { mutableStateOf(false) }
    var imageFailed by remember(photoUrl) { mutableStateOf(false) }

    Box(
        modifier = modifier.clip(CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (photoUrl != null && !imageFailed) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(photoUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                onSuccess = { imageLoaded = true },
                onError = { imageFailed = true },
            )
        }

        if (!imageLoaded) {
            Icon(
                imageVector = Icons.Outlined.AccountCircle,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

internal fun normalizedAccountPhotoUrl(value: String?): String? {
    val candidate = value?.trim()?.takeIf { it.isNotBlank() } ?: return null
    if (candidate.equals("null", ignoreCase = true)) return null
    val scheme = Uri.parse(candidate).scheme?.lowercase()
    return candidate.takeIf { scheme == "https" || scheme == "http" }
}

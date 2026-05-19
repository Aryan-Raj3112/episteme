package com.aryan.reader.desktop

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.aryan.reader.shared.ui.readerString
import java.awt.Desktop
import java.net.URI
import java.net.URLEncoder

internal const val EpistemeSourceUrl = "https://github.com/Aryan-Raj3112/episteme"
internal const val EpistemeIssuesUrl = "https://github.com/Aryan-Raj3112/episteme/issues"
internal const val EpistemeGitHubSponsorsUrl = "https://github.com/sponsors/Aryan-Raj3112"
internal const val EpistemePatreonUrl = "https://www.patreon.com/c/epistemereader"
internal const val EpistemeSupportEmail = "epistemereader@gmail.com"

private const val ExternalLinkLogTag = "EpistemeExternalLink"

internal fun desktopFeedbackSubject(profile: DesktopBuildProfile): String {
    return "Feedback: ${profile.appName}"
}

internal fun desktopAppVersionName(): String {
    val version = System.getProperty(DesktopVersionProperty)
        ?.takeIf { it.isNotBlank() }
        ?: EpistemeDesktopAppVersion::class.java.getPackage()?.implementationVersion
            ?.takeIf { it.isNotBlank() }
    return version?.let { "Version $it" } ?: "Version unavailable"
}

private object EpistemeDesktopAppVersion

@Composable
internal fun DesktopExternalLinkDialog(
    url: String?,
    onDismiss: () -> Unit
) {
    if (url == null) return
    val clipboardManager = LocalClipboardManager.current
    LaunchedEffect(url) {
        logExternalLink("dialog_show url=\"${url.logPreview()}\"")
    }
    fun dismiss() {
        logExternalLink("dialog_dismiss url=\"${url.logPreview()}\"")
        onDismiss()
    }
    DesktopReaderBottomSheet(
        title = readerString("dialog_external_link_title", "External link"),
        onDismiss = ::dismiss
    ) {
        Text(
            readerString("desktop_external_link_desc", "You clicked an external link."),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Text(
                url,
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = ::dismiss) {
                Text(readerString("action_cancel", "Cancel"))
            }
            TextButton(
                onClick = {
                    logExternalLink("dialog_copy url=\"${url.logPreview()}\"")
                    clipboardManager.setText(AnnotatedString(url))
                    onDismiss()
                }
            ) {
                Text(readerString("action_copy", "Copy"))
            }
            TextButton(
                onClick = {
                    logExternalLink("dialog_open url=\"${url.logPreview()}\"")
                    openExternalUrl(url)
                    onDismiss()
                }
            ) {
                Text(readerString("action_open", "Open"))
            }
        }
    }
}

internal fun openExternalUrl(url: String) {
    if (!currentDesktopBuildProfile().featurePolicy.projectLinks) {
        logExternalLink("open_blocked_offline url=\"${url.logPreview()}\"")
        return
    }
    val normalizedUrl = url.normalizedExternalUrl()
    runCatching {
        if (Desktop.isDesktopSupported()) {
            val desktop = Desktop.getDesktop()
            if (normalizedUrl.startsWith("mailto:", ignoreCase = true)) {
                desktop.mail(URI(normalizedUrl))
            } else {
                desktop.browse(URI(normalizedUrl))
            }
            logExternalLink("open_system_browser_success url=\"${normalizedUrl.logPreview()}\"")
        } else {
            logExternalLink("open_system_browser_unavailable url=\"${normalizedUrl.logPreview()}\"")
        }
    }.onFailure { throwable ->
        logExternalLink("open_system_browser_failed url=\"${normalizedUrl.logPreview()}\" error=\"${throwable.message.orEmpty().logPreview()}\"")
    }
}

internal fun String.normalizedExternalUrl(): String {
    val trimmed = trim()
    return if (trimmed.startsWith("www.", ignoreCase = true)) {
        "https://$trimmed"
    } else {
        trimmed
    }
}

internal fun String.isRemoteNetworkUrl(): Boolean {
    val trimmed = trim()
    return trimmed.startsWith("http://", ignoreCase = true) ||
        trimmed.startsWith("https://", ignoreCase = true) ||
        trimmed.startsWith("ws://", ignoreCase = true) ||
        trimmed.startsWith("wss://", ignoreCase = true)
}

internal fun String.urlEncode(): String {
    return URLEncoder.encode(this, Charsets.UTF_8.name())
}

private fun logExternalLink(message: String) {
    logDesktopDiagnostic(ExternalLinkLogTag) { message }
}

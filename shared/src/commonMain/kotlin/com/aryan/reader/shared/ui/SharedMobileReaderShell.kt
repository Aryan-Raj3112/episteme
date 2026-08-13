package com.aryan.reader.shared.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Shared application shell for the mobile EPUB and PDF readers.
 *
 * Reader engines, platform input, system UI, and persistence remain caller-owned slots. Keeping
 * the drawer/scaffold boundary here gives both mobile readers one structural owner without
 * changing their content padding or drawer gesture policy.
 */
@Composable
fun SharedMobileReaderShell(
    drawerState: DrawerState,
    drawerContent: @Composable () -> Unit,
    contentWindowInsets: WindowInsets,
    modifier: Modifier = Modifier,
    drawerGesturesEnabled: Boolean = drawerState.isOpen,
    snackbarHost: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    SharedMobileReaderDrawer(
        drawerState = drawerState,
        gesturesEnabled = drawerGesturesEnabled,
        drawerContent = drawerContent,
        modifier = modifier,
    ) {
        SharedMobileReaderScaffold(
            snackbarHost = snackbarHost,
            contentWindowInsets = contentWindowInsets,
            content = content,
        )
    }
}

@Composable
fun SharedMobileReaderDrawer(
    drawerState: DrawerState,
    drawerContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    gesturesEnabled: Boolean = drawerState.isOpen,
    content: @Composable () -> Unit,
) {
    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = gesturesEnabled,
        drawerContent = drawerContent,
        modifier = modifier,
        content = content,
    )
}

@Composable
fun SharedMobileReaderScaffold(
    contentWindowInsets: WindowInsets,
    modifier: Modifier = Modifier,
    snackbarHost: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = modifier,
        snackbarHost = snackbarHost,
        contentWindowInsets = contentWindowInsets,
        content = content,
    )
}

@Composable
fun SharedMobileReaderRecoveryGate(
    message: String,
    recovering: Boolean,
    isError: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        if (recovering) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }
        Text(
            text = message,
            modifier = Modifier.align(Alignment.BottomCenter).padding(24.dp),
            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun SharedMobileReaderLoadingIndicator(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
fun SharedMobileReaderCenteredError(
    message: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize().padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(16.dp),
        )
    }
}

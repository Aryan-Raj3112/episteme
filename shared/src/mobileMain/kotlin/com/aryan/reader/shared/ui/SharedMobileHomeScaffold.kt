package com.aryan.reader.shared.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.DrawerState
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable

/** Exact structural shell used by Android Home; platform content remains injectable. */
@Composable
fun SharedMobileHomeScaffold(
    drawerState: DrawerState,
    drawerContent: @Composable () -> Unit,
    snackbarHost: @Composable () -> Unit,
    topBar: @Composable () -> Unit,
    content: @Composable (PaddingValues) -> Unit
) {
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = drawerContent
    ) {
        Scaffold(
            snackbarHost = snackbarHost,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = topBar,
            content = content
        )
    }
}

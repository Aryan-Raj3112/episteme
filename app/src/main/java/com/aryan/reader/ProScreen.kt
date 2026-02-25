/*
 * Episteme Reader - A native Android document reader.
 * Copyright (C) 2026 Episteme Authors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 * Electronic mail: epistemereader@gmail.com
 */
package com.aryan.reader

import android.app.Activity
import timber.log.Timber
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Currency

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ProScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val proUpgradeState by viewModel.proUpgradeState.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    var showExistingPurchaseDialog by remember { mutableStateOf(false) }
    var showEarlyAccessInfoDialog by remember { mutableStateOf(false) }
    var showSignInRequiredDialog by remember { mutableStateOf(false) }

    val pagerState = rememberPagerState(initialPage = 1, pageCount = { 2 })
    var selectedTabIndex by remember { mutableIntStateOf(1) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(pagerState.currentPage) {
        selectedTabIndex = pagerState.currentPage
    }

    LaunchedEffect(selectedTabIndex) {
        scope.launch {
            pagerState.animateScrollToPage(selectedTabIndex)
        }
    }

    LaunchedEffect(uiState.isProUser) {
        if (uiState.isProUser) {
            selectedTabIndex = 1
        }
    }

    LaunchedEffect(proUpgradeState.error) {
        proUpgradeState.error?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearBillingError()
        }
    }

    if (showExistingPurchaseDialog) {
        ExistingPurchaseDialog(onDismiss = { showExistingPurchaseDialog = false })
    }

    if (showEarlyAccessInfoDialog) {
        EarlyAccessInfoDialog(onDismiss = { showEarlyAccessInfoDialog = false })
    }

    if (showSignInRequiredDialog) {
        SignInRequiredDialog(
            onSignInClick = {
                scope.launch {
                    context.findActivity()?.let { activity ->
                        viewModel.signIn(activity)
                    }
                }
                showSignInRequiredDialog = false
            },
            onDismiss = { showSignInRequiredDialog = false }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { }, // Removed header content
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = paddingValues.calculateTopPadding(), start = 16.dp, end = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Tabs
            TabRow(
                selectedTabIndex = selectedTabIndex,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.extraLarge)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                indicator = {},
                divider = {}
            ) {
                Tab(
                    selected = selectedTabIndex == 0,
                    onClick = { selectedTabIndex = 0 },
                    modifier = Modifier
                        .height(56.dp)
                        .clip(CircleShape)
                        .background(
                            if (selectedTabIndex == 0) MaterialTheme.colorScheme.surface else Color.Transparent
                        )
                        .border( // Border for selected Free tab
                            width = if (selectedTabIndex == 0) 2.dp else 0.dp,
                            color = if (selectedTabIndex == 0) MaterialTheme.colorScheme.primary else Color.Transparent,
                            shape = CircleShape
                        ),
                    text = {
                        AutoSizeText(
                            "Free",
                            style = LocalTextStyle.current.copy(
                                color = if (selectedTabIndex == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    },
                    selectedContentColor = MaterialTheme.colorScheme.primary,
                    unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Tab(
                    selected = selectedTabIndex == 1,
                    onClick = { selectedTabIndex = 1 },
                    modifier = Modifier
                        .height(56.dp)
                        .clip(CircleShape)
                        .background(
                            if (selectedTabIndex == 1) MaterialTheme.colorScheme.surface else Color.Transparent
                        )
                        .border( // Border for selected Pro tab
                            width = if (selectedTabIndex == 1) 2.dp else 0.dp,
                            color = if (selectedTabIndex == 1) MaterialTheme.colorScheme.primary else Color.Transparent,
                            shape = CircleShape
                        ),
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                painter = painterResource(id = R.drawable.crown),
                                contentDescription = "Pro",
                                modifier = Modifier.size(16.dp),
                                tint = if (selectedTabIndex == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            AutoSizeText(
                                "Episteme Pro",
                                style = LocalTextStyle.current.copy(
                                    color = if (selectedTabIndex == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.SemiBold
                                )
                            )
                        }
                    },
                    selectedContentColor = MaterialTheme.colorScheme.primary,
                    unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxWidth().fillMaxHeight(),
                userScrollEnabled = true
            ) { page ->
                if (page == 0) {
                    FreeTierCard()
                } else {
                    ProTierCard(
                        isProUser = uiState.isProUser,
                        isUserSignedIn = uiState.currentUser != null,
                        proUpgradeState = proUpgradeState,
                        onUpgradeClick = {
                            (context as? Activity)?.let {
                                viewModel.launchPurchaseFlow(it)
                            }
                        },
                        onShowExistingPurchaseDialog = { showExistingPurchaseDialog = true },
                        onShowEarlyAccessInfo = { showEarlyAccessInfoDialog = true },
                        onSignInRequiredClick = { showSignInRequiredDialog = true }
                    )
                }
            }
        }
    }
}

@Composable
private fun FreeTierCard() {
    Card(
        modifier = Modifier.fillMaxWidth().fillMaxHeight(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Free Plan",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "$0",
                style = MaterialTheme.typography.displaySmall.copy(fontSize = 48.sp),
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Forever free",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(24.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.Start
            ) {
                FeatureListItem(iconRes = R.drawable.library_books, text = "Multiple Formats")
                Text(
                    text = "Supports PDF, EPUB, MOBI, AZW3",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 36.dp, bottom = 8.dp)
                )
                FeatureListItem(iconRes = R.drawable.text_to_speech, text = "Android Text-to-Speech")
                Text(
                    text = "Listen to your books with built-in TTS",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 36.dp, bottom = 8.dp)
                )
                FeatureListItem(iconRes = R.drawable.dictionary, text = "Basic Dictionary")
                Text(
                    text = "Look up single words quickly",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 36.dp, bottom = 8.dp)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { /* Do nothing, it's the current plan */ },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = MaterialTheme.shapes.medium,
                enabled = false,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            ) {
                Text("Current Plan", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun ProTierCard(
    isProUser: Boolean,
    isUserSignedIn: Boolean,
    proUpgradeState: ProUpgradeState,
    onUpgradeClick: () -> Unit,
    onShowExistingPurchaseDialog: () -> Unit,
    onShowEarlyAccessInfo: () -> Unit,
    onSignInRequiredClick: () -> Unit
) {
    val productDetails = proUpgradeState.productDetails
    val billingClientReady = proUpgradeState.billingClientReady
    val localPurchaseExistsForOtherAccount = !isProUser && proUpgradeState.hasValidPurchase

    var originalFormattedPrice by remember { mutableStateOf("$9.99") }

    LaunchedEffect(productDetails) {
        productDetails?.let { details ->
            val priceAmountMicros = details.priceAmountMicros
            val priceCurrencyCode = details.currencyCode

            val originalPriceMicros = priceAmountMicros * 2

            val currencyFormatter = NumberFormat.getCurrencyInstance().apply {
                try {
                    currency = Currency.getInstance(priceCurrencyCode)
                } catch (e: IllegalArgumentException) {
                    Timber.e(e, "Invalid currency code: $priceCurrencyCode")
                }
            }
            originalFormattedPrice = currencyFormatter.format(originalPriceMicros / 1_000_000.0)
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth().fillMaxHeight(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(id = R.drawable.crown),
                    contentDescription = "Pro Badge",
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Episteme Pro",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(8.dp))

            if (!isProUser) {
                val formattedPrice = productDetails?.formattedPrice

                if (formattedPrice != null) {
                    Text(
                        text = buildAnnotatedString {
                            withStyle(style = SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                                append(originalFormattedPrice)
                            }
                            append(" 50% OFF")
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = formattedPrice,
                        style = MaterialTheme.typography.displaySmall.copy(fontSize = 48.sp),
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    Text(
                        text = "Loading price...",
                        style = MaterialTheme.typography.displaySmall.copy(fontSize = 32.sp),
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "One-time payment",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = MaterialTheme.shapes.extraSmall,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ) {
                        Text(
                            text = "Lifetime Access",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedButton(
                    onClick = onShowEarlyAccessInfo,
                    modifier = Modifier
                        .height(40.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary
                    ),
                    shape = MaterialTheme.shapes.small,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Info",
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                    Text("Early Access Sale", style = MaterialTheme.typography.labelLarge)
                }
                Spacer(modifier = Modifier.height(16.dp))
            }


            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = "Everything in Free, plus:",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                FeatureListItem(iconRes = R.drawable.cloud_sync, text = "Cloud Sync Across Devices")
                Text(
                    text = "Keep your entire library, including book files and reading progress, synced across up to 4 devices.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 36.dp, bottom = 8.dp)
                )
                FeatureListItem(iconRes = R.drawable.summarize, text = "Summarization")
                Text(
                    text = "Get quick summaries of chapters or pages",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 36.dp, bottom = 8.dp)
                )
                FeatureListItem(iconRes = R.drawable.dictionary, text = "Smart Dictionary")
                Text(
                    text = "Search phrases and even paragraphs, not just single words",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 36.dp, bottom = 8.dp)
                )
                FeatureListItem(iconRes = R.drawable.chat_bubble, text = "Priority Feature Requests")
                Text(
                    text = "Your suggestions get prioritized",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 36.dp, bottom = 8.dp)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))

            if (isProUser) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .background(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                            MaterialTheme.shapes.medium
                        )
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.crown),
                        contentDescription = "Unlocked",
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Pro Features Unlocked!",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            } else {
                when {
                    !isUserSignedIn -> {
                        Button(
                            onClick = onSignInRequiredClick,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = MaterialTheme.shapes.medium,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            AutoSizeText("Sign in Required", style = LocalTextStyle.current.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold))
                        }
                    }
                    proUpgradeState.isVerifying -> {
                        Button(
                            onClick = {},
                            enabled = false,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = MaterialTheme.shapes.medium,
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp,
                                color = LocalContentColor.current
                            )
                            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                            Text("Verifying purchase...")
                        }
                    }
                    localPurchaseExistsForOtherAccount -> {
                        OutlinedButton(
                            onClick = onShowExistingPurchaseDialog,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Info",
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                            AutoSizeText("Existing Purchase Found")
                        }
                    }
                    productDetails != null -> {
                        Button(
                            onClick = {
                                Timber.d("Upgrade button clicked.")
                                onUpgradeClick()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = MaterialTheme.shapes.medium,
                            enabled = billingClientReady
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    painter = painterResource(id = R.drawable.crown),
                                    contentDescription = "Pro",
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                                AutoSizeText("Get Lifetime Access", style = LocalTextStyle.current.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold))
                            }
                        }
                    }
                    !billingClientReady -> {
                        Box(modifier = Modifier.height(48.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    else -> {
                        Text(
                            text = "Upgrade currently unavailable. Please check your internet and try again.",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.height(48.dp)
                        )
                    }
                }

                // Text area below button
                Spacer(modifier = Modifier.height(16.dp)) // Increased spacing
                when {
                    !isUserSignedIn -> {
                        Text(
                            text = "Please sign in to your Google account to purchase Episteme Pro.",
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    proUpgradeState.isVerifying -> {
                        Text(
                            text = "This may take a few moments. Your Pro status will be updated automatically.",
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center
                        )
                    }
                    else -> {
                        LegalText(prefixText = "By purchasing,")
                    }
                }
            }
        }
    }
}

@Composable
private fun FeatureListItem(@androidx.annotation.DrawableRes iconRes: Int? = null, icon: ImageVector? = null, text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (iconRes != null) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        } else if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        } else {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Text(text = text, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun ExistingPurchaseDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Info, contentDescription = null) },
        title = { Text("Existing Purchase Found") },
        text = { Text("This device already has a Pro purchase, but it's linked to a different account. Please sign in to the account that was used for the original purchase to restore your Pro features.") },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("OK") }
        }
    )
}

@Composable
fun EarlyAccessInfoDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Info, contentDescription = null) },
        title = { Text("Early Access Sale") },
        text = { Text("You're getting Episteme Pro at a special discounted price during our early access period! This is a limited-time offer.") },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Got It!") }
        }
    )
}

@Composable
fun SignInRequiredDialog(onSignInClick: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(painter = painterResource(id = R.drawable.crown), contentDescription = null) }, // Using crown icon for Pro
        title = { Text("Sign In Required") },
        text = { Text("Please sign in to your Google account to purchase Episteme Pro and unlock all premium features.") },
        confirmButton = {
            TextButton(onClick = onSignInClick) { Text("Sign In") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Not Now") }
        }
    )
}
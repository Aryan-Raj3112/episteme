/*
 * Episteme Reader - A native Android document reader.
 * Copyright (C) 2026 Episteme
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
 * mail: epistemereader@gmail.com
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aryan.reader.data.ProductDetailsEntity
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Currency

@Suppress("KotlinConstantConditions")
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
    var showSignInRequiredDialog by remember { mutableStateOf(false) }

    // Removed Free Tab, so tabCount is max 2
    val tabCount = if (BuildConfig.FLAVOR == "pro") 2 else 1
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { tabCount })
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(pagerState.currentPage) {
        selectedTabIndex = pagerState.currentPage
    }

    LaunchedEffect(selectedTabIndex) {
        scope.launch {
            pagerState.animateScrollToPage(selectedTabIndex)
        }
    }

    // Default to the Credits tab if they already own Pro
    LaunchedEffect(uiState.isProUser) {
        if (uiState.isProUser && BuildConfig.FLAVOR == "pro") {
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
                title = { },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
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
                        .border(
                            width = if (selectedTabIndex == 0) 2.dp else 0.dp,
                            color = if (selectedTabIndex == 0) MaterialTheme.colorScheme.primary else Color.Transparent,
                            shape = CircleShape
                        ),
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                painter = painterResource(id = R.drawable.crown),
                                contentDescription = stringResource(R.string.drawer_pro_unlocked),
                                modifier = Modifier.size(16.dp),
                                tint = if (selectedTabIndex == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            AutoSizeText(stringResource(R.string.drawer_pro_unlocked),
                                style = LocalTextStyle.current.copy(
                                    color = if (selectedTabIndex == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.SemiBold
                                )
                            )
                        }
                    },
                    selectedContentColor = MaterialTheme.colorScheme.primary,
                    unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (BuildConfig.FLAVOR == "pro") {
                    Tab(
                        selected = selectedTabIndex == 1,
                        onClick = { selectedTabIndex = 1 },
                        modifier = Modifier
                            .height(56.dp)
                            .clip(CircleShape)
                            .background(if (selectedTabIndex == 1) MaterialTheme.colorScheme.surface else Color.Transparent)
                            .border(
                                width = if (selectedTabIndex == 1) 2.dp else 0.dp,
                                color = if (selectedTabIndex == 1) MaterialTheme.colorScheme.primary else Color.Transparent,
                                shape = CircleShape
                            ),
                        text = {
                            AutoSizeText(stringResource(R.string.credits_tab),
                                style = LocalTextStyle.current.copy(
                                    color = if (selectedTabIndex == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.SemiBold
                                )
                            )
                        },
                        selectedContentColor = MaterialTheme.colorScheme.primary,
                        unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxWidth().fillMaxHeight(),
                userScrollEnabled = true
            ) { page ->
                when (page) {
                    0 -> {
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
                            onSignInRequiredClick = { showSignInRequiredDialog = true })
                    }

                    1 -> {
                        if (BuildConfig.FLAVOR == "pro") {
                            CreditTierCard(
                                credits = uiState.credits,
                                creditProducts = proUpgradeState.creditProducts,
                                isVerifying = proUpgradeState.isVerifying,
                                isUserSignedIn = uiState.currentUser != null,
                                onSignInRequiredClick = { showSignInRequiredDialog = true },
                                onBuyCredits = { productId ->
                                    (context as? Activity)?.let { viewModel.launchPurchaseFlow(it, productId) }
                                })
                        }
                    }
                }
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
                    contentDescription = stringResource(R.string.drawer_pro_unlocked),
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.drawer_pro_unlocked),
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
                            append(" " + stringResource(R.string.pro_sale_off)) },
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
                    Text(stringResource(R.string.loading_price),
                        style = MaterialTheme.typography.displaySmall.copy(fontSize = 32.sp),
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.one_time_payment),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = MaterialTheme.shapes.extraSmall,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ) {
                        Text(stringResource(R.string.lifetime_access),
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }


            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.Start
            ) {
                Text(stringResource(R.string.pro_includes),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                FeatureListItem(iconRes = R.drawable.cloud_sync, text = stringResource(R.string.feature_cloud_sync))
                Text(stringResource(R.string.feature_cloud_sync_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 36.dp, bottom = 8.dp)
                )
                FeatureListItem(iconRes = R.drawable.summarize, text = stringResource(R.string.feature_summarize))
                Text(stringResource(R.string.feature_summarize_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 36.dp, bottom = 8.dp)
                )
                FeatureListItem(iconRes = R.drawable.dictionary, text = stringResource(R.string.feature_smart_dict))
                Text(stringResource(R.string.feature_smart_dict_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 36.dp, bottom = 8.dp)
                )
                FeatureListItem(iconRes = R.drawable.chat_bubble, text = stringResource(R.string.feature_priority))
                Text(stringResource(R.string.feature_priority_desc),
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
                        contentDescription = stringResource(R.string.pro_unlocked),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.pro_unlocked),
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
                            AutoSizeText(stringResource(R.string.sign_in_required), style = LocalTextStyle.current.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold))
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
                            Text(stringResource(R.string.verifying_purchase))
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
                                contentDescription = stringResource(R.string.info),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                            AutoSizeText(stringResource(R.string.existing_purchase_found))
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
                                    contentDescription = stringResource(R.string.drawer_pro_unlocked),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                                AutoSizeText(stringResource(R.string.get_lifetime_access), style = LocalTextStyle.current.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold))
                            }
                        }
                    }
                    !billingClientReady -> {
                        Box(modifier = Modifier.height(48.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    else -> {
                        Text(stringResource(R.string.upgrade_unavailable),
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
                        Text(stringResource(R.string.sign_in_to_purchase),
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    proUpgradeState.isVerifying -> {
                        Text(stringResource(R.string.verifying_purchase_desc),
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center
                        )
                    }
                    else -> {
                        LegalText(prefixText = stringResource(R.string.legal_by_purchasing))
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
        title = { Text(stringResource(R.string.existing_purchase_found)) },
        text = { Text(stringResource(R.string.dialog_existing_purchase_desc)) },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_ok)) }
        }
    )
}

@Composable
fun SignInRequiredDialog(onSignInClick: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(painter = painterResource(id = R.drawable.crown), contentDescription = null) },
        title = { Text(stringResource(R.string.sign_in_required)) },
        text = { Text(stringResource(R.string.dialog_sign_in_required_desc)) },
        confirmButton = {
            TextButton(onClick = onSignInClick) { Text(stringResource(R.string.drawer_sign_in)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_not_now)) }
        }
    )
}

@Composable
private fun CreditTierCard(
    credits: Int,
    creditProducts: List<ProductDetailsEntity>,
    isVerifying: Boolean,
    isUserSignedIn: Boolean,
    onSignInRequiredClick: () -> Unit,
    onBuyCredits: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().fillMaxHeight(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(stringResource(R.string.credits_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "$credits",
                style = MaterialTheme.typography.displaySmall.copy(fontSize = 48.sp),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(stringResource(R.string.credits_available), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Spacer(modifier = Modifier.height(24.dp))

            if (isVerifying) {
                CircularProgressIndicator(modifier = Modifier.padding(16.dp))
                Text(stringResource(R.string.verifying_purchase), style = MaterialTheme.typography.bodySmall)
            } else if (creditProducts.isEmpty()) {
                Text(stringResource(R.string.loading_price), modifier = Modifier.padding(16.dp))
            } else {
                creditProducts.forEach { product ->
                    OutlinedCard(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        onClick = {
                            if (isUserSignedIn) onBuyCredits(product.productId)
                            else onSignInRequiredClick()
                        },
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                Text(product.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                                if (product.description.isNotBlank()) {
                                    Text(product.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Button(
                                onClick = {
                                    if (isUserSignedIn) onBuyCredits(product.productId)
                                    else onSignInRequiredClick()
                                },
                                modifier = Modifier.wrapContentWidth()
                            ) {
                                Text(product.formattedPrice)
                            }
                        }
                    }
                }
            }

            if (!isUserSignedIn) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    stringResource(R.string.sign_in_to_purchase_credits),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                stringResource(R.string.credits_estimated_cost_breakdown),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Start)
            )
            Spacer(modifier = Modifier.height(16.dp))

            CostBreakdownItem(
                iconRes = R.drawable.text_to_speech,
                title = stringResource(R.string.credits_cloud_tts_title),
                description = stringResource(R.string.credits_cloud_tts_desc)
            )
            CostBreakdownItem(
                iconRes = R.drawable.summarize,
                title = stringResource(R.string.credits_ai_summaries_title),
                description = stringResource(R.string.credits_ai_summaries_desc)
            )
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun CostBreakdownItem(
    @androidx.annotation.DrawableRes iconRes: Int,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp).padding(top = 2.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(text = title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp
            )
        }
    }
}

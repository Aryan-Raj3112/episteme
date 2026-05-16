// src\oss
package com.aryan.reader

import android.app.Activity
import android.content.Context
import com.aryan.reader.data.ProductDetailsEntity
import com.aryan.reader.data.PurchaseEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ProUpgradeState(
    val productDetails: ProductDetailsEntity? = null,
    val creditProducts: List<ProductDetailsEntity> = emptyList(),
    val hasValidPurchase: Boolean = false,
    val activePurchases: List<PurchaseEntity> = emptyList(),
    val hasAccountConflict: Boolean = false,
    val billingClientReady: Boolean = false,
    val error: String? = null,
    val isVerifying: Boolean = false
)

class BillingClientWrapper(
    context: Context,
    private val externalScope: CoroutineScope,
    private val onPurchaseVerified: (PurchaseEntity) -> Unit
) {
    private val appContext = context.applicationContext
    private val _proUpgradeState = MutableStateFlow(ProUpgradeState())
    val proUpgradeState = _proUpgradeState.asStateFlow()

    fun initializeConnection() {
        // No-op for OSS
    }

    fun refreshPurchasesAsync() {
        // No-op
    }

    fun launchPurchaseFlow(
        activity: Activity,
        productId: String = PRO_LIFETIME_PRODUCT_ID,
        obfuscatedAccountId: String? = null
    ) {
        _proUpgradeState.value = _proUpgradeState.value.copy(error = appContext.getString(R.string.billing_error_not_available_oss))
    }
    fun consumePurchase(purchaseToken: String) {}

    fun clearError() {
        _proUpgradeState.value = _proUpgradeState.value.copy(error = null)
    }

    fun markAccountConflict() {
        _proUpgradeState.value = _proUpgradeState.value.copy(hasAccountConflict = true, isVerifying = false)
    }

    fun clearAccountConflict() {
        _proUpgradeState.value = _proUpgradeState.value.copy(hasAccountConflict = false)
    }

    fun clearVerificationState() {
        _proUpgradeState.value = _proUpgradeState.value.copy(isVerifying = false)
    }

    companion object {
        const val PRO_LIFETIME_PRODUCT_ID = "episteme_pro_lifetime"
    }
}

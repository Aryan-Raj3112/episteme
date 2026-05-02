// src\oss
package com.aryan.reader.data

import kotlinx.serialization.Serializable

@Serializable
data class PurchaseVerificationRequest(
    val purchaseToken: String,
    val idToken: String,
    val productId: String
)

@Serializable
data class VerificationResponse(
    val status: String,
    val message: String
)

class CloudflareRepository {
    suspend fun verifyPurchase(purchaseToken: String, productId: String): Result<VerificationResponse> {
        return Result.failure(Exception("Not available in OSS version"))
    }
}

package com.aryan.reader

import java.security.MessageDigest
import java.util.Base64

object PurchaseAccountObfuscator {
    fun obfuscatedAccountId(uid: String): String {
        return "firebase_${sha256Base64Url(uid)}"
    }

    fun purchaseTokenHash(purchaseToken: String): String {
        return "sha256_${sha256Base64Url(purchaseToken)}"
    }

    private fun sha256Base64Url(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
        return Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(digest)
    }
}

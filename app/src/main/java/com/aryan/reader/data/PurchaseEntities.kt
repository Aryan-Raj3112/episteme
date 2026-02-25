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
package com.aryan.reader.data

/**
 * Agnostic representation of a purchase to decouple MainViewModel from Billing Library.
 */
data class PurchaseEntity(
    val orderId: String?,
    val products: List<String>,
    val purchaseToken: String,
    val purchaseTime: Long,
    val isAcknowledged: Boolean,
    val isAutoRenewing: Boolean
)

/**
 * Agnostic representation of product details.
 */
data class ProductDetailsEntity(
    val productId: String,
    val name: String,
    val description: String,
    val formattedPrice: String,
    val currencyCode: String,
    val priceAmountMicros: Long
)
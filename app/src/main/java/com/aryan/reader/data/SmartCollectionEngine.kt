package com.aryan.reader.data

import com.aryan.reader.toSharedBookItem
import com.aryan.reader.shared.SmartCollectionEngine as SharedSmartCollectionEngine

typealias SmartField = com.aryan.reader.shared.SmartField
typealias SmartOperator = com.aryan.reader.shared.SmartOperator
typealias SmartRule = com.aryan.reader.shared.SmartRule
typealias SmartCollectionDefinition = com.aryan.reader.shared.SmartCollectionDefinition

object SmartCollectionEngine {
    fun toJson(definition: SmartCollectionDefinition): String =
        SharedSmartCollectionEngine.toJson(definition)

    fun fromJson(json: String?): SmartCollectionDefinition? =
        SharedSmartCollectionEngine.fromJson(json)

    fun evaluate(book: RecentFileItem, definition: SmartCollectionDefinition): Boolean =
        SharedSmartCollectionEngine.evaluate(book.toSharedBookItem(), definition)
}

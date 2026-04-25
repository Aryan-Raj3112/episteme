package com.aryan.reader.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
enum class SmartField { TITLE, AUTHOR, PROGRESS, FILE_TYPE, FOLDER, TAG }
@Serializable
enum class SmartOperator { EQUALS, CONTAINS, GREATER_THAN, LESS_THAN }

@Serializable
data class SmartRule(
    val field: SmartField,
    val operator: SmartOperator,
    val value: String
)

@Serializable
data class SmartCollectionDefinition(
    val matchAll: Boolean = true,
    val rules: List<SmartRule> = emptyList()
)

object SmartCollectionEngine {
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    fun toJson(definition: SmartCollectionDefinition): String = json.encodeToString(definition)

    fun fromJson(json: String?): SmartCollectionDefinition? {
        if (json.isNullOrBlank()) return null
        return try {
            this.json.decodeFromString<SmartCollectionDefinition>(json)
        } catch (_: Exception) { null }
    }

    fun evaluate(book: RecentFileItem, definition: SmartCollectionDefinition): Boolean {
        if (definition.rules.isEmpty()) return false

        val results = definition.rules.map { rule ->
            when (rule.field) {
                SmartField.TITLE -> evaluateString(book.title ?: book.displayName, rule)
                SmartField.AUTHOR -> evaluateString(book.author ?: "", rule)
                SmartField.FILE_TYPE -> evaluateString(book.type.name, rule)
                SmartField.FOLDER -> evaluateString(book.sourceFolderUri ?: "", rule)
                SmartField.TAG -> evaluateTags(book.tags.map { it.name }, rule)
                SmartField.PROGRESS -> evaluateNumber(book.progressPercentage ?: 0f, rule)
            }
        }
        return if (definition.matchAll) results.all { it } else results.any { it }
    }

    private fun evaluateString(target: String, rule: SmartRule): Boolean {
        return when (rule.operator) {
            SmartOperator.EQUALS -> target.equals(rule.value, ignoreCase = true)
            SmartOperator.CONTAINS -> target.contains(rule.value, ignoreCase = true)
            else -> false
        }
    }

    private fun evaluateNumber(target: Float, rule: SmartRule): Boolean {
        val ruleValue = rule.value.toFloatOrNull() ?: return false
        return when (rule.operator) {
            SmartOperator.EQUALS -> target == ruleValue
            SmartOperator.GREATER_THAN -> target > ruleValue
            SmartOperator.LESS_THAN -> target < ruleValue
            else -> false
        }
    }

    private fun evaluateTags(tags: List<String>, rule: SmartRule): Boolean {
        return when (rule.operator) {
            SmartOperator.EQUALS -> tags.any { it.equals(rule.value, ignoreCase = true) }
            SmartOperator.CONTAINS -> tags.any { it.contains(rule.value, ignoreCase = true) }
            else -> false
        }
    }
}

package com.aryan.reader.shared

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class ReaderWordReplacementRule(
    val id: String,
    val from: String,
    val to: String,
    val enabled: Boolean = true,
    val isRegex: Boolean = false,
    val matchCase: Boolean = false,
    val wholeWord: Boolean = true,
)

data class ReaderWordReplacementValidation(
    val isValid: Boolean,
    val message: String? = null,
)

data class ReaderWordReplacementError(
    val ruleId: String,
    val message: String,
)

data class ReaderWordReplacementApplyResult(
    val text: String,
    val appliedRuleIds: List<String> = emptyList(),
    val errors: List<ReaderWordReplacementError> = emptyList(),
)

object ReaderWordReplacementEngine {
    fun validate(rule: ReaderWordReplacementRule): ReaderWordReplacementValidation {
        if (rule.from.isBlank()) {
            return ReaderWordReplacementValidation(isValid = false, message = "Enter text to replace.")
        }
        if (!rule.isRegex) {
            return ReaderWordReplacementValidation(isValid = true)
        }
        return runCatching { rule.toRegex() }
            .fold(
                onSuccess = { ReaderWordReplacementValidation(isValid = true) },
                onFailure = {
                    ReaderWordReplacementValidation(
                        isValid = false,
                        message = it.message ?: "This regex is not valid.",
                    )
                },
            )
    }

    fun apply(
        text: String,
        rules: List<ReaderWordReplacementRule>,
    ): ReaderWordReplacementApplyResult {
        if (text.isEmpty() || rules.isEmpty()) {
            return ReaderWordReplacementApplyResult(text = text)
        }

        var current = text
        val applied = mutableListOf<String>()
        val errors = mutableListOf<ReaderWordReplacementError>()

        rules.forEach { rule ->
            if (!rule.enabled || rule.from.isBlank()) return@forEach
            val regex = runCatching { rule.toRegex() }
                .onFailure {
                    errors += ReaderWordReplacementError(
                        ruleId = rule.id,
                        message = it.message ?: "Invalid regex.",
                    )
                }
                .getOrNull() ?: return@forEach
            val replacement = if (rule.isRegex) rule.to else Regex.escapeReplacement(rule.to)
            val next = runCatching { regex.replace(current, replacement) }
                .onFailure {
                    errors += ReaderWordReplacementError(
                        ruleId = rule.id,
                        message = it.message ?: "Invalid replacement.",
                    )
                }
                .getOrNull() ?: return@forEach
            if (next != current) {
                applied += rule.id
                current = next
            }
        }

        return ReaderWordReplacementApplyResult(
            text = current,
            appliedRuleIds = applied,
            errors = errors,
        )
    }

    private fun ReaderWordReplacementRule.toRegex(): Regex {
        val source = if (isRegex) from else Regex.escape(from)
        val boundedSource = if (wholeWord) {
            """(?<![\p{L}\p{N}_])(?:$source)(?![\p{L}\p{N}_])"""
        } else {
            source
        }
        val options = if (matchCase) emptySet() else setOf(RegexOption.IGNORE_CASE)
        return Regex(pattern = boundedSource, options = options)
    }
}

@Serializable
data class ReaderBookReplacementPreferences(
    val fileRules: Map<String, List<ReaderWordReplacementRule>> = emptyMap(),
) {
    fun rulesForFile(fileId: String?): List<ReaderWordReplacementRule> {
        return fileRules[fileId.orEmpty()].orEmpty()
    }

    fun activeRulesForFile(fileId: String?): List<ReaderWordReplacementRule> {
        return rulesForFile(fileId).filter { it.enabled && it.from.isNotBlank() }
    }

    fun withFileRules(
        fileId: String?,
        rules: List<ReaderWordReplacementRule>,
    ): ReaderBookReplacementPreferences {
        val key = fileId.orEmpty()
        val nextRules = if (rules.isEmpty()) {
            fileRules - key
        } else {
            fileRules + (key to rules)
        }
        return copy(fileRules = nextRules)
    }

    fun scopedToFile(fileId: String?): ReaderBookReplacementPreferences {
        val key = fileId.orEmpty()
        val rules = rulesForFile(key)
        return if (rules.isEmpty()) {
            ReaderBookReplacementPreferences()
        } else {
            ReaderBookReplacementPreferences(fileRules = mapOf(key to rules))
        }
    }

    fun signatureForFile(fileId: String?): String {
        return activeRulesForFile(fileId).joinToString(separator = "|") { rule ->
            listOf(
                rule.id,
                rule.from,
                rule.to,
                rule.enabled.toString(),
                rule.isRegex.toString(),
                rule.matchCase.toString(),
                rule.wholeWord.toString(),
            ).joinToString(separator = "\u001F")
        }
    }
}

object ReaderBookReplacementEngine {
    fun validate(rule: ReaderWordReplacementRule): ReaderWordReplacementValidation {
        return ReaderWordReplacementEngine.validate(rule)
    }

    fun apply(
        text: String,
        preferences: ReaderBookReplacementPreferences,
        fileId: String?,
    ): ReaderWordReplacementApplyResult {
        return ReaderWordReplacementEngine.apply(
            text = text,
            rules = preferences.activeRulesForFile(fileId),
        )
    }
}

object ReaderBookReplacementPreferencesJson {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
        encodeDefaults = true
    }

    fun encode(preferences: ReaderBookReplacementPreferences): String {
        return json.encodeToString(preferences)
    }

    fun decodeOrEmpty(raw: String?): ReaderBookReplacementPreferences {
        if (raw.isNullOrBlank()) return ReaderBookReplacementPreferences()
        return runCatching {
            json.decodeFromString<ReaderBookReplacementPreferences>(raw)
        }.getOrNull() ?: ReaderBookReplacementPreferences()
    }
}

package com.aryan.reader.shared

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class ReaderTtsReplacementRule(
    val id: String,
    val from: String,
    val to: String,
    val enabled: Boolean = true,
    val isRegex: Boolean = false,
    val matchCase: Boolean = false,
    val wholeWord: Boolean = true,
)

data class ReaderTtsReplacementBookSettings(
    val localRulesEnabled: Boolean = true,
    val globalRulesEnabled: Boolean = true,
    val disabledGlobalRuleIds: Set<String> = emptySet(),
)

data class ReaderTtsReplacementPreferences(
    val isEnabled: Boolean = true,
    val globalRules: List<ReaderTtsReplacementRule> = emptyList(),
    val bookRules: Map<String, List<ReaderTtsReplacementRule>> = emptyMap(),
    val bookSettings: Map<String, ReaderTtsReplacementBookSettings> = emptyMap(),
) {
    fun settingsForBook(bookId: String?): ReaderTtsReplacementBookSettings {
        return bookSettings[bookId.orEmpty()] ?: ReaderTtsReplacementBookSettings()
    }

    fun rulesForBook(bookId: String?): List<ReaderTtsReplacementRule> {
        return bookRules[bookId.orEmpty()].orEmpty()
    }

    fun activeRulesForBook(bookId: String?): List<ReaderTtsReplacementRule> {
        if (!isEnabled) return emptyList()
        val settings = settingsForBook(bookId)
        val inherited = if (settings.globalRulesEnabled) {
            globalRules.filter { it.id !in settings.disabledGlobalRuleIds }
        } else {
            emptyList()
        }
        val local = if (settings.localRulesEnabled) rulesForBook(bookId) else emptyList()
        return inherited + local
    }

    fun withBookSettings(
        bookId: String?,
        settings: ReaderTtsReplacementBookSettings,
    ): ReaderTtsReplacementPreferences {
        return copy(bookSettings = bookSettings + (bookId.orEmpty() to settings))
    }

    fun withBookRules(
        bookId: String?,
        rules: List<ReaderTtsReplacementRule>,
    ): ReaderTtsReplacementPreferences {
        return copy(bookRules = bookRules + (bookId.orEmpty() to rules))
    }
}

data class ReaderTtsReplacementValidation(
    val isValid: Boolean,
    val message: String? = null,
)

data class ReaderTtsReplacementError(
    val ruleId: String,
    val message: String,
)

data class ReaderTtsReplacementApplyResult(
    val text: String,
    val appliedRuleIds: List<String> = emptyList(),
    val errors: List<ReaderTtsReplacementError> = emptyList(),
) {
    val hasUnmappableChanges: Boolean
        get() = appliedRuleIds.isNotEmpty()
}

object ReaderTtsReplacementEngine {
    fun validate(rule: ReaderTtsReplacementRule): ReaderTtsReplacementValidation {
        if (rule.from.isBlank()) {
            return ReaderTtsReplacementValidation(isValid = false, message = "Enter text to replace.")
        }
        if (!rule.isRegex) {
            return ReaderTtsReplacementValidation(isValid = true)
        }
        return runCatching { rule.toRegex() }
            .fold(
                onSuccess = { ReaderTtsReplacementValidation(isValid = true) },
                onFailure = {
                    ReaderTtsReplacementValidation(
                        isValid = false,
                        message = it.message ?: "This regex is not valid.",
                    )
                },
            )
    }

    fun apply(
        text: String,
        preferences: ReaderTtsReplacementPreferences,
        bookId: String? = null,
    ): ReaderTtsReplacementApplyResult {
        if (text.isEmpty() || !preferences.isEnabled) {
            return ReaderTtsReplacementApplyResult(text = text)
        }

        var current = text
        val applied = mutableListOf<String>()
        val errors = mutableListOf<ReaderTtsReplacementError>()

        preferences.activeRulesForBook(bookId).forEach { rule ->
            if (!rule.enabled || rule.from.isBlank()) return@forEach
            val regex = runCatching { rule.toRegex() }
                .onFailure {
                    errors += ReaderTtsReplacementError(
                        ruleId = rule.id,
                        message = it.message ?: "Invalid regex.",
                    )
                }
                .getOrNull() ?: return@forEach
            val replacement = if (rule.isRegex) rule.to else Regex.escapeReplacement(rule.to)
            val next = runCatching { regex.replace(current, replacement) }
                .onFailure {
                    errors += ReaderTtsReplacementError(
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

        return ReaderTtsReplacementApplyResult(
            text = current,
            appliedRuleIds = applied,
            errors = errors,
        )
    }

    private fun ReaderTtsReplacementRule.toRegex(): Regex {
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

fun ReaderTtsChunk.withTtsReplacements(
    preferences: ReaderTtsReplacementPreferences,
    bookId: String? = null,
): ReaderTtsChunk {
    val result = ReaderTtsReplacementEngine.apply(
        text = text,
        preferences = preferences,
        bookId = bookId,
    )
    return copy(spokenText = result.text)
}

fun List<ReaderTtsChunk>.withTtsReplacements(
    preferences: ReaderTtsReplacementPreferences,
    bookId: String? = null,
): List<ReaderTtsChunk> = map { it.withTtsReplacements(preferences, bookId) }

object ReaderTtsReplacementSuggestions {
    val presets: List<ReaderTtsReplacementRule> = listOf(
        ReaderTtsReplacementRule(
            id = "suggestion_dr",
            from = "Dr.",
            to = "Doctor",
            wholeWord = false,
        ),
        ReaderTtsReplacementRule(
            id = "suggestion_mr",
            from = "Mr.",
            to = "Mister",
            wholeWord = false,
        ),
        ReaderTtsReplacementRule(
            id = "suggestion_mrs",
            from = "Mrs.",
            to = "Missus",
            wholeWord = false,
        ),
        ReaderTtsReplacementRule(
            id = "suggestion_ms",
            from = "Ms.",
            to = "Miss",
            wholeWord = false,
        ),
        ReaderTtsReplacementRule(
            id = "suggestion_vs",
            from = "vs.",
            to = "versus",
            wholeWord = false,
        ),
        ReaderTtsReplacementRule(
            id = "suggestion_et_al",
            from = "et al.",
            to = "and others",
            wholeWord = false,
        ),
        ReaderTtsReplacementRule(
            id = "suggestion_initials",
            from = """\b([A-Z])\.\s*([A-Z])\.""",
            to = "\$1 \$2",
            isRegex = true,
            wholeWord = false,
        ),
    )
}

object ReaderTtsReplacementPreferencesJson {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
    }

    fun encode(preferences: ReaderTtsReplacementPreferences): String {
        return json.encodeToString(JsonElement.serializer(), toJsonElement(preferences))
    }

    fun decodeOrEmpty(raw: String?): ReaderTtsReplacementPreferences {
        if (raw.isNullOrBlank()) return ReaderTtsReplacementPreferences()
        return runCatching {
            fromJsonElement(json.parseToJsonElement(raw))
        }.getOrNull() ?: ReaderTtsReplacementPreferences()
    }

    fun toJsonElement(preferences: ReaderTtsReplacementPreferences): JsonElement {
        return JsonObject(
            mapOf(
                "isEnabled" to JsonPrimitive(preferences.isEnabled),
                "globalRules" to rulesToJson(preferences.globalRules),
                "bookRules" to JsonObject(
                    preferences.bookRules.mapValues { (_, rules) -> rulesToJson(rules) as JsonElement },
                ),
                "bookSettings" to JsonObject(
                    preferences.bookSettings.mapValues { (_, settings) -> settingsToJson(settings) as JsonElement },
                ),
            ),
        )
    }

    fun fromJsonElement(element: JsonElement?): ReaderTtsReplacementPreferences {
        val root = element as? JsonObject ?: return ReaderTtsReplacementPreferences()
        val bookRules = root["bookRules"]?.jsonObjectOrNull()
            ?.mapValues { (_, value) -> value.jsonArrayOrNull()?.mapNotNull(::ruleFromJson).orEmpty() }
            .orEmpty()
        val bookSettings = root["bookSettings"]?.jsonObjectOrNull()
            ?.mapValues { (_, value) -> settingsFromJson(value) }
            .orEmpty()
        return ReaderTtsReplacementPreferences(
            isEnabled = root.booleanValue("isEnabled") ?: true,
            globalRules = root["globalRules"]?.jsonArrayOrNull()?.mapNotNull(::ruleFromJson).orEmpty(),
            bookRules = bookRules,
            bookSettings = bookSettings,
        )
    }

    private fun rulesToJson(rules: List<ReaderTtsReplacementRule>): JsonArray {
        return JsonArray(rules.map(::ruleToJson))
    }

    private fun ruleToJson(rule: ReaderTtsReplacementRule): JsonObject {
        return JsonObject(
            mapOf(
                "id" to JsonPrimitive(rule.id),
                "from" to JsonPrimitive(rule.from),
                "to" to JsonPrimitive(rule.to),
                "enabled" to JsonPrimitive(rule.enabled),
                "isRegex" to JsonPrimitive(rule.isRegex),
                "matchCase" to JsonPrimitive(rule.matchCase),
                "wholeWord" to JsonPrimitive(rule.wholeWord),
            ),
        )
    }

    private fun ruleFromJson(element: JsonElement): ReaderTtsReplacementRule? {
        val root = element as? JsonObject ?: return null
        val id = root.stringValue("id") ?: return null
        val from = root.stringValue("from") ?: return null
        return ReaderTtsReplacementRule(
            id = id,
            from = from,
            to = root.stringValue("to").orEmpty(),
            enabled = root.booleanValue("enabled") ?: true,
            isRegex = root.booleanValue("isRegex") ?: false,
            matchCase = root.booleanValue("matchCase") ?: false,
            wholeWord = root.booleanValue("wholeWord") ?: true,
        )
    }

    private fun settingsToJson(settings: ReaderTtsReplacementBookSettings): JsonObject {
        return JsonObject(
            mapOf(
                "localRulesEnabled" to JsonPrimitive(settings.localRulesEnabled),
                "globalRulesEnabled" to JsonPrimitive(settings.globalRulesEnabled),
                "disabledGlobalRuleIds" to JsonArray(settings.disabledGlobalRuleIds.map(::JsonPrimitive)),
            ),
        )
    }

    private fun settingsFromJson(element: JsonElement): ReaderTtsReplacementBookSettings {
        val root = element as? JsonObject ?: return ReaderTtsReplacementBookSettings()
        return ReaderTtsReplacementBookSettings(
            localRulesEnabled = root.booleanValue("localRulesEnabled") ?: true,
            globalRulesEnabled = root.booleanValue("globalRulesEnabled") ?: true,
            disabledGlobalRuleIds = root["disabledGlobalRuleIds"]?.jsonArrayOrNull()
                ?.mapNotNull { it.jsonPrimitiveOrNull()?.contentOrNull }
                ?.toSet()
                .orEmpty(),
        )
    }

    private fun JsonObject.stringValue(name: String): String? {
        return get(name)?.jsonPrimitiveOrNull()?.contentOrNull
    }

    private fun JsonObject.booleanValue(name: String): Boolean? {
        return get(name)?.jsonPrimitiveOrNull()?.booleanOrNull
    }

    private fun JsonElement.jsonObjectOrNull(): JsonObject? = this as? JsonObject

    private fun JsonElement.jsonArrayOrNull(): JsonArray? = this as? JsonArray

    private fun JsonElement.jsonPrimitiveOrNull() = when (this) {
        is JsonPrimitive -> this
        JsonNull -> null
        else -> null
    }
}

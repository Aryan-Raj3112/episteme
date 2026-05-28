package com.aryan.reader

import android.content.Context
import androidx.core.content.edit
import com.aryan.reader.shared.ReaderBookReplacementPreferences
import com.aryan.reader.shared.ReaderBookReplacementPreferencesJson
import com.aryan.reader.shared.ReaderWordReplacementEngine
import com.aryan.reader.shared.ReaderWordReplacementRule
import org.jsoup.nodes.Document
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

private const val READER_PREFS_NAME = "reader_prefs"
private const val BOOK_REPLACEMENTS_KEY = "book_word_replacements_json"

fun loadBookReplacementPreferences(context: Context): ReaderBookReplacementPreferences {
    val prefs = context.getSharedPreferences(READER_PREFS_NAME, Context.MODE_PRIVATE)
    return ReaderBookReplacementPreferencesJson.decodeOrEmpty(prefs.getString(BOOK_REPLACEMENTS_KEY, null))
}

fun saveBookReplacementPreferences(
    context: Context,
    preferences: ReaderBookReplacementPreferences,
) {
    val prefs = context.getSharedPreferences(READER_PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit {
        putString(BOOK_REPLACEMENTS_KEY, ReaderBookReplacementPreferencesJson.encode(preferences))
    }
}

internal fun applyBookReplacementsToHtmlDocument(
    document: Document,
    preferences: ReaderBookReplacementPreferences,
    fileId: String?,
): Boolean {
    val rules = preferences.activeRulesForFile(fileId)
    if (rules.isEmpty()) return false

    var changed = false

    fun rewriteTextNodes(node: Node) {
        if (node is TextNode && !node.hasReplacementBlockedAncestor()) {
            val original = node.wholeText
            val replaced = applyBookReplacementRules(original, rules)
            if (replaced != original) {
                node.text(replaced)
                changed = true
            }
            return
        }

        node.childNodes().forEach(::rewriteTextNodes)
    }

    document.body()?.let(::rewriteTextNodes)
    return changed
}

private fun applyBookReplacementRules(
    text: String,
    rules: List<ReaderWordReplacementRule>,
): String {
    return ReaderWordReplacementEngine.apply(
        text = text,
        rules = rules,
    ).text
}

private fun TextNode.hasReplacementBlockedAncestor(): Boolean {
    var current: Node? = parent()
    while (current != null) {
        when (current.nodeName().lowercase()) {
            "script",
            "style",
            "noscript" -> return true
        }
        current = current.parent()
    }
    return false
}

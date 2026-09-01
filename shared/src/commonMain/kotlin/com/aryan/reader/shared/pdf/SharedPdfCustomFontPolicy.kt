package com.aryan.reader.shared.pdf

import com.aryan.reader.shared.CustomFontItem

/**
 * Returns imported fonts that have a successfully resolved platform family.
 *
 * The resolver supplies keys for the id, path, and display name so existing
 * annotations can continue to resolve after a library migration changes one
 * of those identifiers.  Keeping this filtering in common code makes the
 * Android and iOS selectors agree about deleted, unavailable, and ordering
 * semantics while the actual file decoding remains platform-specific.
 */
fun availableSharedPdfCustomFonts(
    customFonts: List<CustomFontItem>,
    resolvedFamilyKeys: Set<String>,
): List<CustomFontItem> {
    return customFonts
        .asSequence()
        .filterNot { it.isDeleted }
        .filter { font ->
            font.id in resolvedFamilyKeys ||
                font.path in resolvedFamilyKeys ||
                font.displayName in resolvedFamilyKeys
        }
        .distinctBy(CustomFontItem::id)
        .sortedBy { it.displayName.lowercase() }
        .toList()
}

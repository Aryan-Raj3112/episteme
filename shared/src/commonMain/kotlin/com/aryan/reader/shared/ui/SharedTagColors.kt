package com.aryan.reader.shared.ui

import androidx.compose.ui.graphics.Color

/**
 * Colour for a tag that has none stored.
 *
 * Tag colours are user data, so they stay raw ARGB values instead of theme tokens. The *fallback*
 * is not user data, though: it was copy-pasted as a hex literal into five different chips and dots,
 * which is how a palette change ends up missing one of them. It lives here once.
 */
internal val SharedTagColorFallback = Color(0xFF64B5F6)

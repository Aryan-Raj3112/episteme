package com.aryan.reader

import android.content.Context
import com.aryan.reader.shared.ui.SharedStringResolver

/**
 * Android's side of the shared string boundary.
 *
 * Shared UI resolves its copy through `readerString("tts_settings", "Text-to-Speech Settings")`, where
 * the key is the *Android* resource name — `scripts/generate_shared_mobile_localizations.py` reads
 * the same names out of `app/src/main/res` to package the iOS/desktop catalogs. So a shared surface
 * hosted by Android only has to hand Compose a resolver that looks the key up in its own resources;
 * without one the sheet would render the English fallbacks inside a translated app.
 *
 * This is the key-based sibling of the literal lookup `SettingsScreen` installs for
 * `SharedSettingsHub`'s model-owned English labels, and it reuses the same
 * `resources.getIdentifier` lookup as `SharedText.resolveAndroidText`.
 */
fun androidSharedStringResolver(context: Context): SharedStringResolver {
    val resources = context.resources
    val packageName = context.packageName
    return SharedStringResolver(
        resolve = { name ->
            val id = resources.getIdentifier(name, "string", packageName)
            if (id == 0) null else runCatching { resources.getString(id) }.getOrNull()
        },
        resolveQuantity = { name, quantity ->
            val id = resources.getIdentifier(name, "plurals", packageName)
            // The raw template for this quantity, not a formatted string: the resolver applies
            // the call site's own arguments afterwards, exactly like the iOS catalog does.
            if (id == 0) null
            else runCatching { resources.getQuantityText(id, quantity).toString() }.getOrNull()
        },
    )
}

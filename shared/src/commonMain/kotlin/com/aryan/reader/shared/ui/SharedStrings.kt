package com.aryan.reader.shared.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import com.aryan.reader.shared.BannerMessage
import com.aryan.reader.shared.SharedText

@Immutable
class SharedStringResolver(
    private val resolve: (name: String) -> String? = { null },
    private val resolveQuantity: (name: String, quantity: Int) -> String? = { _, _ -> null }
) {
    fun string(name: String, fallback: String, vararg args: Any?): String {
        val template = resolve(name).takeUnless { it.isNullOrBlank() } ?: fallback
        return formatAndroidString(template, args.toList())
    }

    fun quantityString(
        name: String,
        quantity: Int,
        fallbackOne: String,
        fallbackOther: String,
        vararg args: Any?
    ): String {
        val fallback = if (quantity == 1) fallbackOne else fallbackOther
        val template = resolveQuantity(name, quantity).takeUnless { it.isNullOrBlank() } ?: fallback
        return formatAndroidString(template, args.toList())
    }

    fun sharedText(text: SharedText): String {
        val quantity = text.quantity
        return if (quantity == null) {
            string(text.name, text.fallback, *text.args.toTypedArray())
        } else {
            quantityString(text.name, quantity, text.fallback, text.fallbackOther, *text.args.toTypedArray())
        }
    }
}

val LocalSharedStringResolver = staticCompositionLocalOf { SharedStringResolver() }

@Composable
fun readerString(name: String, fallback: String, vararg args: Any?): String {
    return LocalSharedStringResolver.current.string(name, fallback, *args)
}

@Composable
fun readerQuantityString(
    name: String,
    quantity: Int,
    fallbackOne: String,
    fallbackOther: String,
    vararg args: Any?
): String {
    return LocalSharedStringResolver.current.quantityString(name, quantity, fallbackOne, fallbackOther, *args)
}

@Composable
fun readerSharedText(text: SharedText): String {
    return LocalSharedStringResolver.current.sharedText(text)
}

@Composable
fun readerBannerMessage(message: BannerMessage?): String {
    val text = message?.text ?: return message?.message.orEmpty()
    return readerSharedText(text)
}

internal fun formatAndroidString(template: String, args: List<Any?>): String {
    if (args.isEmpty()) return template.replace("%%", "%")

    val percentPlaceholder = "\u0000PERCENT\u0000"
    var sequentialIndex = 0
    var formatted = template.replace("%%", percentPlaceholder)

    formatted = Regex("%(\\d+)\\$[-+#, .(]*\\d*(?:\\.\\d+)?[a-zA-Z]").replace(formatted) { match ->
        val argIndex = match.groupValues[1].toIntOrNull()?.minus(1)
        args.getOrNull(argIndex ?: -1).toAndroidStringArgument()
    }

    formatted = Regex("%[-+#, .(]*\\d*(?:\\.\\d+)?[a-zA-Z]").replace(formatted) {
        args.getOrNull(sequentialIndex++).toAndroidStringArgument()
    }

    return formatted.replace(percentPlaceholder, "%")
}

private fun Any?.toAndroidStringArgument(): String {
    return when (this) {
        null -> ""
        is Float -> trimTrailingZeroDecimal(toString())
        is Double -> trimTrailingZeroDecimal(toString())
        else -> toString()
    }
}

private fun trimTrailingZeroDecimal(value: String): String {
    return value.removeSuffix(".0")
}

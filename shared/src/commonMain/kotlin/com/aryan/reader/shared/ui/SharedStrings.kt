package com.aryan.reader.shared.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf

@Immutable
class SharedStringResolver(
    private val resolve: (name: String) -> String? = { null }
) {
    fun string(name: String, fallback: String, vararg args: Any?): String {
        val template = resolve(name).takeUnless { it.isNullOrBlank() } ?: fallback
        return formatAndroidString(template, args.toList())
    }
}

val LocalSharedStringResolver = staticCompositionLocalOf { SharedStringResolver() }

@Composable
fun readerString(name: String, fallback: String, vararg args: Any?): String {
    return LocalSharedStringResolver.current.string(name, fallback, *args)
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

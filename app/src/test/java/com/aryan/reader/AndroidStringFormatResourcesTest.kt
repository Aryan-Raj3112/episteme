package com.aryan.reader

import java.io.File
import java.util.Date
import java.util.IllegalFormatException
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidStringFormatResourcesTest {

    @Test
    fun `vietnamese strings cover translatable base resources`() {
        val resDirectory = findResDirectory()
        val baseNames = readResourceNames(
            stringsFile = File(resDirectory, "values/strings.xml"),
            includeNonTranslatable = false
        )
        val vietnameseNames = readResourceNames(File(resDirectory, "values-vi/strings.xml"))
        val missingNames = baseNames.filterNot { it in vietnameseNames }

        assertTrue(
            "Missing Vietnamese strings:\n${missingNames.joinToString(separator = "\n")}",
            missingNames.isEmpty()
        )
    }

    @Test
    fun `localized formatted strings use valid formatter syntax`() {
        val resDirectory = findResDirectory()
        val baseStrings = readStringResources(File(resDirectory, "values/strings.xml"))
        val formattedBaseStrings = baseStrings
            .mapValues { (_, value) -> value.formatArguments() }
            .filterValues { it.isNotEmpty() }

        val failures = resDirectory
            .listFiles()
            .orEmpty()
            .filter { it.isDirectory && it.name.startsWith("values") }
            .map { File(it, "strings.xml") }
            .filter { it.isFile }
            .flatMap { stringsFile ->
                val strings = readStringResources(stringsFile)
                formattedBaseStrings.mapNotNull { (name, arguments) ->
                    val value = strings[name] ?: return@mapNotNull null
                    val sampleArguments = arguments.toSampleArguments()
                    try {
                        String.format(Locale.ROOT, value, *sampleArguments)
                        null
                    } catch (exception: IllegalFormatException) {
                        "${stringsFile.invariantSeparatorsPath}:$name -> ${exception.javaClass.simpleName}: ${exception.message}"
                    }
                }
            }

        assertTrue(failures.joinToString(separator = "\n"), failures.isEmpty())
    }

    private fun findResDirectory(): File {
        return listOf(
            File("src/main/res"),
            File("app/src/main/res")
        ).first { it.isDirectory }
    }

    private fun readResourceNames(
        stringsFile: File,
        includeNonTranslatable: Boolean = true
    ): List<String> {
        val document = DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(stringsFile)
        val nodes = document.documentElement.childNodes

        return buildList {
            for (index in 0 until nodes.length) {
                val node = nodes.item(index)
                val attributes = node.attributes ?: continue
                val name = attributes.getNamedItem("name")?.nodeValue ?: continue
                val translatable = attributes.getNamedItem("translatable")?.nodeValue
                if (includeNonTranslatable || translatable != "false") {
                    add(name)
                }
            }
        }
    }

    private fun readStringResources(stringsFile: File): Map<String, String> {
        val document = DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(stringsFile)
        val nodes = document.getElementsByTagName("string")

        return buildMap {
            for (index in 0 until nodes.length) {
                val node = nodes.item(index)
                val name = node.attributes
                    ?.getNamedItem("name")
                    ?.nodeValue
                    ?: continue
                put(name, node.textContent)
            }
        }
    }

    private fun String.formatArguments(): List<FormatArgument> {
        val arguments = mutableListOf<FormatArgument>()
        var nextImplicitIndex = 0
        var previousIndex = -1

        for (match in formatterPattern.findAll(this)) {
            val conversion = (match.groups[4] ?: match.groups[5])?.value?.singleOrNull() ?: continue
            val dateTimePrefix = match.groups[3]?.value
            if (conversion == '%' || conversion == 'n') continue

            val index = when {
                match.groups[2] != null -> previousIndex
                match.groups[1] != null -> match.groups[1]!!.value.toInt() - 1
                else -> nextImplicitIndex++
            }
            if (index < 0) continue

            previousIndex = index
            val argument = FormatArgument(index, conversion.sampleKind(dateTimePrefix != null))
            val existingIndex = arguments.indexOfFirst { it.index == index }
            if (existingIndex >= 0) {
                arguments[existingIndex] = arguments[existingIndex].merge(argument)
            } else {
                arguments += argument
            }
        }

        return arguments
    }

    private fun Char.sampleKind(isDateTime: Boolean): SampleKind {
        if (isDateTime) return SampleKind.DateTime
        return when (lowercaseChar()) {
            'd', 'o', 'x' -> SampleKind.Integer
            'e', 'f', 'g', 'a' -> SampleKind.Decimal
            'c' -> SampleKind.Character
            'b' -> SampleKind.Boolean
            'h', 's' -> SampleKind.Text
            else -> SampleKind.Text
        }
    }

    private fun List<FormatArgument>.toSampleArguments(): Array<Any> {
        val maxIndex = maxOf { it.index }
        val samples = Array<Any>(maxIndex + 1) { "sample" }
        forEach { argument ->
            samples[argument.index] = argument.kind.sample
        }
        return samples
    }

    private data class FormatArgument(
        val index: Int,
        val kind: SampleKind
    ) {
        fun merge(other: FormatArgument): FormatArgument {
            return if (kind == other.kind) this else copy(kind = SampleKind.Text)
        }
    }

    private enum class SampleKind(val sample: Any) {
        Text("sample"),
        Integer(7),
        Decimal(1.5),
        Character('x'),
        Boolean(true),
        DateTime(Date(0L))
    }

    private companion object {
        private val formatterPattern = Regex(
            "%(?:([1-9]\\d*)\\$|(<))?[-#+ 0,(]*\\d*(?:\\.\\d+)?(?:(?:([tT])([a-zA-Z]))|([bBhHsScCdoxXeEfgGaA%n]))"
        )
    }
}

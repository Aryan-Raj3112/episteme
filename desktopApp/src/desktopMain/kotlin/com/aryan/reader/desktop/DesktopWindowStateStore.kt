package com.aryan.reader.desktop

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

private const val DesktopWindowStateSchemaVersion = 1

internal enum class DesktopSavedWindowPlacement {
    FLOATING,
    MAXIMIZED,
    FULLSCREEN
}

internal data class DesktopWindowStateSnapshot(
    val placement: DesktopSavedWindowPlacement,
    val widthDp: Float,
    val heightDp: Float,
    val xDp: Float? = null,
    val yDp: Float? = null
) {
    fun toWindowPlacement(): WindowPlacement {
        return when (placement) {
            DesktopSavedWindowPlacement.FLOATING -> WindowPlacement.Floating
            DesktopSavedWindowPlacement.MAXIMIZED -> WindowPlacement.Maximized
            DesktopSavedWindowPlacement.FULLSCREEN -> WindowPlacement.Fullscreen
        }
    }

    fun toWindowSize(defaultSize: DpSize): DpSize {
        val width = widthDp.takeIf { it.isFinite() && it >= EpistemeDesktopWindowMinimumWidthPx.toFloat() }
        val height = heightDp.takeIf { it.isFinite() && it >= EpistemeDesktopWindowMinimumHeightPx.toFloat() }
        return DpSize(
            width = width?.dp ?: defaultSize.width,
            height = height?.dp ?: defaultSize.height
        )
    }

    fun toWindowPosition(): WindowPosition {
        val x = xDp?.takeIf { it.isFinite() } ?: return WindowPosition.PlatformDefault
        val y = yDp?.takeIf { it.isFinite() } ?: return WindowPosition.PlatformDefault
        return WindowPosition(x.dp, y.dp)
    }

    fun sanitized(): DesktopWindowStateSnapshot {
        return copy(
            widthDp = widthDp.takeIf { it.isFinite() }?.coerceAtLeast(EpistemeDesktopWindowMinimumWidthPx.toFloat())
                ?: EpistemeDesktopWindowMinimumWidthPx.toFloat(),
            heightDp = heightDp.takeIf { it.isFinite() }?.coerceAtLeast(EpistemeDesktopWindowMinimumHeightPx.toFloat())
                ?: EpistemeDesktopWindowMinimumHeightPx.toFloat(),
            xDp = xDp?.takeIf { it.isFinite() },
            yDp = yDp?.takeIf { it.isFinite() }
        )
    }

    fun toJsonObject(): JsonObject {
        val sanitized = sanitized()
        return JsonObject(
            buildMap {
                put("schemaVersion", JsonPrimitive(DesktopWindowStateSchemaVersion))
                put("placement", JsonPrimitive(sanitized.placement.name))
                put("widthDp", JsonPrimitive(sanitized.widthDp))
                put("heightDp", JsonPrimitive(sanitized.heightDp))
                put("xDp", sanitized.xDp?.let { JsonPrimitive(it) } ?: JsonNull)
                put("yDp", sanitized.yDp?.let { JsonPrimitive(it) } ?: JsonNull)
            }
        )
    }

    companion object {
        fun default(): DesktopWindowStateSnapshot {
            return DesktopWindowStateSnapshot(
                placement = DesktopSavedWindowPlacement.MAXIMIZED,
                widthDp = 1280f,
                heightDp = 820f
            )
        }

        fun fromWindowState(state: WindowState): DesktopWindowStateSnapshot? {
            if (state.isMinimized) return null
            val size = state.size
            val width = size.width.value.takeIf { it.isFinite() } ?: return null
            val height = size.height.value.takeIf { it.isFinite() } ?: return null
            val placement = when (state.placement) {
                WindowPlacement.Floating -> DesktopSavedWindowPlacement.FLOATING
                WindowPlacement.Maximized -> DesktopSavedWindowPlacement.MAXIMIZED
                WindowPlacement.Fullscreen -> DesktopSavedWindowPlacement.FULLSCREEN
            }
            val position = state.position.takeIf { it.isSpecified }
            return DesktopWindowStateSnapshot(
                placement = placement,
                widthDp = width,
                heightDp = height,
                xDp = position?.x?.value?.takeIf { it.isFinite() },
                yDp = position?.y?.value?.takeIf { it.isFinite() }
            ).sanitized()
        }

        fun fromJsonElement(element: JsonElement): DesktopWindowStateSnapshot? {
            val obj = runCatching { element.jsonObject }.getOrNull() ?: return null
            val placement = obj["placement"]
                ?.jsonPrimitive
                ?.content
                ?.let { runCatching { DesktopSavedWindowPlacement.valueOf(it) }.getOrNull() }
                ?: DesktopSavedWindowPlacement.MAXIMIZED
            val width = obj["widthDp"]?.jsonPrimitive?.floatOrNull ?: return null
            val height = obj["heightDp"]?.jsonPrimitive?.floatOrNull ?: return null
            return DesktopWindowStateSnapshot(
                placement = placement,
                widthDp = width,
                heightDp = height,
                xDp = obj["xDp"]?.takeUnless { it is JsonNull }?.jsonPrimitive?.floatOrNull,
                yDp = obj["yDp"]?.takeUnless { it is JsonNull }?.jsonPrimitive?.floatOrNull
            ).sanitized()
        }
    }
}

internal class DesktopWindowStateStore(
    private val stateFile: File = defaultWindowStateFile()
) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    fun load(): DesktopWindowStateSnapshot? {
        if (!stateFile.exists()) return null
        return runCatching {
            DesktopWindowStateSnapshot.fromJsonElement(json.parseToJsonElement(stateFile.readText()))
        }.getOrNull()
    }

    fun save(snapshot: DesktopWindowStateSnapshot) {
        stateFile.parentFile?.mkdirs()
        stateFile.writeText(json.encodeToString(JsonElement.serializer(), snapshot.toJsonObject()))
    }

    companion object {
        fun defaultWindowStateFile(): File {
            return File(desktopUserConfigRoot(), "window_state.json")
        }

        fun defaultReaderWindowStateFile(): File {
            return File(desktopUserConfigRoot(), "reader_window_state.json")
        }
    }
}

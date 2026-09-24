package com.aryan.reader.shared.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aryan.reader.shared.ReaderTexture
import com.aryan.reader.shared.ReaderAutoScrollProfile
import com.aryan.reader.shared.ReaderMusicianHoldDurationMillis
import com.aryan.reader.shared.currentTimestamp
import com.aryan.reader.shared.readerAutoScrollPixelsPerSecond
import com.aryan.reader.shared.generated.resources.Res
import com.aryan.reader.shared.generated.resources.classy_fabric
import com.aryan.reader.shared.generated.resources.ep_naturalwhite
import com.aryan.reader.shared.generated.resources.grey_wash_wall
import com.aryan.reader.shared.generated.resources.light_veneer
import com.aryan.reader.shared.generated.resources.retina_wood
import com.aryan.reader.shared.generated.resources.retro_intro
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.imageResource

@Composable
internal fun SharedMobileEpubMusicianOverlay(
    onGesture: (isRightRegion: Boolean, isLongPress: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    Box(modifier) {
        listOf(false, true).forEach { isRightRegion ->
            var holdProgress by remember { mutableFloatStateOf(0f) }
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.25f)
                    .fillMaxHeight(0.4f)
                    .align(if (isRightRegion) Alignment.TopEnd else Alignment.TopStart)
                    .offset(y = 100.dp)
                    .padding(
                        start = if (isRightRegion) 0.dp else 8.dp,
                        end = if (isRightRegion) 8.dp else 0.dp,
                    )
                    .border(2.dp, Color.Gray.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                    .pointerInput(isRightRegion) {
                        awaitEachGesture {
                            awaitFirstDown()
                            var longPressTriggered = false
                            val holdJob = scope.launch {
                                val startedAt = currentTimestamp()
                                while (true) {
                                    val elapsed = currentTimestamp() - startedAt
                                    holdProgress = (elapsed.toFloat() / ReaderMusicianHoldDurationMillis).coerceIn(0f, 1f)
                                    if (elapsed >= ReaderMusicianHoldDurationMillis) {
                                        longPressTriggered = true
                                        holdProgress = 0f
                                        onGesture(isRightRegion, true)
                                        break
                                    }
                                    delay(16L)
                                }
                            }
                            val up = waitForUpOrCancellation()
                            holdJob.cancel()
                            holdProgress = 0f
                            if (!longPressTriggered && up != null) {
                                up.consume()
                                onGesture(isRightRegion, false)
                            }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (holdProgress > 0f) {
                    CircularProgressIndicator(
                        progress = { holdProgress },
                        modifier = Modifier.size(48.dp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        trackColor = Color.Transparent,
                        strokeWidth = 4.dp,
                    )
                    Icon(
                        if (isRightRegion) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                }
            }
        }
    }
}

@Composable
internal fun sharedMobileEpubTextureBitmap(textureId: String?): ImageBitmap? {
    val resource = when (textureId) {
        ReaderTexture.NATURAL_WHITE.id -> Res.drawable.ep_naturalwhite
        ReaderTexture.RETINA_WOOD.id -> Res.drawable.retina_wood
        ReaderTexture.LIGHT_VENEER.id -> Res.drawable.light_veneer
        ReaderTexture.GREY_WASH.id -> Res.drawable.grey_wash_wall
        ReaderTexture.CLASSY_FABRIC.id -> Res.drawable.classy_fabric
        ReaderTexture.RETRO_INTRO.id -> Res.drawable.retro_intro
        else -> null
    }
    return resource?.let { imageResource(it) }
}

internal fun sharedMobileEpubAutoScrollStartScript(speed: Float): String {
    val pixelsPerSecond = readerAutoScrollPixelsPerSecond(speed)
    // Android parity (epub_reader.js autoScroll loop): Android runs the pixel
    // accumulator off requestAnimationFrame, so the scroll moves in lockstep
    // with the display refresh. iOS used a 50ms setInterval, which can only
    // tick ~20 times a second and lands off-frame — that is what made the
    // EPUB auto-scroll visibly jitter. Drive the same accumulator from
    // requestAnimationFrame and scale it by the real frame delta, so the
    // speed stays $pixelsPerSecond px/s (readerAutoScrollPixelsPerSecond) on
    // 60Hz and 120Hz displays alike.
    return """
    (function () {
      if (window.readerIosAutoScrollFrame) window.cancelAnimationFrame(window.readerIosAutoScrollFrame);
      window.readerIosAutoScrollFrame = null;
      window.readerIosAutoScrollAccumulator = 0;
      window.readerIosAutoScrollLastFrame = 0;
      window.readerIosAutoScrollStep = function (timestamp) {
        if (!window.readerIosAutoScrollFrame) return;
        var previous = window.readerIosAutoScrollLastFrame || timestamp;
        var deltaSeconds = (timestamp - previous) / 1000;
        window.readerIosAutoScrollLastFrame = timestamp;
        window.readerIosAutoScrollFrame = window.requestAnimationFrame(window.readerIosAutoScrollStep);
        if (deltaSeconds <= 0 || deltaSeconds > 0.1) return;
        window.readerIosAutoScrollAccumulator += $pixelsPerSecond * deltaSeconds;
        var pixels = Math.floor(window.readerIosAutoScrollAccumulator);
        if (pixels >= 1) {
          window.readerIosAutoScrollAccumulator -= pixels;
          window.scrollBy(0, pixels);
        }
        var root = document.documentElement;
        if (window.scrollY + window.innerHeight >= root.scrollHeight - 2) {
          window.cancelAnimationFrame(window.readerIosAutoScrollFrame);
          window.readerIosAutoScrollFrame = null;
          if (window.kmpJsBridge && window.kmpJsBridge.callNative) {
            window.kmpJsBridge.callNative('readerAutoScrollChapterEnd', '{}');
          }
        }
      };
      window.readerIosAutoScrollFrame = window.requestAnimationFrame(window.readerIosAutoScrollStep);
    })();
""".trimIndent()
}

internal val SharedMobileEpubAutoScrollStopScript = """
    (function () {
      if (window.readerIosAutoScrollFrame) window.cancelAnimationFrame(window.readerIosAutoScrollFrame);
      window.readerIosAutoScrollFrame = null;
    })();
""".trimIndent()

internal fun sharedMobileEpubScrollToEndScript(chunkIndex: Int, chunkHtml: String?): String {
    val chunkInjection = when {
        chunkIndex < 0 || chunkHtml == null -> ""
        chunkHtml.length <= com.aryan.reader.shared.reader.ReaderHtmlDocumentBuilder.MaxInlineVirtualChunkChars ->
            "if (window.readerVirtualization) window.readerVirtualization.provideChunk($chunkIndex, ${JsonPrimitive(chunkHtml)});"
        else ->
            "if (window.readerVirtualization&&window.readerVirtualization.requestChunk) window.readerVirtualization.requestChunk($chunkIndex);"
    }
    return """
        (function () {
          $chunkInjection
          function scrollEnd() {
            var root = document.scrollingElement || document.documentElement;
            window.scrollTo(0, Math.max(0, root.scrollHeight - window.innerHeight));
          }
          scrollEnd();
          // Large tail chunks arrive via the bridge; re-scroll once loaded.
          window.setTimeout(scrollEnd, 500);
        })();
    """.trimIndent()
}

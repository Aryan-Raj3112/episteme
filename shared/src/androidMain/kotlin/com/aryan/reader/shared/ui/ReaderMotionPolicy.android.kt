package com.aryan.reader.shared.ui

import android.animation.ValueAnimator
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.aryan.reader.shared.ReaderMotionPolicy

@Composable
internal actual fun rememberPlatformReaderMotionPolicy(): ReaderMotionPolicy {
    val context = LocalContext.current
    var animatorScaleEnabled by remember(context) {
        mutableStateOf(ValueAnimator.areAnimatorsEnabled())
    }
    DisposableEffect(context) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                animatorScaleEnabled = ValueAnimator.areAnimatorsEnabled()
            }
        }
        context.contentResolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
            false,
            observer,
        )
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }
    return androidReaderMotionPolicy(animatorScaleEnabled)
}

/** Pure adapter decision kept separate so the platform signal is testable. */
internal fun androidReaderMotionPolicy(
    animatorScaleEnabled: Boolean,
): ReaderMotionPolicy = ReaderMotionPolicy(
    reduceMotion = !animatorScaleEnabled,
)

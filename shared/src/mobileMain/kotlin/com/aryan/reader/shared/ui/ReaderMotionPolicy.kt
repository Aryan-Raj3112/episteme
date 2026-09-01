package com.aryan.reader.shared.ui

import androidx.compose.runtime.Composable
import com.aryan.reader.shared.ReaderMotionPolicy

/** Reads the current platform accessibility/animation setting for the reader. */
@Composable
fun rememberReaderMotionPolicy(): ReaderMotionPolicy = rememberPlatformReaderMotionPolicy()

@Composable
internal expect fun rememberPlatformReaderMotionPolicy(): ReaderMotionPolicy

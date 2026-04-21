/*
 * Episteme Reader - A native Android document reader.
 * Copyright (C) 2026 Episteme
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 * mail: epistemereader@gmail.com
 */
package com.aryan.reader.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import com.materialkolor.PaletteStyle
import androidx.compose.ui.platform.LocalContext
import com.materialkolor.dynamicColorScheme

private val lightScheme = lightColorScheme(
    primary = primaryLight,
    onPrimary = onPrimaryLight,
    primaryContainer = primaryContainerLight,
    onPrimaryContainer = onPrimaryContainerLight,
    secondary = secondaryLight,
    onSecondary = onSecondaryLight,
    secondaryContainer = secondaryContainerLight,
    onSecondaryContainer = onSecondaryContainerLight,
    tertiary = tertiaryLight,
    onTertiary = onTertiaryLight,
    tertiaryContainer = tertiaryContainerLight,
    onTertiaryContainer = onTertiaryContainerLight,
    error = errorLight,
    onError = onErrorLight,
    errorContainer = errorContainerLight,
    onErrorContainer = onErrorContainerLight,
    background = backgroundLight,
    onBackground = onBackgroundLight,
    surface = surfaceLight,
    onSurface = onSurfaceLight,
    surfaceVariant = surfaceVariantLight,
    onSurfaceVariant = onSurfaceVariantLight,
    outline = outlineLight,
    outlineVariant = outlineVariantLight,
    scrim = scrimLight,
    inverseSurface = inverseSurfaceLight,
    inverseOnSurface = inverseOnSurfaceLight,
    inversePrimary = inversePrimaryLight,
    surfaceDim = surfaceDimLight,
    surfaceBright = surfaceBrightLight,
    surfaceContainerLowest = surfaceContainerLowestLight,
    surfaceContainerLow = surfaceContainerLowLight,
    surfaceContainer = surfaceContainerLight,
    surfaceContainerHigh = surfaceContainerHighLight,
    surfaceContainerHighest = surfaceContainerHighestLight,
)

private val darkScheme = darkColorScheme(
    primary = primaryDark,
    onPrimary = onPrimaryDark,
    primaryContainer = primaryContainerDark,
    onPrimaryContainer = onPrimaryContainerDark,
    secondary = secondaryDark,
    onSecondary = onSecondaryDark,
    secondaryContainer = secondaryContainerDark,
    onSecondaryContainer = onSecondaryContainerDark,
    tertiary = tertiaryDark,
    onTertiary = onTertiaryDark,
    tertiaryContainer = tertiaryContainerDark,
    onTertiaryContainer = onTertiaryContainerDark,
    error = errorDark,
    onError = onErrorDark,
    errorContainer = errorContainerDark,
    onErrorContainer = onErrorContainerDark,
    background = backgroundDark,
    onBackground = onBackgroundDark,
    surface = surfaceDark,
    onSurface = onSurfaceDark,
    surfaceVariant = surfaceVariantDark,
    onSurfaceVariant = onSurfaceVariantDark,
    outline = outlineDark,
    outlineVariant = outlineVariantDark,
    scrim = scrimDark,
    inverseSurface = inverseSurfaceDark,
    inverseOnSurface = inverseOnSurfaceDark,
    inversePrimary = inversePrimaryDark,
    surfaceDim = surfaceDimDark,
    surfaceBright = surfaceBrightDark,
    surfaceContainerLowest = surfaceContainerLowestDark,
    surfaceContainerLow = surfaceContainerLowDark,
    surfaceContainer = surfaceContainerDark,
    surfaceContainerHigh = surfaceContainerHighDark,
    surfaceContainerHighest = surfaceContainerHighestDark,
)

@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    seedColor: Color? = null,
    contrastLevel: Double = 0.0,
    textDimFactor: Float = 1.0f,
    content: @Composable () -> Unit
) {
    val supportsDynamicColor = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val context = LocalContext.current

    val baseColorScheme = remember(
        darkTheme, dynamicColor, supportsDynamicColor, seedColor, contrastLevel
    ) {
        when {
            seedColor != null -> dynamicColorScheme(
                seedColor = seedColor,
                isDark = darkTheme,
                contrastLevel = contrastLevel,
                style = PaletteStyle.Fidelity
            )
            dynamicColor && supportsDynamicColor -> {
                if (darkTheme) dynamicDarkColorScheme(context)
                else dynamicLightColorScheme(context)
            }
            darkTheme -> darkScheme
            else -> lightScheme
        }
    }

    val finalColorScheme = remember(baseColorScheme, textDimFactor) {
        if (textDimFactor >= 1.0f) {
            baseColorScheme
        } else {
            baseColorScheme.copy(
                primary = baseColorScheme.primary.copy(alpha = textDimFactor),
                secondary = baseColorScheme.secondary.copy(alpha = textDimFactor),
                tertiary = baseColorScheme.tertiary.copy(alpha = textDimFactor),
                error = baseColorScheme.error.copy(alpha = textDimFactor),
                primaryContainer = baseColorScheme.primaryContainer.copy(alpha = textDimFactor),
                secondaryContainer = baseColorScheme.secondaryContainer.copy(alpha = textDimFactor),
                tertiaryContainer = baseColorScheme.tertiaryContainer.copy(alpha = textDimFactor),
                errorContainer = baseColorScheme.errorContainer.copy(alpha = textDimFactor),
                outline = baseColorScheme.outline.copy(alpha = textDimFactor),
                outlineVariant = baseColorScheme.outlineVariant.copy(alpha = textDimFactor),
                inversePrimary = baseColorScheme.inversePrimary.copy(alpha = textDimFactor),
                inverseOnSurface = baseColorScheme.inverseOnSurface.copy(alpha = textDimFactor),
                onPrimary = baseColorScheme.onPrimary.copy(alpha = textDimFactor),
                onSecondary = baseColorScheme.onSecondary.copy(alpha = textDimFactor),
                onTertiary = baseColorScheme.onTertiary.copy(alpha = textDimFactor),
                onBackground = baseColorScheme.onBackground.copy(alpha = textDimFactor),
                onSurface = baseColorScheme.onSurface.copy(alpha = textDimFactor),
                onSurfaceVariant = baseColorScheme.onSurfaceVariant.copy(alpha = textDimFactor),
                onError = baseColorScheme.onError.copy(alpha = textDimFactor),
                onPrimaryContainer = baseColorScheme.onPrimaryContainer.copy(alpha = textDimFactor),
                onSecondaryContainer = baseColorScheme.onSecondaryContainer.copy(alpha = textDimFactor),
                onTertiaryContainer = baseColorScheme.onTertiaryContainer.copy(alpha = textDimFactor),
                onErrorContainer = baseColorScheme.onErrorContainer.copy(alpha = textDimFactor),
            )
        }
    }

    MaterialTheme(
        colorScheme = finalColorScheme,
        typography = AppTypography,
        content = content
    )
}
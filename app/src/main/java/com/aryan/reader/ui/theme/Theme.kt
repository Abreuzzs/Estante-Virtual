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

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Resources
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import com.materialkolor.PaletteStyle
import androidx.compose.ui.platform.LocalContext
import com.aryan.reader.shared.ui.withAppFontFamily
import com.materialkolor.dynamicColorScheme
import timber.log.Timber

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

/**
 * The one and only Episteme look: deep green with warm beige, in the spirit of a library.
 *
 * Theme choice (light/dark, dynamic colours, custom seeds, contrast) was removed on purpose: the app is
 * for reading, and a single, carefully balanced palette keeps every screen consistent. The old
 * parameters are still accepted so existing callers keep compiling, but they no longer change anything.
 */
private val EpistemeScheme = lightColorScheme(
    primary = Color(0xFF2F6B55),
    onPrimary = Color(0xFFFFFBF0),
    primaryContainer = Color(0xFFD5E7DB),
    onPrimaryContainer = Color(0xFF0E3326),
    secondary = Color(0xFF7B6A4A),
    onSecondary = Color(0xFFFFFBF0),
    // Sage: selected chips, drawer items and tonal buttons pick up the green identity.
    secondaryContainer = Color(0xFFD5E7DB),
    onSecondaryContainer = Color(0xFF0E3326),
    tertiary = Color(0xFFA9823F),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF2E3BC),
    onTertiaryContainer = Color(0xFF3A2A06),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    background = Color(0xFFF1EADA),
    onBackground = Color(0xFF22201A),
    surface = Color(0xFFF1EADA),
    onSurface = Color(0xFF22201A),
    surfaceVariant = Color(0xFFE3DAC6),
    onSurfaceVariant = Color(0xFF565041),
    outline = Color(0xFF857D6A),
    outlineVariant = Color(0xFFCFC6AF),
    scrim = Color(0xFF000000),
    inverseSurface = Color(0xFF2E2B24),
    inverseOnSurface = Color(0xFFF4EEDF),
    inversePrimary = Color(0xFF9ED2B7),
    surfaceDim = Color(0xFFD8D0BC),
    surfaceBright = Color(0xFFF7F2E5),
    surfaceContainerLowest = Color(0xFFFBF8EF),
    surfaceContainerLow = Color(0xFFEDE5D3),
    surfaceContainer = Color(0xFFE8DFCB),
    surfaceContainerHigh = Color(0xFFE2D9C4),
    surfaceContainerHighest = Color(0xFFDCD2BC),
)

private val EpistemeShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(30.dp),
)

@Suppress("UNUSED_PARAMETER")
@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    seedColor: Color? = null,
    contrastLevel: Double = 0.0,
    textDimFactor: Float = 1.0f,
    appFontFamily: FontFamily? = null,
    content: @Composable () -> Unit
) {
    val finalColorScheme = remember(textDimFactor) {
        if (textDimFactor >= 1.0f) {
            EpistemeScheme
        } else {
            EpistemeScheme.copy(
                onBackground = EpistemeScheme.onBackground.copy(alpha = textDimFactor),
                onSurface = EpistemeScheme.onSurface.copy(alpha = textDimFactor),
                onSurfaceVariant = EpistemeScheme.onSurfaceVariant.copy(alpha = textDimFactor),
            )
        }
    }

    // Light surfaces everywhere, so system bar icons are always dark.
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = view.context.findThemeActivity()?.window
            if (window != null) {
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = true
                controller.isAppearanceLightNavigationBars = true
            }
        }
    }

    MaterialTheme(
        colorScheme = finalColorScheme,
        typography = appFontFamily?.let { AppTypography.withAppFontFamily(it) } ?: AppTypography,
        shapes = EpistemeShapes,
        content = content
    )
}

private tailrec fun Context.findThemeActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findThemeActivity()
    else -> null
}

/**
 * Resolves a framework dynamic color scheme, falling back to the static
 * scheme when the device cannot provide it. The S+ SDK check can pass on
 * broken firmware whose framework still lacks the dynamic-color resources
 * (same class of issue as the `SafeWorkManager` JobScheduler guard), which
 * otherwise crashes every launch in `MainActivity.onCreate`. Only `Exception`
 * is caught — `Error`s (e.g. OOM) still propagate.
 */
internal fun resolveDynamicScheme(
    dynamic: () -> ColorScheme,
    static: ColorScheme,
): ColorScheme {
    return try {
        dynamic()
    } catch (error: Exception) {
        if (error is Resources.NotFoundException) {
            Timber.w(error, "Framework dynamic colors missing; using static scheme.")
        } else {
            Timber.w(error, "Dynamic color resolution failed; using static scheme.")
        }
        static
    }
}

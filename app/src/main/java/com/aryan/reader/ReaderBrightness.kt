package com.aryan.reader

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import com.aryan.reader.shared.ui.ReaderMinimalSlider
import com.aryan.reader.shared.ui.SharedReaderBrightnessLabels
import com.aryan.reader.shared.ui.SharedReaderBrightnessSheet
import kotlin.math.roundToInt

private const val READER_PREFS_NAME = "reader_prefs"
private const val PREF_READER_BRIGHTNESS_USE_SYSTEM = "reader_brightness_use_system"
private const val PREF_READER_BRIGHTNESS_VALUE = "reader_brightness_value"
private const val PREF_READER_WARM_FILTER = "reader_eye_warm_filter"
private const val PREF_READER_EXTRA_DIM = "reader_eye_extra_dim"
typealias ReaderBrightnessSettings = com.aryan.reader.shared.ReaderBrightnessSettings

internal fun normalizeReaderBrightness(brightness: Float): Float {
    return com.aryan.reader.shared.normalizeReaderBrightness(brightness)
}

internal fun stepReaderBrightness(brightness: Float, percentDelta: Int): Float {
    return com.aryan.reader.shared.stepReaderBrightness(brightness, percentDelta)
}

fun loadReaderBrightnessSettings(context: Context): ReaderBrightnessSettings {
    val prefs = context.getSharedPreferences(READER_PREFS_NAME, Context.MODE_PRIVATE)
    return ReaderBrightnessSettings(
        useSystemBrightness = prefs.getBoolean(PREF_READER_BRIGHTNESS_USE_SYSTEM, true),
        customBrightness = prefs.getFloat(PREF_READER_BRIGHTNESS_VALUE, com.aryan.reader.shared.DefaultReaderCustomBrightness)
            .let(::normalizeReaderBrightness),
        warmFilter = prefs.getFloat(PREF_READER_WARM_FILTER, 0f).coerceIn(0f, 1f),
        extraDim = prefs.getFloat(PREF_READER_EXTRA_DIM, 0f).coerceIn(0f, 1f)
    )
}

fun saveReaderBrightnessSettings(context: Context, settings: ReaderBrightnessSettings) {
    context.getSharedPreferences(READER_PREFS_NAME, Context.MODE_PRIVATE).edit {
        putBoolean(PREF_READER_BRIGHTNESS_USE_SYSTEM, settings.useSystemBrightness)
        putFloat(PREF_READER_BRIGHTNESS_VALUE, settings.safeCustomBrightness)
        putFloat(PREF_READER_WARM_FILTER, settings.safeWarmFilter)
        putFloat(PREF_READER_EXTRA_DIM, settings.safeExtraDim)
    }
}

@Composable
fun ReaderBrightnessEffect(
    window: Window?,
    settings: ReaderBrightnessSettings
) {
    DisposableEffect(window) {
        val originalBrightness = window?.attributes?.screenBrightness
            ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        onDispose {
            window?.setReaderBrightness(originalBrightness)
        }
    }

    LaunchedEffect(window, settings) {
        val brightness = if (settings.useSystemBrightness) {
            WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        } else {
            settings.safeCustomBrightness
        }
        window?.setReaderBrightness(brightness)
    }

    // Eye comfort: two plain, non-interactive views over the whole window (a warm tint and a dim
    // layer). They are removed as soon as the reader leaves composition.
    val overlays = remember(window) { window?.let(::EyeComfortOverlays) }
    DisposableEffect(overlays) {
        overlays?.attach()
        onDispose { overlays?.detach() }
    }
    LaunchedEffect(overlays, settings.warmFilter, settings.extraDim) {
        overlays?.render(settings)
    }
}

/** Warm tint + dim layers added to a window's decor view; they never take touches or focus. */
private class EyeComfortOverlays(window: Window) {
    private val decor = window.decorView as? ViewGroup
    private val warm = overlayView(window)
    private val dim = overlayView(window)

    private fun overlayView(window: Window): View = View(window.context).apply {
        isClickable = false
        isFocusable = false
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        visibility = View.GONE
    }

    fun attach() {
        val parent = decor ?: return
        val params = { ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT) }
        if (warm.parent == null) parent.addView(warm, params())
        if (dim.parent == null) parent.addView(dim, params())
    }

    fun detach() {
        (warm.parent as? ViewGroup)?.removeView(warm)
        (dim.parent as? ViewGroup)?.removeView(dim)
    }

    fun render(settings: ReaderBrightnessSettings) {
        warm.setOverlay(settings.warmOverlayAlpha, WARM_TINT_RGB)
        dim.setOverlay(settings.dimOverlayAlpha, 0x000000)
    }

    private fun View.setOverlay(alpha: Float, rgb: Int) {
        if (alpha <= 0f) {
            visibility = View.GONE
        } else {
            setBackgroundColor(((alpha * 255f).roundToInt().coerceIn(0, 255) shl 24) or rgb)
            visibility = View.VISIBLE
        }
    }

    private companion object {
        const val WARM_TINT_RGB = 0xFF8A1F
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderBrightnessSheet(
    settings: ReaderBrightnessSettings,
    onSettingsChange: (ReaderBrightnessSettings) -> Unit,
    onDismiss: () -> Unit
) {
    SharedReaderBrightnessSheet(
        settings = settings,
        onSettingsChange = onSettingsChange,
        labels = SharedReaderBrightnessLabels(
            title = stringResource(R.string.reader_brightness_title),
            done = stringResource(R.string.action_done),
            system = stringResource(R.string.reader_brightness_system),
            systemDescription = stringResource(R.string.reader_brightness_system_desc),
            custom = stringResource(R.string.reader_brightness_custom),
            customDescription = stringResource(R.string.reader_brightness_custom_desc),
            percent = stringResource(R.string.reader_brightness_percent, (settings.safeCustomBrightness * 100f).roundToInt()),
            decrease = stringResource(R.string.content_desc_decrease),
            increase = stringResource(R.string.content_desc_increase),
            warmFilter = stringResource(R.string.reader_eye_warm_title),
            warmFilterDescription = stringResource(R.string.reader_eye_warm_desc),
            extraDim = stringResource(R.string.reader_eye_dim_title),
            extraDimDescription = stringResource(R.string.reader_eye_dim_desc)
        ),
        onDismiss = onDismiss
    )
}

private fun Window.setReaderBrightness(brightness: Float) {
    attributes = attributes.apply {
        screenBrightness = brightness
    }
}

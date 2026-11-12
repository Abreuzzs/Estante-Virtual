package com.aryan.reader.shared

import kotlin.math.roundToInt

const val DefaultReaderCustomBrightness = 0.75f
const val MinimumReaderCustomBrightness = 0.01f

/** Strongest tint / dim a reader overlay may apply (at slider = 100%), so text always stays legible. */
const val MaxEyeComfortWarmAlpha = 0.45f
const val MaxEyeComfortDimAlpha = 0.75f

data class ReaderBrightnessSettings(
    val useSystemBrightness: Boolean = true,
    val customBrightness: Float = DefaultReaderCustomBrightness,
    /** 0..1: amber tint that cuts blue light. */
    val warmFilter: Float = 0f,
    /** 0..1: black overlay that dims below the lowest hardware brightness. */
    val extraDim: Float = 0f
) {
    val safeCustomBrightness: Float
        get() = normalizeReaderBrightness(customBrightness)

    val safeWarmFilter: Float
        get() = warmFilter.coerceIn(0f, 1f)

    val safeExtraDim: Float
        get() = extraDim.coerceIn(0f, 1f)

    val warmOverlayAlpha: Float
        get() = safeWarmFilter * MaxEyeComfortWarmAlpha

    val dimOverlayAlpha: Float
        get() = safeExtraDim * MaxEyeComfortDimAlpha

    val hasEyeComfortOverlay: Boolean
        get() = safeWarmFilter > 0f || safeExtraDim > 0f
}

fun normalizeReaderBrightness(brightness: Float): Float {
    return (brightness * 100f).roundToInt().coerceIn(1, 100) / 100f
}

fun stepReaderBrightness(brightness: Float, percentDelta: Int): Float {
    val currentPercent = (normalizeReaderBrightness(brightness) * 100f).roundToInt()
    return (currentPercent + percentDelta).coerceIn(1, 100) / 100f
}

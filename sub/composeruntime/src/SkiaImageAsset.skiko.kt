/*
 * Copyright 2026 copi143
 *
 * Pure-JVM replacement for the skiko-backed image asset
 * (internal actual fun ActualImageBitmap()). Pixels are stored as a
 * plain ARGB IntArray; [jvmArgb] / [jvmGeneration] / [setArgbPixels]
 * are framework extensions used by the Minecraft canvas bridge
 * (texture upload) and the resource image loader.
 */

@file:JvmName("SkiaImageAsset_skikoKt")

package androidx.compose.ui.graphics

import androidx.compose.ui.graphics.colorspace.ColorSpace

internal fun ActualImageBitmap(
    width: Int,
    height: Int,
    config: ImageBitmapConfig,
    hasAlpha: Boolean,
    colorSpace: ColorSpace,
): ImageBitmap {
    require(width > 0 && height > 0) { "width and height must be > 0" }
    return SkiaBackedImageBitmap(width, height, config, hasAlpha, colorSpace, IntArray(width * height))
}

internal class SkiaBackedImageBitmap(
    private val bitmapWidth: Int,
    private val bitmapHeight: Int,
    private val bitmapConfig: ImageBitmapConfig,
    private val bitmapHasAlpha: Boolean,
    private val bitmapColorSpace: ColorSpace,
    internal val pixels: IntArray,
) : ImageBitmap {

    override val colorSpace: ColorSpace get() = bitmapColorSpace
    override val config: ImageBitmapConfig get() = bitmapConfig
    override val hasAlpha: Boolean get() = bitmapHasAlpha
    override val height: Int get() = bitmapHeight
    override val width: Int get() = bitmapWidth

    /** Bumped every time the pixel content changes; texture caches key on it. */
    @Volatile
    var generation: Int = 0
        private set

    override fun readPixels(
        buffer: IntArray,
        startX: Int,
        startY: Int,
        width: Int,
        height: Int,
        bufferOffset: Int,
        stride: Int,
    ) {
        require(startX >= 0 && startY >= 0 && width >= 0 && height >= 0)
        require(startX + width <= bitmapWidth && startY + height <= bitmapHeight)
        var dst = bufferOffset
        for (row in 0 until height) {
            val src = (startY + row) * bitmapWidth + startX
            pixels.copyInto(buffer, dst, src, src + width)
            dst += stride
        }
    }

    override fun prepareToDraw() = Unit

    internal fun replacePixels(next: IntArray) {
        require(next.size == pixels.size) { "pixel buffer size mismatch" }
        next.copyInto(pixels)
        generation++
    }
}

/** Live ARGB pixel array (no copy); mutated only through [setArgbPixels]. */
fun ImageBitmap.jvmArgb(): IntArray {
    require(this is SkiaBackedImageBitmap) {
        "Pixel access is only supported for SkiaBackedImageBitmap instances but received ${this::class}"
    }
    return pixels
}

/** Content generation counter; texture caches re-upload when it changes. */
fun ImageBitmap.jvmGeneration(): Int {
    require(this is SkiaBackedImageBitmap) {
        "Pixel access is only supported for SkiaBackedImageBitmap instances but received ${this::class}"
    }
    return generation
}

/** Replaces the whole pixel content (must match width * height); bumps the generation. */
fun ImageBitmap.setArgbPixels(pixels: IntArray) {
    require(this is SkiaBackedImageBitmap) {
        "Pixel access is only supported for SkiaBackedImageBitmap instances but received ${this::class}"
    }
    replacePixels(pixels)
}

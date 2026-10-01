/*
 * Copyright 2026 copi143
 *
 * Pure-JVM replacement for the skiko-backed encoded-image decoder
 * (internal actual fun createImageBitmap()). Decodes PNG/JPEG/GIF/BMP
 * through javax.imageio, so ByteArray.decodeToImageBitmap() works
 * without skiko natives.
 */

@file:JvmName("ImageBitmap_skikoKt")

package androidx.compose.ui.graphics

import androidx.compose.ui.graphics.colorspace.ColorSpaces
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO

internal fun createImageBitmap(bytes: ByteArray): ImageBitmap {
    val image = ByteArrayInputStream(bytes).use { ImageIO.read(it) }
        ?: error("Unable to decode image bytes (${bytes.size} bytes)")
    val width = image.width
    val height = image.height
    val pixels = IntArray(width * height)
    image.getRGB(0, 0, width, height, pixels, 0, width)
    val bitmap = ActualImageBitmap(width, height, ImageBitmapConfig.Argb8888, true, ColorSpaces.Srgb)
    bitmap.setArgbPixels(pixels)
    return bitmap
}

package minecraftx.compose.material

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.setArgbPixels
import androidx.compose.ui.layout.Layout
import com.mojang.blaze3d.platform.NativeImage
import net.minecraft.client.Minecraft
import net.minecraft.resources.ResourceLocation

/**
 * Loads a client resource PNG into a framework [ImageBitmap] (pure-JVM pixels,
 * drawn by [McCanvas] through a cached GL texture). Returns null when the
 * resource is missing or undecodable.
 */
fun ResourceLocation.toImageBitmap(): ImageBitmap? {
    val resource = Minecraft.getInstance().resourceManager.getResource(this).orElse(null) ?: return null
    return resource.open().use { stream ->
        val native = NativeImage.read(stream)
        try {
            val width = native.width
            val height = native.height
            val pixels = IntArray(width * height)
            for (y in 0 until height) {
                for (x in 0 until width) {
                    pixels[y * width + x] = abgrToArgb(native.getPixelRGBA(x, y))
                }
            }
            ImageBitmap(width, height).also { it.setArgbPixels(pixels) }
        } finally {
            native.close()
        }
    }
}

/** NativeImage stores pixels as ABGR-packed ints; framework bitmaps use ARGB. */
internal fun abgrToArgb(abgr: Int): Int {
    val a = (abgr ushr 24) and 0xFF
    val b = (abgr ushr 16) and 0xFF
    val g = (abgr ushr 8) and 0xFF
    val r = abgr and 0xFF
    return (a shl 24) or (r shl 16) or (g shl 8) or b
}

/**
 * Draws a framework [ImageBitmap] at its intrinsic pixel size. Nothing is
 * drawn for a null bitmap.
 */
@Composable
fun McImage(bitmap: ImageBitmap?, modifier: Modifier = Modifier, alpha: Float = 1f) {
    if (bitmap == null) return
    val paint = remember(alpha) { Paint().apply { this.alpha = alpha } }
    Layout(
        modifier = modifier.drawBehind {
            drawIntoCanvas { canvas -> canvas.drawImage(bitmap, Offset.Zero, paint) }
        },
    ) { _, _ ->
        layout(bitmap.width, bitmap.height) {}
    }
}

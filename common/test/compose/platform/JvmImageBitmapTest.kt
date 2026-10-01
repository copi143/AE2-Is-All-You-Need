package allyouneed.compose.platform

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import androidx.compose.ui.graphics.decodeToImageBitmap
import androidx.compose.ui.graphics.jvmArgb
import androidx.compose.ui.graphics.jvmGeneration
import androidx.compose.ui.graphics.setArgbPixels
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

class JvmImageBitmapTest {

    @Test
    fun `pixels round-trip through readPixels`() {
        val bitmap = ImageBitmap(2, 2, ImageBitmapConfig.Argb8888, true, ColorSpaces.Srgb)
        val pixels = intArrayOf(0xFFFF0000.toInt(), 0xFF00FF00.toInt(), 0xFF0000FF.toInt(), 0xFFFFFFFF.toInt())
        bitmap.setArgbPixels(pixels)
        val out = IntArray(4)
        bitmap.readPixels(out, 0, 0, 2, 2, 0, 2)
        assertArrayEquals(pixels, out)
    }

    @Test
    fun `generation bumps on write`() {
        val bitmap = ImageBitmap(1, 1)
        val before = bitmap.jvmGeneration()
        bitmap.setArgbPixels(intArrayOf(0xFF123456.toInt()))
        assertEquals(before + 1, bitmap.jvmGeneration())
        assertEquals(0xFF123456.toInt(), bitmap.jvmArgb()[0])
    }

    @Test
    fun `png bytes decode to image bitmap`() {
        val image = java.awt.image.BufferedImage(2, 2, java.awt.image.BufferedImage.TYPE_INT_ARGB)
        image.setRGB(0, 0, 0xFFFF0000.toInt())
        image.setRGB(1, 1, 0xFF00FF00.toInt())
        val bytes = ByteArrayOutputStream().use {
            ImageIO.write(image, "png", it)
            it.toByteArray()
        }
        val bitmap = bytes.decodeToImageBitmap()
        assertEquals(2, bitmap.width)
        assertEquals(2, bitmap.height)
        val out = IntArray(4)
        bitmap.readPixels(out, 0, 0, 2, 2, 0, 2)
        assertEquals(0xFFFF0000.toInt(), out[0])
        assertEquals(0xFF00FF00.toInt(), out[3])
    }
}

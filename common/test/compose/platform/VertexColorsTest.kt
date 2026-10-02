package allyouneed.compose.platform

import allyouneed.client.compose.platform.putRgba
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class VertexColorsTest {
    @Test
    fun `theme blue uploads as RGBA rather than native endian ARGB`() {
        for (order in listOf(ByteOrder.LITTLE_ENDIAN, ByteOrder.BIG_ENDIAN)) {
            val buffer = ByteBuffer.allocate(4).order(order)
            buffer.putRgba(0xFF6BA3D4.toInt())
            assertArrayEquals(byteArrayOf(0x6B, 0xA3.toByte(), 0xD4.toByte(), 0xFF.toByte()), buffer.array())
        }
    }

    @Test
    fun `triangle and SDF attribute layouts preserve primary colors and alpha`() {
        val colors = listOf(0xFFFF0000.toInt(), 0xFF00FF00.toInt(), 0xFF0000FF.toInt(), 0x806BA3D4.toInt(), 0x006BA3D4)
        val rgba = listOf(
            byteArrayOf(-1, 0, 0, -1), byteArrayOf(0, -1, 0, -1), byteArrayOf(0, 0, -1, -1),
            byteArrayOf(0x6B, 0xA3.toByte(), 0xD4.toByte(), 0x80.toByte()),
            byteArrayOf(0x6B, 0xA3.toByte(), 0xD4.toByte(), 0),
        )
        for (floatCount in listOf(2, 4)) {
            val stride = floatCount * 4 + 4
            val buffer = ByteBuffer.allocate(colors.size * stride).order(ByteOrder.nativeOrder())
            for (color in colors) {
                repeat(floatCount) { buffer.putFloat(it + 0.25f) }
                buffer.putRgba(color)
            }
            assertEquals(colors.size * stride, buffer.position())
            buffer.flip()
            for (expected in rgba) {
                repeat(floatCount) { assertEquals(it + 0.25f, buffer.float) }
                assertArrayEquals(expected, ByteArray(4).also { buffer.get(it) })
            }
        }
    }
}

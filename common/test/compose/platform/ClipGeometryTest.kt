@file:Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")

package allyouneed.compose.platform

import allyouneed.client.compose.platform.ClipGeometry
import allyouneed.client.compose.platform.McCanvas
import allyouneed.client.compose.platform.McDrawRecorder
import allyouneed.client.compose.platform.McGraphics
import allyouneed.client.compose.platform.McScissor
import allyouneed.client.compose.platform.PassthroughLayer
import allyouneed.compose.spike.RecordingCanvas
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.ReusableGraphicsLayerScope
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.IntOffset
import org.joml.Matrix4f
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ClipGeometryTest {
    @Test
    fun `all corners are transformed and negative fractional bounds round outward`() {
        val matrix = Matrix4f().translate(-0.25f, 0.25f, 0f).scale(-2f, 3f, 1f)
        assertTrue(ClipGeometry.axisAligned(matrix))
        assertArrayEquals(intArrayOf(-5, 0, 0, 10), ClipGeometry.bounds(ClipGeometry.rectangle(Rect(0f, 0f, 2f, 3f), matrix)))
        val rotation = Matrix4f().rotateZ((Math.PI / 4).toFloat())
        assertFalse(ClipGeometry.axisAligned(rotation))
        val points = ClipGeometry.rectangle(Rect(0f, 0f, 10f, 10f), rotation)
        assertEquals(-7.071f, points[3].x, 0.001f)
        assertEquals(14.142f, points[2].y, 0.001f)
    }

    @Test
    fun `hit testing respects holes and winding rules`() {
        val outer = ClipGeometry.rectangle(Rect(0f, 0f, 20f, 20f), Matrix4f())
        val hole = ClipGeometry.rectangle(Rect(5f, 5f, 15f, 15f), Matrix4f())
        assertFalse(ClipGeometry.contains(listOf(outer, hole), Offset(10f, 10f), PathFillType.EvenOdd))
        assertTrue(ClipGeometry.contains(listOf(outer, hole), Offset(10f, 10f), PathFillType.NonZero))
        assertFalse(ClipGeometry.contains(listOf(outer, hole.reversed()), Offset(10f, 10f), PathFillType.NonZero))
    }

    @Test
    fun `canvas restore removes only clips created after save while recording without GL`() {
        val recorder = McDrawRecorder()
        McGraphics.activeRecorder = recorder
        McScissor.reset()
        try {
            val canvas = McCanvas(null, recorder)
            canvas.save()
            canvas.clipRect(0f, 0f, 40f, 40f, ClipOp.Intersect)
            assertEquals(1, McScissor.depth)
            canvas.save()
            canvas.rotate(35f)
            val path = Path().apply { addOval(Rect(0f, 0f, 10f, 10f)) }
            canvas.clipPath(path, ClipOp.Difference)
            assertEquals(2, McScissor.depth)
            canvas.restore()
            assertEquals(1, McScissor.depth)
            canvas.restore()
            assertEquals(0, McScissor.depth)
            assertEquals(9, recorder.ops.size)
        } finally {
            McGraphics.activeRecorder = null
            McScissor.reset()
        }
    }

    @Test
    fun `graphics layer clips rounded shape and restores canvas when drawing throws`() {
        val layer = PassthroughLayer({ _, _ -> error("drawing failed") }, {})
        layer.resize(IntSize(20, 20))
        layer.move(IntOffset(100, 200))
        layer.updateLayerProperties(ReusableGraphicsLayerScope().apply {
            clip = true
            shape = RoundedCornerShape(8f)
            rotationZ = 30f
            scaleX = 2f
            translationX = 5f
            translationY = 2f
        })
        assertFalse(layer.isInLayer(Offset(0f, 0f)))
        assertTrue(layer.isInLayer(Offset(10f, 10f)))
        assertEquals(Offset(15f, 12f), layer.mapOffset(Offset(10f, 10f), false))
        val point = Offset(4f, 7f)
        val mapped = layer.mapOffset(layer.mapOffset(point, false), true)
        assertEquals(point.x, mapped.x, 0.001f)
        assertEquals(point.y, mapped.y, 0.001f)
        var saved = 0
        var paths = 0
        val canvas = object : Canvas by RecordingCanvas() {
            override fun save() { saved++ }
            override fun restore() { saved-- }
            override fun clipPath(path: Path, clipOp: ClipOp) { paths++ }
        }
        assertThrows(IllegalStateException::class.java) { layer.drawLayer(canvas, null) }
        assertEquals(1, paths)
        assertEquals(0, saved)
    }
}

package allyouneed.compose.platform

import allyouneed.client.compose.platform.McCanvas
import allyouneed.client.compose.platform.McDrawRecorder
import allyouneed.client.compose.platform.TriangleSoup
import allyouneed.client.compose.platform.rectangleGeometry
import allyouneed.client.compose.platform.strokeContours
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import kotlin.math.abs

class RectangleGeometryTest {
    private val rect = Rect(0f, 0f, 10f, 20f)

    private fun area(soup: TriangleSoup): Double {
        var area = 0.0
        for (i in soup.colors.indices step 3) {
            val a = Offset(soup.positions[i * 2], soup.positions[i * 2 + 1])
            val b = Offset(soup.positions[i * 2 + 2], soup.positions[i * 2 + 3])
            val c = Offset(soup.positions[i * 2 + 4], soup.positions[i * 2 + 5])
            area += abs((b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x)) / 2.0
        }
        return area
    }

    /** Raster sample each triangle separately to detect duplicate blending, including AA bands. */
    private fun samples(soup: TriangleSoup, point: Offset): List<Float> = buildList {
        for (i in soup.colors.indices step 3) {
            val ax = soup.positions[i * 2]
            val ay = soup.positions[i * 2 + 1]
            val bx = soup.positions[i * 2 + 2]
            val by = soup.positions[i * 2 + 3]
            val cx = soup.positions[i * 2 + 4]
            val cy = soup.positions[i * 2 + 5]
            val determinant = (by - cy) * (ax - cx) + (cx - bx) * (ay - cy)
            if (abs(determinant) < 1e-6f) continue
            val a = ((by - cy) * (point.x - cx) + (cx - bx) * (point.y - cy)) / determinant
            val b = ((cy - ay) * (point.x - cx) + (ax - cx) * (point.y - cy)) / determinant
            val c = 1f - a - b
            if (a > 1e-5f && b > 1e-5f && c > 1e-5f) {
                add((a * (soup.colors[i] ushr 24) + b * (soup.colors[i + 1] ushr 24) +
                    c * (soup.colors[i + 2] ushr 24)) / 255f)
            }
        }
    }

    @Test
    fun `skew uses horizontal and vertical factors in the correct matrix entries`() {
        fun mapped(sx: Float, sy: Float): Offset {
            val recorder = McDrawRecorder()
            val canvas = McCanvas(null, recorder)
            canvas.skew(sx, sy)
            val matrix = recorder.poseStack.last().pose()
            return Offset(matrix.m00() * 2f + matrix.m10() * 3f, matrix.m01() * 2f + matrix.m11() * 3f)
        }
        assertEquals(Offset(5f, 3f), mapped(1f, 0f))
        assertEquals(Offset(2f, 5f), mapped(0f, 1f))
        assertEquals(Offset(0.5f, 3.5f), mapped(-0.5f, 0.25f))
    }

    @Test
    fun `rectangle stroke is centered and counts each corner only once`() {
        val soup = rectangleGeometry(rect, -1, 2f, antiAlias = false)
        assertEquals(120.0, area(soup), 1e-4) // 12*22 - 8*18
        assertEquals(-1f, soup.positions.filterIndexed { i, _ -> i % 2 == 0 }.min())
        assertEquals(11f, soup.positions.filterIndexed { i, _ -> i % 2 == 0 }.max())
        assertTrue(samples(soup, Offset(5.2f, 10.3f)).isEmpty())
        for (point in listOf(Offset(-0.7f, -0.8f), Offset(-0.3f, 5.4f), Offset(9.7f, 19.8f))) {
            assertEquals(1, samples(soup, point).size, "stroke core at $point")
        }
    }

    @Test
    fun `translucent stroke core and corner feather do not overlap`() {
        val soup = rectangleGeometry(rect, 0x80FFFFFF.toInt(), 2f)
        assertEquals(128f / 255f, samples(soup, Offset(-0.7f, -0.8f)).single(), 1e-5f)
        for (point in listOf(Offset(-1.2f, -1.3f), Offset(1.2f, 1.3f), Offset(-1.2f, 5.3f))) {
            val alpha = samples(soup, point).single()
            assertTrue(alpha > 0f && alpha < 128f / 255f, "feather coverage at $point")
        }
    }

    @Test
    fun `wide strokes fill collapsed holes and all joins have disjoint cores`() {
        assertEquals(600.0, area(rectangleGeometry(rect, -1, 10f, antiAlias = false)), 1e-4)
        assertEquals(118.0, area(rectangleGeometry(rect, -1, 2f, StrokeJoin.Bevel, antiAlias = false)), 1e-4)
        assertEquals(118.0, area(rectangleGeometry(rect, -1, 2f, StrokeJoin.Miter, 1f, false)), 1e-4)
        val round = rectangleGeometry(rect, -1, 2f, StrokeJoin.Round, antiAlias = false)
        assertEquals(116.0 + Math.PI, area(round), 0.03)
        for (join in listOf(StrokeJoin.Miter, StrokeJoin.Bevel, StrokeJoin.Round)) {
            val soup = rectangleGeometry(rect, 0x80FFFFFF.toInt(), 2f, join)
            for (x in -20..120) for (y in -20..220) {
                assertTrue(samples(soup, Offset(x * 0.1f + 0.013f, y * 0.1f + 0.027f)).size <= 1,
                    "overlapping $join triangles at ($x, $y)")
            }
        }
    }

    @Test
    fun `fractional rectangle and positive subpixel stroke widths survive tessellation`() {
        val fill = rectangleGeometry(Rect(-0.25f, 0.125f, 0.25f, 0.375f), -1, antiAlias = false)
        assertEquals(0.125, area(fill), 1e-5)
        assertEquals(15.0, area(rectangleGeometry(rect, -1, 0.25f, antiAlias = false)), 1e-4)
        val line = strokeContours(listOf(listOf(Offset(0f, 0f), Offset(8f, 0f))),
            0.25f, StrokeCap.Butt, StrokeJoin.Miter, -1)
        val coreY = line.colors.indices.filter { line.colors[it] ushr 24 == 255 }.map { line.positions[it * 2 + 1] }
        assertEquals(-0.125f, coreY.min())
        assertEquals(0.125f, coreY.max())
    }

    @Test
    fun `paint defaults match Compose miter contract`() {
        val paint = Paint()
        assertEquals(StrokeJoin.Miter, paint.strokeJoin)
        assertEquals(4f, paint.strokeMiterLimit)
    }
}

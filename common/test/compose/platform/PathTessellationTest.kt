package allyouneed.compose.platform

import allyouneed.client.compose.platform.TriangleSoup
import allyouneed.client.compose.platform.fillContours
import allyouneed.client.compose.platform.strokeContours
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.flattenContours
import androidx.compose.ui.graphics.singleRectOrNull
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs

class PathTessellationTest {

    /** True when all three vertices of triangle [t] are fully opaque (i.e. not a feather fringe). */
    private fun isCoreTri(soup: TriangleSoup, t: Int): Boolean {
        val c = soup.colors
        return ((c[t] ushr 24) == 0xFF) && ((c[t + 1] ushr 24) == 0xFF) && ((c[t + 2] ushr 24) == 0xFF)
    }

    private fun coreTriCount(soup: TriangleSoup): Int {
        var count = 0
        for (v in soup.colors.indices step 3) {
            if (isCoreTri(soup, v)) count++
        }
        return count
    }

    /** Area of the opaque core triangles only; the feather fringe is excluded. */
    private fun soupArea(soup: TriangleSoup): Double {
        var total = 0.0
        val pos = soup.positions
        var i = 0
        var v = 0
        while (i + 5 < pos.size) {
            if (isCoreTri(soup, v)) {
                val ax = pos[i].toDouble()
                val ay = pos[i + 1].toDouble()
                val bx = pos[i + 2].toDouble()
                val by = pos[i + 3].toDouble()
                val cx = pos[i + 4].toDouble()
                val cy = pos[i + 5].toDouble()
                total += abs((bx - ax) * (cy - ay) - (cx - ax) * (by - ay)) / 2.0
            }
            i += 6
            v += 3
        }
        return total
    }

    private fun rectPath(l: Float, t: Float, r: Float, b: Float): Path =
        Path().apply {
            moveTo(l, t)
            lineTo(r, t)
            lineTo(r, b)
            lineTo(l, b)
            close()
        }

    @Test
    fun `rect fill produces two triangles with exact area`() {
        val soup = fillContours(rectPath(0f, 0f, 10f, 20f).flattenContours(), PathFillType.NonZero, 0xFFFFFFFF.toInt())
        assertEquals(2, coreTriCount(soup))
        assertEquals(200.0, soupArea(soup), 1e-3)
    }

    @Test
    fun `evenodd hole is subtracted`() {
        val outer = rectPath(0f, 0f, 10f, 10f).flattenContours()
        val hole = rectPath(3f, 3f, 7f, 7f).flattenContours()
        val soup = fillContours(outer + hole, PathFillType.EvenOdd, 0xFFFFFFFF.toInt())
        assertEquals(100.0 - 16.0, soupArea(soup), 1e-3)
    }

    @Test
    fun `nonzero nested same winding stays filled`() {
        val outer = rectPath(0f, 0f, 10f, 10f).flattenContours()
        val inner = rectPath(3f, 3f, 7f, 7f).flattenContours()
        val soup = fillContours(outer + inner, PathFillType.NonZero, 0xFFFFFFFF.toInt())
        assertEquals(100.0, soupArea(soup), 1e-3)
    }

    @Test
    fun `nonzero opposed winding hole is subtracted`() {
        val outer = Path().apply {
            moveTo(0f, 0f)
            lineTo(10f, 0f)
            lineTo(10f, 10f)
            lineTo(0f, 10f)
            close()
        }.flattenContours()
        val hole = Path().apply {
            // Clockwise inner rect opposes the outer winding.
            moveTo(3f, 3f)
            lineTo(3f, 7f)
            lineTo(7f, 7f)
            lineTo(7f, 3f)
            close()
        }.flattenContours()
        val soup = fillContours(outer + hole, PathFillType.NonZero, 0xFFFFFFFF.toInt())
        assertEquals(100.0 - 16.0, soupArea(soup), 1e-3)
    }

    @Test
    fun `single segment stroke produces one quad`() {
        val soup = strokeContours(
            listOf(listOf(Offset(0f, 0f), Offset(10f, 0f))),
            2f, StrokeCap.Butt, StrokeJoin.Bevel, 0xFFFFFFFF.toInt(),
        )
        assertEquals(2, coreTriCount(soup))
        assertEquals(20.0, soupArea(soup), 1e-3)
    }

    @Test
    fun `circle approximation fills pi r squared`() {
        val path = Path().apply { addOval(Rect(0f, 0f, 20f, 20f)) }
        val soup = fillContours(path.flattenContours(0.1f), PathFillType.NonZero, 0xFFFFFFFF.toInt())
        assertEquals(Math.PI * 100.0, soupArea(soup), 1.0)
    }

    @Test
    fun `oval bounds match input rect`() {
        val path = Path().apply { addOval(Rect(0f, 0f, 30f, 10f)) }
        val bounds = path.getBounds()
        assertEquals(0f, bounds.left, 1e-3f)
        assertEquals(0f, bounds.top, 1e-3f)
        assertEquals(30f, bounds.right, 0.5f)
        assertEquals(10f, bounds.bottom, 0.5f)
    }

    @Test
    fun `round rect flattens to one closed contour`() {
        val path = Path().apply {
            addRoundRect(RoundRect(0f, 0f, 20f, 10f, 4f, 4f))
        }
        val contours = path.flattenContours()
        assertEquals(1, contours.size)
        assertTrue(contours[0].size > 8)
    }

    @Test
    fun `singleRectOrNull detects rectangles and rejects curves`() {
        assertNotNull(rectPath(1f, 2f, 5f, 6f).singleRectOrNull())
        assertEquals(Rect(1f, 2f, 5f, 6f), rectPath(1f, 2f, 5f, 6f).singleRectOrNull())
        val curved = Path().apply {
            moveTo(0f, 0f)
            quadraticTo(5f, 5f, 10f, 0f)
            close()
        }
        assertNull(curved.singleRectOrNull())
    }

    @Test
    fun `path transform and translate move bounds`() {
        val path = rectPath(0f, 0f, 10f, 10f)
        path.translate(Offset(5f, 5f))
        assertEquals(Rect(5f, 5f, 15f, 15f), path.getBounds())
    }
}

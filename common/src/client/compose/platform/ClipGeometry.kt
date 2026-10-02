package allyouneed.client.compose.platform

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.PathFillType
import org.joml.Matrix4f
import kotlin.math.ceil
import kotlin.math.floor

/** GUI-space geometry, captured while recording rather than from a mutable replay pose. */
internal object ClipGeometry {
    fun contains(contours: List<List<Offset>>, point: Offset, fillType: PathFillType): Boolean {
        var winding = 0
        for (contour in contours) {
            if (contour.size < 3) continue
            for (i in contour.indices) {
                val a = contour[i]
                val b = contour[(i + 1) % contour.size]
                val cross = (b.x - a.x) * (point.y - a.y) - (point.x - a.x) * (b.y - a.y)
                if (a.y <= point.y && b.y > point.y && cross > 0f) winding++
                if (a.y > point.y && b.y <= point.y && cross < 0f) winding--
            }
        }
        return if (fillType == PathFillType.EvenOdd) winding % 2 != 0 else winding != 0
    }
    fun transform(points: List<Offset>, matrix: Matrix4f): List<Offset> = points.map {
        val w = matrix.m03() * it.x + matrix.m13() * it.y + matrix.m33()
        Offset(
            (matrix.m00() * it.x + matrix.m10() * it.y + matrix.m30()) / w,
            (matrix.m01() * it.x + matrix.m11() * it.y + matrix.m31()) / w,
        )
    }

    fun rectangle(rect: Rect, matrix: Matrix4f): List<Offset> = transform(
        listOf(rect.topLeft, rect.topRight, rect.bottomRight, rect.bottomLeft, rect.topLeft), matrix,
    )

    fun axisAligned(matrix: Matrix4f): Boolean =
        matrix.m03() == 0f && matrix.m13() == 0f && matrix.m33() == 1f &&
            ((matrix.m01() == 0f && matrix.m10() == 0f) || (matrix.m00() == 0f && matrix.m11() == 0f))

    /** Outward rounding also works for negative coordinates and mirrored axes. */
    fun bounds(points: List<Offset>): IntArray = intArrayOf(
        floor(points.minOf { it.x }).toInt(), floor(points.minOf { it.y }).toInt(),
        ceil(points.maxOf { it.x }).toInt(), ceil(points.maxOf { it.y }).toInt(),
    )
}

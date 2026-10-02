package allyouneed.client.compose.platform

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.StrokeJoin
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Rectangle fill/stroke with disjoint core and AA bands, including the corners. */
internal fun rectangleGeometry(
    rect: Rect,
    color: Int,
    strokeWidth: Float = 0f,
    join: StrokeJoin = StrokeJoin.Miter,
    miterLimit: Float = 4f,
    antiAlias: Boolean = true,
): TriangleSoup {
    val soup = TriangleSoup()
    if (rect.isEmpty || !rect.left.isFinite() || !rect.top.isFinite() ||
        !rect.right.isFinite() || !rect.bottom.isFinite() || !strokeWidth.isFinite()) return soup
    val half = strokeWidth.coerceAtLeast(0f) / 2f
    val actualJoin = when {
        half == 0f -> StrokeJoin.Miter
        join == StrokeJoin.Miter && miterLimit < sqrt(2f) -> StrokeJoin.Bevel
        else -> join
    }
    val corners = listOf(rect.topLeft, rect.topRight, rect.bottomRight, rect.bottomLeft)
    val signs = listOf(Offset(-1f, -1f), Offset(1f, -1f), Offset(1f, 1f), Offset(-1f, 1f))
    val steps = if (actualJoin == StrokeJoin.Round) 8 else 1
    // Non-miter corners have matching repeated inner vertices, forming triangle fans without
    // overlapping the adjacent straight edges. Increasing the outer radius adds the AA band.
    fun outer(radius: Float): List<Offset> = buildList {
        for (corner in 0..3) {
            if (actualJoin == StrokeJoin.Miter) {
                add(corners[corner] + signs[corner] * radius)
            } else {
                for (step in 0..steps) {
                    val angle = PI + corner * PI / 2 + step * PI / (2 * steps)
                    add(corners[corner] + Offset(cos(angle).toFloat(), sin(angle).toFloat()) * radius)
                }
            }
        }
    }
    fun inner(inset: Float): List<Offset> = buildList {
        val dx = min(inset, rect.width / 2f)
        val dy = min(inset, rect.height / 2f)
        for (corner in 0..3) {
            val point = corners[corner] - Offset(signs[corner].x * dx, signs[corner].y * dy)
            repeat(if (actualJoin == StrokeJoin.Miter) 1 else steps + 1) { add(point) }
        }
    }
    fun band(outer: List<Offset>, inner: List<Offset>, outerColor: Int, innerColor: Int) {
        for (i in outer.indices) {
            val next = (i + 1) % outer.size
            val a = outer[i]
            val b = outer[next]
            val c = inner[next]
            val d = inner[i]
            soup.tri(a.x, a.y, b.x, b.y, c.x, c.y, outerColor, outerColor, innerColor)
            soup.tri(a.x, a.y, c.x, c.y, d.x, d.y, outerColor, innerColor, innerColor)
        }
    }

    val outside = outer(half)
    val hasHole = half > 0f && strokeWidth < min(rect.width, rect.height)
    val inside = if (hasHole) inner(half) else List(outside.size) { rect.center }
    band(outside, inside, color, color)
    if (antiAlias) {
        val transparent = color and 0x00FFFFFF
        band(outer(half + 0.75f), outside, transparent, color)
        if (hasHole) band(inside, inner(half + 0.75f), color, transparent)
    }
    return soup
}

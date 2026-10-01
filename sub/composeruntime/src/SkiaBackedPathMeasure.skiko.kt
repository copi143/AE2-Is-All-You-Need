/*
 * Copyright 2026 copi143
 *
 * Pure-JVM replacement for the skiko-backed path measure
 * (actual fun PathMeasure()). Measures the flattened first contour;
 * used by vector drawables for stroke trimming.
 */

@file:JvmName("SkiaBackedPathMeasure_skikoKt")

package androidx.compose.ui.graphics

import androidx.compose.ui.geometry.Offset
import kotlin.math.sqrt

fun PathMeasure(): PathMeasure = SkiaBackedPathMeasure()

internal class SkiaBackedPathMeasure : PathMeasure {

    private var points: List<Offset> = emptyList()
    private var cumulative = floatArrayOf()

    override val length: Float
        get() = if (cumulative.isEmpty()) 0f else cumulative.last()

    override fun setPath(path: Path?, forceClosed: Boolean) {
        if (path == null) {
            points = emptyList()
            cumulative = floatArrayOf()
            return
        }
        val contour = path.flattenContours().firstOrNull { it.size >= 2 }
        if (contour == null) {
            points = emptyList()
            cumulative = floatArrayOf()
            return
        }
        val pts = contour.toMutableList()
        if (forceClosed && pts.first() != pts.last()) {
            pts += pts.first()
        }
        points = pts
        cumulative = FloatArray(pts.size)
        var total = 0f
        for (i in 1 until pts.size) {
            total += distance(pts[i - 1], pts[i])
            cumulative[i] = total
        }
    }

    override fun getPosition(distance: Float): Offset {
        if (points.size < 2) return Offset.Unspecified
        val d = distance.coerceIn(0f, length)
        val seg = segmentAt(d)
        val a = points[seg]
        val b = points[seg + 1]
        val segLen = cumulative[seg + 1] - cumulative[seg]
        if (segLen <= 0f) return a
        val t = (d - cumulative[seg]) / segLen
        return Offset(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
    }

    override fun getTangent(distance: Float): Offset {
        if (points.size < 2) return Offset.Unspecified
        val d = distance.coerceIn(0f, length)
        val seg = segmentAt(d)
        val a = points[seg]
        val b = points[seg + 1]
        val dx = b.x - a.x
        val dy = b.y - a.y
        val len = sqrt(dx * dx + dy * dy)
        if (len <= 0f) return Offset.Zero
        return Offset(dx / len, dy / len)
    }

    override fun getSegment(
        startDistance: Float,
        stopDistance: Float,
        destination: Path,
        startWithMoveTo: Boolean,
    ): Boolean {
        if (points.size < 2) return false
        val total = length
        val start = startDistance.coerceIn(0f, total)
        val stop = stopDistance.coerceIn(0f, total)
        if (start >= stop) return false
        val startSeg = segmentAt(start)
        val stopSeg = segmentAt(stop)
        val collected = ArrayList<Offset>()
        collected += interpolated(startSeg, start)
        for (i in startSeg + 1..stopSeg) {
            collected += points[i]
        }
        val end = interpolated(stopSeg, stop)
        if (end != collected.last()) collected += end
        if (collected.size < 2) return false
        if (startWithMoveTo) {
            destination.moveTo(collected[0].x, collected[0].y)
        } else {
            destination.lineTo(collected[0].x, collected[0].y)
        }
        for (i in 1 until collected.size) {
            destination.lineTo(collected[i].x, collected[i].y)
        }
        return true
    }

    private fun segmentAt(d: Float): Int {
        var lo = 0
        var hi = cumulative.size - 2
        if (hi < 0) return 0
        if (d >= cumulative.last()) return hi
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (cumulative[mid + 1] < d) lo = mid + 1 else hi = mid
        }
        return lo
    }

    private fun interpolated(seg: Int, d: Float): Offset {
        val a = points[seg]
        val b = points[seg + 1]
        val segLen = cumulative[seg + 1] - cumulative[seg]
        if (segLen <= 0f) return a
        val t = ((d - cumulative[seg]) / segLen).coerceIn(0f, 1f)
        return Offset(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
    }

    private fun distance(a: Offset, b: Offset): Float {
        val dx = b.x - a.x
        val dy = b.y - a.y
        return sqrt(dx * dx + dy * dy)
    }
}

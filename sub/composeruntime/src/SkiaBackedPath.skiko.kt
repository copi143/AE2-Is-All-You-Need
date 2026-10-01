/*
 * Copyright 2026 copi143
 *
 * Pure-JVM replacement for the skiko-backed path implementation
 * (SkiaBackedPath / actual fun Path() / actual fun PathIterator()).
 *
 * Segments are stored as plain Kotlin verbs instead of an
 * org.jetbrains.skia.Path, so path construction never touches skiko
 * natives. [flattenContours] exposes the tessellation-friendly form
 * consumed by the Minecraft canvas bridge; arcs/ovals/round-rects are
 * decomposed into lines and cubics at build time.
 */

@file:JvmName("SkiaBackedPath_skikoKt")
@file:Suppress("DEPRECATION", "DEPRECATION_ERROR")

package androidx.compose.ui.graphics

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

fun Path(): Path = SkiaBackedPath()

internal sealed interface PathVerb {
    data class Move(val x: Float, val y: Float) : PathVerb
    data class Line(val x: Float, val y: Float) : PathVerb
    data class Quad(val x1: Float, val y1: Float, val x2: Float, val y2: Float) : PathVerb
    data class Cubic(
        val x1: Float,
        val y1: Float,
        val x2: Float,
        val y2: Float,
        val x3: Float,
        val y3: Float,
    ) : PathVerb

    data object Close : PathVerb
}

/**
 * Flattens this path into closed point contours (curves subdivided so the
 * chord error stays under [tolerance] px). Only paths built by the
 * framework [Path] implementation can be flattened.
 */
fun Path.flattenContours(tolerance: Float = 0.5f): List<List<Offset>> {
    require(this is SkiaBackedPath) {
        "Flattening is only supported for SkiaBackedPath instances but received ${this::class}"
    }
    return flattenVerbs(verbs, tolerance)
}

/**
 * Returns the axis-aligned rect when this path is exactly one rectangle
 * (any winding, optional close), null otherwise. Used to keep rectangular
 * clips pixel-exact instead of falling back to bounds.
 */
fun Path.singleRectOrNull(): Rect? {
    if (this !is SkiaBackedPath) return null
    val points = ArrayList<Offset>(4)
    for (verb in verbs) {
        when (verb) {
            is PathVerb.Move -> points += Offset(verb.x, verb.y)
            is PathVerb.Line -> points += Offset(verb.x, verb.y)
            is PathVerb.Quad, is PathVerb.Cubic -> return null
            PathVerb.Close -> Unit
        }
    }
    if (points.size != 4) return null
    val xs = points.map { it.x }.toSet()
    val ys = points.map { it.y }.toSet()
    if (xs.size != 2 || ys.size != 2) return null
    return Rect(xs.min(), ys.min(), xs.max(), ys.max())
}

internal fun flattenVerbs(verbs: List<PathVerb>, tolerance: Float): List<List<Offset>> {
    val contours = ArrayList<List<Offset>>()
    var current = ArrayList<Offset>()
    var cursor = Offset.Zero
    var subpathStart = Offset.Zero
    fun flush() {
        if (current.size >= 3) contours += current
        current = ArrayList()
    }
    for (verb in verbs) {
        when (verb) {
            is PathVerb.Move -> {
                flush()
                cursor = Offset(verb.x, verb.y)
                subpathStart = cursor
                current += cursor
            }
            is PathVerb.Line -> {
                if (current.isEmpty()) {
                    subpathStart = cursor
                    current += cursor
                }
                cursor = Offset(verb.x, verb.y)
                current += cursor
            }
            is PathVerb.Quad -> {
                if (current.isEmpty()) {
                    subpathStart = cursor
                    current += cursor
                }
                subdivideQuad(cursor, Offset(verb.x1, verb.y1), Offset(verb.x2, verb.y2), tolerance, current)
                cursor = Offset(verb.x2, verb.y2)
            }
            is PathVerb.Cubic -> {
                if (current.isEmpty()) {
                    subpathStart = cursor
                    current += cursor
                }
                subdivideCubic(
                    cursor,
                    Offset(verb.x1, verb.y1),
                    Offset(verb.x2, verb.y2),
                    Offset(verb.x3, verb.y3),
                    tolerance,
                    current,
                )
                cursor = Offset(verb.x3, verb.y3)
            }
            PathVerb.Close -> {
                if (current.isNotEmpty()) {
                    cursor = subpathStart
                    current += cursor
                }
                flush()
                cursor = subpathStart
            }
        }
    }
    flush()
    return contours
}

private fun flatnessQuad(p0: Offset, p1: Offset, p2: Offset): Float {
    val ux = 2f * p1.x - p0.x - p2.x
    val uy = 2f * p1.y - p0.y - p2.y
    return sqrt(ux * ux + uy * uy)
}

private fun subdivideQuad(p0: Offset, p1: Offset, p2: Offset, tolerance: Float, out: MutableList<Offset>) {
    if (flatnessQuad(p0, p1, p2) <= tolerance) {
        out += p2
        return
    }
    val p01 = (p0 + p1) / 2f
    val p12 = (p1 + p2) / 2f
    val mid = (p01 + p12) / 2f
    subdivideQuad(p0, p01, mid, tolerance, out)
    subdivideQuad(mid, p12, p2, tolerance, out)
}

private fun flatnessCubic(p0: Offset, p1: Offset, p2: Offset, p3: Offset): Float {
    val ux = 3f * p1.x - 2f * p0.x - p3.x
    val uy = 3f * p1.y - 2f * p0.y - p3.y
    val vx = 3f * p2.x - 2f * p3.x - p0.x
    val vy = 3f * p2.y - 2f * p3.y - p0.y
    return maxOf(sqrt(ux * ux + uy * uy), sqrt(vx * vx + vy * vy))
}

private fun subdivideCubic(
    p0: Offset,
    p1: Offset,
    p2: Offset,
    p3: Offset,
    tolerance: Float,
    out: MutableList<Offset>,
) {
    if (flatnessCubic(p0, p1, p2, p3) <= tolerance) {
        out += p3
        return
    }
    val p01 = (p0 + p1) / 2f
    val p12 = (p1 + p2) / 2f
    val p23 = (p2 + p3) / 2f
    val p012 = (p01 + p12) / 2f
    val p123 = (p12 + p23) / 2f
    val mid = (p012 + p123) / 2f
    subdivideCubic(p0, p01, p012, mid, tolerance, out)
    subdivideCubic(mid, p123, p23, p3, tolerance, out)
}

internal class SkiaBackedPath : Path {

    internal val verbs = ArrayList<PathVerb>(16)
    private var cursor = Offset.Zero
    private var subpathStart = Offset.Zero
    private var hasCurrentContour = false

    override var fillType: PathFillType = PathFillType.NonZero

    private var minX = Float.POSITIVE_INFINITY
    private var minY = Float.POSITIVE_INFINITY
    private var maxX = Float.NEGATIVE_INFINITY
    private var maxY = Float.NEGATIVE_INFINITY

    private fun note(x: Float, y: Float) {
        if (x < minX) minX = x
        if (y < minY) minY = y
        if (x > maxX) maxX = x
        if (y > maxY) maxY = y
    }

    private fun beginContour(x: Float, y: Float) {
        cursor = Offset(x, y)
        subpathStart = cursor
        hasCurrentContour = true
    }

    override fun moveTo(x: Float, y: Float) {
        verbs += PathVerb.Move(x, y)
        note(x, y)
        beginContour(x, y)
    }

    override fun relativeMoveTo(dx: Float, dy: Float) {
        moveTo(cursor.x + dx, cursor.y + dy)
    }

    override fun lineTo(x: Float, y: Float) {
        if (!hasCurrentContour) {
            verbs += PathVerb.Move(cursor.x, cursor.y)
        }
        verbs += PathVerb.Line(x, y)
        note(x, y)
        cursor = Offset(x, y)
        hasCurrentContour = true
    }

    override fun relativeLineTo(dx: Float, dy: Float) {
        lineTo(cursor.x + dx, cursor.y + dy)
    }

    override fun quadraticBezierTo(x1: Float, y1: Float, x2: Float, y2: Float) {
        quadraticTo(x1, y1, x2, y2)
    }

    override fun quadraticTo(x1: Float, y1: Float, x2: Float, y2: Float) {
        if (!hasCurrentContour) {
            verbs += PathVerb.Move(cursor.x, cursor.y)
        }
        verbs += PathVerb.Quad(x1, y1, x2, y2)
        note(x1, y1)
        note(x2, y2)
        cursor = Offset(x2, y2)
        hasCurrentContour = true
    }

    override fun relativeQuadraticBezierTo(dx1: Float, dy1: Float, dx2: Float, dy2: Float) {
        relativeQuadraticTo(dx1, dy1, dx2, dy2)
    }

    override fun relativeQuadraticTo(dx1: Float, dy1: Float, dx2: Float, dy2: Float) {
        quadraticTo(cursor.x + dx1, cursor.y + dy1, cursor.x + dx2, cursor.y + dy2)
    }

    override fun cubicTo(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) {
        if (!hasCurrentContour) {
            verbs += PathVerb.Move(cursor.x, cursor.y)
        }
        verbs += PathVerb.Cubic(x1, y1, x2, y2, x3, y3)
        note(x1, y1)
        note(x2, y2)
        note(x3, y3)
        cursor = Offset(x3, y3)
        hasCurrentContour = true
    }

    override fun relativeCubicTo(
        dx1: Float,
        dy1: Float,
        dx2: Float,
        dy2: Float,
        dx3: Float,
        dy3: Float,
    ) {
        cubicTo(
            cursor.x + dx1, cursor.y + dy1,
            cursor.x + dx2, cursor.y + dy2,
            cursor.x + dx3, cursor.y + dy3,
        )
    }

    override fun arcTo(rect: Rect, startAngleDegrees: Float, sweepAngleDegrees: Float, forceMoveTo: Boolean) {
        appendArc(rect, startAngleDegrees, sweepAngleDegrees, forceMoveTo)
    }

    override fun addRect(rect: Rect) {
        addRect(rect, Path.Direction.CounterClockwise)
    }

    override fun addRect(rect: Rect, direction: Path.Direction) {
        // Reverse the winding for clockwise rects so NonZero fill stays correct.
        if (direction == Path.Direction.Clockwise) {
            moveTo(rect.left, rect.top)
            lineTo(rect.left, rect.bottom)
            lineTo(rect.right, rect.bottom)
            lineTo(rect.right, rect.top)
        } else {
            moveTo(rect.left, rect.top)
            lineTo(rect.right, rect.top)
            lineTo(rect.right, rect.bottom)
            lineTo(rect.left, rect.bottom)
        }
        close()
    }

    override fun addOval(oval: Rect) {
        addOval(oval, Path.Direction.CounterClockwise)
    }

    override fun addOval(oval: Rect, direction: Path.Direction) {
        appendEllipse(oval, forceMoveTo = true)
    }

    override fun addRoundRect(roundRect: RoundRect) {
        addRoundRect(roundRect, Path.Direction.CounterClockwise)
    }

    override fun addRoundRect(roundRect: RoundRect, direction: Path.Direction) {
        val left = roundRect.left
        val top = roundRect.top
        val right = roundRect.right
        val bottom = roundRect.bottom
        // Clamp each corner radius into the rect, like the platform implementation.
        val maxRx = (right - left) / 2f
        val maxRy = (bottom - top) / 2f
        val tlx = roundRect.topLeftCornerRadius.x.coerceIn(0f, maxRx)
        val tly = roundRect.topLeftCornerRadius.y.coerceIn(0f, maxRy)
        val trx = roundRect.topRightCornerRadius.x.coerceIn(0f, maxRx)
        val try_ = roundRect.topRightCornerRadius.y.coerceIn(0f, maxRy)
        val brx = roundRect.bottomRightCornerRadius.x.coerceIn(0f, maxRx)
        val bry = roundRect.bottomRightCornerRadius.y.coerceIn(0f, maxRy)
        val blx = roundRect.bottomLeftCornerRadius.x.coerceIn(0f, maxRx)
        val bly = roundRect.bottomLeftCornerRadius.y.coerceIn(0f, maxRy)
        moveTo(left + tlx, top)
        lineTo(right - trx, top)
        appendArc(Rect(right - 2f * trx, top, right, top + 2f * try_), -90f, 90f, forceMoveTo = false)
        lineTo(right, bottom - bry)
        appendArc(Rect(right - 2f * brx, bottom - 2f * bry, right, bottom), 0f, 90f, forceMoveTo = false)
        lineTo(left + blx, bottom)
        appendArc(Rect(left, bottom - 2f * bly, left + 2f * blx, bottom), 90f, 90f, forceMoveTo = false)
        lineTo(left, top + tly)
        appendArc(Rect(left, top, left + 2f * tlx, top + 2f * tly), 180f, 90f, forceMoveTo = false)
        close()
    }

    override fun addArcRad(oval: Rect, startAngleRadians: Float, sweepAngleRadians: Float) {
        addArc(oval, Math.toDegrees(startAngleRadians.toDouble()).toFloat(), Math.toDegrees(sweepAngleRadians.toDouble()).toFloat())
    }

    override fun addArc(oval: Rect, startAngleDegrees: Float, sweepAngleDegrees: Float) {
        appendArc(oval, startAngleDegrees, sweepAngleDegrees, forceMoveTo = true)
    }

    override fun addPath(path: Path, offset: Offset) {
        require(path is SkiaBackedPath) {
            "Merging is only supported for SkiaBackedPath instances but received ${path::class}"
        }
        if (offset == Offset.Zero) {
            verbs += path.verbs
        } else {
            for (verb in path.verbs) {
                verbs += when (verb) {
                    is PathVerb.Move -> verb.copy(x = verb.x + offset.x, y = verb.y + offset.y)
                    is PathVerb.Line -> verb.copy(x = verb.x + offset.x, y = verb.y + offset.y)
                    is PathVerb.Quad -> verb.copy(
                        x1 = verb.x1 + offset.x, y1 = verb.y1 + offset.y,
                        x2 = verb.x2 + offset.x, y2 = verb.y2 + offset.y,
                    )
                    is PathVerb.Cubic -> verb.copy(
                        x1 = verb.x1 + offset.x, y1 = verb.y1 + offset.y,
                        x2 = verb.x2 + offset.x, y2 = verb.y2 + offset.y,
                        x3 = verb.x3 + offset.x, y3 = verb.y3 + offset.y,
                    )
                    PathVerb.Close -> verb
                }
            }
        }
        note(offset.x + path.minX, offset.y + path.minY)
        note(offset.x + path.maxX, offset.y + path.maxY)
        cursor = Offset(path.cursor.x + offset.x, path.cursor.y + offset.y)
        subpathStart = Offset(path.subpathStart.x + offset.x, path.subpathStart.y + offset.y)
        hasCurrentContour = hasCurrentContour || path.hasCurrentContour
    }

    override fun close() {
        verbs += PathVerb.Close
        note(subpathStart.x, subpathStart.y)
        cursor = subpathStart
        hasCurrentContour = false
    }

    override fun reset() {
        verbs.clear()
        cursor = Offset.Zero
        subpathStart = Offset.Zero
        hasCurrentContour = false
        minX = Float.POSITIVE_INFINITY
        minY = Float.POSITIVE_INFINITY
        maxX = Float.NEGATIVE_INFINITY
        maxY = Float.NEGATIVE_INFINITY
    }

    override fun translate(offset: Offset) {
        if (offset == Offset.Zero) return
        for (i in verbs.indices) {
            verbs[i] = when (val verb = verbs[i]) {
                is PathVerb.Move -> verb.copy(x = verb.x + offset.x, y = verb.y + offset.y)
                is PathVerb.Line -> verb.copy(x = verb.x + offset.x, y = verb.y + offset.y)
                is PathVerb.Quad -> verb.copy(
                    x1 = verb.x1 + offset.x, y1 = verb.y1 + offset.y,
                    x2 = verb.x2 + offset.x, y2 = verb.y2 + offset.y,
                )
                is PathVerb.Cubic -> verb.copy(
                    x1 = verb.x1 + offset.x, y1 = verb.y1 + offset.y,
                    x2 = verb.x2 + offset.x, y2 = verb.y2 + offset.y,
                    x3 = verb.x3 + offset.x, y3 = verb.y3 + offset.y,
                )
                PathVerb.Close -> verb
            }
        }
        cursor = Offset(cursor.x + offset.x, cursor.y + offset.y)
        subpathStart = Offset(subpathStart.x + offset.x, subpathStart.y + offset.y)
        minX += offset.x
        minY += offset.y
        maxX += offset.x
        maxY += offset.y
    }

    override fun transform(matrix: Matrix) {
        fun map(x: Float, y: Float): Offset = matrix.map(Offset(x, y))
        for (i in verbs.indices) {
            verbs[i] = when (val verb = verbs[i]) {
                is PathVerb.Move -> map(verb.x, verb.y).let { PathVerb.Move(it.x, it.y) }
                is PathVerb.Line -> map(verb.x, verb.y).let { PathVerb.Line(it.x, it.y) }
                is PathVerb.Quad -> {
                    val a = map(verb.x1, verb.y1)
                    val b = map(verb.x2, verb.y2)
                    PathVerb.Quad(a.x, a.y, b.x, b.y)
                }
                is PathVerb.Cubic -> {
                    val a = map(verb.x1, verb.y1)
                    val b = map(verb.x2, verb.y2)
                    val c = map(verb.x3, verb.y3)
                    PathVerb.Cubic(a.x, a.y, b.x, b.y, c.x, c.y)
                }
                PathVerb.Close -> verb
            }
        }
        cursor = map(cursor.x, cursor.y)
        subpathStart = map(subpathStart.x, subpathStart.y)
        recomputeBounds()
    }

    override fun getBounds(): Rect {
        if (verbs.isEmpty()) return Rect.Zero
        return Rect(minX, minY, maxX, maxY)
    }

    override fun op(path1: Path, path2: Path, operation: PathOperation): Boolean = false

    override val isConvex: Boolean
        get() = false

    override val isEmpty: Boolean
        get() = verbs.isEmpty()

    private fun recomputeBounds() {
        minX = Float.POSITIVE_INFINITY
        minY = Float.POSITIVE_INFINITY
        maxX = Float.NEGATIVE_INFINITY
        maxY = Float.NEGATIVE_INFINITY
        for (verb in verbs) {
            when (verb) {
                is PathVerb.Move -> note(verb.x, verb.y)
                is PathVerb.Line -> note(verb.x, verb.y)
                is PathVerb.Quad -> {
                    note(verb.x1, verb.y1)
                    note(verb.x2, verb.y2)
                }
                is PathVerb.Cubic -> {
                    note(verb.x1, verb.y1)
                    note(verb.x2, verb.y2)
                    note(verb.x3, verb.y3)
                }
                PathVerb.Close -> Unit
            }
        }
    }

    /**
     * Appends an elliptical arc as cubic segments. Angles follow the platform
     * convention: 0° at 3 o'clock, positive sweep clockwise (y-down).
     */
    private fun appendArc(rect: Rect, startDegrees: Float, sweepDegrees: Float, forceMoveTo: Boolean) {
        if (sweepDegrees == 0f) return
        val cx = (rect.left + rect.right) / 2f
        val cy = (rect.top + rect.bottom) / 2f
        val rx = abs(rect.right - rect.left) / 2f
        val ry = abs(rect.bottom - rect.top) / 2f
        if (rx == 0f || ry == 0f) return
        var start = startDegrees
        var sweep = sweepDegrees
        // Normalize the sweep into segments of at most 90°.
        val segments = maxOf(1, kotlin.math.ceil(abs(sweep) / 90f).toInt())
        val step = sweep / segments
        // Standard arc-to-bezier control distance for a 90° span.
        var first = true
        repeat(segments) {
            val a0 = Math.toRadians(start.toDouble())
            val a1 = Math.toRadians((start + step).toDouble())
            val t = (4.0 / 3.0 * kotlin.math.tan((a1 - a0) / 4.0)).toFloat()
            val p0x = cx + rx * cos(a0).toFloat()
            val p0y = cy + ry * sin(a0).toFloat()
            val p3x = cx + rx * cos(a1).toFloat()
            val p3y = cy + ry * sin(a1).toFloat()
            val p1x = p0x + t * (-rx * sin(a0).toFloat())
            val p1y = p0y + t * (ry * cos(a0).toFloat())
            val p2x = p3x - t * (-rx * sin(a1).toFloat())
            val p2y = p3y - t * (ry * cos(a1).toFloat())
            if (first) {
                first = false
                if (forceMoveTo || !hasCurrentContour) {
                    moveTo(p0x, p0y)
                } else {
                    lineTo(p0x, p0y)
                }
            }
            cubicTo(p1x, p1y, p2x, p2y, p3x, p3y)
            start += step
        }
    }

    private fun appendEllipse(oval: Rect, forceMoveTo: Boolean) {
        val cx = (oval.left + oval.right) / 2f
        val cy = (oval.top + oval.bottom) / 2f
        val rx = abs(oval.right - oval.left) / 2f
        val ry = abs(oval.bottom - oval.top) / 2f
        if (rx == 0f || ry == 0f) return
        // kappa*c approximated ellipse with 4 cubics, counter-clockwise in y-down space
        // (matches the platform default winding).
        val k = 0.5522847498307936f
        moveTo(cx - rx, cy)
        cubicTo(cx - rx, cy - k * ry, cx - k * rx, cy - ry, cx, cy - ry)
        cubicTo(cx + k * rx, cy - ry, cx + rx, cy - k * ry, cx + rx, cy)
        cubicTo(cx + rx, cy + k * ry, cx + k * rx, cy + ry, cx, cy + ry)
        cubicTo(cx - k * rx, cy + ry, cx - rx, cy + k * ry, cx - rx, cy)
        close()
    }
}

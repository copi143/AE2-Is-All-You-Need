/*
 * Copyright 2026 copi143
 *
 * Pure-JVM replacement for the skiko-backed path iterator
 * (actual fun PathIterator()). Walks the verb list stored by
 * [SkiaBackedPath]; no conic verbs are ever produced by the
 * framework path implementation.
 */

@file:JvmName("SkiaPathIterator_skikoKt")

package androidx.compose.ui.graphics

import androidx.compose.ui.geometry.Offset

fun PathIterator(
    path: Path,
    conicEvaluation: PathIterator.ConicEvaluation,
    tolerance: Float,
): PathIterator = JvmPathIterator(path, conicEvaluation, tolerance)

private class JvmPathIterator(
    override val path: Path,
    override val conicEvaluation: PathIterator.ConicEvaluation,
    override val tolerance: Float,
) : PathIterator {

    private val verbs: List<PathVerb> = run {
        require(path is SkiaBackedPath) {
            "Iterating is only supported for SkiaBackedPath instances but received ${path::class}"
        }
        path.verbs
    }

    private var index = 0
    private var pen = Offset.Zero

    override fun calculateSize(includeConvertedConics: Boolean): Int = verbs.size

    override fun hasNext(): Boolean = index < verbs.size

    override fun next(outPoints: FloatArray, offset: Int): PathSegment.Type {
        check(outPoints.size - offset >= 8) { "The points array must contain at least 8 floats" }
        if (!hasNext()) return PathSegment.Type.Done
        return when (val verb = verbs[index++]) {
            is PathVerb.Move -> {
                pen = Offset(verb.x, verb.y)
                outPoints[offset] = verb.x
                outPoints[offset + 1] = verb.y
                PathSegment.Type.Move
            }
            is PathVerb.Line -> {
                outPoints[offset] = pen.x
                outPoints[offset + 1] = pen.y
                outPoints[offset + 2] = verb.x
                outPoints[offset + 3] = verb.y
                pen = Offset(verb.x, verb.y)
                PathSegment.Type.Line
            }
            is PathVerb.Quad -> {
                outPoints[offset] = pen.x
                outPoints[offset + 1] = pen.y
                outPoints[offset + 2] = verb.x1
                outPoints[offset + 3] = verb.y1
                outPoints[offset + 4] = verb.x2
                outPoints[offset + 5] = verb.y2
                pen = Offset(verb.x2, verb.y2)
                PathSegment.Type.Quadratic
            }
            is PathVerb.Cubic -> {
                outPoints[offset] = pen.x
                outPoints[offset + 1] = pen.y
                outPoints[offset + 2] = verb.x1
                outPoints[offset + 3] = verb.y1
                outPoints[offset + 4] = verb.x2
                outPoints[offset + 5] = verb.y2
                outPoints[offset + 6] = verb.x3
                outPoints[offset + 7] = verb.y3
                pen = Offset(verb.x3, verb.y3)
                PathSegment.Type.Cubic
            }
            PathVerb.Close -> PathSegment.Type.Close
        }
    }

    override fun next(): PathSegment {
        if (!hasNext()) return PathSegment(PathSegment.Type.Done, floatArrayOf(), 0f)
        val points = FloatArray(8)
        return when (next(points, 0)) {
            PathSegment.Type.Move -> PathSegment(PathSegment.Type.Move, points.copyOf(2), 0f)
            PathSegment.Type.Line -> PathSegment(PathSegment.Type.Line, points.copyOf(4), 0f)
            PathSegment.Type.Quadratic -> PathSegment(PathSegment.Type.Quadratic, points.copyOf(6), 0f)
            PathSegment.Type.Cubic -> PathSegment(PathSegment.Type.Cubic, points.copyOf(8), 0f)
            PathSegment.Type.Close -> PathSegment(PathSegment.Type.Close, floatArrayOf(), 0f)
            else -> PathSegment(PathSegment.Type.Done, floatArrayOf(), 0f)
        }
    }
}

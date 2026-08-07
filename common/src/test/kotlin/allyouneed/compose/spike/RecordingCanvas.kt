package allyouneed.compose.spike

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.Vertices

class RecordingCanvas : Canvas {
    data class Fill(val left: Float, val top: Float, val right: Float, val bottom: Float, val color: ULong)

    val fills = mutableListOf<Fill>()
    val translations = mutableListOf<Offset>()

    override fun save() = Unit
    override fun restore() = Unit
    override fun saveLayer(bounds: Rect, paint: Paint) = Unit
    override fun translate(dx: Float, dy: Float) {
        translations += Offset(dx, dy)
    }
    override fun scale(sx: Float, sy: Float) = Unit
    override fun rotate(degrees: Float) = Unit
    override fun skew(sx: Float, sy: Float) = Unit
    override fun concat(matrix: Matrix) = Unit
    override fun clipRect(left: Float, top: Float, right: Float, bottom: Float, clipOp: ClipOp) = Unit
    override fun clipPath(path: Path, clipOp: ClipOp) = Unit
    override fun drawLine(p1: Offset, p2: Offset, paint: Paint) = Unit

    override fun drawRect(left: Float, top: Float, right: Float, bottom: Float, paint: Paint) {
        fills += Fill(left, top, right, bottom, paint.color.value)
    }

    override fun drawRoundRect(
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        radiusX: Float,
        radiusY: Float,
        paint: Paint,
    ) {
        fills += Fill(left, top, right, bottom, paint.color.value)
    }

    override fun drawOval(left: Float, top: Float, right: Float, bottom: Float, paint: Paint) = Unit
    override fun drawCircle(center: Offset, radius: Float, paint: Paint) = Unit

    override fun drawArc(
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        startAngle: Float,
        sweepAngle: Float,
        useCenter: Boolean,
        paint: Paint,
    ) = Unit

    override fun drawPath(path: Path, paint: Paint) = Unit
    override fun drawImage(image: ImageBitmap, topLeftOffset: Offset, paint: Paint) = Unit
    override fun drawImageRect(
        image: ImageBitmap,
        srcOffset: IntOffset,
        srcSize: IntSize,
        dstOffset: IntOffset,
        dstSize: IntSize,
        paint: Paint,
    ) = Unit

    override fun drawPoints(pointMode: PointMode, points: List<Offset>, paint: Paint) = Unit
    override fun drawRawPoints(pointMode: PointMode, points: FloatArray, paint: Paint) = Unit
    override fun drawVertices(vertices: Vertices, blendMode: BlendMode, paint: Paint) = Unit
    override fun enableZ() = Unit
    override fun disableZ() = Unit
}

private typealias IntOffset = androidx.compose.ui.unit.IntOffset
private typealias IntSize = androidx.compose.ui.unit.IntSize

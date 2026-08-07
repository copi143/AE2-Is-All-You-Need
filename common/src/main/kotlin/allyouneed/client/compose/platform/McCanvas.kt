package allyouneed.client.compose.platform

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.Vertices
import androidx.compose.ui.graphics.toArgb
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.BufferBuilder
import com.mojang.blaze3d.vertex.BufferUploader
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.Tesselator
import com.mojang.blaze3d.vertex.VertexFormat
import com.mojang.math.Axis
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.renderer.GameRenderer
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Bridges the official androidx.compose Canvas drawing commands to Minecraft's [GuiGraphics]
 * (immediate-mode GUI rendering). No offscreen surface, no skiko: every command is translated
 * directly into GuiGraphics calls, so the Compose tree paints with vanilla MC rendering state.
 *
 * Transform support is limited to translations (mirroring how the official layout places nodes);
 * scale/rotation/clipping are ignored.
 */
class McCanvas(private val graphics: GuiGraphics) : Canvas {

    private fun argb(paint: Paint): Int = paint.color.toArgb()

    private fun strokeWidth(paint: Paint): Float = max(paint.strokeWidth, 1f)

    private fun strokeRect(left: Float, top: Float, right: Float, bottom: Float, paint: Paint) {
        val color = argb(paint)
        val w = strokeWidth(paint)
        graphics.fill(left.toInt(), top.toInt(), right.toInt(), (top + w).toInt(), color)
        graphics.fill(left.toInt(), (bottom - w).toInt(), right.toInt(), bottom.toInt(), color)
        graphics.fill(left.toInt(), top.toInt(), (left + w).toInt(), bottom.toInt(), color)
        graphics.fill((right - w).toInt(), top.toInt(), right.toInt(), bottom.toInt(), color)
    }

    override fun save() = graphics.pose().pushPose()

    override fun restore() = graphics.pose().popPose()

    override fun saveLayer(bounds: Rect, paint: Paint) = graphics.pose().pushPose()

    override fun translate(dx: Float, dy: Float) {
        graphics.pose().translate(dx, dy, 0f)
    }

    override fun scale(sx: Float, sy: Float) {
        if (sx == 1f && sy == 1f) return
        graphics.pose().scale(sx, sy, 1f)
    }

    override fun rotate(degrees: Float) {
        graphics.pose().mulPose(Axis.ZP.rotationDegrees(degrees))
    }

    override fun skew(sx: Float, sy: Float) = Unit

    override fun concat(matrix: Matrix) = Unit

    override fun clipRect(left: Float, top: Float, right: Float, bottom: Float, clipOp: ClipOp) = Unit

    override fun clipPath(path: Path, clipOp: ClipOp) = Unit

    override fun drawLine(p1: Offset, p2: Offset, paint: Paint) {
        val color = argb(paint)
        val w = strokeWidth(paint)
        val left = min(p1.x, p2.x)
        val top = min(p1.y, p2.y)
        val width = abs(p2.x - p1.x)
        val height = abs(p2.y - p1.y)
        if (width >= height) {
            graphics.fill(left.toInt(), (top - w / 2).toInt(), (left + width).toInt(), (top - w / 2 + w).toInt(), color)
        } else {
            graphics.fill((left - w / 2).toInt(), top.toInt(), (left - w / 2 + w).toInt(), (top + height).toInt(), color)
        }
    }

    override fun drawRect(left: Float, top: Float, right: Float, bottom: Float, paint: Paint) {
        if (paint.style == PaintingStyle.Stroke) {
            strokeRect(left, top, right, bottom, paint)
        } else {
            graphics.fill(left.toInt(), top.toInt(), right.toInt(), bottom.toInt(), argb(paint))
        }
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
        if (paint.style == PaintingStyle.Stroke) {
            strokeRect(left, top, right, bottom, paint)
        } else {
            graphics.fill(left.toInt(), top.toInt(), right.toInt(), bottom.toInt(), argb(paint))
        }
    }

    override fun drawOval(left: Float, top: Float, right: Float, bottom: Float, paint: Paint) {
        val center = Offset((left + right) / 2f, (top + bottom) / 2f)
        drawCircle(center, min(right - left, bottom - top) / 2f, paint)
    }

    override fun drawCircle(center: Offset, radius: Float, paint: Paint) {
        val color = argb(paint)
        val segments = 24
        val step = (2.0 * Math.PI) / segments
        val colorInt = color
        val x0 = center.x
        val y0 = center.y
        var p0x = 0f
        var p0y = 0f
        for (i in 0 until segments) {
            val angle = i * step
            val px = x0 + radius * cos(angle).toFloat()
            val py = y0 + radius * sin(angle).toFloat()
            if (i > 0) {
                graphics.fillTriangle(p0x, p0y, px, py, x0, y0, colorInt)
            }
            p0x = px
            p0y = py
        }
    }

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

private fun GuiGraphics.fillTriangle(
    x1: Float, y1: Float,
    x2: Float, y2: Float,
    x3: Float, y3: Float,
    color: Int,
) {
    val matrix = pose().last().pose()
    val r = ((color ushr 16) and 0xFF) / 255.0f
    val g = ((color ushr 8) and 0xFF) / 255.0f
    val b = (color and 0xFF) / 255.0f
    val a = ((color ushr 24) and 0xFF) / 255.0f
    RenderSystem.enableBlend()
    RenderSystem.defaultBlendFunc()
    RenderSystem.setShader(GameRenderer::getPositionColorShader)
    val builder = Tesselator.getInstance().getBuilder()
    builder.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR)
    builder.vertex(matrix, x1, y1, 0f).color(r, g, b, a)
    builder.vertex(matrix, x2, y2, 0f).color(r, g, b, a)
    builder.vertex(matrix, x3, y3, 0f).color(r, g, b, a)
    BufferUploader.drawWithShader(builder.end())
    RenderSystem.disableBlend()
}

private typealias IntOffset = androidx.compose.ui.unit.IntOffset
private typealias IntSize = androidx.compose.ui.unit.IntSize

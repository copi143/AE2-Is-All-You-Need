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
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.VertexMode
import androidx.compose.ui.graphics.Vertices
import androidx.compose.ui.graphics.flattenContours
import androidx.compose.ui.graphics.jvmArgb
import androidx.compose.ui.graphics.jvmGeneration
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.BufferUploader
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.Tesselator
import com.mojang.blaze3d.vertex.VertexFormat
import com.mojang.math.Axis
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.renderer.GameRenderer
import net.minecraft.client.renderer.RenderType
import org.joml.Matrix4f
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL12
import org.lwjgl.system.MemoryUtil
import java.util.WeakHashMap
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Bridges the official androidx.compose Canvas drawing commands to Minecraft's [GuiGraphics]
 * (immediate-mode GUI rendering). No offscreen surface, no skiko: every command is translated
 * directly into GuiGraphics calls or GL triangle/texture draws, so the Compose tree paints with
 * vanilla MC rendering state.
 *
 * Transform support: translate / scale / rotate / skew / concat and intersect/difference clips.
 * Axis-aligned rectangles use scissor; transformed rectangles and paths use offscreen masks.
 * Path fill uses CPU ear-clipping triangulation (EvenOdd holes supported); path stroke is
 * expanded to triangles (miter renders as bevel). Known gaps, kept deliberately:
 * paint shaders / color filters / path effects, saveLayer alpha compositing, vertex textures,
 * shadow / blur render effects.
 *
 * Two modes, driven by the two-phase pipeline in [ComposeOwner]:
 *  - **live** ([graphics] != null): every command executes immediately (legacy fallback path).
 *  - **record** ([recorder] != null): all CPU work (tessellation, colors with alpha baked) runs
 *    now, but the resulting draw is appended to [McDrawRecorder.ops] as a closure executed later
 *    against the live GUI-stage graphics. Pose transforms are applied to the recorder's virtual
 *    pose stack and mirrored as recorded ops, so replay sees identical matrices.
 */
class McCanvas internal constructor(private val graphics: GuiGraphics?, private val recorder: McDrawRecorder?) : Canvas {

    private var alphaMultiplier: Float = 1f
    private val clipDepthAtSave = ArrayDeque<Int>()

    /** Runs [block] with all subsequent drawing commands alpha-multiplied by [alpha]. */
    fun withAlpha(alpha: Float, block: () -> Unit) {
        if (alpha >= 1f) {
            block()
            return
        }
        val previous = alphaMultiplier
        alphaMultiplier = previous * alpha
        try {
            block()
        } finally {
            alphaMultiplier = previous
        }
    }

    private fun withAlpha(color: Int): Int {
        if (alphaMultiplier >= 1f) return color
        val a = ((color ushr 24) and 0xFF) * alphaMultiplier
        return (color and 0x00FFFFFF) or ((a.toInt().coerceIn(0, 0xFF)) shl 24)
    }

    private fun argb(paint: Paint): Int = withAlpha(paint.color.toArgb())

    // Preserve fractional positive widths. Zero-width hairlines retain the logical-pixel fallback.
    private fun strokeWidth(paint: Paint): Float = if (paint.strokeWidth > 0f) paint.strokeWidth else 1f

    /** Executes [op] now (live mode) or appends it to the recording (record mode). */
    private inline fun emit(crossinline op: (GuiGraphics) -> Unit) {
        val r = recorder
        if (r != null) {
            r.ops += { g -> op(g) }
        } else {
            op(graphics!!)
        }
    }

    /** Applies [op] to the active pose stack and, when recording, mirrors it as a recorded op. */
    private inline fun poseOp(crossinline op: (com.mojang.blaze3d.vertex.PoseStack) -> Unit) {
        val r = recorder
        if (r != null) {
            op(r.poseStack)
            r.ops += { g -> op(g.pose()) }
        } else {
            op(graphics!!.pose())
        }
    }

    private fun strokeRect(left: Float, top: Float, right: Float, bottom: Float, paint: Paint) {
        emitSoup(rectangleGeometry(Rect(left, top, right, bottom), argb(paint), strokeWidth(paint),
            paint.strokeJoin, paint.strokeMiterLimit, paint.isAntiAlias))
    }

    override fun save() {
        poseOp { it.pushPose() }
        clipDepthAtSave.addLast(McScissor.depth)
    }

    override fun restore() {
        poseOp { it.popPose() }
        val mark = if (clipDepthAtSave.isEmpty()) 0 else clipDepthAtSave.removeLast()
        while (McScissor.depth > mark) McScissor.pop(graphics)
    }

    override fun saveLayer(bounds: Rect, paint: Paint) = save()

    override fun translate(dx: Float, dy: Float) {
        poseOp { it.translate(dx, dy, 0f) }
    }

    override fun scale(sx: Float, sy: Float) {
        if (sx == 1f && sy == 1f) return
        poseOp { it.scale(sx, sy, 1f) }
    }

    override fun rotate(degrees: Float) {
        poseOp { it.mulPose(Axis.ZP.rotationDegrees(degrees)) }
    }

    override fun skew(sx: Float, sy: Float) {
        if (sx == 0f && sy == 0f) return
        poseOp { pose ->
            pose.mulPoseMatrix(
                Matrix4f().set(
                    1f, sy, 0f, 0f,
                    sx, 1f, 0f, 0f,
                    0f, 0f, 1f, 0f,
                    0f, 0f, 0f, 1f,
                ),
            )
        }
    }

    override fun concat(matrix: Matrix) {
        val v = matrix.values.copyOf()
        poseOp { pose ->
            pose.mulPoseMatrix(
                Matrix4f().set(
                    v[0], v[1], v[2], v[3],
                    v[4], v[5], v[6], v[7],
                    v[8], v[9], v[10], v[11],
                    v[12], v[13], v[14], v[15],
                ),
            )
        }
    }

    override fun clipRect(left: Float, top: Float, right: Float, bottom: Float, clipOp: ClipOp) {
        val matrix = (recorder?.poseStack ?: graphics!!.pose()).last().pose()
        McScissor.pushRect(graphics, Rect(left, top, right, bottom), matrix, clipOp)
    }

    override fun clipPath(path: Path, clipOp: ClipOp) {
        val matrix = (recorder?.poseStack ?: graphics!!.pose()).last().pose()
        McScissor.pushPath(graphics, path, matrix, clipOp)
    }

    override fun drawLine(p1: Offset, p2: Offset, paint: Paint) {
        emitSoup(strokeContours(listOf(listOf(p1, p2)), strokeWidth(paint), paint.strokeCap, paint.strokeJoin, argb(paint)))
    }

    override fun drawRect(left: Float, top: Float, right: Float, bottom: Float, paint: Paint) {
        val rect = Rect(left, top, right, bottom)
        if (rect.isEmpty) return
        if (paint.style == PaintingStyle.Stroke) {
            strokeRect(left, top, right, bottom, paint)
        } else {
            val color = argb(paint)
            val matrix = (recorder?.poseStack ?: graphics!!.pose()).last().pose()
            val integerLocal = listOf(left, top, right, bottom).all { it == it.toInt().toFloat() }
            val aligned = integerLocal && ClipGeometry.axisAligned(matrix) &&
                ClipGeometry.rectangle(rect, matrix).all { it.x == it.x.toInt().toFloat() && it.y == it.y.toInt().toFloat() }
            if (aligned) {
                emit { g -> g.fill(left.toInt(), top.toInt(), right.toInt(), bottom.toInt(), color) }
            } else {
                emitSoup(rectangleGeometry(rect, color, antiAlias = paint.isAntiAlias))
            }
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
        if (radiusX <= 0f || radiusY <= 0f) {
            drawRect(left, top, right, bottom, paint)
            return
        }
        if (right <= left || bottom <= top) return
        val rx = min(radiusX, (right - left) / 2f)
        val ry = min(radiusY, (bottom - top) / 2f)
        val color = argb(paint)
        val strokeHalf = if (paint.style == PaintingStyle.Stroke) strokeWidth(paint) / 2f else 0f
        // Prefer the SDF shader (analytic AA); fall back to tessellation + feather fringe.
        emit { g ->
            if (rx == ry && McShapePipeline.roundRect(g, left, top, right, bottom, rx, color, strokeHalf)) {
                return@emit
            }
            val contour = roundRectContour(left, top, right, bottom, rx, ry)
            val soup = if (strokeHalf > 0f) {
                strokeContours(listOf(contour), strokeHalf * 2f, paint.strokeCap, paint.strokeJoin, color)
            } else {
                fillContours(listOf(contour), PathFillType.NonZero, color)
            }
            flushSoup(g, soup)
        }
    }

    /** Closed polygon approximating a rounded rect: four quarter arcs joined by the edge runs. */
    private fun roundRectContour(
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        rx: Float,
        ry: Float,
    ): List<Offset> {
        val steps = (max(rx, ry) / 2f).toInt().coerceIn(2, 16)
        val pts = ArrayList<Offset>(steps * 4 + 5)
        fun arc(cx: Float, cy: Float, startDegrees: Float) {
            for (i in 0..steps) {
                val a = Math.toRadians(startDegrees + 90.0 * i / steps)
                pts += Offset(cx + rx * cos(a).toFloat(), cy + ry * sin(a).toFloat())
            }
        }
        // Y grows downward: TL starts at 180°, then sweep clockwise through TR, BR, BL.
        arc(left + rx, top + ry, 180f)
        arc(right - rx, top + ry, 270f)
        arc(right - rx, bottom - ry, 0f)
        arc(left + rx, bottom - ry, 90f)
        pts += pts.first()
        return pts
    }

    override fun drawOval(left: Float, top: Float, right: Float, bottom: Float, paint: Paint) {
        val cx = (left + right) / 2f
        val cy = (top + bottom) / 2f
        val rx = abs(right - left) / 2f
        val ry = abs(bottom - top) / 2f
        if (rx <= 0f || ry <= 0f) return
        if (paint.style == PaintingStyle.Stroke) {
            emitSoup(
                strokeContours(
                    listOf(sampleEllipse(cx, cy, rx, ry)),
                    strokeWidth(paint),
                    paint.strokeCap,
                    paint.strokeJoin,
                    argb(paint),
                ),
            )
        } else {
            emitSoup(fillContours(listOf(sampleEllipse(cx, cy, rx, ry)), PathFillType.NonZero, argb(paint)))
        }
    }

    override fun drawCircle(center: Offset, radius: Float, paint: Paint) {
        if (radius <= 0f) return
        val color = argb(paint)
        val strokeHalf = if (paint.style == PaintingStyle.Stroke) strokeWidth(paint) / 2f else 0f
        emit { g ->
            if (McShapePipeline.roundRect(
                    g,
                    center.x - radius, center.y - radius, center.x + radius, center.y + radius,
                    radius, color, strokeHalf,
                )
            ) {
                return@emit
            }
            val contour = sampleEllipse(center.x, center.y, radius, radius)
            val soup = if (strokeHalf > 0f) {
                strokeContours(listOf(contour), strokeHalf * 2f, paint.strokeCap, paint.strokeJoin, color)
            } else {
                fillContours(listOf(contour), PathFillType.NonZero, color)
            }
            flushSoup(g, soup)
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
    ) {
        if (sweepAngle == 0f) return
        val cx = (left + right) / 2f
        val cy = (top + bottom) / 2f
        val rx = abs(right - left) / 2f
        val ry = abs(bottom - top) / 2f
        if (rx <= 0f || ry <= 0f) return
        if (paint.style == PaintingStyle.Stroke) {
            val pts = sampleArcPoints(cx, cy, rx, ry, startAngle, sweepAngle, useCenter)
            emitSoup(strokeContours(listOf(pts), strokeWidth(paint), paint.strokeCap, paint.strokeJoin, argb(paint)))
        } else {
            // Pie (useCenter) / chord contours close implicitly; fillContours feathers the rim.
            val pts = sampleArcPoints(cx, cy, rx, ry, startAngle, sweepAngle, useCenter)
            emitSoup(fillContours(listOf(pts), PathFillType.NonZero, argb(paint)))
        }
    }

    private fun sampleEllipse(cx: Float, cy: Float, rx: Float, ry: Float): List<Offset> {
        val steps = max(24, (max(rx, ry) / 4f).toInt())
        return List(steps + 1) { i ->
            val a = (i.toDouble() / steps * 2.0 * Math.PI).toFloat()
            Offset(cx + rx * cos(a), cy + ry * sin(a))
        }
    }

    private fun sampleArcPoints(
        cx: Float,
        cy: Float,
        rx: Float,
        ry: Float,
        startDegrees: Float,
        sweepDegrees: Float,
        useCenter: Boolean,
    ): List<Offset> {
        val steps = max(2, (abs(sweepDegrees) / 10f).toInt() + 1)
        val pts = ArrayList<Offset>(steps + 2)
        if (useCenter) pts += Offset(cx, cy)
        for (i in 0..steps) {
            val a = Math.toRadians((startDegrees + sweepDegrees * i / steps).toDouble())
            pts += Offset(cx + rx * cos(a).toFloat(), cy + ry * sin(a).toFloat())
        }
        return pts
    }

    override fun drawPath(path: Path, paint: Paint) {
        val contours = path.flattenContours(0.25f)
        if (contours.isEmpty()) {
            return
        }
        val color = argb(paint)
        val soup = if (paint.style == PaintingStyle.Stroke) {
            strokeContours(contours, strokeWidth(paint), paint.strokeCap, paint.strokeJoin, color)
        } else {
            fillContours(contours, path.fillType, color)
        }
        emitSoup(soup)
    }

    override fun drawImage(image: ImageBitmap, topLeftOffset: Offset, paint: Paint) {
        val alpha = paint.alpha * alphaMultiplier
        emit { g ->
            drawTexturedQuad(
                g,
                image,
                srcX = 0, srcY = 0, srcW = image.width, srcH = image.height,
                dstX = topLeftOffset.x, dstY = topLeftOffset.y,
                dstW = image.width.toFloat(), dstH = image.height.toFloat(),
                alpha = alpha,
            )
        }
    }

    override fun drawImageRect(
        image: ImageBitmap,
        srcOffset: IntOffset,
        srcSize: IntSize,
        dstOffset: IntOffset,
        dstSize: IntSize,
        paint: Paint,
    ) {
        if (srcSize.width <= 0 || srcSize.height <= 0 || dstSize.width <= 0 || dstSize.height <= 0) return
        val alpha = paint.alpha * alphaMultiplier
        emit { g ->
            drawTexturedQuad(
                g,
                image,
                srcX = srcOffset.x, srcY = srcOffset.y, srcW = srcSize.width, srcH = srcSize.height,
                dstX = dstOffset.x.toFloat(), dstY = dstOffset.y.toFloat(),
                dstW = dstSize.width.toFloat(), dstH = dstSize.height.toFloat(),
                alpha = alpha,
            )
        }
    }

    private fun drawTexturedQuad(
        graphics: GuiGraphics,
        image: ImageBitmap,
        srcX: Int, srcY: Int, srcW: Int, srcH: Int,
        dstX: Float, dstY: Float, dstW: Float, dstH: Float,
        alpha: Float,
    ) {
        val tex = McTextureCache.textureFor(image) ?: return
        graphics.flush()
        val matrix = graphics.pose().last().pose()
        val u0 = srcX.toFloat() / tex.width
        val v0 = (srcY + srcH).toFloat() / tex.height
        val u1 = (srcX + srcW).toFloat() / tex.width
        val v1 = srcY.toFloat() / tex.height
        RenderSystem.disableDepthTest()
        RenderSystem.enableBlend()
        RenderSystem.defaultBlendFunc()
        RenderSystem.setShader(GameRenderer::getPositionTexShader)
        RenderSystem.setShaderTexture(0, tex.id)
        RenderSystem.setShaderColor(1f, 1f, 1f, alpha.coerceIn(0f, 1f))
        val builder = Tesselator.getInstance().getBuilder()
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX)
        builder.vertex(matrix, dstX, dstY + dstH, 0f).uv(u0, v0).endVertex()
        builder.vertex(matrix, dstX + dstW, dstY + dstH, 0f).uv(u1, v0).endVertex()
        builder.vertex(matrix, dstX + dstW, dstY, 0f).uv(u1, v1).endVertex()
        builder.vertex(matrix, dstX, dstY, 0f).uv(u0, v1).endVertex()
        BufferUploader.drawWithShader(builder.end())
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f)
        RenderSystem.disableBlend()
    }

    override fun drawPoints(pointMode: PointMode, points: List<Offset>, paint: Paint) {
        val color = argb(paint)
        when (pointMode) {
            PointMode.Points -> {
                val r = strokeWidth(paint) / 2f
                val soup = TriangleSoup()
                for (p in points) {
                    soup.tri(p.x - r, p.y - r, p.x + r, p.y - r, p.x + r, p.y + r, color)
                    soup.tri(p.x - r, p.y - r, p.x + r, p.y + r, p.x - r, p.y + r, color)
                }
                emitSoup(soup)
            }
            PointMode.Lines -> {
                val contours = ArrayList<List<Offset>>()
                var i = 0
                while (i + 1 < points.size) {
                    contours += listOf(points[i], points[i + 1])
                    i += 2
                }
                emitSoup(strokeContours(contours, strokeWidth(paint), StrokeCap.Butt, StrokeJoin.Bevel, color))
            }
            PointMode.Polygon -> {
                if (points.size >= 2) {
                    emitSoup(strokeContours(listOf(points), strokeWidth(paint), paint.strokeCap, paint.strokeJoin, color))
                }
            }
        }
    }

    override fun drawRawPoints(pointMode: PointMode, points: FloatArray, paint: Paint) {
        val list = ArrayList<Offset>(points.size / 2)
        var i = 0
        while (i + 1 < points.size) {
            list += Offset(points[i], points[i + 1])
            i += 2
        }
        drawPoints(pointMode, list, paint)
    }

    override fun drawVertices(vertices: Vertices, blendMode: BlendMode, paint: Paint) {
        // Vertex textures are not supported; positions and per-vertex colors are honored.
        val positions = vertices.positions
        val colors = vertices.colors
        val indices = vertices.indices
        val fallback = argb(paint)
        fun colorAt(i: Int): Int = if (i in colors.indices) colors[i] else fallback
        val soup = TriangleSoup()
        fun tri(a: Int, b: Int, c: Int) {
            soup.positions.add(positions[a * 2]); soup.positions.add(positions[a * 2 + 1])
            soup.positions.add(positions[b * 2]); soup.positions.add(positions[b * 2 + 1])
            soup.positions.add(positions[c * 2]); soup.positions.add(positions[c * 2 + 1])
            soup.colors.add(colorAt(a)); soup.colors.add(colorAt(b)); soup.colors.add(colorAt(c))
        }
        val count = positions.size / 2
        if (indices.isNotEmpty()) {
            var i = 0
            when (vertices.vertexMode) {
                VertexMode.Triangles -> while (i + 2 < indices.size) {
                    tri(indices[i].toInt(), indices[i + 1].toInt(), indices[i + 2].toInt())
                    i += 3
                }
                VertexMode.TriangleStrip -> while (i + 2 < indices.size) {
                    if (i % 2 == 0) tri(indices[i].toInt(), indices[i + 1].toInt(), indices[i + 2].toInt())
                    else tri(indices[i + 1].toInt(), indices[i].toInt(), indices[i + 2].toInt())
                    i++
                }
                else -> while (i + 2 < indices.size) {
                    tri(indices[0].toInt(), indices[i + 1].toInt(), indices[i + 2].toInt())
                    i++
                }
            }
        } else {
            when (vertices.vertexMode) {
                VertexMode.Triangles -> {
                    var i = 0
                    while (i + 2 < count) {
                        tri(i, i + 1, i + 2)
                        i += 3
                    }
                }
                VertexMode.TriangleStrip -> {
                    var i = 0
                    while (i + 2 < count) {
                        if (i % 2 == 0) tri(i, i + 1, i + 2) else tri(i + 1, i, i + 2)
                        i++
                    }
                }
                else -> {
                    var i = 1
                    while (i + 1 < count) {
                        tri(0, i, i + 1)
                        i++
                    }
                }
            }
        }
        emitSoup(soup)
    }

    private fun emitSoup(soup: TriangleSoup) {
        if (soup.isEmpty()) return
        emit { g -> flushSoup(g, soup) }
    }

    private fun flushSoup(graphics: GuiGraphics, soup: TriangleSoup) {
        // Fast path: own GL pipeline (no winding normalization needed — culling is off there).
        if (McShapePipeline.draw(graphics, soup)) return
        val matrix = graphics.pose().last().pose()
        // Fallback: batch into the shared RenderType.gui() buffer so all color geometry of the
        // pass merges into one draw call with vanilla fills. gui() culls back faces: the GUI
        // projection flips Y, so only triangles with negative signed area (in post-transform
        // coordinates) survive — normalize winding per triangle before writing.
        val consumer = graphics.bufferSource().getBuffer(RenderType.gui())
        val mirrored = matrix.m00() * matrix.m11() - matrix.m01() * matrix.m10() < 0f
        val pos = soup.positions
        val col = soup.colors
        var i = 0
        var v = 0
        while (i < pos.size) {
            var c1 = col[v]
            var c2 = col[v + 1]
            var c3 = col[v + 2]
            val x1 = pos[i]
            val y1 = pos[i + 1]
            var x2 = pos[i + 2]
            var y2 = pos[i + 3]
            var x3 = pos[i + 4]
            var y3 = pos[i + 5]
            val area2 = (x2 - x1) * (y3 - y1) - (x3 - x1) * (y2 - y1)
            val backFacing = if (mirrored) area2 < 0f else area2 > 0f
            if (backFacing) {
                val tx = x2; x2 = x3; x3 = tx
                val ty = y2; y2 = y3; y3 = ty
                val tc = c2; c2 = c3; c3 = tc
            }
            // As QUADS with degenerate 4th vertex
            consumer.vertex(matrix, x1, y1, 0f)
                .color((c1 ushr 16) and 0xFF, (c1 ushr 8) and 0xFF, c1 and 0xFF, (c1 ushr 24) and 0xFF)
                .endVertex()
            consumer.vertex(matrix, x2, y2, 0f)
                .color((c2 ushr 16) and 0xFF, (c2 ushr 8) and 0xFF, c2 and 0xFF, (c2 ushr 24) and 0xFF)
                .endVertex()
            consumer.vertex(matrix, x3, y3, 0f)
                .color((c3 ushr 16) and 0xFF, (c3 ushr 8) and 0xFF, c3 and 0xFF, (c3 ushr 24) and 0xFF)
                .endVertex()
            consumer.vertex(matrix, x3, y3, 0f)
                .color((c3 ushr 16) and 0xFF, (c3 ushr 8) and 0xFF, c3 and 0xFF, (c3 ushr 24) and 0xFF)
                .endVertex()
            i += 6
            v += 3
        }
    }

    override fun enableZ() = Unit

    override fun disableZ() = Unit
}

/**
 * GL texture backing for framework [ImageBitmap]s. Uploads once per bitmap
 * instance and re-uploads when its pixel generation changes; GL ids are
 * deleted when replaced. Must be used on the render thread.
 */
internal object McTextureCache {
    internal data class Entry(val id: Int, val generation: Int, val width: Int, val height: Int)

    private val cache = WeakHashMap<ImageBitmap, Entry>()

    fun textureFor(image: ImageBitmap): Entry? {
        val width = image.width
        val height = image.height
        if (width <= 0 || height <= 0) return null
        val generation = runCatching { image.jvmGeneration() }.getOrNull() ?: 0
        val cached = cache[image]
        if (cached != null && cached.generation == generation && cached.width == width && cached.height == height) {
            return cached
        }
        if (cached != null) {
            runCatching { GL11.glDeleteTextures(cached.id) }
        }
        val pixels = runCatching { image.jvmArgb() }.getOrNull() ?: return null
        if (pixels.size != width * height) return null
        val id = GL11.glGenTextures()
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, id)
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR)
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR)
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE)
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE)
        // GL 驱动只接受直接内存；逐像素展开为 RGBA 字节，避免任何字节序歧义。
        val buffer = MemoryUtil.memAlloc(pixels.size * 4)
        try {
            for (pixel in pixels) {
                buffer.put((pixel shr 16).toByte())
                buffer.put((pixel shr 8).toByte())
                buffer.put(pixel.toByte())
                buffer.put((pixel ushr 24).toByte())
            }
            buffer.flip()
            GL11.glTexImage2D(
                GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, width, height, 0,
                GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buffer,
            )
        } finally {
            MemoryUtil.memFree(buffer)
        }
        val entry = Entry(id, generation, width, height)
        cache[image] = entry
        return entry
    }
}

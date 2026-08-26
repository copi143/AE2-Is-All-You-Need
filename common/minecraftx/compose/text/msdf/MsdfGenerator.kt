package minecraftx.compose.text.msdf

import java.awt.Color
import java.awt.geom.PathIterator
import java.awt.image.BufferedImage
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt

internal data class MsdfPoint(val x: Float, val y: Float)

internal class MsdfEdge(
    val ax: Float, val ay: Float,
    val bx: Float, val by: Float,
    val channels: Int,
)

class MsdfBitmap(
    val width: Int,
    val height: Int,
    val originX: Float,
    val originY: Float,
    val pixels: ByteArray,
    val pxRange: Float,
)

object MsdfGenerator {
    const val PX_RANGE = 8f
    private const val INF = 1e6f
    private const val RED = 1
    private const val GREEN = 2
    private const val BLUE = 4
    private const val CYAN = 6
    private const val WHITE = 7
    private val CROSS_THRESH = sin(3.0).toFloat()

    internal fun generate(face: AwtFontFace, cp: Int, pxRange: Float = PX_RANGE): MsdfBitmap? {
        val outline = face.glyphVector(cp).getGlyphOutline(0)
        val bounds = outline.bounds2D
        if (bounds.width <= 0.0 && bounds.height <= 0.0) return null
        val contours = parse(outline)
        if (contours.isEmpty()) return null
        colorSimple(contours)
        val pad = pxRange * 0.5f + 1f
        val x0 = floor(bounds.minX - pad).toFloat()
        val y0 = floor(bounds.minY - pad).toFloat()
        val x1 = ceil(bounds.maxX + pad).toFloat()
        val y1 = ceil(bounds.maxY + pad).toFloat()
        val w = (x1 - x0).toInt().coerceAtLeast(1)
        val h = (y1 - y0).toInt().coerceAtLeast(1)
        val segs = contours.flatten()
        if (segs.isEmpty()) return null
        return raster(segs, w, h, x0, y0, pxRange, rasterMask(outline, w, h, x0, y0))
    }

    internal fun generate(edges: List<MsdfEdge>, width: Int, height: Int, originX: Float, originY: Float, pxRange: Float): MsdfBitmap =
        raster(edges.map { Seg.line(it.ax, it.ay, it.bx, it.by, it.channels) }, width, height, originX, originY, pxRange, null)

    internal fun colorEdges(contours: List<List<MsdfPoint>>): List<MsdfEdge> {
        val segs = contours.map { pts ->
            val closed = closeContour(pts)
            closed.indices.map { i ->
                val a = closed[i]
                val b = closed[(i + 1) % closed.size]
                Seg.line(a.x, a.y, b.x, b.y)
            }.toMutableList()
        }
        colorSimple(segs)
        return segs.flatten().map { MsdfEdge(it.x0, it.y0, it.x3, it.y3, it.color) }
    }

    private class Seg(
        val x0: Float, val y0: Float,
        val x1: Float, val y1: Float,
        val x2: Float, val y2: Float,
        val x3: Float, val y3: Float,
        val kind: Int,
        var color: Int = WHITE,
    ) {
        fun startDir(): Pair<Float, Float> = when (kind) {
            1 -> Pair(x3 - x0, y3 - y0)
            2 -> dir(x1 - x0, y1 - y0, x3 - x0, y3 - y0)
            else -> dir(x1 - x0, y1 - y0, x3 - x0, y3 - y0)
        }

        fun endDir(): Pair<Float, Float> = when (kind) {
            1 -> Pair(x3 - x0, y3 - y0)
            2 -> dir(x3 - x1, y3 - y1, x3 - x0, y3 - y0)
            else -> dir(x3 - x2, y3 - y2, x3 - x0, y3 - y0)
        }

        companion object {
            fun line(ax: Float, ay: Float, bx: Float, by: Float, color: Int = WHITE) =
                Seg(ax, ay, bx, by, bx, by, bx, by, 1, color)
        }
    }

    private fun dir(dx: Float, dy: Float, fbX: Float, fbY: Float): Pair<Float, Float> =
        if (dx * dx + dy * dy < 1e-12f) Pair(fbX, fbY) else Pair(dx, dy)

    private fun parse(shape: java.awt.Shape): List<MutableList<Seg>> {
        val contours = ArrayList<MutableList<Seg>>()
        var current = ArrayList<Seg>()
        val coords = FloatArray(6)
        val it = shape.getPathIterator(null)
        var sx = 0f
        var sy = 0f
        var cx = 0f
        var cy = 0f
        while (!it.isDone) {
            when (it.currentSegment(coords)) {
                PathIterator.SEG_MOVETO -> {
                    if (current.isNotEmpty()) contours += current
                    current = ArrayList()
                    sx = coords[0]; sy = coords[1]
                    cx = sx; cy = sy
                }
                PathIterator.SEG_LINETO -> {
                    current += Seg.line(cx, cy, coords[0], coords[1])
                    cx = coords[0]; cy = coords[1]
                }
                PathIterator.SEG_QUADTO -> {
                    current += Seg(cx, cy, coords[0], coords[1], coords[2], coords[3], coords[2], coords[3], 2)
                    cx = coords[2]; cy = coords[3]
                }
                PathIterator.SEG_CUBICTO -> {
                    current += Seg(cx, cy, coords[0], coords[1], coords[2], coords[3], coords[4], coords[5], 3)
                    cx = coords[4]; cy = coords[5]
                }
                PathIterator.SEG_CLOSE -> {
                    if (abs(cx - sx) > 1e-5f || abs(cy - sy) > 1e-5f) {
                        current += Seg.line(cx, cy, sx, sy)
                    }
                    if (current.isNotEmpty()) contours += current
                    current = ArrayList()
                    cx = sx; cy = sy
                }
            }
            it.next()
        }
        if (current.isNotEmpty()) contours += current
        return contours
    }

    private fun colorSimple(contours: List<MutableList<Seg>>) {
        var color = CYAN
        for (contour in contours) {
            if (contour.isEmpty()) continue
            val n = contour.size
            val corners = ArrayList<Int>()
            var prevDir = contour[n - 1].endDir()
            for (i in 0 until n) {
                val nextDir = contour[i].startDir()
                if (isCorner(prevDir, nextDir)) corners += i
                prevDir = contour[i].endDir()
            }
            when (corners.size) {
                0 -> {
                    color = switchColor(color)
                    for (e in contour) e.color = color
                }
                1 -> {
                    color = switchColor(color)
                    val c0 = color
                    color = switchColor(color)
                    val c2 = color
                    val corner = corners[0]
                    if (n >= 3) {
                        for (i in 0 until n) {
                            contour[(corner + i) % n].color = when (symTrichotomy(i, n)) {
                                -1 -> c0
                                0 -> WHITE
                                else -> c2
                            }
                        }
                    } else {
                        for (e in contour) e.color = WHITE
                    }
                }
                else -> {
                    val start = corners[0]
                    color = switchColor(color)
                    val initial = color
                    var spline = 0
                    for (i in 0 until n) {
                        val index = (start + i) % n
                        if (spline + 1 < corners.size && corners[spline + 1] == index) {
                            spline++
                            color = if (spline == corners.size - 1) switchColor(color, initial) else switchColor(color)
                        }
                        contour[index].color = color
                    }
                }
            }
        }
    }

    private fun switchColor(color: Int): Int {
        val shifted = color shl 1
        return (shifted or (shifted shr 3)) and WHITE
    }

    private fun switchColor(color: Int, banned: Int): Int {
        val combined = color and banned
        return if (combined == RED || combined == GREEN || combined == BLUE) combined xor WHITE
        else switchColor(color)
    }

    private fun symTrichotomy(position: Int, n: Int): Int {
        if (n <= 1) return 0
        return (3.0 + 2.875 * position / (n - 1) - 1.4375 + 0.5).toInt() - 3
    }

    private class Near {
        var d = INF
        var ad = INF
        var param = 0f
        var seg: Seg? = null
        fun consider(sd: Float, p: Float, s: Seg) {
            val a = abs(sd)
            if (a < ad) {
                ad = a
                d = sd
                param = p
                seg = s
            }
        }
    }

    private fun raster(
        segs: List<Seg>,
        w: Int,
        h: Int,
        originX: Float,
        originY: Float,
        pxRange: Float,
        mask: BooleanArray?,
    ): MsdfBitmap {
        val enc = FloatArray(w * h * 3)
        val truArr = FloatArray(w * h)
        val fill = BooleanArray(w * h)
        var agree = 0
        var disagree = 0
        val nr = Near()
        val ng = Near()
        val nb = Near()
        val nt = Near()
        for (y in 0 until h) {
            val py = originY + y + 0.5f
            for (x in 0 until w) {
                val px = originX + x + 0.5f
                nr.ad = INF; ng.ad = INF; nb.ad = INF; nt.ad = INF
                nr.seg = null; ng.seg = null; nb.seg = null
                for (s in segs) {
                    val (sd, param) = signedDist(px, py, s)
                    nt.consider(sd, param, s)
                    if (s.color and RED != 0) nr.consider(sd, param, s)
                    if (s.color and GREEN != 0) ng.consider(sd, param, s)
                    if (s.color and BLUE != 0) nb.consider(sd, param, s)
                }
                if (nr.seg == null) nr.d = nt.d
                if (ng.seg == null) ng.d = nt.d
                if (nb.seg == null) nb.d = nt.d
                nr.seg?.let { nr.d = toPerp(nr.d, nr.param, px, py, it) }
                ng.seg?.let { ng.d = toPerp(ng.d, ng.param, px, py, it) }
                nb.seg?.let { nb.d = toPerp(nb.d, nb.param, px, py, it) }
                val i = y * w + x
                val inside = mask?.get(i) ?: evenOdd(px, py, segs)
                fill[i] = inside
                enc[i * 3] = nr.d
                enc[i * 3 + 1] = ng.d
                enc[i * 3 + 2] = nb.d
                truArr[i] = nt.d
                if (nt.ad > 0.75f) {
                    if ((nt.d > 0f) == inside) agree++ else disagree++
                }
            }
        }
        val flip = disagree > agree
        val range = pxRange
        for (i in 0 until w * h) {
            var r = enc[i * 3]
            var g = enc[i * 3 + 1]
            var b = enc[i * 3 + 2]
            var tru = truArr[i]
            if (flip) {
                r = -r; g = -g; b = -b; tru = -tru
            }
            val inside = fill[i]
            if ((median(r, g, b) > 0f) != inside) {
                val s = if (inside) abs(tru) else -abs(tru)
                r = s; g = s; b = s
            }
            enc[i * 3] = r / range + 0.5f
            enc[i * 3 + 1] = g / range + 0.5f
            enc[i * 3 + 2] = b / range + 0.5f
        }
        msdfErrorCorrection(enc, w, h, 1.001f / pxRange)
        val pixels = ByteArray(w * h * 4)
        for (i in 0 until w * h) {
            val r = enc[i * 3]
            val g = enc[i * 3 + 1]
            val b = enc[i * 3 + 2]
            val o = i * 4
            pixels[o] = toByte(r)
            pixels[o + 1] = toByte(g)
            pixels[o + 2] = toByte(b)
            pixels[o + 3] = toByte(median(r, g, b))
        }
        return MsdfBitmap(w, h, originX, originY, pixels, pxRange)
    }

    private fun msdfErrorCorrection(enc: FloatArray, w: Int, h: Int, threshold: Float) {
        fun clash(i: Int, j: Int, t: Float): Boolean {
            var a0 = enc[i]; var a1 = enc[i + 1]; var a2 = enc[i + 2]
            var b0 = enc[j]; var b1 = enc[j + 1]; var b2 = enc[j + 2]
            if (abs(b0 - a0) < abs(b1 - a1)) {
                var tmp = a0; a0 = a1; a1 = tmp
                tmp = b0; b0 = b1; b1 = tmp
            }
            if (abs(b1 - a1) < abs(b2 - a2)) {
                var tmp = a1; a1 = a2; a2 = tmp
                tmp = b1; b1 = b2; b2 = tmp
                if (abs(b0 - a0) < abs(b1 - a1)) {
                    tmp = a0; a0 = a1; a1 = tmp
                    tmp = b0; b0 = b1; b1 = tmp
                }
            }
            return abs(b1 - a1) >= t &&
                !(b0 == b1 && b0 == b2) &&
                abs(a2 - 0.5f) >= abs(b2 - 0.5f)
        }
        fun equalize(hits: BooleanArray) {
            for (i in 0 until w * h) {
                if (!hits[i]) continue
                val o = i * 3
                val med = median(enc[o], enc[o + 1], enc[o + 2])
                enc[o] = med; enc[o + 1] = med; enc[o + 2] = med
            }
        }
        val hits = BooleanArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val i = y * w + x
                val o = i * 3
                if (
                    (x > 0 && clash(o, o - 3, threshold)) ||
                    (x < w - 1 && clash(o, o + 3, threshold)) ||
                    (y > 0 && clash(o, (i - w) * 3, threshold)) ||
                    (y < h - 1 && clash(o, (i + w) * 3, threshold))
                ) hits[i] = true
            }
        }
        equalize(hits)
        hits.fill(false)
        val diag = threshold + threshold
        for (y in 0 until h) {
            for (x in 0 until w) {
                val i = y * w + x
                val o = i * 3
                if (
                    (x > 0 && y > 0 && clash(o, (i - w - 1) * 3, diag)) ||
                    (x < w - 1 && y > 0 && clash(o, (i - w + 1) * 3, diag)) ||
                    (x > 0 && y < h - 1 && clash(o, (i + w - 1) * 3, diag)) ||
                    (x < w - 1 && y < h - 1 && clash(o, (i + w + 1) * 3, diag))
                ) hits[i] = true
            }
        }
        equalize(hits)
    }

    private fun toByte(v: Float): Byte =
        (v.coerceIn(0f, 1f) * 255f + 0.5f).toInt().coerceIn(0, 255).toByte()

    private fun signedDist(px: Float, py: Float, s: Seg): Pair<Float, Float> = when (s.kind) {
        1 -> signedLine(px, py, s.x0, s.y0, s.x3, s.y3)
        2 -> signedQuad(px, py, s)
        else -> signedCubic(px, py, s)
    }

    private fun signedLine(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Pair<Float, Float> {
        val aqx = px - ax
        val aqy = py - ay
        val abx = bx - ax
        val aby = by - ay
        val ab2 = abx * abx + aby * aby
        val param = if (ab2 > 1e-20f) (aqx * abx + aqy * aby) / ab2 else 0.5f
        val eqx = if (param > 0.5f) bx - px else ax - px
        val eqy = if (param > 0.5f) by - py else ay - py
        val endpoint = hypot(eqx, eqy)
        if (param > 0f && param < 1f) {
            val len = sqrt(ab2)
            val ortho = if (len > 1e-12f) (aqx * aby - aqy * abx) / len else 0f
            if (abs(ortho) < endpoint) return ortho to param
        }
        val cross = aqx * aby - aqy * abx
        return (if (cross > 0f) 1f else -1f) * endpoint to param
    }

    private fun signedQuad(px: Float, py: Float, s: Seg): Pair<Float, Float> {
        val p0x = s.x0.toDouble(); val p0y = s.y0.toDouble()
        val p1x = s.x1.toDouble(); val p1y = s.y1.toDouble()
        val p2x = s.x3.toDouble(); val p2y = s.y3.toDouble()
        val ox = px.toDouble(); val oy = py.toDouble()
        val qax = p0x - ox; val qay = p0y - oy
        val abx = p1x - p0x; val aby = p1y - p0y
        val brx = p2x - p1x - abx; val bry = p2y - p1y - aby
        val t = DoubleArray(3)
        val n = solveCubic(
            t,
            brx * brx + bry * bry,
            3.0 * (abx * brx + aby * bry),
            2.0 * (abx * abx + aby * aby) + (qax * brx + qay * bry),
            qax * abx + qay * aby,
        )
        var epx = abx; var epy = aby
        if (epx * epx + epy * epy < 1e-20) {
            epx = p2x - p0x; epy = p2y - p0y
        }
        var minD = nzSign(epx * qay - epy * qax) * hypot(qax, qay)
        var param = -(qax * epx + qay * epy) / (epx * epx + epy * epy).let { if (it < 1e-20) 1.0 else it }
        val distB = hypot(p2x - ox, p2y - oy)
        if (distB < abs(minD)) {
            epx = p2x - p1x; epy = p2y - p1y
            if (epx * epx + epy * epy < 1e-20) {
                epx = p2x - p0x; epy = p2y - p0y
            }
            minD = nzSign(epx * (p2y - oy) - epy * (p2x - ox)) * distB
            val den = epx * epx + epy * epy
            param = if (den < 1e-20) 1.0 else ((ox - p1x) * epx + (oy - p1y) * epy) / den
        }
        for (i in 0 until n) {
            val ti = t[i]
            if (ti <= 0.0 || ti >= 1.0) continue
            val qex = qax + 2.0 * ti * abx + ti * ti * brx
            val qey = qay + 2.0 * ti * aby + ti * ti * bry
            val distance = hypot(qex, qey)
            if (distance <= abs(minD)) {
                val dx = abx + ti * brx
                val dy = aby + ti * bry
                minD = nzSign(dx * qey - dy * qex) * distance
                param = ti
            }
        }
        return minD.toFloat() to param.toFloat()
    }

    private fun signedCubic(px: Float, py: Float, s: Seg): Pair<Float, Float> {
        val p0x = s.x0.toDouble(); val p0y = s.y0.toDouble()
        val p1x = s.x1.toDouble(); val p1y = s.y1.toDouble()
        val p2x = s.x2.toDouble(); val p2y = s.y2.toDouble()
        val p3x = s.x3.toDouble(); val p3y = s.y3.toDouble()
        val ox = px.toDouble(); val oy = py.toDouble()
        val qax = p0x - ox; val qay = p0y - oy
        val abx = p1x - p0x; val aby = p1y - p0y
        val brx = p2x - p1x - abx; val bry = p2y - p1y - aby
        val asx = (p3x - p2x) - (p2x - p1x) - brx
        val asy = (p3y - p2y) - (p2y - p1y) - bry
        var epx = abx; var epy = aby
        if (epx * epx + epy * epy < 1e-20) {
            epx = p2x - p0x; epy = p2y - p0y
        }
        var minD = nzSign(epx * qay - epy * qax) * hypot(qax, qay)
        var param = -(qax * epx + qay * epy) / (epx * epx + epy * epy).let { if (it < 1e-20) 1.0 else it }
        val distB = hypot(p3x - ox, p3y - oy)
        if (distB < abs(minD)) {
            epx = p3x - p2x; epy = p3y - p2y
            if (epx * epx + epy * epy < 1e-20) {
                epx = p3x - p1x; epy = p3y - p1y
            }
            minD = nzSign(epx * (p3y - oy) - epy * (p3x - ox)) * distB
            val den = epx * epx + epy * epy
            param = if (den < 1e-20) 1.0 else ((epx - (p3x - ox)) * epx + (epy - (p3y - oy)) * epy) / den
        }
        for (i in 0..4) {
            var t = i / 4.0
            var qex = qax + 3 * t * abx + 3 * t * t * brx + t * t * t * asx
            var qey = qay + 3 * t * aby + 3 * t * t * bry + t * t * t * asy
            var d1x = 3 * abx + 6 * t * brx + 3 * t * t * asx
            var d1y = 3 * aby + 6 * t * bry + 3 * t * t * asy
            val d2x = 6 * brx + 6 * t * asx
            val d2y = 6 * bry + 6 * t * asy
            val den = d1x * d1x + d1y * d1y + qex * d2x + qey * d2y
            var improved = if (abs(den) < 1e-20) t else t - (qex * d1x + qey * d1y) / den
            if (improved <= 0.0 || improved >= 1.0) continue
            repeat(4) {
                t = improved
                qex = qax + 3 * t * abx + 3 * t * t * brx + t * t * t * asx
                qey = qay + 3 * t * aby + 3 * t * t * bry + t * t * t * asy
                d1x = 3 * abx + 6 * t * brx + 3 * t * t * asx
                d1y = 3 * aby + 6 * t * bry + 3 * t * t * asy
                val d2x2 = 6 * brx + 6 * t * asx
                val d2y2 = 6 * bry + 6 * t * asy
                val den2 = d1x * d1x + d1y * d1y + qex * d2x2 + qey * d2y2
                improved = if (abs(den2) < 1e-20) t else t - (qex * d1x + qey * d1y) / den2
                if (improved <= 0.0 || improved >= 1.0) return@repeat
            }
            val distance = hypot(qex, qey)
            if (distance < abs(minD)) {
                minD = nzSign(d1x * qey - d1y * qex) * distance
                param = t
            }
        }
        return minD.toFloat() to param.toFloat()
    }

    private fun toPerp(dist: Float, param: Float, px: Float, py: Float, s: Seg): Float {
        if (param in 0f..1f) return dist
        if (param < 0f) {
            val (dx, dy) = s.startDir()
            val len = hypot(dx, dy)
            if (len < 1e-12f) return dist
            val dirx = dx / len
            val diry = dy / len
            val aqx = px - s.x0
            val aqy = py - s.y0
            if (aqx * dirx + aqy * diry < 0f) {
                val perp = aqx * diry - aqy * dirx
                if (abs(perp) <= abs(dist)) return perp
            }
        } else {
            val (dx, dy) = s.endDir()
            val len = hypot(dx, dy)
            if (len < 1e-12f) return dist
            val dirx = dx / len
            val diry = dy / len
            val bqx = px - s.x3
            val bqy = py - s.y3
            if (bqx * dirx + bqy * diry > 0f) {
                val perp = bqx * diry - bqy * dirx
                if (abs(perp) <= abs(dist)) return perp
            }
        }
        return dist
    }

    private fun rasterMask(outline: java.awt.Shape, w: Int, h: Int, originX: Float, originY: Float): BooleanArray {
        val img = BufferedImage(w, h, BufferedImage.TYPE_BYTE_GRAY)
        val g2 = img.createGraphics()
        g2.color = Color.WHITE
        g2.translate(-originX.toDouble(), -originY.toDouble())
        g2.fill(outline)
        g2.dispose()
        val samples = img.raster
        val out = BooleanArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                out[y * w + x] = samples.getSample(x, y, 0) > 0
            }
        }
        return out
    }

    private fun evenOdd(px: Float, py: Float, segs: List<Seg>): Boolean {
        var inside = false
        for (s in segs) {
            if (s.kind != 1) continue
            if ((s.y0 > py) != (s.y3 > py)) {
                val t = (py - s.y0) / (s.y3 - s.y0)
                if (px < s.x0 + (s.x3 - s.x0) * t) inside = !inside
            }
        }
        return inside
    }

    private fun closeContour(raw: List<MsdfPoint>): List<MsdfPoint> {
        if (raw.size < 2) return raw
        val first = raw.first()
        val last = raw.last()
        return if (first.x == last.x && first.y == last.y) raw.dropLast(1) else raw
    }

    private fun isCorner(prev: Pair<Float, Float>, next: Pair<Float, Float>): Boolean {
        val pn = hypot(prev.first, prev.second)
        val cn = hypot(next.first, next.second)
        if (pn < 1e-6f || cn < 1e-6f) return false
        val ax = prev.first / pn
        val ay = prev.second / pn
        val bx = next.first / cn
        val by = next.second / cn
        return ax * bx + ay * by <= 0f || abs(ax * by - ay * bx) > CROSS_THRESH
    }

    private fun solveCubic(x: DoubleArray, a: Double, b: Double, c: Double, d: Double): Int {
        if (a != 0.0 && abs(b / a) < 1e6) return solveCubicNormed(x, b / a, c / a, d / a)
        return solveQuadratic(x, b, c, d)
    }

    private fun solveQuadratic(x: DoubleArray, a: Double, b: Double, c: Double): Int {
        if (a == 0.0 || abs(b) > 1e12 * abs(a)) {
            if (b == 0.0) return 0
            x[0] = -c / b
            return 1
        }
        var dscr = b * b - 4 * a * c
        if (dscr > 0.0) {
            dscr = sqrt(dscr)
            x[0] = (-b + dscr) / (2 * a)
            x[1] = (-b - dscr) / (2 * a)
            return 2
        }
        if (dscr == 0.0) {
            x[0] = -b / (2 * a)
            return 1
        }
        return 0
    }

    private fun solveCubicNormed(x: DoubleArray, a0: Double, b: Double, c: Double): Int {
        var a = a0
        val a2 = a * a
        val q = (a2 - 3 * b) / 9.0
        val r = (a * (2 * a2 - 9 * b) + 27 * c) / 54.0
        val r2 = r * r
        val q3 = q * q * q
        a *= 1.0 / 3.0
        if (r2 < q3) {
            var t = r / sqrt(q3)
            t = t.coerceIn(-1.0, 1.0)
            t = acos(t)
            val qq = -2 * sqrt(q)
            x[0] = qq * cos(t / 3.0) - a
            x[1] = qq * cos((t + 2 * PI) / 3.0) - a
            x[2] = qq * cos((t - 2 * PI) / 3.0) - a
            return 3
        }
        val u = (if (r < 0) 1.0 else -1.0) * Math.pow(abs(r) + sqrt(r2 - q3), 1.0 / 3.0)
        val v = if (u == 0.0) 0.0 else q / u
        x[0] = (u + v) - a
        if (u == v || abs(u - v) < 1e-12 * abs(u + v)) {
            x[1] = -0.5 * (u + v) - a
            return 2
        }
        return 1
    }

    private fun nzSign(v: Double): Double = if (v > 0.0) 1.0 else -1.0

    private fun hypot(x: Float, y: Float): Float = sqrt(x * x + y * y)

    private fun hypot(x: Double, y: Double): Double = sqrt(x * x + y * y)

    private fun median(a: Float, b: Float, c: Float): Float =
        maxOf(minOf(a, b), minOf(maxOf(a, b), c))

    private fun encode(d: Float, range: Float): Byte {
        val v = (d / range + 0.5f).coerceIn(0f, 1f)
        return (v * 255f + 0.5f).toInt().coerceIn(0, 255).toByte()
    }

}

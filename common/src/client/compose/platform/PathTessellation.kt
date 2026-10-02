package allyouneed.client.compose.platform

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * CPU triangulation for flattened path contours, feeding the immediate-mode
 * GL bridge ([McCanvas]). Fill uses ear clipping with hole bridging
 * (mapbox-earcut algorithm); stroke expands polylines into triangle strips
 * with bevel/round joins and butt/round caps (miter is rendered as bevel).
 */

/** Triangle soup: xyz per vertex is flattened as xy pairs; colors parallel per vertex (ARGB). */
class TriangleSoup {
    val positions = ArrayList<Float>()
    val colors = ArrayList<Int>()

    fun tri(ax: Float, ay: Float, bx: Float, by: Float, cx: Float, cy: Float, color: Int) {
        positions.add(ax); positions.add(ay)
        positions.add(bx); positions.add(by)
        positions.add(cx); positions.add(cy)
        colors.add(color); colors.add(color); colors.add(color)
    }

    fun tri(
        ax: Float, ay: Float, bx: Float, by: Float, cx: Float, cy: Float,
        colorA: Int, colorB: Int, colorC: Int,
    ) {
        positions.add(ax); positions.add(ay)
        positions.add(bx); positions.add(by)
        positions.add(cx); positions.add(cy)
        colors.add(colorA); colors.add(colorB); colors.add(colorC)
    }

    fun isEmpty(): Boolean = positions.isEmpty()
}

/**
 * Edge feather width in logical px: shapes get a ring of triangles whose vertex alpha fades
 * to 0 over this distance, emulating anti-aliasing in a pipeline (RenderType.gui quads) that
 * has no MSAA.
 */
private const val FEATHER = 0.75f

private fun alpha0(color: Int): Int = color and 0x00FFFFFF

/**
 * Appends one feather quad along edge a→b, fading from [color] at the edge to transparent
 * at offset (nx, ny) (the outward offset, length ~[FEATHER]).
 */
private fun featherEdge(
    soup: TriangleSoup,
    ax: Float, ay: Float, bx: Float, by: Float,
    nx: Float, ny: Float,
    color: Int,
) {
    soup.tri(ax, ay, bx, by, bx + nx, by + ny, color, color, alpha0(color))
    soup.tri(ax, ay, bx + nx, by + ny, ax + nx, ay + ny, color, alpha0(color), alpha0(color))
}

/**
 * Feathers every edge of [ring] pointing away from the ring centroid. For outer rings that is
 * the shape exterior; for hole rings the centroid sits inside the hole, so the same rule
 * feathers into the hole — both are the correct AA direction. (If a concave edge ever gets the
 * "wrong" side, the fade lands on top of the same-color interior and is invisible.)
 */
private fun featherRing(soup: TriangleSoup, ring: List<Offset>, color: Int) {
    val n = ring.size
    if (n < 3) return
    var cx = 0f
    var cy = 0f
    for (p in ring) {
        cx += p.x
        cy += p.y
    }
    cx /= n
    cy /= n
    for (i in 0 until n) {
        val a = ring[i]
        val b = ring[(i + 1) % n]
        val dx = b.x - a.x
        val dy = b.y - a.y
        val len = sqrt(dx * dx + dy * dy)
        if (len <= 0f) continue
        var nx = -dy / len
        var ny = dx / len
        val mx = (a.x + b.x) / 2f - cx
        val my = (a.y + b.y) / 2f - cy
        if (nx * mx + ny * my < 0f) {
            nx = -nx
            ny = -ny
        }
        featherEdge(soup, a.x, a.y, b.x, b.y, nx * FEATHER, ny * FEATHER, color)
    }
}

fun fillContours(contours: List<List<Offset>>, fillType: PathFillType, color: Int): TriangleSoup {
    val soup = TriangleSoup()
    if (contours.isEmpty()) return soup
    val rings = contours.mapNotNull { cleaned(it) }.filter { it.size >= 3 }
    if (rings.isEmpty()) return soup
    val evenOdd = fillType == PathFillType.EvenOdd
    triangulateLevel(rings, soup, color, evenOdd)
    for (ring in rings) featherRing(soup, ring, color)
    return soup
}

fun strokeContours(
    contours: List<List<Offset>>,
    width: Float,
    cap: StrokeCap,
    join: StrokeJoin,
    color: Int,
): TriangleSoup {
    val soup = TriangleSoup()
    val w = (width / 2f).coerceAtLeast(0.5f)
    for (raw in contours) {
        // Detect closure on the raw contour: cleanedOpen drops the duplicated
        // closing point, after which first==last can never hold.
        val closed = raw.size >= 3 && raw.first() == raw.last()
        val pts = cleanedOpen(raw) ?: continue
        if (pts.size < 2) continue
        val ring = pts
        if (ring.size < 2) continue
        val roundJoin = join == StrokeJoin.Round
        val roundCap = cap == StrokeCap.Round
        // Segment quads + edge feather.
        val count = ring.size
        for (i in 0 until (if (closed) count else count - 1)) {
            var a = ring[i]
            var b = ring[(i + 1) % count]
            if (!closed && cap == StrokeCap.Square) {
                val dx = b.x - a.x
                val dy = b.y - a.y
                val length = sqrt(dx * dx + dy * dy)
                if (length > 0f) {
                    val extension = Offset(dx / length * w, dy / length * w)
                    if (i == 0) a -= extension
                    if (i == count - 2) b += extension
                }
            }
            quadForSegment(soup, a, b, w, color)
            featherSegment(soup, a, b, w, color)
        }
        // Joins.
        for (i in (if (closed) 0 else 1) until (if (closed) count else count - 1)) {
            val prev = ring[(i - 1 + count) % count]
            val curr = ring[i]
            val next = ring[(i + 1) % count]
            if (roundJoin) {
                roundJoinFan(soup, prev, curr, next, w, color)
            } else {
                bevelJoin(soup, prev, curr, next, w, color)
            }
        }
        // Caps for open contours.
        if (!closed && roundCap) {
            roundCapFan(soup, ring[1], ring[0], w, color, start = true)
            roundCapFan(soup, ring[count - 2], ring[count - 1], w, color, start = false)
        }
    }
    return soup
}

private fun quadForSegment(soup: TriangleSoup, a: Offset, b: Offset, halfWidth: Float, color: Int) {
    val dx = b.x - a.x
    val dy = b.y - a.y
    val len = sqrt(dx * dx + dy * dy)
    if (len <= 0f) return
    val nx = -dy / len * halfWidth
    val ny = dx / len * halfWidth
    soup.tri(a.x + nx, a.y + ny, a.x - nx, a.y - ny, b.x + nx, b.y + ny, color)
    soup.tri(a.x - nx, a.y - ny, b.x - nx, b.y - ny, b.x + nx, b.y + ny, color)
}

/** Fades both long edges of the a→b stroke segment to transparent over [FEATHER] px. */
private fun featherSegment(soup: TriangleSoup, a: Offset, b: Offset, halfWidth: Float, color: Int) {
    val dx = b.x - a.x
    val dy = b.y - a.y
    val len = sqrt(dx * dx + dy * dy)
    if (len <= 0f) return
    val nx = -dy / len
    val ny = dx / len
    featherEdge(
        soup,
        a.x + nx * halfWidth, a.y + ny * halfWidth, b.x + nx * halfWidth, b.y + ny * halfWidth,
        nx * FEATHER, ny * FEATHER, color,
    )
    featherEdge(
        soup,
        a.x - nx * halfWidth, a.y - ny * halfWidth, b.x - nx * halfWidth, b.y - ny * halfWidth,
        -nx * FEATHER, -ny * FEATHER, color,
    )
}

private fun normals(p: Offset, q: Offset, halfWidth: Float): Pair<Offset, Offset> {
    val dx = q.x - p.x
    val dy = q.y - p.y
    val len = sqrt(dx * dx + dy * dy)
    if (len <= 0f) return Offset.Zero to Offset.Zero
    return Offset(-dy / len * halfWidth, dx / len * halfWidth) to Offset(dy / len * halfWidth, -dx / len * halfWidth)
}

private fun bevelJoin(soup: TriangleSoup, prev: Offset, curr: Offset, next: Offset, w: Float, color: Int) {
    val (n1, _) = normals(prev, curr, w)
    val (n2, _) = normals(curr, next, w)
    soup.tri(curr.x + n1.x, curr.y + n1.y, curr.x, curr.y, curr.x + n2.x, curr.y + n2.y, color)
    soup.tri(curr.x - n1.x, curr.y - n1.y, curr.x, curr.y, curr.x - n2.x, curr.y - n2.y, color)
    // Feather the two outer bevel edges, along the averaged normal direction.
    val mx = n1.x + n2.x
    val my = n1.y + n2.y
    val mlen = sqrt(mx * mx + my * my)
    if (mlen <= 0f) return
    val fx = mx / mlen * FEATHER
    val fy = my / mlen * FEATHER
    featherEdge(soup, curr.x + n1.x, curr.y + n1.y, curr.x + n2.x, curr.y + n2.y, fx, fy, color)
    featherEdge(soup, curr.x - n1.x, curr.y - n1.y, curr.x - n2.x, curr.y - n2.y, -fx, -fy, color)
}

private fun roundJoinFan(soup: TriangleSoup, prev: Offset, curr: Offset, next: Offset, w: Float, color: Int) {
    val (n1, _) = normals(prev, curr, w)
    val (n2, _) = normals(curr, next, w)
    fanBetween(soup, curr, n1, n2, w, color)
    fanBetween(soup, curr, Offset(-n1.x, -n1.y), Offset(-n2.x, -n2.y), w, color)
}

private fun fanBetween(soup: TriangleSoup, center: Offset, from: Offset, to: Offset, w: Float, color: Int) {
    val a0 = atan2(from.y, from.x)
    var a1 = atan2(to.y, to.x)
    while (a1 < a0) a1 += (2f * Math.PI).toFloat()
    while (a1 - a0 > Math.PI.toFloat()) a1 -= (2f * Math.PI).toFloat()
    val steps = maxOf(1, ((abs(a1 - a0) / (Math.PI.toFloat() / 8f))).toInt())
    var px = center.x + w * cos(a0)
    var py = center.y + w * sin(a0)
    for (i in 1..steps) {
        val a = a0 + (a1 - a0) * i / steps
        val qx = center.x + w * cos(a)
        val qy = center.y + w * sin(a)
        soup.tri(center.x, center.y, px, py, qx, qy, color)
        // Radial feather on the outer arc.
        val mid = a0 + (a1 - a0) * (i - 0.5f) / steps
        featherEdge(soup, px, py, qx, qy, cos(mid) * FEATHER, sin(mid) * FEATHER, color)
        px = qx
        py = qy
    }
}

private fun roundCapFan(soup: TriangleSoup, inner: Offset, end: Offset, w: Float, color: Int, start: Boolean) {
    // Half-disc around the endpoint, facing away from the segment.
    val dx = end.x - inner.x
    val dy = end.y - inner.y
    val len = sqrt(dx * dx + dy * dy)
    if (len <= 0f) {
        soup.tri(end.x - w, end.y, end.x + w, end.y, end.x, end.y - w, color)
        return
    }
    val base = atan2(dy, dx)
    val a0 = if (start) base + Math.PI.toFloat() / 2f else base - Math.PI.toFloat() / 2f
    val steps = 8
    var px = end.x + w * cos(a0)
    var py = end.y + w * sin(a0)
    for (i in 1..steps) {
        val a = a0 + Math.PI.toFloat() * i / steps * (if (start) 1f else -1f)
        val qx = end.x + w * cos(a)
        val qy = end.y + w * sin(a)
        soup.tri(end.x, end.y, px, py, qx, qy, color)
        val mid = a0 + Math.PI.toFloat() * (i - 0.5f) / steps * (if (start) 1f else -1f)
        featherEdge(soup, px, py, qx, qy, cos(mid) * FEATHER, sin(mid) * FEATHER, color)
        px = qx
        py = qy
    }
}

// ---------------------------------------------------------------------------
// Earcut (mapbox algorithm, adapted): filters rings, bridges holes, clips ears.
// ---------------------------------------------------------------------------

private class EarNode(val x: Float, val y: Float, val index: Int) {
    var prev: EarNode = this
    var next: EarNode = this
}

private fun cleaned(contour: List<Offset>): List<Offset>? {
    // Drop the duplicated closing point and consecutive duplicates.
    var pts = contour
    if (pts.size >= 2 && pts.first() == pts.last()) pts = pts.dropLast(1)
    val out = ArrayList<Offset>(pts.size)
    for (p in pts) {
        if (out.isEmpty() || out.last() != p) out += p
    }
    if (out.size >= 3 && out.first() == out.last()) out.removeAt(out.size - 1)
    // Drop collinear points.
    var i = 0
    while (out.size >= 3 && i < out.size) {
        val a = out[(i - 1 + out.size) % out.size]
        val b = out[i % out.size]
        val c = out[(i + 1) % out.size]
        if (abs(area(a, b, c)) < 1e-6f) {
            out.removeAt(i % out.size)
        } else {
            i++
        }
    }
    return if (out.size >= 3) out else null
}

private fun cleanedOpen(contour: List<Offset>): List<Offset>? {
    var pts = contour
    if (pts.size >= 2 && pts.first() == pts.last()) pts = pts.dropLast(1)
    val out = ArrayList<Offset>(pts.size)
    for (p in pts) {
        if (out.isEmpty() || out.last() != p) out += p
    }
    return if (out.size >= 2) out else null
}

private fun area(a: Offset, b: Offset, c: Offset): Float =
    (b.y - a.y) * (c.x - b.x) - (b.x - a.x) * (c.y - b.y)

private fun area(a: EarNode, b: EarNode, c: EarNode): Float =
    (b.y - a.y) * (c.x - b.x) - (b.x - a.x) * (c.y - b.y)

private fun signedArea(ring: List<Offset>): Double {
    var sum = 0.0
    for (i in ring.indices) {
        val a = ring[i]
        val b = ring[(i + 1) % ring.size]
        sum += (b.x - a.x) * (b.y + a.y)
    }
    return sum
}

private fun pointInRing(px: Float, py: Float, ring: List<Offset>): Boolean {
    var inside = false
    var j = ring.size - 1
    for (i in ring.indices) {
        val xi = ring[i].x
        val yi = ring[i].y
        val xj = ring[j].x
        val yj = ring[j].y
        if ((yi > py) != (yj > py) && px < (xj - xi) * (py - yi) / (yj - yi) + xi) {
            inside = !inside
        }
        j = i
    }
    return inside
}

private fun triangulateLevel(rings: List<List<Offset>>, soup: TriangleSoup, color: Int, evenOdd: Boolean) {
    if (rings.isEmpty()) return
    // Nesting depth per ring (number of containers).
    val order = rings.indices.sortedByDescending { abs(signedArea(rings[it])) }
    val depth = IntArray(rings.size)
    val parent = IntArray(rings.size) { -1 }
    for (rank in order.indices) {
        val i = order[rank]
        var best = -1
        var bestArea = Double.POSITIVE_INFINITY
        for (prev in 0 until rank) {
            val j = order[prev]
            if (pointInRing(rings[i][0].x, rings[i][0].y, rings[j])) {
                val a = abs(signedArea(rings[j]))
                if (a < bestArea) {
                    bestArea = a
                    best = j
                }
            }
        }
        parent[i] = best
        depth[i] = if (best < 0) 0 else depth[best] + 1
    }
    val outers = rings.indices.filter { depth[it] % 2 == 0 }
    for (o in outers) {
        val outerArea = signedArea(rings[o])
        val holes = rings.indices.filter { parent[it] == o && isHole(rings[it], outerArea, evenOdd) }
        val holeRings = holes.map { rings[it] }
        earcutWithHoles(rings[o], holeRings, soup, color)
        if (!evenOdd) {
            // NonZero: same-winding children are already filled by the outer ring itself;
            // only descend into hole subtrees, whose content needs fresh triangulation.
            for (h in holes) {
                val sub = rings.indices.filter { parent[it] == h }.map { rings[it] }
                triangulateLevel(sub, soup, color, evenOdd)
            }
        }
        // EvenOdd: hole children have even depth, so they are already covered
        // by the top-level outer pass — no recursion needed.
    }
}

private fun isHole(ring: List<Offset>, outerSignedArea: Double, evenOdd: Boolean): Boolean {
    if (evenOdd) return true
    // NonZero: a child is a hole when its winding opposes the outer ring.
    return (signedArea(ring) > 0) != (outerSignedArea > 0)
}

private fun earcutWithHoles(outer: List<Offset>, holes: List<List<Offset>>, soup: TriangleSoup, color: Int) {
    var outerRing = outer
    // Ensure outer is clockwise (negative signedArea with our definition) so isEar's area check is consistent.
    if (signedArea(outerRing) > 0) {
        outerRing = outerRing.reversed()
    }
    var head: EarNode = linkedList(outerRing, 0) ?: return
    val outerSign = signedArea(outerRing) >= 0.0
    var holeIndex = 0
    for (hole in holes) {
        var ring = hole
        if (ring.size >= 3 && (signedArea(ring) >= 0.0) == outerSign) {
            ring = ring.reversed()
        }
        val holeHead = linkedList(ring, holeIndex)
        if (holeHead != null) {
            head = eliminateHole(holeHead, head) ?: head
        }
        holeIndex += hole.size
    }
    earcutLinked(head, soup, color)
}

private fun linkedList(ring: List<Offset>, indexOffset: Int): EarNode? {
    if (ring.isEmpty()) return null
    var head: EarNode? = null
    var prev: EarNode? = null
    for (i in ring.indices) {
        val node = EarNode(ring[i].x, ring[i].y, indexOffset + i)
        if (head == null) {
            head = node
        } else {
            node.prev = prev!!
            prev.next = node
        }
        prev = node
    }
    head!!.prev = prev!!
    prev.next = head
    return head
}

private fun countNodes(head: EarNode): Int {
    var n = 0
    var cur = head
    do {
        n++
        cur = cur.next
        if (n > 1000) break
    } while (cur !== head)
    return n
}

private fun eliminateHole(hole: EarNode, outer: EarNode): EarNode? {
    val bridge = findHoleBridge(hole, outer) ?: return null
    val bridgeReverse = splitPolygon(bridge, hole)
    filterPoints(bridge, bridge.next)
    filterPoints(bridgeReverse, bridgeReverse.next)
    return outer
}

private fun findHoleBridge(hole: EarNode, outer: EarNode): EarNode? {
    var hx = hole.x
    var hy = hole.y
    var m = hole
    // Leftmost hole point.
    var node = hole
    do {
        if (node.x < hx || (node.x == hx && node.y < hy)) {
            hx = node.x
            hy = node.y
            m = node
        }
        node = node.next
    } while (node !== hole)
    // Find an outer edge intersected by the leftward ray, pick the rightmost such point.
    var qx = Float.NEGATIVE_INFINITY
    var bridge: EarNode? = null
    node = outer
    do {
        if (hy <= max(node.y, node.next.y) && hy >= min(node.y, node.next.y)) {
            val x = node.x + (hy - node.y) * (node.next.x - node.x) / (node.next.y - node.y)
            if (x <= hx && x > qx) {
                qx = x
                bridge = if (node.x < node.next.x) node else node.next
            }
        }
        node = node.next
    } while (node !== outer)
    if (bridge == null) return null
    if (hx == qx) return m
    // Look for a better (visible) connection inside the triangle (m, bridge, qx).
    val stop = bridge
    val mx = m.x
    val my = m.y
    var tanMin = Float.POSITIVE_INFINITY
    var tan: Float
    node = bridge
    do {
        if (hx >= node.x && node.x >= qx && pointInTriangle(
                if (hy < my) hx else qx, hy, mx, my,
                if (hy < my) qx else hx, hy, node.x, node.y,
            )
        ) {
            tan = abs(hy - node.y) / (hx - node.x)
            if (locallyInside(node, m) &&
                (tan < tanMin || (tan == tanMin && (node.x > m.x || (node.x == m.x && sectorContainsSector(m, node)))))
            ) {
                bridge = node
                tanMin = tan
            }
        }
        node = node.next
    } while (node !== stop)
    return bridge
}

private fun sectorContainsSector(m: EarNode, p: EarNode): Boolean {
    return area(m.prev, m, m.next) < 0 != area(p.prev, p, p.next) < 0
}

private fun splitPolygon(a: EarNode, b: EarNode): EarNode {
    val a2 = EarNode(a.x, a.y, a.index)
    val b2 = EarNode(b.x, b.y, b.index)
    val an = a.next
    val bp = b.prev
    a.next = b
    b.prev = a
    a2.next = an
    an.prev = a2
    b2.next = a2
    a2.prev = b2
    bp.next = b2
    b2.prev = bp
    return b2
}

private fun filterPoints(start: EarNode, end: EarNode?) {
    var p = start
    var again: Boolean
    do {
        again = false
        if (p.next === p || p.prev === p) return
        if (equals(p, p.next) || area(p.prev, p, p.next) == 0f) {
            p.prev.next = p.next
            p.next.prev = p.prev
            p = p.prev
            if (p === end) return
            again = true
        } else {
            p = p.next
        }
    } while (again || p !== start)
}

private fun equals(a: EarNode, b: EarNode): Boolean = a.x == b.x && a.y == b.y

private fun earcutLinked(ear: EarNode, soup: TriangleSoup, color: Int) {
    var node: EarNode? = ear
    var stop = ear
    var iterations = 0
    val maxIterations = 100000
    while (node!!.prev !== node.next && iterations++ < maxIterations) {
        val prev = node.prev
        val next = node.next
        if (isEar(prev, node, next)) {
            soup.tri(prev.x, prev.y, node.x, node.y, next.x, next.y, color)
            next.prev = prev
            prev.next = next
            node = next.next
            stop = next.next
            continue
        }
        node = next
        if (node === stop) {
            // No ear found (degenerate ring): cure by removing a point.
            if (node === node.next) break
            val p = node
            node = node.next
            p.prev.next = p.next
            p.next.prev = p.prev
            stop = node
        }
    }
}

private fun isEar(a: EarNode, b: EarNode, c: EarNode): Boolean {
    if (area(a, b, c) >= 0f) return false
    var p = c.next
    while (p !== a) {
        if (pointInTriangle(a.x, a.y, b.x, b.y, c.x, c.y, p.x, p.y) && area(p.prev, p, p.next) >= 0f) {
            return false
        }
        p = p.next
    }
    return true
}

private fun pointInTriangle(
    ax: Float, ay: Float, bx: Float, by: Float, cx: Float, cy: Float, px: Float, py: Float,
): Boolean {
    return (cx - px) * (ay - py) >= (ax - px) * (cy - py) &&
        (ax - px) * (by - py) >= (bx - px) * (ay - py) &&
        (bx - px) * (cy - py) >= (cx - px) * (by - py)
}

private fun locallyInside(a: EarNode, b: EarNode): Boolean {
    return if (area(a.prev, a, a.next) < 0) {
        area(a, b, a.next) >= 0 && area(a, a.prev, b) >= 0
    } else {
        area(a, b, a.next) < 0 || area(a, a.prev, b) < 0
    }
}

private fun min(a: Float, b: Float): Float = if (a < b) a else b
private fun max(a: Float, b: Float): Float = if (a > b) a else b

package allyouneed.me.pathing

import io.github.copi143.valueschema.ValueSchema

@ValueSchema
data class FlowArc(
    val to: Int,
    val cap: Int,
    val rev: Int,
    val next: Int,
)

class ChannelMaxFlow(vertexCount: Int, edgeHint: Int = vertexCount * 4) {
    val vertexCount: Int = vertexCount
    private val arcs = FlowArcColumns(max(8, edgeHint * 2))
    private val head = IntArray(vertexCount) { -1 }
    private val seen = IntArray(vertexCount)
    private val queue = IntArray(vertexCount)
    private val parentEdge = IntArray(vertexCount)
    private var stamp = 1

    fun addEdge(u: Int, v: Int, capacity: Int): Int {
        val e = arcs.size
        addArc(u, v, capacity, e + 1)
        addArc(v, u, 0, e)
        return e
    }

    fun flow(edge: Int): Int = arcs.caps[arcs.revs[edge]]

    fun residual(edge: Int): Int = arcs.caps[edge]

    fun setResidual(edge: Int, value: Int) {
        arcs.caps[edge] = value
    }

    fun addFlow(edge: Int, amount: Int) {
        arcs.caps[edge] -= amount
        arcs.caps[arcs.revs[edge]] += amount
    }

    fun maxFlow(s: Int, t: Int): Int {
        var flow = 0
        while (true) {
            val add = augmentOnce(s, t)
            if (add == 0) break
            flow += add
        }
        return flow
    }

    fun reachable(s: Int, t: Int): Boolean {
        if (s == t) return true
        bump()
        return bfs(s, t)
    }

    private fun augmentOnce(s: Int, t: Int): Int {
        bump()
        if (!bfs(s, t)) return 0
        var bneck = INF
        var v = t
        while (v != s) {
            val e = parentEdge[v]
            val r = arcs.caps[e]
            if (r < bneck) bneck = r
            v = arcs.tos[arcs.revs[e]]
        }
        v = t
        while (v != s) {
            val e = parentEdge[v]
            arcs.caps[e] -= bneck
            arcs.caps[arcs.revs[e]] += bneck
            v = arcs.tos[arcs.revs[e]]
        }
        return bneck
    }

    private fun bfs(s: Int, t: Int): Boolean {
        seen[s] = stamp
        queue[0] = s
        var qh = 0
        var qt = 1
        while (qh < qt) {
            val u = queue[qh++]
            var e = head[u]
            while (e >= 0) {
                val v = arcs.tos[e]
                if (arcs.caps[e] > 0 && seen[v] != stamp) {
                    seen[v] = stamp
                    parentEdge[v] = e
                    if (v == t) return true
                    queue[qt++] = v
                }
                e = arcs.nexts[e]
            }
        }
        return false
    }

    private fun bump() {
        stamp++
        if (stamp == Int.MAX_VALUE) {
            seen.fill(0)
            stamp = 1
        }
    }

    private fun addArc(u: Int, v: Int, capacity: Int, revEdge: Int) {
        val e = arcs.size
        arcs.add(to = v, cap = capacity, rev = revEdge, next = head[u])
        head[u] = e
    }

    companion object {
        const val INF = 1_000_000_000

        private fun max(a: Int, b: Int) = if (a > b) a else b
    }
}

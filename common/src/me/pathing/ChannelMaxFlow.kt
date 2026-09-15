package allyouneed.me.pathing

class ChannelMaxFlow(vertexCount: Int, edgeHint: Int = vertexCount * 4) {
    val vertexCount: Int = vertexCount
    private var edgeCount = 0
    private var to = IntArray(max(8, edgeHint * 2))
    private var cap = IntArray(to.size)
    private var rev = IntArray(to.size)
    private var next = IntArray(to.size)
    private val head = IntArray(vertexCount) { -1 }
    private val seen = IntArray(vertexCount)
    private val queue = IntArray(vertexCount)
    private val parentEdge = IntArray(vertexCount)
    private var stamp = 1

    fun addEdge(u: Int, v: Int, capacity: Int): Int {
        val e = edgeCount
        addArc(u, v, capacity, e + 1)
        addArc(v, u, 0, e)
        return e
    }

    fun flow(edge: Int): Int = cap[rev[edge]]

    fun residual(edge: Int): Int = cap[edge]

    fun setResidual(edge: Int, value: Int) {
        cap[edge] = value
    }

    fun addFlow(edge: Int, amount: Int) {
        cap[edge] -= amount
        cap[rev[edge]] += amount
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
            val r = cap[e]
            if (r < bneck) bneck = r
            v = to[rev[e]]
        }
        v = t
        while (v != s) {
            val e = parentEdge[v]
            cap[e] -= bneck
            cap[rev[e]] += bneck
            v = to[rev[e]]
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
                val v = to[e]
                if (cap[e] > 0 && seen[v] != stamp) {
                    seen[v] = stamp
                    parentEdge[v] = e
                    if (v == t) return true
                    queue[qt++] = v
                }
                e = next[e]
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
        if (edgeCount == to.size) grow()
        val e = edgeCount++
        to[e] = v
        cap[e] = capacity
        rev[e] = revEdge
        next[e] = head[u]
        head[u] = e
    }

    private fun grow() {
        val n = to.size * 2
        to = to.copyOf(n)
        cap = cap.copyOf(n)
        rev = rev.copyOf(n)
        next = next.copyOf(n)
    }

    companion object {
        const val INF = 1_000_000_000

        private fun max(a: Int, b: Int) = if (a > b) a else b
    }
}

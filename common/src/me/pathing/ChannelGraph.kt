package allyouneed.me.pathing

class ChannelGraph(
    val nodeCount: Int,
    val maxChannels: IntArray,
    val flags: IntArray,
    val isController: BooleanArray,
    val demand: BooleanArray,
    val swallow: BooleanArray,
    val group: IntArray,
    val connA: IntArray,
    val connB: IntArray,
) {
    val connCount: Int = connA.size
    val nodeAdj: Array<IntArray> = Array(nodeCount) { IntArray(0) }

    init {
        val deg = IntArray(nodeCount)
        for (c in 0 until connCount) {
            deg[connA[c]]++
            deg[connB[c]]++
        }
        val acc = IntArray(nodeCount)
        for (i in 0 until nodeCount) {
            nodeAdj[i] = IntArray(deg[i])
        }
        for (c in 0 until connCount) {
            val a = connA[c]
            val b = connB[c]
            nodeAdj[a][acc[a]++] = c
            nodeAdj[b][acc[b]++] = c
        }
    }

    fun other(conn: Int, node: Int): Int {
        val a = connA[conn]
        return if (a == node) connB[conn] else a
    }

    fun has(node: Int, bit: Int): Boolean = flags[node] and bit != 0

    companion object {
        const val REQUIRE = 1
        const val COMPRESSED = 2
        const val NO_COMPRESSED = 4
        const val DENSE = 8
        const val PREFERRED = 16
    }
}

class ChannelResult(
    val nodeUsed: IntArray,
    val connUsed: IntArray,
    val assigned: BooleanArray,
    val ride: BooleanArray,
    val channelsInUse: Int,
    val channelsByBlocks: Int,
    val usedPhase2: Boolean,
)

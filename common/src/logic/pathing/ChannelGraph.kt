package allyouneed.logic.pathing

import io.github.copi143.valueschema.ValueSchema

@ValueSchema
data class ChannelNode(
    val maxChannel: Int,
    val flag: Int,
    val controller: Boolean,
    val demand: Boolean,
    val swallow: Boolean,
    val group: Int,
)

@ValueSchema
data class ChannelEdge(
    val endA: Int,
    val endB: Int,
)

class ChannelGraph(
    val nodes: ChannelNodeColumns,
    val edges: ChannelEdgeColumns,
) {
    val nodeCount: Int get() = nodes.size
    val connCount: Int get() = edges.size
    val nodeAdj: Array<IntArray> = Array(nodeCount) { IntArray(0) }

    init {
        val deg = IntArray(nodeCount)
        for (c in 0 until connCount) {
            deg[edges.endAs[c]]++
            deg[edges.endBs[c]]++
        }
        val acc = IntArray(nodeCount)
        for (i in 0 until nodeCount) {
            nodeAdj[i] = IntArray(deg[i])
        }
        for (c in 0 until connCount) {
            val a = edges.endAs[c]
            val b = edges.endBs[c]
            nodeAdj[a][acc[a]++] = c
            nodeAdj[b][acc[b]++] = c
        }
    }

    fun other(conn: Int, node: Int): Int {
        val a = edges.endAs[conn]
        return if (a == node) edges.endBs[conn] else a
    }

    fun has(node: Int, bit: Int): Boolean = nodes.flags[node] and bit != 0

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

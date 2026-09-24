package allyouneed.me.pathing

import allyouneed.api.AsyncChannelNodeHolder
import allyouneed.mixin.ae2.GridConnectionAccessor
import allyouneed.mixin.ae2.GridNodeAccessor
import allyouneed.multiblock.async.IAsyncChannelSink
import allyouneed.parts.planebus.PlaneBusClusters
import allyouneed.parts.planebus.PlaneBusPart
import appeng.api.networking.GridFlags
import appeng.api.networking.IGrid
import appeng.api.networking.IGridMultiblock
import appeng.blockentity.networking.ControllerBlockEntity
import appeng.me.GridConnection
import appeng.me.GridNode
import appeng.parts.automation.AnnihilationPlanePart
import appeng.parts.automation.FormationPlanePart
import java.util.IdentityHashMap

class ChannelAllocStats(val channelsInUse: Int, val channelsByBlocks: Int)

object ChannelAllocator {
    @JvmStatic
    fun allocate(grid: IGrid): ChannelAllocStats {
        for (node in grid.nodes) {
            if (node is AsyncChannelNodeHolder) node.asyncSwallowedChannels = 0
        }
        val nodes = ArrayList<GridNode>(grid.size())
        val index = IdentityHashMap<GridNode, Int>()
        for (node in grid.nodes) {
            if (node is GridNode) {
                index[node] = nodes.size
                nodes.add(node)
            }
        }
        val n = nodes.size
        if (n == 0) return ChannelAllocStats(0, 0)

        val connIndex = IdentityHashMap<GridConnection, Int>()
        val conns = ArrayList<GridConnection>()
        for (node in nodes) {
            for (gc in node.connections) {
                val conn = gc as GridConnection
                if (connIndex.putIfAbsent(conn, conns.size) == null) conns.add(conn)
            }
        }

        val nodeCols = ChannelNodeColumns(n)
        for (i in 0 until n) {
            val node = nodes[i]
            var swallow = false
            val owner = node.owner
            if (owner is IAsyncChannelSink && owner.isFormed() && node is AsyncChannelNodeHolder) {
                swallow = node.maxChannels != Int.MAX_VALUE
            }
            nodeCols.add(
                maxChannel = node.maxChannels,
                flag = packFlags(node),
                controller = node.owner is ControllerBlockEntity,
                demand = node.hasFlag(GridFlags.REQUIRE_CHANNEL),
                swallow = swallow,
                group = -1,
            )
        }
        denyUnformedPlanes(nodes, nodeCols.demands)
        assignGroups(nodes, nodeCols.demands, nodeCols.groups)

        val edgeCols = ChannelEdgeColumns(conns.size)
        for (c in conns.indices) {
            edgeCols.add(
                endA = index.getValue(conns[c].a()),
                endB = index.getValue(conns[c].b()),
            )
        }

        val graph = ChannelGraph(nodeCols, edgeCols)
        val result = ChannelTreeAllocator.allocate(graph)

        for (i in 0 until n) {
            val node = nodes[i]
            (node as GridNodeAccessor).`allyouneed$setUsedChannels`(result.nodeUsed[i])
            if (nodeCols.swallows[i] && result.assigned[i] && node is AsyncChannelNodeHolder) {
                node.asyncSwallowedChannels = nodeCols.maxChannels[i]
            }
        }
        for (c in conns.indices) {
            (conns[c] as GridConnectionAccessor).`allyouneed$setUsedChannels`(result.connUsed[c])
        }
        return ChannelAllocStats(result.channelsInUse, result.channelsByBlocks)
    }

    private fun packFlags(node: GridNode): Int {
        var bits = 0
        if (node.hasFlag(GridFlags.REQUIRE_CHANNEL)) bits = bits or ChannelGraph.REQUIRE
        if (node.hasFlag(GridFlags.COMPRESSED_CHANNEL)) bits = bits or ChannelGraph.COMPRESSED
        if (node.hasFlag(GridFlags.CANNOT_CARRY_COMPRESSED)) bits = bits or ChannelGraph.NO_COMPRESSED
        if (node.hasFlag(GridFlags.DENSE_CAPACITY)) bits = bits or ChannelGraph.DENSE
        if (node.hasFlag(GridFlags.PREFERRED)) bits = bits or ChannelGraph.PREFERRED
        return bits
    }

    private fun denyUnformedPlanes(nodes: List<GridNode>, demand: BooleanArray) {
        var clusters: PlaneBusClusters.PlaneClusters? = null
        for (i in nodes.indices) {
            val owner = nodes[i].owner
            val pos = when (owner) {
                is PlaneBusPart -> owner.blockEntity.blockPos to owner.side
                is FormationPlanePart -> owner.blockEntity.blockPos to owner.side
                is AnnihilationPlanePart -> owner.blockEntity.blockPos to owner.side
                else -> continue
            }
            if (clusters == null) clusters = PlaneBusClusters.resolve(nodes[i].level)
            val id = clusters.idAt(pos.first, pos.second)
            if (id != null && clusters.formedById[id] != true) demand[i] = false
        }
    }

    private fun assignGroups(nodes: List<GridNode>, demand: BooleanArray, group: IntArray) {
        val seen = BooleanArray(nodes.size)
        var next = 0
        val index = IdentityHashMap<GridNode, Int>()
        for (i in nodes.indices) index[nodes[i]] = i
        for (i in nodes.indices) {
            if (seen[i] || !demand[i] || !nodes[i].hasFlag(GridFlags.MULTIBLOCK)) continue
            val mb = nodes[i].getService(IGridMultiblock::class.java) ?: continue
            val members = ArrayList<Int>()
            val it = mb.multiblockNodes
            while (it.hasNext()) {
                val other = it.next() ?: continue
                val idx = index[other as GridNode] ?: continue
                members.add(idx)
            }
            if (members.size <= 1) continue
            val gid = next++
            for (idx in members) {
                group[idx] = gid
                seen[idx] = true
            }
        }
    }
}

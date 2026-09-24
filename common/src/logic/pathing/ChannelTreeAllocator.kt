package allyouneed.logic.pathing

import io.github.copi143.valueschema.Default
import io.github.copi143.valueschema.ValueSchema
import java.util.ArrayDeque

@ValueSchema
data class TreeNodeState(
    val used: Int,
    val assigned: Boolean,
    val ride: Boolean,
    @Default("-1") val parentNode: Int,
    @Default("-1") val parentConn: Int,
    val allowsCompressed: Boolean,
    val bottleneck: Int,
    val visited: Boolean,
    val ridePending: Boolean,
)

@ValueSchema
data class ConnFlow(
    val ab: Int,
    val ba: Int,
)

object ChannelTreeAllocator {
    fun allocate(g: ChannelGraph): ChannelResult {
        val n = g.nodeCount
        val st = TreeNodeStateColumns(n)
        st.resize(n)
        val connFlow = ConnFlowColumns(g.connCount)
        connFlow.resize(g.connCount)
        val members = groupMembers(g)
        var extraEdges = false
        var paid = 0

        val queues = Array(3) { ArrayDeque<Int>() }
        for (i in 0 until n) {
            if (g.nodes.controllers[i]) st.visiteds[i] = true
        }
        for (i in 0 until n) {
            if (!g.nodes.controllers[i]) continue
            for (c in g.nodeAdj[i]) {
                val peer = g.other(c, i)
                if (g.nodes.controllers[peer] || st.visiteds[peer]) continue
                discover(g, peer, i, c, 0, st, queues)
            }
        }
        for (q in 0..2) {
            val queue = queues[q]
            while (queue.isNotEmpty()) {
                val u = queue.removeFirst()
                if (g.nodes.demands[u] && !st.ridePendings[u]) {
                    if (tryUse(g, u, st, connFlow)) {
                        paid++
                        if (g.nodes.swallows[u]) st.bottlenecks[u] = g.nodes.maxChannels[u]
                        val gid = g.nodes.groups[u]
                        if (gid >= 0) {
                            for (m in members[gid]) {
                                if (m != u) st.ridePendings[m] = true
                            }
                        }
                    }
                }
                for (c in g.nodeAdj[u]) {
                    val peer = g.other(c, u)
                    if (st.visiteds[peer]) {
                        if (c != st.parentConns[u]) {
                            val parent = st.parentNodes[u]
                            if (!(g.nodes.controllers[peer] && parent >= 0 && g.nodes.controllers[parent])) {
                                extraEdges = true
                            }
                        }
                        continue
                    }
                    discover(g, peer, u, c, q, st, queues)
                }
            }
        }

        val demandGroups = countDemand(g)
        var usedPhase2 = false
        if (paid < demandGroups && extraEdges && canReachUnpaid(g, st, members)) {
            val extra = augment(g, st, connFlow, members, paid)
            if (extra > 0) {
                paid += extra
                usedPhase2 = true
            }
        }

        val connUsed = IntArray(g.connCount) { connFlow.abs[it] + connFlow.bas[it] }
        var byBlocks = 0
        for (v in st.useds) byBlocks += v
        for (v in connUsed) byBlocks += v

        for (gid in members.indices) {
            var payer = -1
            for (m in members[gid]) {
                if (st.assigneds[m]) {
                    payer = m
                    break
                }
            }
            if (payer < 0) continue
            for (m in members[gid]) {
                if (m == payer) continue
                st.rides[m] = true
                st.useds[m]++
            }
        }

        return ChannelResult(st.useds, connUsed, st.assigneds, st.rides, paid, byBlocks, usedPhase2)
    }

    private fun discover(
        g: ChannelGraph,
        node: Int,
        parent: Int,
        conn: Int,
        queueIndex: Int,
        st: TreeNodeStateColumns,
        queues: Array<ArrayDeque<Int>>,
    ) {
        st.visiteds[node] = true
        st.parentNodes[node] = parent
        st.parentConns[node] = conn
        val selfCc = !g.has(node, ChannelGraph.NO_COMPRESSED)
        st.allowsCompresseds[node] = if (g.nodes.controllers[parent]) selfCc else st.allowsCompresseds[parent] && selfCc
        val idx = max(nodeQueue(g, node), queueIndex)
        queues[idx].addLast(node)
    }

    private fun tryUse(
        g: ChannelGraph,
        start: Int,
        st: TreeNodeStateColumns,
        connFlow: ConnFlowColumns,
    ): Boolean {
        if (g.has(start, ChannelGraph.COMPRESSED) && !st.allowsCompresseds[start]) return false
        var pi = start
        while (pi >= 0 && !g.nodes.controllers[pi]) {
            val cap = g.nodes.maxChannels[pi]
            if (cap == 0 || st.bottlenecks[pi] >= cap) return false
            pi = st.parentNodes[pi]
        }
        pi = start
        while (pi >= 0 && !g.nodes.controllers[pi]) {
            st.bottlenecks[pi]++
            pi = st.parentNodes[pi]
        }
        st.assigneds[start] = true
        addPath(g, start, st, connFlow)
        return true
    }

    private fun addPath(
        g: ChannelGraph,
        start: Int,
        st: TreeNodeStateColumns,
        connFlow: ConnFlowColumns,
    ) {
        var child = start
        while (true) {
            st.useds[child]++
            val c = st.parentConns[child]
            if (c < 0) break
            val p = st.parentNodes[child]
            if (g.edges.endAs[c] == p && g.edges.endBs[c] == child) connFlow.abs[c]++ else connFlow.bas[c]++
            if (g.nodes.controllers[p]) break
            child = p
        }
    }

    private fun augment(
        g: ChannelGraph,
        st: TreeNodeStateColumns,
        connFlow: ConnFlowColumns,
        members: Array<IntArray>,
        paid: Int,
    ): Int {
        val n = g.nodeCount
        val unpaidGroups = ArrayList<Int>()
        val groupUnpaid = BooleanArray(members.size) { true }
        for (i in 0 until n) {
            if (st.assigneds[i]) {
                val gid = g.nodes.groups[i]
                if (gid >= 0) groupUnpaid[gid] = false
            }
        }
        for (gid in groupUnpaid.indices) {
            if (groupUnpaid[gid] && members[gid].isNotEmpty() && members[gid].any { g.nodes.demands[it] }) {
                unpaidGroups.add(gid)
            }
        }
        val solos = ArrayList<Int>()
        for (i in 0 until n) {
            if (g.nodes.demands[i] && !st.assigneds[i] && g.nodes.groups[i] < 0) solos.add(i)
        }
        if (solos.isEmpty() && unpaidGroups.isEmpty()) return 0

        val s = n * 2
        val t = n * 2 + 1
        val groupBase = n * 2 + 2
        val vertexCount = groupBase + unpaidGroups.size
        val flow = ChannelMaxFlow(vertexCount, n * 8 + g.connCount * 4)
        val vin = IntArray(n) { it * 2 }
        val vout = IntArray(n) { it * 2 + 1 }
        val vertEdge = IntArray(n) { -1 }
        val sEdge = IntArray(n) { -1 }
        val connEdgeAB = IntArray(g.connCount)
        val connEdgeBA = IntArray(g.connCount)
        for (i in 0 until n) {
            if (g.nodes.controllers[i]) {
                sEdge[i] = flow.addEdge(s, vout[i], ChannelMaxFlow.INF)
            } else {
                val cap = capOf(g.nodes.maxChannels[i])
                vertEdge[i] = flow.addEdge(vin[i], vout[i], cap)
            }
        }
        for (c in 0 until g.connCount) {
            val a = g.edges.endAs[c]
            val b = g.edges.endBs[c]
            connEdgeAB[c] = flow.addEdge(vout[a], vin[b], ChannelMaxFlow.INF)
            connEdgeBA[c] = flow.addEdge(vout[b], vin[a], ChannelMaxFlow.INF)
        }

        for (i in 0 until n) {
            if (!st.assigneds[i]) continue
            applyAssigned(g, i, flow, vin, vout, vertEdge, sEdge, connEdgeAB, connEdgeBA, st)
            flow.addFlow(flow.addEdge(vout[i], t, 1), 1)
        }
        for (i in 0 until n) {
            if (st.assigneds[i] && g.nodes.swallows[i] && vertEdge[i] >= 0) {
                flow.setResidual(vertEdge[i], 0)
            }
        }

        val soloEdge = IntArray(n) { -1 }
        val compressedSolos = ArrayList<Int>()
        val normalSolos = ArrayList<Int>()
        for (i in solos) {
            soloEdge[i] = flow.addEdge(vout[i], t, 1)
            if (g.has(i, ChannelGraph.COMPRESSED)) compressedSolos.add(i) else normalSolos.add(i)
        }
        val groupTEdge = IntArray(unpaidGroups.size)
        val compressedGroups = ArrayList<Int>()
        val normalGroups = ArrayList<Int>()
        for ((idx, gid) in unpaidGroups.withIndex()) {
            val gv = groupBase + idx
            for (m in members[gid]) {
                if (g.nodes.demands[m]) flow.addEdge(vout[m], gv, ChannelMaxFlow.INF)
            }
            groupTEdge[idx] = flow.addEdge(gv, t, 1)
            val compressed = members[gid].any { g.has(it, ChannelGraph.COMPRESSED) }
            if (compressed) compressedGroups.add(idx) else normalGroups.add(idx)
        }

        val noComp = ArrayList<Int>()
        for (i in 0 until n) {
            if (!g.nodes.controllers[i] && g.has(i, ChannelGraph.NO_COMPRESSED) && vertEdge[i] >= 0) {
                noComp.add(i)
            }
        }

        if (!flow.reachable(s, t)) return 0
        if (compressedSolos.isNotEmpty() || compressedGroups.isNotEmpty()) {
            val savedSolo = IntArray(normalSolos.size)
            for ((k, i) in normalSolos.withIndex()) savedSolo[k] = flow.residual(soloEdge[i])
            val savedGroup = IntArray(normalGroups.size)
            for ((k, idx) in normalGroups.withIndex()) savedGroup[k] = flow.residual(groupTEdge[idx])
            val savedCc = IntArray(noComp.size)
            for ((k, i) in noComp.withIndex()) savedCc[k] = flow.residual(vertEdge[i])
            for (i in normalSolos) flow.setResidual(soloEdge[i], 0)
            for (idx in normalGroups) flow.setResidual(groupTEdge[idx], 0)
            for (i in noComp) flow.setResidual(vertEdge[i], 0)
            flow.maxFlow(s, t)
            for ((k, i) in noComp.withIndex()) flow.setResidual(vertEdge[i], savedCc[k])
            for ((k, i) in normalSolos.withIndex()) flow.setResidual(soloEdge[i], savedSolo[k])
            for ((k, idx) in normalGroups.withIndex()) flow.setResidual(groupTEdge[idx], savedGroup[k])
        }
        flow.maxFlow(s, t)

        for (i in 0 until n) {
            if (vertEdge[i] >= 0) st.useds[i] = flow.flow(vertEdge[i])
        }
        for (c in 0 until g.connCount) {
            connFlow.abs[c] = flow.flow(connEdgeAB[c])
            connFlow.bas[c] = flow.flow(connEdgeBA[c])
        }
        var extra = 0
        for (i in solos) {
            if (flow.flow(soloEdge[i]) > 0 && !st.assigneds[i]) {
                st.assigneds[i] = true
                extra++
            }
        }
        for ((idx, gid) in unpaidGroups.withIndex()) {
            if (flow.flow(groupTEdge[idx]) > 0) {
                var payer = members[gid].firstOrNull { g.nodes.demands[it] } ?: continue
                for (m in members[gid]) {
                    if (g.nodes.demands[m] && st.useds[m] > 0) {
                        payer = m
                        break
                    }
                }
                st.assigneds[payer] = true
                extra++
            }
        }
        return extra
    }

    private fun applyAssigned(
        g: ChannelGraph,
        start: Int,
        flow: ChannelMaxFlow,
        vin: IntArray,
        vout: IntArray,
        vertEdge: IntArray,
        sEdge: IntArray,
        connEdgeAB: IntArray,
        connEdgeBA: IntArray,
        st: TreeNodeStateColumns,
    ) {
        var child = start
        if (vertEdge[child] >= 0) flow.addFlow(vertEdge[child], 1)
        while (true) {
            val c = st.parentConns[child]
            if (c < 0) break
            val p = st.parentNodes[child]
            if (g.edges.endAs[c] == p && g.edges.endBs[c] == child) flow.addFlow(connEdgeAB[c], 1)
            else flow.addFlow(connEdgeBA[c], 1)
            if (g.nodes.controllers[p]) {
                if (sEdge[p] >= 0) flow.addFlow(sEdge[p], 1)
                break
            }
            if (vertEdge[p] >= 0) flow.addFlow(vertEdge[p], 1)
            child = p
        }
    }

    private fun canReachUnpaid(
        g: ChannelGraph,
        st: TreeNodeStateColumns,
        members: Array<IntArray>,
    ): Boolean {
        val n = g.nodeCount
        val groupPaid = BooleanArray(members.size)
        for (i in 0 until n) {
            if (!st.assigneds[i]) continue
            val gid = g.nodes.groups[i]
            if (gid >= 0) groupPaid[gid] = true
        }
        val seen = BooleanArray(n)
        val q = ArrayDeque<Int>()
        for (i in 0 until n) {
            if (!g.nodes.controllers[i]) continue
            seen[i] = true
            q.addLast(i)
        }
        while (q.isNotEmpty()) {
            val u = q.removeFirst()
            for (c in g.nodeAdj[u]) {
                val v = g.other(c, u)
                if (seen[v]) continue
                if (g.nodes.demands[v] && !st.assigneds[v]) {
                    val gid = g.nodes.groups[v]
                    if (gid < 0 || !groupPaid[gid]) return true
                }
                if (g.nodes.controllers[v] || remaining(g, v, st) > 0) {
                    seen[v] = true
                    q.addLast(v)
                }
            }
        }
        return false
    }

    private fun remaining(g: ChannelGraph, node: Int, st: TreeNodeStateColumns): Int {
        if (g.nodes.swallows[node] && st.assigneds[node]) return 0
        val cap = capOf(g.nodes.maxChannels[node])
        val used = st.useds[node]
        return if (used >= cap) 0 else cap - used
    }

    private fun countDemand(g: ChannelGraph): Int {
        var maxG = -1
        for (v in g.nodes.groups) if (v > maxG) maxG = v
        var d = 0
        val seen = if (maxG >= 0) BooleanArray(maxG + 1) else BooleanArray(0)
        for (i in 0 until g.nodeCount) {
            if (!g.nodes.demands[i]) continue
            val gid = g.nodes.groups[i]
            if (gid >= 0) {
                if (seen[gid]) continue
                seen[gid] = true
            }
            d++
        }
        return d
    }

    private fun groupMembers(g: ChannelGraph): Array<IntArray> {
        var maxG = -1
        for (v in g.nodes.groups) if (v > maxG) maxG = v
        if (maxG < 0) return emptyArray()
        val deg = IntArray(maxG + 1)
        for (v in g.nodes.groups) if (v >= 0) deg[v]++
        val out = Array(maxG + 1) { IntArray(deg[it]) }
        deg.fill(0)
        for (i in 0 until g.nodeCount) {
            val gid = g.nodes.groups[i]
            if (gid >= 0) out[gid][deg[gid]++] = i
        }
        return out
    }

    private fun nodeQueue(g: ChannelGraph, node: Int): Int = when {
        g.has(node, ChannelGraph.DENSE) -> 0
        g.has(node, ChannelGraph.PREFERRED) -> 1
        else -> 2
    }

    private fun capOf(maxChannels: Int): Int =
        if (maxChannels >= ChannelMaxFlow.INF || maxChannels < 0) ChannelMaxFlow.INF else maxChannels

    private fun max(a: Int, b: Int) = if (a > b) a else b
}

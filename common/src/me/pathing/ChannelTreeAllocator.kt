package allyouneed.me.pathing

import java.util.ArrayDeque

object ChannelTreeAllocator {
    fun allocate(g: ChannelGraph): ChannelResult {
        val n = g.nodeCount
        val nodeUsed = IntArray(n)
        val connAB = IntArray(g.connCount)
        val connBA = IntArray(g.connCount)
        val assigned = BooleanArray(n)
        val ride = BooleanArray(n)
        val parentNode = IntArray(n) { -1 }
        val parentConn = IntArray(n) { -1 }
        val ancestor = IntArray(n) { -1 }
        val subtreeMax = IntArray(n)
        val allowsCompressed = BooleanArray(n)
        val bottleneck = IntArray(n)
        val visited = BooleanArray(n)
        val ridePending = BooleanArray(n)
        val members = groupMembers(g)
        var extraEdges = false
        var paid = 0

        val queues = Array(3) { ArrayDeque<Int>() }
        for (i in 0 until n) {
            if (g.isController[i]) visited[i] = true
        }
        for (i in 0 until n) {
            if (!g.isController[i]) continue
            for (c in g.nodeAdj[i]) {
                val peer = g.other(c, i)
                if (g.isController[peer]) continue
                if (visited[peer]) {
                    extraEdges = true
                    continue
                }
                discover(
                    g, peer, i, c, 0, visited, parentNode, parentConn, ancestor, subtreeMax,
                    allowsCompressed, queues,
                )
            }
        }
        for (q in 0..2) {
            val queue = queues[q]
            while (queue.isNotEmpty()) {
                val u = queue.removeFirst()
                if (g.demand[u] && !ridePending[u]) {
                    if (tryUse(
                            g, u, ancestor, bottleneck, allowsCompressed, assigned,
                            nodeUsed, connAB, connBA, parentNode, parentConn,
                        )
                    ) {
                        paid++
                        if (g.swallow[u]) bottleneck[u] = g.maxChannels[u]
                        val gid = g.group[u]
                        if (gid >= 0) {
                            for (m in members[gid]) {
                                if (m != u) ridePending[m] = true
                            }
                        }
                    }
                }
                for (c in g.nodeAdj[u]) {
                    val peer = g.other(c, u)
                    if (visited[peer]) {
                        if (c != parentConn[u]) extraEdges = true
                        continue
                    }
                    discover(
                        g, peer, u, c, q, visited, parentNode, parentConn, ancestor, subtreeMax,
                        allowsCompressed, queues,
                    )
                }
            }
        }

        val demandGroups = countDemand(g)
        var usedPhase2 = false
        if (paid < demandGroups && extraEdges && boundRemaining(g, nodeUsed, assigned) > 0) {
            val extra = augment(
                g, nodeUsed, connAB, connBA, assigned, parentNode, parentConn, members, paid,
            )
            if (extra > 0) {
                paid += extra
                usedPhase2 = true
            }
        }

        val connUsed = IntArray(g.connCount) { connAB[it] + connBA[it] }
        var byBlocks = 0
        for (v in nodeUsed) byBlocks += v
        for (v in connUsed) byBlocks += v

        for (gid in members.indices) {
            var payer = -1
            for (m in members[gid]) {
                if (assigned[m]) {
                    payer = m
                    break
                }
            }
            if (payer < 0) continue
            for (m in members[gid]) {
                if (m == payer) continue
                ride[m] = true
                nodeUsed[m]++
            }
        }

        return ChannelResult(nodeUsed, connUsed, assigned, ride, paid, byBlocks, usedPhase2)
    }

    private fun discover(
        g: ChannelGraph,
        node: Int,
        parent: Int,
        conn: Int,
        queueIndex: Int,
        visited: BooleanArray,
        parentNode: IntArray,
        parentConn: IntArray,
        ancestor: IntArray,
        subtreeMax: IntArray,
        allowsCompressed: BooleanArray,
        queues: Array<ArrayDeque<Int>>,
    ) {
        visited[node] = true
        parentNode[node] = parent
        parentConn[node] = conn
        val selfMax = g.maxChannels[node]
        val selfCc = !g.has(node, ChannelGraph.NO_COMPRESSED)
        if (g.isController[parent]) {
            ancestor[node] = -1
            subtreeMax[node] = selfMax
            allowsCompressed[node] = selfCc
        } else {
            ancestor[node] = when {
                ancestor[parent] < 0 -> parent
                subtreeMax[parent] == subtreeMax[ancestor[parent]] -> ancestor[parent]
                else -> parent
            }
            subtreeMax[node] = min(subtreeMax[parent], selfMax)
            allowsCompressed[node] = allowsCompressed[parent] && selfCc
        }
        val idx = max(nodeQueue(g, node), queueIndex)
        queues[idx].addLast(node)
    }

    private fun tryUse(
        g: ChannelGraph,
        start: Int,
        ancestor: IntArray,
        bottleneck: IntArray,
        allowsCompressed: BooleanArray,
        assigned: BooleanArray,
        nodeUsed: IntArray,
        connAB: IntArray,
        connBA: IntArray,
        parentNode: IntArray,
        parentConn: IntArray,
    ): Boolean {
        if (g.has(start, ChannelGraph.COMPRESSED) && !allowsCompressed[start]) return false
        var pi = start
        while (pi >= 0) {
            val cap = g.maxChannels[pi]
            if (cap == 0 || bottleneck[pi] >= cap) return false
            pi = ancestor[pi]
        }
        pi = start
        while (pi >= 0) {
            bottleneck[pi]++
            pi = ancestor[pi]
        }
        assigned[start] = true
        addPath(g, start, nodeUsed, connAB, connBA, parentNode, parentConn)
        return true
    }

    private fun addPath(
        g: ChannelGraph,
        start: Int,
        nodeUsed: IntArray,
        connAB: IntArray,
        connBA: IntArray,
        parentNode: IntArray,
        parentConn: IntArray,
    ) {
        var child = start
        while (true) {
            nodeUsed[child]++
            val c = parentConn[child]
            if (c < 0) break
            val p = parentNode[child]
            if (g.connA[c] == p && g.connB[c] == child) connAB[c]++ else connBA[c]++
            if (g.isController[p]) break
            child = p
        }
    }

    private fun augment(
        g: ChannelGraph,
        nodeUsed: IntArray,
        connAB: IntArray,
        connBA: IntArray,
        assigned: BooleanArray,
        parentNode: IntArray,
        parentConn: IntArray,
        members: Array<IntArray>,
        paid: Int,
    ): Int {
        val n = g.nodeCount
        val unpaidGroups = ArrayList<Int>()
        val groupUnpaid = BooleanArray(members.size) { true }
        for (i in 0 until n) {
            if (assigned[i]) {
                val gid = g.group[i]
                if (gid >= 0) groupUnpaid[gid] = false
            }
        }
        for (gid in groupUnpaid.indices) {
            if (groupUnpaid[gid] && members[gid].isNotEmpty() && members[gid].any { g.demand[it] }) {
                unpaidGroups.add(gid)
            }
        }
        val solos = ArrayList<Int>()
        for (i in 0 until n) {
            if (g.demand[i] && !assigned[i] && g.group[i] < 0) solos.add(i)
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
            if (g.isController[i]) {
                sEdge[i] = flow.addEdge(s, vout[i], ChannelMaxFlow.INF)
            } else {
                val cap = capOf(g.maxChannels[i])
                vertEdge[i] = flow.addEdge(vin[i], vout[i], cap)
            }
        }
        for (c in 0 until g.connCount) {
            val a = g.connA[c]
            val b = g.connB[c]
            connEdgeAB[c] = flow.addEdge(vout[a], vin[b], ChannelMaxFlow.INF)
            connEdgeBA[c] = flow.addEdge(vout[b], vin[a], ChannelMaxFlow.INF)
        }

        for (i in 0 until n) {
            if (!assigned[i]) continue
            applyAssigned(g, i, flow, vin, vout, vertEdge, sEdge, connEdgeAB, connEdgeBA, parentNode, parentConn)
            if (g.swallow[i] && vertEdge[i] >= 0) {
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
                if (g.demand[m]) flow.addEdge(vout[m], gv, ChannelMaxFlow.INF)
            }
            groupTEdge[idx] = flow.addEdge(gv, t, 1)
            val compressed = members[gid].any { g.has(it, ChannelGraph.COMPRESSED) }
            if (compressed) compressedGroups.add(idx) else normalGroups.add(idx)
        }

        val noComp = ArrayList<Int>()
        for (i in 0 until n) {
            if (!g.isController[i] && g.has(i, ChannelGraph.NO_COMPRESSED) && vertEdge[i] >= 0) {
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
            if (vertEdge[i] >= 0) nodeUsed[i] = flow.flow(vertEdge[i])
        }
        for (c in 0 until g.connCount) {
            connAB[c] = flow.flow(connEdgeAB[c])
            connBA[c] = flow.flow(connEdgeBA[c])
        }
        var extra = 0
        for (i in solos) {
            if (flow.flow(soloEdge[i]) > 0 && !assigned[i]) {
                assigned[i] = true
                extra++
            }
        }
        for ((idx, gid) in unpaidGroups.withIndex()) {
            if (flow.flow(groupTEdge[idx]) > 0) {
                var payer = members[gid].firstOrNull { g.demand[it] } ?: continue
                for (m in members[gid]) {
                    if (g.demand[m] && nodeUsed[m] > 0) {
                        payer = m
                        break
                    }
                }
                assigned[payer] = true
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
        parentNode: IntArray,
        parentConn: IntArray,
    ) {
        var child = start
        if (vertEdge[child] >= 0) flow.addFlow(vertEdge[child], 1)
        while (true) {
            val c = parentConn[child]
            if (c < 0) break
            val p = parentNode[child]
            if (g.connA[c] == p && g.connB[c] == child) flow.addFlow(connEdgeAB[c], 1)
            else flow.addFlow(connEdgeBA[c], 1)
            if (g.isController[p]) {
                if (sEdge[p] >= 0) flow.addFlow(sEdge[p], 1)
                break
            }
            if (vertEdge[p] >= 0) flow.addFlow(vertEdge[p], 1)
            child = p
        }
    }

    private fun boundRemaining(g: ChannelGraph, nodeUsed: IntArray, assigned: BooleanArray): Int {
        val seen = BooleanArray(g.nodeCount)
        var bound = 0
        for (i in 0 until g.nodeCount) {
            if (!g.isController[i]) continue
            for (c in g.nodeAdj[i]) {
                val peer = g.other(c, i)
                if (g.isController[peer] || seen[peer]) continue
                seen[peer] = true
                bound += remaining(g, peer, nodeUsed, assigned)
            }
        }
        return bound
    }

    private fun remaining(g: ChannelGraph, node: Int, nodeUsed: IntArray, assigned: BooleanArray): Int {
        if (g.swallow[node] && assigned[node]) return 0
        val cap = capOf(g.maxChannels[node])
        val used = nodeUsed[node]
        return if (used >= cap) 0 else cap - used
    }

    private fun countDemand(g: ChannelGraph): Int {
        var maxG = -1
        for (v in g.group) if (v > maxG) maxG = v
        var d = 0
        val seen = if (maxG >= 0) BooleanArray(maxG + 1) else BooleanArray(0)
        for (i in 0 until g.nodeCount) {
            if (!g.demand[i]) continue
            val gid = g.group[i]
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
        for (v in g.group) if (v > maxG) maxG = v
        if (maxG < 0) return emptyArray()
        val deg = IntArray(maxG + 1)
        for (v in g.group) if (v >= 0) deg[v]++
        val out = Array(maxG + 1) { IntArray(deg[it]) }
        deg.fill(0)
        for (i in 0 until g.nodeCount) {
            val gid = g.group[i]
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

    private fun min(a: Int, b: Int) = if (a < b) a else b

    private fun max(a: Int, b: Int) = if (a > b) a else b
}

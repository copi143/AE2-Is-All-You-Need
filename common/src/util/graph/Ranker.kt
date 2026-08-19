package allyouneed.util.graph

import java.util.*

class Ranker<T> {
    private data class GEdge<T>(val from: T, val to: T, val type: EdgeType)

    private val nodes = LinkedHashSet<T>()

    private val out = HashMap<T, MutableList<GEdge<T>>>()

    private val `in` = HashMap<T, MutableList<GEdge<T>>>()

    /**
     * 同一对节点只保留一条边。
     *
     * Strong > Weak
     */
    private val edgeMap = HashMap<Pair<T, T>, GEdge<T>>()

    private fun addEdge(from: T, to: T, type: EdgeType) {
        nodes.add(from)
        nodes.add(to)

        val key = from to to

        val old = edgeMap[key]

        if (old != null) {

            // 已经是 strong，不需要改变
            if (old.type == EdgeType.STRONG) {
                return
            }

            // weak -> strong
            if (type == EdgeType.STRONG) {
                val newEdge = GEdge(from, to, EdgeType.STRONG)

                edgeMap[key] = newEdge

                out[from]!!.remove(old)

                `in`[to]!!.remove(old)

                out[from]!!.add(newEdge)

                `in`[to]!!.add(newEdge)
            }

            return
        }

        val edge = GEdge(from, to, type)

        edgeMap[key] = edge

        out.getOrPut(from) {
            ArrayList()
        }.add(edge)

        `in`.getOrPut(to) {
            ArrayList()
        }.add(edge)
    }

    // ==========================================================
    // 构造图
    // ==========================================================

    private fun build(constraints: List<Constraint<T>>) {
        for ((sources, targets) in constraints) {
            val strongTarget = targets[0]

            // 第一个 target = strong
            for (source in sources) {
                addEdge(source, strongTarget, EdgeType.STRONG)
            }

            // 后续 target = weak
            for (i in 1 until targets.size) {
                val target = targets[i]
                for (source in sources) {
                    addEdge(source, target, EdgeType.WEAK)
                }
            }
        }
    }

    // ==========================================================
    // Tarjan SCC
    // 只看 strong edge
    // ==========================================================

    private class Tarjan<T>(private val nodes: Collection<T>, private val out: Map<T, List<GEdge<T>>>) {

        private var time = 0

        private val index = HashMap<T, Int>()

        private val low = HashMap<T, Int>()

        private val stack = ArrayDeque<T>()

        private val onStack = HashSet<T>()

        val components = ArrayList<List<T>>()

        fun run(): List<List<T>> {

            for (node in nodes) {

                if (!index.containsKey(node)) {
                    dfs(node)
                }
            }

            return components
        }

        private fun dfs(v: T) {

            index[v] = time
            low[v] = time
            time++

            stack.addLast(v)
            onStack.add(v)

            for (edge in out[v].orEmpty()) {

                if (edge.type != EdgeType.STRONG) {
                    continue
                }

                val w = edge.to

                if (!index.containsKey(w)) {

                    dfs(w)

                    low[v] = minOf(
                        low[v]!!, low[w]!!
                    )

                } else if (onStack.contains(w)) {

                    low[v] = minOf(
                        low[v]!!, index[w]!!
                    )
                }
            }

            if (low[v] == index[v]) {

                val component = ArrayList<T>()

                while (true) {

                    val x = stack.removeLast()

                    onStack.remove(x)

                    component.add(x)

                    if (x == v) {
                        break
                    }
                }

                components.add(component)
            }
        }
    }

    // ==========================================================
    // Component
    // ==========================================================

    private data class Component<T>(
        val id: Int, val nodes: List<T>
    )

    // ==========================================================
    // SCC DAG
    // ==========================================================

    private fun buildComponentGraph(components: List<Component<T>>, componentOf: Map<T, Int>): Array<MutableSet<Int>> {

        val dag = Array(components.size) {
            mutableSetOf<Int>()
        }

        for (edge in edgeMap.values) {

            if (edge.type != EdgeType.STRONG) {
                continue
            }

            val a = componentOf[edge.from]!!

            val b = componentOf[edge.to]!!

            if (a != b) {
                dag[a].add(b)
            }
        }

        return dag
    }

    // ==========================================================
    // SCC DAG 拓扑排序
    //
    // 这里强边一定不会反向。
    //
    // ready component 中优先选择 weakOut - weakIn 大的。
    // ==========================================================

    private fun topoComponents(
        components: List<Component<T>>,
        dag: Array<MutableSet<Int>>,
        componentOf: Map<T, Int>,
    ): List<Int> {

        val n = components.size

        val indegree = IntArray(n)

        for (u in 0 until n) {
            for (v in dag[u]) {
                indegree[v]++
            }
        }

        val weakOut = IntArray(n)

        val weakIn = IntArray(n)

        for (edge in edgeMap.values) {

            if (edge.type != EdgeType.WEAK) {
                continue
            }

            val a = componentOf[edge.from]!!

            val b = componentOf[edge.to]!!

            if (a != b) {
                weakOut[a]++
                weakIn[b]++
            }
        }

        val ready = PriorityQueue<Int>(compareByDescending<Int> {
            weakOut[it] - weakIn[it]
        }.thenBy {
            it
        })

        for (i in 0 until n) {
            if (indegree[i] == 0) {
                ready.add(i)
            }
        }

        val result = ArrayList<Int>(n)

        while (ready.isNotEmpty()) {

            val u = ready.poll()

            result.add(u)

            for (v in dag[u]) {

                indegree[v]--

                if (indegree[v] == 0) {
                    ready.add(v)
                }
            }
        }

        check(result.size == n)

        return result
    }

    // ==========================================================
    // SCC 内部排序
    //
    // 只在 SCC > 1 时调用。
    // ==========================================================

    private fun internalOrder(
        component: List<T>
    ): List<T> {

        if (component.size <= 1) {
            return component
        }

        val set = component.toHashSet()

        // ------------------------------------------------------
        // 计算每个节点的：
        //
        // strongOut - strongIn
        // weakOut   - weakIn
        //
        // strong 优先
        // ------------------------------------------------------

        fun score(node: T): Pair<Int, Int> {

            var strong = 0

            var weak = 0

            for (e in out[node].orEmpty()) {

                if (!set.contains(e.to)) {
                    continue
                }

                when (e.type) {
                    EdgeType.STRONG -> strong++
                    EdgeType.WEAK -> weak++
                }
            }

            for (e in `in`[node].orEmpty()) {

                if (!set.contains(e.from)) {
                    continue
                }

                when (e.type) {
                    EdgeType.STRONG -> strong--
                    EdgeType.WEAK -> weak--
                }
            }

            return strong to weak
        }

        // ------------------------------------------------------
        // 初始排序
        // ------------------------------------------------------

        val order = component.sortedWith(
            Comparator { a, b ->

                val sa = score(a)
                val sb = score(b)

                when {

                    sa.first != sb.first -> sb.first.compareTo(sa.first)

                    sa.second != sb.second -> sb.second.compareTo(sa.second)

                    else -> a.toString().compareTo(
                        b.toString()
                    )
                }
            }).toMutableList()

        // ------------------------------------------------------
        // 相邻交换优化
        //
        // SCC 很小，所以直接做。
        //
        // 不重新计算整个 cost。
        // ------------------------------------------------------

        optimizeAdjacent(
            order, set
        )

        return order
    }

    // ==========================================================
    // 判断一条边是否反向
    // ==========================================================

    private fun isReversed(
        edge: GEdge<T>, position: Map<T, Int>
    ): Boolean {

        return position[edge.from]!! >= position[edge.to]!!
    }

    // ==========================================================
    // 相邻交换优化
    //
    // 每次只检查与 A/B 相关的边。
    //
    // 优先：
    //
    //   strong reverse 少
    //   weak reverse 少
    // ==========================================================

    private fun optimizeAdjacent(
        order: MutableList<T>, component: Set<T>
    ) {

        if (order.size < 2) {
            return
        }

        val position = HashMap<T, Int>()

        fun rebuildPosition() {

            position.clear()

            for (i in order.indices) {
                position[order[i]] = i
            }
        }

        rebuildPosition()

        /**
         * 计算交换 a,b 前后的局部代价。
         *
         * 只考虑涉及 a 或 b 的边。
         */
        fun localCost(
            a: T, b: T
        ): Pair<Int, Int> {

            var strong = 0
            var weak = 0

            val affected = HashSet<GEdge<T>>()

            for (e in out[a].orEmpty()) {
                if (component.contains(e.to)) {
                    affected.add(e)
                }
            }

            for (e in `in`[a].orEmpty()) {
                if (component.contains(e.from)) {
                    affected.add(e)
                }
            }

            for (e in out[b].orEmpty()) {
                if (component.contains(e.to)) {
                    affected.add(e)
                }
            }

            for (e in `in`[b].orEmpty()) {
                if (component.contains(e.from)) {
                    affected.add(e)
                }
            }

            for (e in affected) {

                if (isReversed(e, position)) {

                    when (e.type) {
                        EdgeType.STRONG -> strong++
                        EdgeType.WEAK -> weak++
                    }
                }
            }

            return strong to weak
        }

        /**
         * 最多做若干 pass。
         *
         * SCC 很小，通常 1~3 次就够。
         */
        repeat(4) {

            var changed = false

            var i = 0

            while (i < order.size - 1) {

                val a = order[i]

                val b = order[i + 1]

                val oldCost = localCost(a, b)

                // swap
                order[i] = b
                order[i + 1] = a

                position[b] = i
                position[a] = i + 1

                val newCost = localCost(a, b)

                val better =
                    newCost.first < oldCost.first || (newCost.first == oldCost.first && newCost.second < oldCost.second)

                if (better) {

                    changed = true

                    // 保持 swap

                } else {

                    // rollback
                    order[i] = a
                    order[i + 1] = b

                    position[a] = i
                    position[b] = i + 1

                    i++
                }
            }

            if (!changed) {
                return@repeat
            }
        }
    }

    // ==========================================================
    // 主入口
    // ==========================================================

    fun rank(constraints: List<Constraint<T>>): RankResult<T> {

        build(constraints)

        if (nodes.isEmpty()) {

            return RankResult(
                rank = emptyMap(),
                order = emptyList(),
                reversedEdges = emptyList(),
                reversedStrongCount = 0,
                reversedWeakCount = 0
            )
        }

        // ------------------------------------------------------
        // 1. 强边 SCC
        // ------------------------------------------------------

        val scc = Tarjan(nodes, out).run()

        val components = scc.mapIndexed { id, list ->
            Component(id, list)
        }

        val componentOf = HashMap<T, Int>()

        for ((id, nodes) in components) {
            for (node in nodes) {
                componentOf[node] = id
            }
        }

        // ------------------------------------------------------
        // 2. SCC DAG
        // ------------------------------------------------------

        val dag = buildComponentGraph(
            components, componentOf
        )

        // ------------------------------------------------------
        // 3. SCC 拓扑排序
        // ------------------------------------------------------

        val componentOrder = topoComponents(
            components, dag, componentOf
        )

        // ------------------------------------------------------
        // 4. 拼最终 order
        // ------------------------------------------------------

        val finalOrder = ArrayList<T>(nodes.size)

        for (componentId in componentOrder) {

            val component = components[componentId]

            if (component.nodes.size == 1) {

                finalOrder.add(
                    component.nodes[0]
                )

            } else {

                finalOrder.addAll(
                    internalOrder(
                        component.nodes
                    )
                )
            }
        }

        // ------------------------------------------------------
        // 5. rank
        // ------------------------------------------------------

        val rank = LinkedHashMap<T, Int>()

        for (i in finalOrder.indices) {
            rank[finalOrder[i]] = i
        }

        // ------------------------------------------------------
        // 6. 找反向边
        // ------------------------------------------------------

        val reversed = ArrayList<Edge<T>>()

        var reversedStrong = 0
        var reversedWeak = 0

        for (e in edgeMap.values) {

            if (rank[e.from]!! >= rank[e.to]!!) {

                reversed.add(
                    Edge(
                        e.from, e.to, e.type
                    )
                )

                when (e.type) {

                    EdgeType.STRONG -> reversedStrong++

                    EdgeType.WEAK -> reversedWeak++
                }
            }
        }

        return RankResult(
            rank = rank,
            order = finalOrder,
            reversedEdges = reversed,
            reversedStrongCount = reversedStrong,
            reversedWeakCount = reversedWeak
        )
    }

    companion object {
        fun <T> rank(constraints: List<Constraint<T>>): RankResult<T> {
            return Ranker<T>().rank(constraints)
        }
    }
}

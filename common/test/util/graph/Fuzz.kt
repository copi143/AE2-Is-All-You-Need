package allyouneed.util.graph

import kotlin.random.Random
import kotlin.system.measureNanoTime
import kotlin.system.measureTimeMillis

private const val DEFAULT_SEED = 0x1234ABCD

/**
 * 一次 fuzz 的配置
 */
data class Config(
    val seed: Int = DEFAULT_SEED,
    /** 测试多少轮 */
    val iterations: Int = 100_000,
    /** 节点数量范围 */
    val minNodes: Int = 1,
    /** 节点数量范围 */
    val maxNodes: Int = 100,
    /** constraint 数量 */
    val minConstraints: Int = 1,
    /** constraint 数量 */
    val maxConstraints: Int = 300,
    /** 每条 constraint source 数量 */
    val maxSources: Int = 5,
    /** 每条 constraint target 数量 */
    val maxTargets: Int = 5,
    /** 人为制造环的概率 */
    val cycleProbability: Double = 0.20,
    /** 自环概率 */
    val selfLoopProbability: Double = 0.01,
    /** 打印失败案例 */
    val printFailure: Boolean = true,
)

// ========================================================
// 随机 Constraint
// ========================================================

private fun generateConstraints(
    random: Random,
    nodeCount: Int,
    constraintCount: Int,
    maxSources: Int,
    maxTargets: Int,
    cycleProbability: Double,
    selfLoopProbability: Double
): List<Constraint<Int>> {

    val result = ArrayList<Constraint<Int>>(
        constraintCount
    )

    repeat(constraintCount) {

        val sourceCount = random.nextInt(
            1, maxSources.coerceAtMost(nodeCount) + 1
        )

        val targetCount = random.nextInt(
            1, maxTargets + 1
        )

        val sources = ArrayList<Int>(sourceCount)

        repeat(sourceCount) {

            sources.add(
                random.nextInt(nodeCount)
            )
        }

        val targets = ArrayList<Int>(targetCount)

        /**
         * 普通随机 target
         */
        repeat(targetCount) {

            val source = sources[random.nextInt(
                sources.size
            )]

            val target = if (random.nextDouble() < selfLoopProbability) {
                source
            } else if (random.nextDouble() < cycleProbability) {
                /**
                 * 有一定概率选择 source，
                 * 更容易制造环。
                 */
                source
            } else {
                random.nextInt(nodeCount)
            }

            targets.add(target)
        }

        result.add(
            Constraint(
                sources = sources, targets = targets
            )
        )
    }

    return result
}

// ========================================================
// 生成一些“人工环”
//
// 比完全随机更容易覆盖真正的环场景。
// ========================================================

private fun addRandomCycle(
    random: Random, constraints: MutableList<Constraint<Int>>, nodeCount: Int
) {

    if (nodeCount < 2) {
        return
    }

    val maxCycleLength = minOf(12, nodeCount)

    val length = random.nextInt(
        2, maxCycleLength + 1
    )

    val cycle = (0 until nodeCount).shuffled(random).take(length)

    for (i in cycle.indices) {

        val a = cycle[i]

        val b = cycle[(i + 1) % cycle.size]

        constraints.add(
            Constraint(
                sources = listOf(a), targets = listOf(b)
            )
        )
    }
}

// ========================================================
// 收集输入中所有节点
// ========================================================

private fun collectInputNodes(constraints: List<Constraint<Int>>): Set<Int> {
    val result = LinkedHashSet<Int>()
    for ((sources, targets) in constraints) {
        result.addAll(sources)
        result.addAll(targets)
    }
    return result
}

// ========================================================
// 将 Constraint 展开成实际 edge
//
// targets[0] = STRONG
// targets[1..] = WEAK
//
// 与 FastRanker 的语义一致。
// ========================================================

private data class TestEdge(
    val from: Int, val to: Int, val type: EdgeType
)

private fun expandEdges(
    constraints: List<Constraint<Int>>
): List<TestEdge> {

    /**
     * 同一 from -> to：
     *
     * STRONG > WEAK
     */
    val map = HashMap<Pair<Int, Int>, EdgeType>()

    for (c in constraints) {

        val strongTarget = c.targets[0]

        for (source in c.sources) {

            val key = source to strongTarget

            map[key] = stronger(
                map[key], EdgeType.STRONG
            )
        }

        for (i in 1 until c.targets.size) {

            val target = c.targets[i]

            for (source in c.sources) {

                val key = source to target

                map[key] = stronger(
                    map[key], EdgeType.WEAK
                )
            }
        }
    }

    return map.map { (key, type) ->
        TestEdge(
            from = key.first, to = key.second, type = type
        )
    }
}

private fun stronger(
    a: EdgeType?, b: EdgeType
): EdgeType {

    if (a == EdgeType.STRONG) {
        return EdgeType.STRONG
    }

    return b
}

// ========================================================
// 基础 invariant
// ========================================================

private fun checkBasicInvariants(
    constraints: List<Constraint<Int>>, result: RankResult<Int>
) {

    val inputNodes = collectInputNodes(constraints)

    // ----------------------------------------------------
    // 1. 所有节点都存在
    // ----------------------------------------------------

    check(
        result.rank.keys == inputNodes
    ) {
        """
            Rank nodes mismatch.

            input=$inputNodes
            result=${result.rank.keys}
            """.trimIndent()
    }

    // ----------------------------------------------------
    // 2. rank 必须唯一
    // ----------------------------------------------------

    val ranks = result.rank.values

    check(
        ranks.size == ranks.toSet().size
    ) {
        "Duplicate ranks: $result"
    }

    // ----------------------------------------------------
    // 3. rank 必须是 0..N-1
    // ----------------------------------------------------

    val expected = inputNodes.indices.toSet()

    check(
        ranks.toSet() == expected
    ) {
        """
            Rank is not contiguous.

            expected=$expected
            actual=${ranks.toSet()}
            """.trimIndent()
    }

    // ----------------------------------------------------
    // 4. order 与 rank 双向一致
    // ----------------------------------------------------

    check(
        result.order.size == inputNodes.size
    ) {
        "order size mismatch"
    }

    for (i in result.order.indices) {

        val node = result.order[i]

        check(
            result.rank[node] == i
        ) {
            """
                order/rank mismatch.

                index=$i
                node=$node
                rank=${result.rank[node]}
                """.trimIndent()
        }
    }

    // ----------------------------------------------------
    // 5. order 不允许重复
    // ----------------------------------------------------

    check(
        result.order.toSet().size == result.order.size
    ) {
        "Duplicate node in order"
    }
}

// ========================================================
// 检查 edge invariant
// ========================================================

private fun checkEdgeInvariants(
    constraints: List<Constraint<Int>>, result: RankResult<Int>
) {

    val edges = expandEdges(constraints)

    val reversedSet = result.reversedEdges.map {
        Triple(
            it.from, it.to, it.type
        )
    }.toSet()

    var strongReverse = 0
    var weakReverse = 0

    for (edge in edges) {

        val fromRank = result.rank[edge.from]!!

        val toRank = result.rank[edge.to]!!

        val reversed = fromRank >= toRank

        val key = Triple(
            edge.from, edge.to, edge.type
        )

        if (reversed) {

            check(
                reversedSet.contains(key)
            ) {
                """
                    Missing reversed edge.

                    edge=$edge
                    rankFrom=$fromRank
                    rankTo=$toRank

                    result.reversedEdges=
                    ${result.reversedEdges}
                    """.trimIndent()
            }

            when (edge.type) {
                EdgeType.STRONG -> strongReverse++

                EdgeType.WEAK -> weakReverse++
            }

        } else {

            check(
                !reversedSet.contains(key)
            ) {
                """
                    Edge incorrectly marked reversed.

                    edge=$edge
                    rankFrom=$fromRank
                    rankTo=$toRank
                    """.trimIndent()
            }
        }
    }

    // ----------------------------------------------------
    // reversedEdges 数量
    // ----------------------------------------------------

    check(
        result.reversedEdges.size == strongReverse + weakReverse
    ) {
        "reversed edge count mismatch"
    }

    // ----------------------------------------------------
    // strong / weak 统计
    // ----------------------------------------------------

    check(
        result.reversedStrongCount == strongReverse
    ) {
        """
            reversedStrongCount mismatch.

            expected=$strongReverse
            actual=${result.reversedStrongCount}
            """.trimIndent()
    }

    check(
        result.reversedWeakCount == weakReverse
    ) {
        """
            reversedWeakCount mismatch.

            expected=$weakReverse
            actual=${result.reversedWeakCount}
            """.trimIndent()
    }
}

// ========================================================
// 检查强边 SCC
//
// 不是检查最优解，而是检查一个很重要的性质：
//
// 如果某条 strong edge 不在原始 strong SCC 内部，
// 它不应该被反向。
//
// 因为 SCC 压缩后是 DAG。
// ========================================================

private fun checkCrossSccStrongEdges(
    constraints: List<Constraint<Int>>, result: RankResult<Int>
) {

    val edges = expandEdges(constraints)

    val strongEdges = edges.filter {
        it.type == EdgeType.STRONG
    }

    if (strongEdges.isEmpty()) {
        return
    }

    /**
     * 一个简单的 SCC 计算。
     *
     * fuzz 测试本身不追求速度，
     * 这里可以直接 Floyd-Warshall。
     *
     * 但为了适应 100~1000 节点，
     * 这里还是用 DFS。
     */

    val nodeSet = collectInputNodes(constraints)

    val out = HashMap<Int, MutableList<Int>>()

    val reverse = HashMap<Int, MutableList<Int>>()

    for (node in nodeSet) {
        out[node] = ArrayList()
        reverse[node] = ArrayList()
    }

    for ((from, to) in strongEdges) {

        if (from == to) {
            continue
        }

        out[from]!!.add(to)
        reverse[to]!!.add(from)
    }

    /**
     * Kosaraju
     */

    val visited = HashSet<Int>()

    val finish = ArrayList<Int>()

    fun dfs1(start: Int) {

        val stack = ArrayDeque<Pair<Int, Int>>()

        stack.addLast(start to 0)
        visited.add(start)

        while (stack.isNotEmpty()) {

            val (u, index) = stack.removeLast()

            val list = out[u]!!

            if (index < list.size) {

                stack.addLast(
                    u to (index + 1)
                )

                val v = list[index]

                if (visited.add(v)) {
                    stack.addLast(
                        v to 0
                    )
                }

            } else {

                finish.add(u)
            }
        }
    }

    for (node in nodeSet) {

        if (!visited.contains(node)) {
            dfs1(node)
        }
    }

    val component = HashMap<Int, Int>()

    var componentId = 0

    fun dfs2(start: Int) {

        val stack = ArrayDeque<Int>()

        stack.addLast(start)
        component[start] = componentId

        while (stack.isNotEmpty()) {

            val u = stack.removeLast()

            for (v in reverse[u]!!) {

                if (!component.containsKey(v)) {

                    component[v] = componentId

                    stack.addLast(v)
                }
            }
        }
    }

    for (i in finish.indices.reversed()) {

        val node = finish[i]

        if (!component.containsKey(node)) {

            dfs2(node)

            componentId++
        }
    }

    /**
     * 如果 strong edge 跨 SCC，
     * 必须正向。
     */
    for (e in strongEdges) {

        if (component[e.from] != component[e.to]) {

            val rf = result.rank[e.from]!!

            val rt = result.rank[e.to]!!

            check(rf < rt) {

                """
                    Cross-SCC strong edge reversed!

                    edge=$e

                    fromRank=$rf
                    toRank=$rt

                    order=${result.order}

                    constraints=$constraints
                    """.trimIndent()
            }
        }
    }
}

private fun run(random: Random, iteration: Int, config: Config): Long {
    val nodeCount = random.nextInt(config.minNodes, config.maxNodes + 1)
    val constraintCount = random.nextInt(config.minConstraints, config.maxConstraints + 1)
    val constraints = generateConstraints(
        random = random,
        nodeCount = nodeCount,
        constraintCount = constraintCount,
        maxSources = config.maxSources,
        maxTargets = config.maxTargets,
        cycleProbability = config.cycleProbability,
        selfLoopProbability = config.selfLoopProbability
    ).toMutableList()

    if (random.nextDouble() < config.cycleProbability) {
        addRandomCycle(random, constraints, nodeCount)
    }

    var result: RankResult<Int>
    val nanoTime = measureNanoTime {
        result = Ranker.rank(constraints)
    }

    checkBasicInvariants(constraints, result)
    checkEdgeInvariants(constraints, result)
    checkCrossSccStrongEdges(constraints, result)

    return nanoTime
}

fun main() {
    val config = Config(
        seed = 123456789,
        iterations = 100,
        minNodes = 1,
        maxNodes = 100,
        minConstraints = 1,
        maxConstraints = 300,
        maxSources = 5,
        maxTargets = 5,
        cycleProbability = 0.25,
        selfLoopProbability = 0.02,
    )

    println()

    val random = Random(config.seed)
    var completed = 0
    val timeList = mutableListOf<Long>()
    val elapsed = measureTimeMillis {
        repeat(config.iterations) { i ->
            try {
                timeList.add(run(random = random, iteration = i, config = config))
                completed++
            } catch (e: Throwable) {
                println("================================")
                println("FUZZ FAILURE")
                println("================================")
                println("iteration = $i")
                println("seed = ${config.seed}")
                println("completed = $completed")
                if (config.printFailure) {
                    e.printStackTrace()
                }
                throw e
            }
        }
    }

    println("================================")
    println("Fuzz test PASSED")
    println("================================")
    println("iterations = ${config.iterations}")
    println("seed = ${config.seed}")
    println("totalTime = $elapsed ms")
    println("totalAvg = ${elapsed.toDouble() / config.iterations} ms / case")
    println("time = ${timeList.sum()} ns")
    println("avg = ${timeList.average()}")
}

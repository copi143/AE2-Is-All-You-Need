package allyouneed.util.graph

enum class EdgeType {
    STRONG, WEAK
}

data class Constraint<T>(val sources: List<T>, val targets: List<T>) {
    constructor(pair: Pair<List<T>, List<T>>) : this(pair.first, pair.second)

    init {
        require(sources.isNotEmpty() && targets.isNotEmpty())
    }
}

data class Edge<T>(val from: T, val to: T, val type: EdgeType)

data class RankResult<T>(
    val rank: Map<T, Int>,
    val order: List<T>,
    val reversedEdges: List<Edge<T>>,
    val reversedStrongCount: Int,
    val reversedWeakCount: Int
)

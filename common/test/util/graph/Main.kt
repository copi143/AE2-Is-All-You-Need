package allyouneed.util.graph

@Suppress("KotlinPrintToLogpoint")
fun main() {
    val constraints = listOf(
        Constraint(sources = listOf("A", "B"), targets = listOf("C", "D", "E")),
        Constraint(sources = listOf("C"), targets = listOf("F")),
        Constraint(sources = listOf("F"), targets = listOf("A")),
        Constraint(sources = listOf("E"), targets = listOf("A", "X")),
    )

    val result = Ranker<String>().rank(constraints)

    println()
    println("order:")
    println(result.order)

    println()
    println("rank:")
    for ((node, rank) in result.rank) {
        println("$node -> $rank")
    }

    println()
    println("reversed strong = " + result.reversedStrongCount)
    println("reversed weak = " + result.reversedWeakCount)

    println()
    println("reversed edges:")
    for ((from, to, type) in result.reversedEdges) {
        println("$from -> $to [$type]")
    }
}

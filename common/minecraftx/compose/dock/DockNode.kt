package minecraftx.compose.dock

enum class DockAxis { Horizontal, Vertical }

enum class DockSide { Left, Right, Top, Bottom }

sealed class DockNode {
    abstract val id: String

    data class Split(
        override val id: String,
        val axis: DockAxis,
        val ratio: Float,
        val first: DockNode,
        val second: DockNode,
    ) : DockNode()

    data class Leaf(
        override val id: String,
        val tabs: List<String>,
        val active: String?,
    ) : DockNode()
}

sealed class DockDrop {
    data class TabBar(val leafId: String, val index: Int) : DockDrop()
    data class Center(val leafId: String) : DockDrop()
    data class Edge(val leafId: String, val side: DockSide) : DockDrop()
}

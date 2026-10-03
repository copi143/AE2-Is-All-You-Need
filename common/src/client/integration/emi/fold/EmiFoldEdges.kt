package allyouneed.client.integration.emi.fold

/**
 * 合并外轮廓的纯逻辑：同组相邻格共享边不画，只描外圈。
 *
 * 无 Minecraft / EMI / Gson 依赖，可单元测试。运行时由
 * [EmiFoldOutline][allyouneed.client.integration.emi.fold.EmiFoldOutline]
 * 按侧边栏当前页的网格调用。
 */
object EmiFoldEdges {
    enum class Edge {
        TOP,
        BOTTOM,
        LEFT,
        RIGHT,
    }

    /**
     * @param cells 网格坐标 (x, y) → 组键
     * @return 每个格需要绘制的边集合；同组相邻的共享边会被双方同时省去
     */
    fun edgesFor(cells: Map<Pair<Int, Int>, String>): Map<Pair<Int, Int>, Set<Edge>> {
        if (cells.isEmpty()) return emptyMap()
        val result = LinkedHashMap<Pair<Int, Int>, Set<Edge>>(cells.size)
        for ((pos, key) in cells) {
            val (x, y) = pos
            val edges = LinkedHashSet<Edge>(4)
            if (cells[x to y - 1] != key) edges.add(Edge.TOP)
            if (cells[x to y + 1] != key) edges.add(Edge.BOTTOM)
            if (cells[x - 1 to y] != key) edges.add(Edge.LEFT)
            if (cells[x + 1 to y] != key) edges.add(Edge.RIGHT)
            result[pos] = edges
        }
        return result
    }
}

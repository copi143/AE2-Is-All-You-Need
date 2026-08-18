package minecraftx.compose.dock

data class DockState(
    val root: DockNode,
    val closed: List<String> = emptyList(),
    val focusedLeafId: String? = firstLeafId(root),
    val nextId: Int = 1,
) {
    fun selectTab(leafId: String, tabId: String): DockState {
        val leaf = findLeaf(leafId) ?: return this
        if (tabId !in leaf.tabs) return this
        return copy(
            root = root.mapLeaf(leafId) { it.copy(active = tabId) },
            focusedLeafId = leafId,
        )
    }

    fun setRatio(splitId: String, ratio: Float): DockState =
        copy(root = root.mapSplit(splitId) { it.copy(ratio = ratio.coerceIn(MIN_RATIO, MAX_RATIO)) })

    fun closeTab(tabId: String): DockState {
        if (findLeafOf(tabId) == null) return this
        val next = copy(root = root.removeTab(tabId), closed = closed + tabId)
        return next.copy(focusedLeafId = next.resolveFocus())
    }

    fun openTab(tabId: String): DockState {
        if (findLeafOf(tabId) != null) return copy(closed = closed.filter { it != tabId })
        val leafId = focusedLeafId ?: firstLeafId(root) ?: return this
        return copy(
            root = root.insertTab(leafId, tabId, -1),
            closed = closed.filter { it != tabId },
            focusedLeafId = leafId,
        )
    }

    fun moveTab(tabId: String, target: DockDrop): DockState {
        val source = findLeafOf(tabId) ?: return this
        return when (target) {
            is DockDrop.TabBar -> if (target.leafId == source.id) {
                reorderInLeaf(source.id, tabId, target.index)
            } else {
                applyMove(tabId) { removed ->
                    removed.copy(
                        root = removed.root.insertTab(target.leafId, tabId, target.index),
                        focusedLeafId = target.leafId,
                    )
                }
            }
            is DockDrop.Center -> if (target.leafId == source.id) {
                selectTab(source.id, tabId)
            } else {
                applyMove(tabId) { removed ->
                    removed.copy(
                        root = removed.root.insertTab(target.leafId, tabId, -1),
                        focusedLeafId = target.leafId,
                    )
                }
            }
            is DockDrop.Edge -> {
                if (target.leafId == source.id && source.tabs.size <= 1) this
                else applyMove(tabId) { removed ->
                    val leafId = "n${removed.nextId}"
                    val splitId = "n${removed.nextId + 1}"
                    val newLeaf = DockNode.Leaf(leafId, listOf(tabId), tabId)
                    removed.copy(
                        root = removed.root.splitAt(target.leafId, target.side, newLeaf, splitId),
                        nextId = removed.nextId + 2,
                        focusedLeafId = leafId,
                    )
                }
            }
        }
    }

    fun findLeaf(id: String): DockNode.Leaf? = root.findLeaf(id)

    fun findLeafOf(tabId: String): DockNode.Leaf? = root.findLeafOf(tabId)

    private fun reorderInLeaf(leafId: String, tabId: String, index: Int): DockState {
        val leaf = findLeaf(leafId) ?: return this
        val without = leaf.tabs.filter { it != tabId }
        val clamped = index.coerceIn(0, without.size)
        val tabs = without.take(clamped) + tabId + without.drop(clamped)
        return copy(
            root = root.mapLeaf(leafId) { it.copy(tabs = tabs, active = tabId) },
            focusedLeafId = leafId,
        )
    }

    private fun applyMove(tabId: String, insert: (DockState) -> DockState): DockState {
        val removed = copy(root = root.removeTab(tabId))
        return insert(removed).let { it.copy(focusedLeafId = it.focusedLeafId ?: it.resolveFocus()) }
    }

    private fun resolveFocus(): String? {
        focusedLeafId?.let { if (root.findLeaf(it) != null) return it }
        return firstLeafId(root)
    }

    companion object {
        const val MIN_RATIO = 0.15f
        const val MAX_RATIO = 0.85f
    }
}

private fun firstLeafId(node: DockNode): String? = when (node) {
    is DockNode.Leaf -> node.id
    is DockNode.Split -> firstLeafId(node.first) ?: firstLeafId(node.second)
}

internal fun DockNode.findLeaf(id: String): DockNode.Leaf? = when (this) {
    is DockNode.Leaf -> if (this.id == id) this else null
    is DockNode.Split -> first.findLeaf(id) ?: second.findLeaf(id)
}

internal fun DockNode.findLeafOf(tabId: String): DockNode.Leaf? = when (this) {
    is DockNode.Leaf -> if (tabId in tabs) this else null
    is DockNode.Split -> first.findLeafOf(tabId) ?: second.findLeafOf(tabId)
}

internal fun DockNode.mapLeaf(id: String, transform: (DockNode.Leaf) -> DockNode.Leaf): DockNode = when (this) {
    is DockNode.Leaf -> if (this.id == id) transform(this) else this
    is DockNode.Split -> copy(first = first.mapLeaf(id, transform), second = second.mapLeaf(id, transform))
}

internal fun DockNode.mapSplit(id: String, transform: (DockNode.Split) -> DockNode.Split): DockNode = when (this) {
    is DockNode.Leaf -> this
    is DockNode.Split -> if (this.id == id) transform(this) else copy(
        first = first.mapSplit(id, transform),
        second = second.mapSplit(id, transform),
    )
}

internal fun DockNode.removeTab(tabId: String): DockNode = when (this) {
    is DockNode.Leaf -> {
        val tabs = tabs.filter { it != tabId }
        copy(tabs = tabs, active = when {
            active != tabId -> active
            tabs.isNotEmpty() -> tabs.last()
            else -> null
        })
    }
    is DockNode.Split -> collapse(copy(first = first.removeTab(tabId), second = second.removeTab(tabId)))
}

private fun collapse(split: DockNode.Split): DockNode {
    val aEmpty = split.first.isEmpty()
    val bEmpty = split.second.isEmpty()
    return when {
        aEmpty && bEmpty -> DockNode.Leaf(split.id, emptyList(), null)
        aEmpty -> split.second
        bEmpty -> split.first
        else -> split
    }
}

private fun DockNode.isEmpty(): Boolean = when (this) {
    is DockNode.Leaf -> tabs.isEmpty()
    is DockNode.Split -> first.isEmpty() && second.isEmpty()
}

internal fun DockNode.insertTab(leafId: String, tabId: String, index: Int): DockNode = mapLeaf(leafId) { leaf ->
    val tabs = leaf.tabs.filter { it != tabId }
    val clamped = if (index < 0) tabs.size else index.coerceIn(0, tabs.size)
    leaf.copy(tabs = tabs.take(clamped) + tabId + tabs.drop(clamped), active = tabId)
}

internal fun DockNode.splitAt(
    leafId: String,
    side: DockSide,
    newLeaf: DockNode.Leaf,
    splitId: String,
): DockNode = when (this) {
    is DockNode.Leaf -> if (id != leafId) this else {
        val axis = if (side == DockSide.Left || side == DockSide.Right) DockAxis.Horizontal else DockAxis.Vertical
        val first = if (side == DockSide.Left || side == DockSide.Top) newLeaf else this
        val second = if (side == DockSide.Left || side == DockSide.Top) this else newLeaf
        DockNode.Split(splitId, axis, 0.5f, first, second)
    }
    is DockNode.Split -> copy(
        first = first.splitAt(leafId, side, newLeaf, splitId),
        second = second.splitAt(leafId, side, newLeaf, splitId),
    )
}

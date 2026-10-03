package allyouneed.client.integration.emi.fold.model

enum class FoldInlineMode { EXPANDED, COLLAPSED }

/** 只负责一层网格的展开决策，不修改分类树。手动展开可以翻页，自动展开受整页容量约束。 */
object FoldInlineLayout {
    fun arrange(
        entries: List<FoldTree.Entry>,
        pageSize: Int,
        overrides: Map<String, FoldInlineMode> = emptyMap(),
        extraSlots: Int = 0,
    ): List<FoldTree.Entry> {
        val candidates = entries.filterIsInstance<FoldTree.Entry.Group>().filter { it.canInline }
        val expanded = candidates.filter { overrides[it.key] == FoldInlineMode.EXPANDED }.mapTo(HashSet()) { it.key }
        val occupied = entries.size + extraSlots.coerceAtLeast(0) + candidates.filter { it.key in expanded }.sumOf { it.members.size - 1 }
        if (pageSize > occupied) {
            var remaining = pageSize - occupied
            // 每展开一组增加 N-1 个槽。较小组优先，可在预算内展开最多的组；同大小保持源顺序。
            for (group in candidates.filter { it.key !in expanded && overrides[it.key] != FoldInlineMode.COLLAPSED }
                .sortedBy { it.members.size }) {
                val cost = group.members.size - 1
                if (cost > remaining) break
                expanded.add(group.key)
                remaining -= cost
            }
        }
        if (expanded.isEmpty()) return entries
        return buildList {
            for (entry in entries) {
                if (entry is FoldTree.Entry.Group && entry.key in expanded) {
                    entry.members.forEach { add(FoldTree.Entry.Stack(it, entry.key)) }
                } else add(entry)
            }
        }
    }
}

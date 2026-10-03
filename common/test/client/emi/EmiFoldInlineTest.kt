package allyouneed.client.integration.emi.fold

import allyouneed.client.integration.emi.fold.model.FoldBrowserState
import allyouneed.client.integration.emi.fold.model.FoldClassifier
import allyouneed.client.integration.emi.fold.model.FoldDecision
import allyouneed.client.integration.emi.fold.model.FoldFeature
import allyouneed.client.integration.emi.fold.model.FoldInlineLayout
import allyouneed.client.integration.emi.fold.model.FoldInlineMode
import allyouneed.client.integration.emi.fold.model.FoldKind
import allyouneed.client.integration.emi.fold.model.FoldTree
import org.junit.jupiter.api.Test
import kotlin.test.*

class EmiFoldInlineTest {
    private fun tree(vararg childSizes: Int): FoldTree {
        val children = childSizes.flatMapIndexed { child, size -> List(size) { child } }
        return FoldTree.build(children.mapIndexed { i, _ ->
            FoldFeature("example:resource_$i", FoldKind.ITEM, "Example", "Example", emptySet(), "")
        }, children.map { FoldDecision(family = listOf("parts", "child$it")) })
    }

    private fun group(key: String, start: Int, size: Int) = FoldTree.Entry.Group(key, IntArray(size) { start + it })
    private fun indices(entries: List<FoldTree.Entry>): List<Int> = entries.flatMap { when (it) {
        is FoldTree.Entry.Stack -> listOf(it.index)
        is FoldTree.Entry.Group -> it.members.toList()
    } }

    @Test
    fun `4加1加1加4加1作为一个11成员叶组 不再展示二级分类`() {
        val tree = tree(4, 1, 1, 4, 1)
        val visible = IntArray(11) { it }
        val projected = tree.project(visible)
        val group = projected.entries.single() as FoldTree.Entry.Group
        assertEquals(11, group.members.size)
        assertTrue(group.canInline)
        assertEquals("parts", tree.nodes.getValue(group.key).label)
        assertTrue(tree.project(visible, nodeKey = group.key).entries.all { it is FoldTree.Entry.Stack })
        assertEquals(projected.entries, FoldInlineLayout.arrange(projected.entries, 10))
        val inline = FoldInlineLayout.arrange(projected.entries, 11)
        assertEquals(11, inline.size)
        assertTrue(inline.all { it is FoldTree.Entry.Stack && it.inlineGroup == group.key })
        assertEquals(visible.toList(), indices(inline))
    }

    @Test
    fun `十九个可以压平 二十个继续展示子类`() {
        for (size in listOf(19, 20)) {
            val tree = tree(10, size - 10)
            val visible = IntArray(size) { it }
            val group = tree.project(visible).entries.single() as FoldTree.Entry.Group
            assertEquals(size < 20, group.canInline)
            val inside = tree.project(visible, nodeKey = group.key)
            if (size == 19) assertTrue(inside.entries.all { it is FoldTree.Entry.Stack })
            else assertEquals(2, inside.entries.filterIsInstance<FoldTree.Entry.Group>().size)
        }
    }

    @Test
    fun `搜索后的匹配数量决定压平和阈值 而非完整组的大小`() {
        val tree = tree(12, 12)
        val all = IntArray(24) { it }
        val root = tree.project(all).entries.single() as FoldTree.Entry.Group
        assertFalse(root.canInline)
        val nineteen = tree.project(all.take(19).toIntArray()).entries.single() as FoldTree.Entry.Group
        assertEquals(root.key, nineteen.key)
        assertTrue(nineteen.canInline)
        val narrow = tree.project(all.take(3).toIntArray())
        assertTrue(narrow.entries.all { it is FoldTree.Entry.Stack && it.inlineGroup == null })
        assertEquals(narrow.entries, FoldInlineLayout.arrange(narrow.entries, 100, mapOf(root.key to FoldInlineMode.COLLAPSED)))
        assertTrue(tree.project(intArrayOf()).entries.isEmpty())
        assertFalse((tree.project(all).entries.single() as FoldTree.Entry.Group).canInline)
    }

    @Test
    fun `容量预算优先展开较小组 但显示顺序和成员不变`() {
        val entries = listOf(group("large", 0, 12), group("small", 12, 4), group("medium", 16, 6),
            FoldTree.Entry.Stack(22), FoldTree.Entry.Stack(23))
        val inline = FoldInlineLayout.arrange(entries, 14)
        assertEquals(13, inline.size)
        assertEquals(entries.first(), inline.first())
        assertEquals(setOf("small", "medium"), inline.filterIsInstance<FoldTree.Entry.Stack>().mapNotNull { it.inlineGroup }.toSet())
        assertEquals(indices(entries), indices(inline))
    }

    @Test
    fun `等于或超过一页不自动展开 未分类的外部槽位也计入预算`() {
        val entries = listOf(group("a", 0, 8), FoldTree.Entry.Stack(8), FoldTree.Entry.Stack(9))
        assertEquals(entries, FoldInlineLayout.arrange(entries, 3))
        assertEquals(entries, FoldInlineLayout.arrange(entries, 2))
        assertEquals(entries, FoldInlineLayout.arrange(entries, 0))
        assertEquals(10, FoldInlineLayout.arrange(entries, 10).size)
        assertEquals(entries, FoldInlineLayout.arrange(entries, 10, extraSlots = 1))
    }

    @Test
    fun `手动收起阻止自动展开 显式展开允许翻页`() {
        val entries = listOf(group("a", 0, 7), group("b", 7, 5))
        val closed = mapOf("a" to FoldInlineMode.COLLAPSED)
        val automatic = FoldInlineLayout.arrange(entries, 100, closed)
        assertEquals(entries.first(), automatic.first())
        assertEquals(6, automatic.size)
        assertEquals(automatic, FoldInlineLayout.arrange(entries, 100, closed))
        val manual = FoldInlineLayout.arrange(entries, 4, mapOf("a" to FoldInlineMode.EXPANDED))
        assertEquals(8, manual.size)
        assertEquals(entries.last(), manual.last())
        assertEquals(indices(entries), indices(manual))
        val large = listOf(group("large", 0, 20))
        assertEquals(large, FoldInlineLayout.arrange(large, 100, mapOf("large" to FoldInlineMode.EXPANDED)))
    }

    @Test
    fun `页容量变化可重算自动展开 手动状态保持优先`() {
        val entries = listOf(group("a", 0, 5), group("b", 5, 7))
        assertEquals(12, FoldInlineLayout.arrange(entries, 12).size)
        assertEquals(6, FoldInlineLayout.arrange(entries, 8).size)
        assertEquals(entries, FoldInlineLayout.arrange(entries, 5))
        assertEquals(12, FoldInlineLayout.arrange(entries, 12).size)
        assertEquals(8, FoldInlineLayout.arrange(entries, 12, mapOf("a" to FoldInlineMode.COLLAPSED)).size)
    }

    @Test
    fun `不同大小与容量组合的自动展开始终完整且不超一页`() {
        for (a in 4..19) for (b in 4..19) for (pageSize in 2..40) {
            val entries = listOf(group("a", 0, a), group("b", a, b))
            val shown = FoldInlineLayout.arrange(entries, pageSize)
            assertTrue(shown.size <= pageSize)
            assertEquals((0 until a + b).toList(), indices(shown))
        }
    }

    @Test
    fun `手动内联状态按导航层与搜索隔离 清空搜索恢复原状态`() {
        val state = FoldBrowserState()
        state.setInline("a", FoldInlineMode.COLLAPSED)
        val revision = state.revision
        state.setInline("a", FoldInlineMode.COLLAPSED)
        assertEquals(revision, state.revision)
        state.enter("parent", 3)
        assertTrue(state.inlineModes.isEmpty())
        state.setInline("b", FoldInlineMode.EXPANDED)
        state.synchronizeQuery("iron", 2)
        assertTrue(state.inlineModes.isEmpty())
        state.setInline("b", FoldInlineMode.COLLAPSED)
        state.synchronizeQuery("copper", 0)
        assertTrue(state.inlineModes.isEmpty())
        assertEquals(2, state.synchronizeQuery("", 0))
        assertEquals(FoldInlineMode.EXPANDED, state.inlineModes["b"])
        assertEquals(3, state.back(2))
        assertEquals(FoldInlineMode.COLLAPSED, state.inlineModes["a"])
        state.selectKind(FoldKind.BLOCK)
        assertTrue(state.inlineModes.isEmpty())
        state.setInline("c", FoldInlineMode.EXPANDED)
        state.resetTree()
        assertTrue(state.inlineModes.isEmpty())
    }
}

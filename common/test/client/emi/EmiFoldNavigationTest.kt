package allyouneed.client.integration.emi.fold

import allyouneed.client.integration.emi.fold.model.FoldBrowserState
import allyouneed.client.integration.emi.fold.model.FoldClassifier
import allyouneed.client.integration.emi.fold.model.FoldFeature
import allyouneed.client.integration.emi.fold.model.FoldKind
import allyouneed.client.integration.emi.fold.model.FoldTree
import org.junit.jupiter.api.Test
import kotlin.test.*

class EmiFoldNavigationTest {
    private fun feature(id: String, kind: FoldKind = FoldKind.ITEM, tags: List<String> = emptyList()) =
        FoldFeature(id, kind, "example.Resource", "example.Resource", emptySet(), "", tags)

    @Test
    fun `搜索四个成员仍成组 三个成员散开 零个成员隐藏`() {
        val tree = FoldClassifier.build((1..8).map { feature("sample:${it}k_item_storage_cell") })
        val all = tree.project(IntArray(8) { it })
        val key = (all.entries.single() as FoldTree.Entry.Group).key
        val four = tree.project(intArrayOf(7, 3, 1, 0))
        val group = four.entries.single() as FoldTree.Entry.Group
        assertEquals(key, group.key)
        assertContentEquals(intArrayOf(7, 3, 1, 0), group.members)
        val three = tree.project(intArrayOf(7, 3, 1))
        assertEquals(listOf(7, 3, 1), three.entries.map { (it as FoldTree.Entry.Stack).index })
        assertTrue(tree.project(intArrayOf()).entries.isEmpty())
        assertEquals(key, (tree.project(IntArray(8) { it }).entries.single() as FoldTree.Entry.Group).key)
    }

    @Test
    fun `大类过滤与搜索取交集且计数不重复`() {
        val tree = FoldClassifier.build(FoldKind.entries.flatMap { kind -> (1..4).map { feature("sample:${it}k_cell", kind) } })
        val result = tree.project(intArrayOf(0, 1, 4, 5, 8, 9, 12, 13, 0, 4), FoldKind.FLUID)
        assertEquals(2, result.matched)
        assertTrue(result.kindCounts.values.all { it == 2 })
        assertEquals(listOf(8, 9), result.entries.map { (it as FoldTree.Entry.Stack).index })
    }

    @Test
    fun `钻入显示直接子类 叶级显示原物品 空搜索仍可返回`() {
        val features = listOf("small", "tiny").flatMap { size -> (1..12).map { n ->
            feature("sample:${size}_material${n}_dust", tags = listOf("shared:${size}_dusts"))
        } }
        val tree = FoldClassifier.build(features)
        val root = tree.project(IntArray(features.size) { it }).entries.single() as FoldTree.Entry.Group
        assertEquals("dust", tree.nodes.getValue(root.key).label)
        val children = tree.project(IntArray(features.size) { it }, nodeKey = root.key).entries
        assertEquals(setOf("small dust", "tiny dust"), children.map { tree.nodes.getValue((it as FoldTree.Entry.Group).key).label }.toSet())
        val child = children[0] as FoldTree.Entry.Group
        val leaf = tree.project(child.members, nodeKey = child.key)
        assertTrue(leaf.entries.all { it is FoldTree.Entry.Stack })
        assertEquals(12, leaf.matched)
        assertTrue(tree.project(intArrayOf(), nodeKey = child.key).entries.isEmpty())
    }

    @Test
    fun `无标签依靠类群补全 孤立矿辞不能误合并纸`() {
        val features = (1..5).map { n -> feature("sample:metal${n}_plate", tags = listOf("shared:plates")) } +
            feature("sample:paper", tags = listOf("shared:plates", "shared:plates/paper"))
        val tree = FoldClassifier.build(features.map { it.copy(guard = "net.minecraft.world.item.Item") })
        val projection = tree.project(IntArray(features.size) { it })
        assertTrue(projection.entries.any { it == FoldTree.Entry.Stack(5) })
        assertEquals(5, (projection.entries.first() as FoldTree.Entry.Group).members.size)
    }

    @Test
    fun `搜索状态独立 清空恢复分类与原页码`() {
        val state = FoldBrowserState()
        state.enter("ore", 6)
        assertEquals(0, state.synchronizeQuery("iron", 3))
        assertNull(state.nodeKey)
        state.enter("search-iron", 1)
        assertEquals(0, state.synchronizeQuery("copper", 2))
        assertNull(state.nodeKey)
        assertEquals(3, state.synchronizeQuery("", 0))
        assertEquals("ore", state.nodeKey)
        assertEquals(6, state.back(3))
        assertNull(state.nodeKey)
    }

    @Test
    fun `切换大类重置路径 重建索引清理旧节点但保留大类`() {
        val state = FoldBrowserState()
        state.enter("old", 7)
        assertEquals(0, state.selectKind(FoldKind.BLOCK))
        assertNull(state.nodeKey)
        state.enter("blocks", 1)
        state.resetTree()
        assertEquals(FoldKind.BLOCK, state.kind)
        assertNull(state.nodeKey)
        assertFalse(state.canBack)
    }

    @Test
    fun `跨层返回和页码钳制`() {
        val state = FoldBrowserState()
        state.enter("a", 5)
        state.enter("b", 2)
        state.enter("c", 3)
        assertEquals(2, state.jump("a", 8))
        assertEquals(5, state.jump(null, 2))
        assertEquals(0, FoldBrowserState.clampPage(10, 0, 40))
        assertEquals(1, FoldBrowserState.clampPage(10, 41, 40))
        assertEquals(0, FoldBrowserState.clampPage(10, 41, 0))
    }

    @Test
    fun `NBT 包装物钻入引用分类仍保留原包装物及搜索阈值`() {
        val targets = listOf("slab", "stairs").flatMap { shape -> (1..36).map { n ->
            feature("sample:stone${n}_$shape", FoldKind.BLOCK, listOf("shared:${shape}s"))
        } }
        val wrappers = targets.map { target -> feature("sample:cover").copy(references = mapOf("content" to target.id)) }
        val tree = FoldClassifier.build(targets + wrappers)
        val visible = IntArray(wrappers.size) { targets.size + it }
        val group = tree.project(visible, FoldKind.ITEM).entries.single() as FoldTree.Entry.Group
        assertEquals(72, group.members.size)
        val children = tree.project(visible, FoldKind.ITEM, group.key).entries
        assertEquals(2, children.size)
        assertTrue(children.all { it is FoldTree.Entry.Group && it.members.size == 36 })
        val narrow = tree.project(visible.take(3).toIntArray(), FoldKind.ITEM, group.key).entries
        assertEquals(listOf(72, 73, 74), narrow.map { (it as FoldTree.Entry.Stack).index })
    }
}

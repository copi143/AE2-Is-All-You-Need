package allyouneed.client.integration.emi.fold.model

import java.util.BitSet

/** 不可变全索引分类树。检索结果只决定成员可见性，不参与重新聚类。 */
class FoldTree private constructor(val features: List<FoldFeature>, val nodes: Map<String, Node>, private val leafKeys: List<String>) {
    enum class Stage { STRUCTURAL, FAMILY, FACET, IDENTITY, REFERENCE }
    data class Node(val key: String, val label: String, val stage: Stage, val parent: String?,
                    val children: List<String>, val members: IntArray, val direct: IntArray)
    sealed interface Entry {
        data class Group(val key: String, val members: IntArray) : Entry {
            /** 小组在显示层压平后成为叶组，不需要再钻入子分类。 */
            val canInline: Boolean get() = members.size in FoldClassifier.MIN_GROUP until SMALL_GROUP_LIMIT
        }
        data class Stack(val index: Int, val inlineGroup: String? = null) : Entry
    }
    data class Projection(val entries: List<Entry>, val matched: Int, val kindCounts: Map<FoldKind, Int>)

    fun project(visible: IntArray, kind: FoldKind? = null, nodeKey: String? = null): Projection {
        val bits = BitSet(features.size)
        val rank = IntArray(features.size) { Int.MAX_VALUE }
        val counts = FoldKind.entries.associateWith { 0 }.toMutableMap()
        visible.forEachIndexed { order, i ->
            if (i !in features.indices || rank[i] != Int.MAX_VALUE) return@forEachIndexed
            counts[features[i].kind] = counts.getValue(features[i].kind) + 1
            rank[i] = order
            if (kind == null || features[i].kind == kind) bits.set(i)
        }
        val result = ArrayList<Entry>()
        fun members(node: Node): IntArray = node.members.filter { bits[it] }.sortedBy { rank[it] }.toIntArray()
        fun visit(key: String, opening: Boolean = false) {
            val node = nodes.getValue(key)
            val visibleMembers = members(node)
            if (visibleMembers.isEmpty()) return
            // 保留完整树，但小分类的当前视图直接列出全部成员（如 4+1+1+4+1 → 11）。
            // 全部/资源种类/实现类等结构节点不能把不相关的小分类混成一组。
            if (opening && node.stage != Stage.STRUCTURAL && visibleMembers.size < SMALL_GROUP_LIMIT) {
                visibleMembers.forEach { result.add(Entry.Stack(it)) }
                return
            }
            if (opening || node.stage == Stage.STRUCTURAL) {
                node.direct.filter { bits[it] }.forEach { result.add(Entry.Stack(it)) }
                node.children.forEach { visit(it) }
                return
            }
            if (visibleMembers.size < FoldClassifier.MIN_GROUP) {
                visibleMembers.forEach { result.add(Entry.Stack(it)) }
                return
            }
            // 只压缩家族内的单子链；注册物品/NBT 引用仍有明确的身份边界。
            if (node.stage in setOf(Stage.FAMILY, Stage.FACET) && node.direct.none { bits[it] }) {
                val children = node.children.filter { child -> nodes.getValue(child).members.any { bits[it] } }
                if (children.size == 1 && nodes.getValue(children[0]).stage in setOf(Stage.FAMILY, Stage.FACET)) {
                    visit(children[0]); return
                }
            }
            result.add(Entry.Group(key, visibleMembers))
        }
        val root = nodes[nodeKey] ?: nodes.getValue(ROOT)
        visit(root.key, true)
        result.sortBy { when (it) { is Entry.Stack -> rank[it.index]; is Entry.Group -> rank[it.members[0]] } }
        return Projection(result, root.members.count { bits[it] }, counts)
    }

    fun ancestors(key: String?): List<Node> {
        val path = ArrayList<Node>()
        var node = nodes[key]
        while (node != null && node.key != ROOT) { path.add(node); node = nodes[node.parent] }
        return path.asReversed().filter { it.stage != Stage.STRUCTURAL }
    }
    fun pathFor(index: Int): List<Node> = ancestors(leafKeys[index])

    companion object {
        const val ROOT = "v2:root"
        const val SMALL_GROUP_LIMIT = 20
        private data class Part(val token: String, val label: String, val stage: Stage)
        private data class MutableNode(val key: String, val part: Part, val parent: String?,
                                       val children: MutableSet<String> = linkedSetOf(),
                                       val members: MutableList<Int> = arrayListOf(), val direct: MutableList<Int> = arrayListOf())
        private fun key(path: List<Part>): String = if (path.isEmpty()) ROOT else "v2:" + path.joinToString("") { "${it.token.length}:${it.token}" }

        internal fun build(features: List<FoldFeature>, decisions: List<FoldDecision>): FoldTree {
            val builders = linkedMapOf(ROOT to MutableNode(ROOT, Part("", "", Stage.STRUCTURAL), null))
            val paths = ArrayList<List<Part>>()
            fun append(path: List<Part>, i: Int) {
                val nodeKey = key(path)
                val parent = key(path.dropLast(1))
                val node = builders.getOrPut(nodeKey) {
                    builders.getValue(parent).children.add(nodeKey)
                    MutableNode(nodeKey, path.last(), parent)
                }
                node.members.add(i)
            }
            features.forEachIndexed { i, feature ->
                builders.getValue(ROOT).members.add(i)
                val decision = decisions[i]
                val parts = ArrayList<Part>()
                parts.add(Part("kind:${feature.kind.id}", feature.kind.id, Stage.STRUCTURAL))
                parts.add(Part("domain:${feature.domain}", feature.domain, Stage.STRUCTURAL))
                if (decision.tagScope != null) parts.add(Part("tag:${decision.tagScope}", decision.tagScope, Stage.STRUCTURAL))
                else if (decision.family.isNotEmpty()) parts.add(Part("guard:${feature.guard}", feature.guard, Stage.STRUCTURAL))
                decision.family.forEachIndexed { n, word -> parts.add(Part("family:$word", decision.family.take(n + 1).reversed().joinToString(" "), Stage.FAMILY)) }
                decision.facets.forEach { parts.add(Part("facet:$it", it.replace('_', ' '), Stage.FACET)) }
                parts.add(Part("id:${feature.id}", feature.id.substringAfter(':').replace('_', ' '), Stage.IDENTITY))
                for (depth in 1..parts.size) append(parts.take(depth), i)
                paths.add(parts)
            }
            // 引用路径只读取基本树，避免递归引用/循环。字段名与物品 ID 没有特判。
            val referenceParts = HashMap<Int, List<Part>>()
            val byId = features.indices.groupBy { features[it].id }
            for (variants in features.indices.groupBy { features[it].domain to features[it].id }.values) {
                if (variants.size <= 64) continue
                val slots = HashMap<String, MutableList<Int>>()
                for (i in variants) for ((slot, target) in features[i].references) {
                    if (target != features[i].id && target in byId) slots.getOrPut(slot) { arrayListOf() }.add(i)
                }
                val slot = slots.keys.minWithOrNull(compareBy<String> { -slots.getValue(it).size }.thenBy { it }) ?: continue
                if (slots.getValue(slot).size < variants.size * .9) continue
                for (i in variants) {
                    val candidates = byId[features[i].references[slot]].orEmpty()
                    val parts = arrayListOf(Part("reference-slot:$slot", "reference", Stage.STRUCTURAL))
                    if (candidates.isNotEmpty() && candidates.map { features[it].domain }.distinct().size == 1 && features[candidates[0]].id != features[i].id) {
                        val target = candidates[0]
                        for (part in paths[target].takeWhile { it.stage != Stage.IDENTITY }) {
                            parts.add(Part("reference:${part.token}", part.label,
                                if (part.stage == Stage.STRUCTURAL) Stage.STRUCTURAL else Stage.REFERENCE))
                        }
                        parts.add(Part("reference-id:${features[target].id}", features[target].id, Stage.REFERENCE))
                    } else parts.add(Part("reference-unresolved", "other variants", Stage.REFERENCE))
                    referenceParts[i] = parts
                }
            }
            for ((i, parts) in referenceParts) {
                val path = paths[i].toMutableList()
                for (part in parts) { path.add(part); append(path, i) }
                paths[i] = path
            }
            paths.forEachIndexed { i, path -> builders.getValue(key(path)).direct.add(i) }
            return FoldTree(features.toList(), builders.mapValues { (_, n) ->
                Node(n.key, n.part.label, n.part.stage, n.parent, n.children.toList(), n.members.toIntArray(), n.direct.toIntArray())
            }, paths.map(::key))
        }
    }
}

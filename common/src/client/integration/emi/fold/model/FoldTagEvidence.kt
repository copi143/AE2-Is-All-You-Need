package allyouneed.client.integration.emi.fold.model

import kotlin.math.round

/** Python 候选策略的标签证据层。命名空间保留标签身份，没有模组白名单。 */
internal class FoldTagEvidence(private val rows: List<FoldRow>, private val words: FoldWords) {
    data class Root(val domain: String, val words: List<String>)
    data class Stats(val coverage: Double, val members: Set<Int>)
    private data class Component(val root: Root, val sources: Set<String>, val members: Set<Int>)
    private data class Phrase(val scope: Pair<String, String>, val words: List<String>)
    val roots: Map<Root, Stats>
    private val paths = HashMap<Pair<Int, Root>, MutableSet<List<String>>>()
    private val components = ArrayList<Component>()
    private val explicit = HashMap<Int, MutableSet<Int>>()
    private val scopes = HashMap<Pair<String, String>, MutableSet<Int>>()
    private val aliases = HashMap<Int, MutableSet<Phrase>>()
    private val cohorts = HashSet<Pair<Int, String>>()
    private val familyScopes = HashMap<Int, String>()

    init {
        val members = LinkedHashMap<Root, MutableSet<Int>>()
        val origins = LinkedHashMap<Pair<Root, String>, MutableSet<Int>>()
        val scopeMembers = HashMap<Pair<String, String>, MutableSet<Int>>()
        val phraseMembers = HashMap<Phrase, MutableSet<Int>>()
        rows.forEachIndexed { i, row ->
            scopeMembers.getOrPut(row.scope) { linkedSetOf() }.add(i)
            for (n in 1..minOf(2, row.words.size)) {
                for (phrase in setOf(row.words.take(n), row.words.takeLast(n))) {
                    phraseMembers.getOrPut(Phrase(row.scope, phrase)) { linkedSetOf() }.add(i)
                }
            }
            for (tag in row.feature.tags) {
                val parts = tag.substringAfter(':').split('/')
                val root = Root(row.feature.domain, words.of(parts[0]))
                if (root.words.isEmpty()) continue
                members.getOrPut(root) { linkedSetOf() }.add(i)
                origins.getOrPut(root to tag.substringBefore('/')) { linkedSetOf() }.add(i)
                paths.getOrPut(i to root) { linkedSetOf() }.add(parts.drop(1).map { words.of(it).joinToString("_") })
            }
        }
        roots = members.mapValues { (root, indices) ->
            val coverage = indices.sumOf { i -> root.words.toSet().count { it in rows[i].words }.toDouble() / root.words.toSet().size } / indices.size
            // 与原型一样固定精度；避免等价分数因求和舍入而改变词数/字典序消歧。
            Stats(round(coverage * 1e12) / 1e12, indices)
        }
        origins.keys.map { it.first }.distinct().filter(::accepted).forEach { root ->
            val names = origins.keys.filter { it.first == root }.map { it.second }.sorted()
            val parent = names.associateWith { it }.toMutableMap()
            fun find(value: String): String {
                var current = value
                while (parent.getValue(current) != current) current = parent.getValue(current)
                return current
            }
            val owners = HashMap<Int, String>()
            for (name in names) for (i in origins.getValue(root to name)) {
                owners.put(i, name)?.let { previous ->
                    val a = find(name); val b = find(previous)
                    parent[maxOf(a, b)] = minOf(a, b)
                }
            }
            for (sourceNames in names.groupBy(::find).values) {
                val component = Component(root, sourceNames.toSet(), sourceNames.flatMap { origins.getValue(root to it) }.toSet())
                val c = components.size
                components.add(component)
                for (i in component.members) {
                    explicit.getOrPut(i) { linkedSetOf() }.add(c)
                    scopes.getOrPut(rows[i].scope) { linkedSetOf() }.add(c)
                }
                val votes = HashMap<Phrase, MutableSet<Int>>()
                for (i in component.members) {
                    val row = rows[i]
                    if (root.words.any { it in row.words }) continue
                    for (n in 1..minOf(2, row.words.size)) for (phrase in setOf(row.words.take(n), row.words.takeLast(n))) {
                        votes.getOrPut(Phrase(row.scope, phrase)) { linkedSetOf() }.add(i)
                    }
                }
                for ((phrase, indices) in votes) {
                    val population = phraseMembers.getValue(phrase).count { i ->
                        rows[i].feature.tags.any { tag -> accepted(Root(root.domain, words.of(tag.substringAfter(':').substringBefore('/')))) }
                    }
                    if (indices.size >= 4 && population > 0 && indices.size.toDouble() / population >= .85) {
                        aliases.getOrPut(c) { linkedSetOf() }.add(phrase)
                    }
                }
                for (guard in component.members.map { rows[it].feature.guard }.toSet()) {
                    val population = scopeMembers.getValue(root.domain to guard)
                    val covered = population.count { it in component.members }
                    if (guard !in setOf("net.minecraft.world.item.Item", "net.minecraft.world.level.block.Block", "Fluid") &&
                        covered >= 4 && covered.toDouble() / population.size >= .85) cohorts.add(c to guard)
                }
            }
        }
        // 同一来源中的 dust/small_dust 共用父家族；不同来源需有成员关系才连接。
        for (indices in components.indices.groupBy { components[it].root.let { r -> r.domain to r.words.last() } }.values) {
            val namespaces = indices.associateWith { c -> components[c].sources.map { it.substringBefore(':') }.toMutableSet() }
            var changed: Boolean
            do {
                changed = false
                for (a in indices) for (b in indices) {
                    val x = namespaces.getValue(a); val y = namespaces.getValue(b)
                    if (x != y && x.any { it in y }) {
                        val union = x + y
                        x.addAll(union); y.addAll(union); changed = true
                    }
                }
            } while (changed)
            for (c in indices) familyScopes[c] = components[c].root.words.last() + ":" + namespaces.getValue(c).sorted().joinToString("|")
        }
    }

    private fun accepted(root: Root): Boolean = roots[root]?.let { it.members.size >= 4 && it.coverage >= .5 - 1e-12 } == true

    fun materialPhrases(): Set<List<String>> {
        val heads = roots.keys.filter(::accepted).map { it.words.last() }.toSet()
        return buildSet {
            for ((key, alternatives) in paths) {
                val (i, root) = key
                if (!accepted(root)) continue
                for (path in alternatives) for (part in path) {
                    val phrase = part.split('_')
                    if (!(phrase.size == 1 && phrase[0] in heads) && rows[i].words.containsPhrase(phrase)) add(phrase)
                }
            }
        }
    }

    fun heads(): Set<String> = roots.keys.filter(::accepted).map { it.words.last() }.toSet()

    fun choose(i: Int): FoldDecision? {
        val row = rows[i]
        val own = explicit[i].orEmpty()
        val candidates = (own + scopes[row.scope].orEmpty()).filter { c ->
            val component = components[c]
            val name = component.root.words.any { it in row.words }
            val cohort = (c to row.feature.guard) in cohorts
            val alias = aliases[c].orEmpty().any { p -> p.scope == row.scope &&
                (row.words.take(p.words.size) == p.words || row.words.takeLast(p.words.size) == p.words) }
            if (c in own) name || alias || cohort else cohort && (name || alias)
        }
        val c = candidates.minWithOrNull(compareBy<Int> { -roots.getValue(components[it].root).coverage }
            .thenBy { -components[it].root.words.size }.thenBy { components[it].root.words.joinToString("\u0000") }
            .thenBy { components[it].sources.sorted().joinToString("|") }) ?: return null
        val root = components[c].root
        val facets = paths[i to root].orEmpty().minWithOrNull(compareBy<List<String>> { p ->
            -p.sumOf { part -> words.of(part).toSet().count { it in row.words } }
        }.thenBy { -it.size }.thenBy { it.joinToString("\u0000") }).orEmpty()
        return FoldDecision(root.words.reversed(), facets, familyScopes.getValue(c), "tag")
    }
}

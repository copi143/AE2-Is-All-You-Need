package allyouneed.client.integration.emi.fold.model

/** 只使用快照。词法、类、标签与重复模板共同决定家族；没有模组/档位/颜色表。 */
object FoldClassifier {
    const val MIN_GROUP = 4
    private val quantity = Regex("^\\d+[a-z]*$")
    private data class Phrase(val scope: Pair<String, String>, val words: List<String>)
    private data class Parts(val words: List<String>, val qualifiers: List<String>)

    fun build(features: List<FoldFeature>): FoldTree {
        val words = FoldWords(features)
        val all = features.map { FoldRow(it, words.of(it.id.substringAfter(':'))) }
        val unique = all.distinctBy { it.identity }.sortedWith(compareBy<FoldRow> { it.feature.domain }.thenBy { it.feature.id })
        val rowIds = unique.withIndex().associate { it.value.identity to it.index }
        val tags = FoldTagEvidence(unique, words)
        val materials = tags.materialPhrases()
        val contexts = HashMap<Phrase, MutableSet<List<String>>>()
        for (row in unique) {
            val terms = row.words.filterNot { quantity.matches(it) }
            for (i in terms.indices) for (n in 1..terms.size - i) {
                val phrase = terms.subList(i, i + n)
                if (phrase in materials) contexts.getOrPut(Phrase(row.scope, phrase)) { hashSetOf() }
                    .add(terms.take(i) + "*" + terms.drop(i + n))
            }
        }
        val scopedMaterials = contexts.filterValues { it.size >= 3 }.keys.groupBy { it.scope }
            .mapValues { (_, phrases) -> phrases.map { it.words }.toSet() }
        fun baseParts(row: FoldRow): Parts {
            val result = ArrayList<String>(); val qualifiers = ArrayList<String>(); val numbers = ArrayList<String>()
            var i = 0
            while (i < row.words.size) {
                if (quantity.matches(row.words[i])) { numbers.add(row.words[i++]); continue }
                val n = (row.words.size - i downTo 1).firstOrNull {
                    row.words.subList(i, i + it) in scopedMaterials[row.scope].orEmpty()
                }
                if (n != null) { qualifiers.add(row.words.subList(i, i + n).joinToString("_")); i += n }
                else result.add(row.words[i++])
            }
            return Parts(result, qualifiers + numbers)
        }
        val base = unique.map(::baseParts)
        val coreSupport = HashMap<Phrase, MutableSet<Int>>()
        unique.forEachIndexed { i, row ->
            if (base[i].words.isNotEmpty() && base[i].qualifiers.isNotEmpty()) {
                coreSupport.getOrPut(Phrase(row.scope, base[i].words)) { hashSetOf() }.add(i)
            }
        }
        val protected = coreSupport.filterValues { it.size >= MIN_GROUP }.keys.groupBy { it.scope }
            .mapValues { (_, phrases) -> phrases.map { it.words }.filter { p ->
                phrases.none { it.words.size < p.size && p.containsPhrase(it.words) }
            } }
        val templates = HashMap<Phrase, MutableSet<String>>()
        unique.forEachIndexed { i, row ->
            val terms = base[i].words
            if (terms.size > 1) for (p in terms.indices) {
                templates.getOrPut(Phrase(row.scope, terms.take(p) + "*" + terms.drop(p + 1))) { hashSetOf() }.add(terms[p])
            }
        }
        val heads = tags.heads()
        val repeated = templates.filterValues { it.size in 4..24 && it.none { w -> w in heads } }
            .entries.groupBy { it.value.toSet() }.filterValues { it.size >= 3 }
        val templateScores = repeated.flatMap { (values, templates) -> templates.map { it.key to (templates.size to values.size) } }.toMap()
        val parts = unique.mapIndexed { i, row ->
            val terms = base[i].words
            val chosen = terms.indices.filter { p ->
                terms[p] !in row.feature.classWords && protected[row.scope].orEmpty().none { core ->
                    terms.containsPhrase(core) && !(terms.take(p) + terms.drop(p + 1)).containsPhrase(core)
                } && Phrase(row.scope, terms.take(p) + "*" + terms.drop(p + 1)) in templateScores
            }.minWithOrNull(compareBy<Int> { p -> -templateScores.getValue(Phrase(row.scope, terms.take(p) + "*" + terms.drop(p + 1))).first }
                .thenBy { p -> templateScores.getValue(Phrase(row.scope, terms.take(p) + "*" + terms.drop(p + 1))).second }
                .thenBy { p -> (terms.take(p) + "*" + terms.drop(p + 1)).joinToString("\u0000") })
            if (chosen == null) base[i] else Parts(terms.take(chosen) + terms.drop(chosen + 1), base[i].qualifiers + terms[chosen])
        }
        val tagged = unique.indices.map { tags.choose(it) }
        val prefixes = HashMap<Phrase, MutableSet<Int>>(); val suffixes = HashMap<Phrase, MutableSet<Int>>()
        unique.forEachIndexed { i, row ->
            if (tagged[i] == null) for (n in 1..parts[i].words.size) {
                prefixes.getOrPut(Phrase(row.scope, parts[i].words.take(n))) { hashSetOf() }.add(i)
                suffixes.getOrPut(Phrase(row.scope, parts[i].words.takeLast(n))) { hashSetOf() }.add(i)
            }
        }
        data class Candidate(val phrase: List<String>, val suffix: Boolean, val support: Int)
        val decisions = unique.mapIndexed { i, row ->
            tagged[i] ?: run {
                val candidates = ArrayList<Candidate>()
                val terms = parts[i].words
                for (n in 1..terms.size) {
                    val tail = terms.takeLast(n)
                    val suffixCount = suffixes[Phrase(row.scope, tail)].orEmpty().size
                    if (suffixCount >= MIN_GROUP && tail !in setOf(listOf("item"), listOf("block"))) candidates.add(Candidate(tail, true, suffixCount))
                    val head = terms.take(n)
                    val members = prefixes[Phrase(row.scope, head)].orEmpty()
                    val purity = if (members.isEmpty()) 0.0 else members.count { unique[it].feature.className == row.feature.className }.toDouble() / members.size
                    if (members.size >= MIN_GROUP && (n >= 2 || head[0] == row.feature.classHead || purity >= .9)) candidates.add(Candidate(head, false, members.size))
                }
                val chosen = candidates.minWithOrNull(compareBy<Candidate> { row.feature.classHead !in it.phrase }
                    .thenBy { -it.phrase.size }.thenBy { !it.suffix }.thenBy { -it.support }.thenBy { it.phrase.joinToString("\u0000") })
                if (chosen == null) FoldDecision() else FoldDecision(chosen.phrase.reversed(), parts[i].qualifiers, reason = if (chosen.suffix) "suffix" else "prefix")
            }
        }
        return FoldTree.build(features, all.map { decisions[rowIds.getValue(it.identity)] })
    }
}

package allyouneed.client.integration.emi.fold.model

import java.util.Locale

enum class FoldKind(val id: String) { BLOCK("block"), ITEM("item"), FLUID("fluid"), CUSTOM("custom") }

/** 脱离注册表/EMI 的只读快照，可以交给后台分类器。序号对应原始索引，NBT 变体不去重。 */
data class FoldFeature(
    val id: String,
    val kind: FoldKind,
    val className: String,
    val guard: String,
    val classWords: Set<String>,
    val classHead: String,
    val tags: List<String> = emptyList(),
    val armorSlot: String? = null,
    val references: Map<String, String> = emptyMap(),
) {
    val domain: String get() = kind.id + "/" + when (kind) {
        FoldKind.ITEM -> armorSlot ?: "resource"
        FoldKind.CUSTOM -> className
        else -> "resource"
    }

    companion object {
        private val camel = Regex("(?<=[a-z0-9])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])")
        private val generic = setOf("item", "block", "base", "abstract", "impl", "tiered", "digger", "object", "behaviour")
        fun classWords(hierarchy: List<String>): Set<String> = hierarchy.flatMap {
            camel.split(it.substringAfterLast('.').substringAfterLast('$')).map { w -> w.lowercase(Locale.ROOT) }
        }.toSet() - generic

        fun classHead(name: String): String = camel.split(name.substringAfterLast('.').substringAfterLast('$'))
            .map { it.lowercase(Locale.ROOT) }.filterNot { it in setOf("item", "block", "base", "abstract", "impl") }
            .lastOrNull().orEmpty()

        fun guard(hierarchy: List<String>, base: String): String {
            val position = hierarchy.indexOf(base)
            return if (position > 0) hierarchy[position - 1] else hierarchy.firstOrNull() ?: base
        }
    }
}

internal class FoldWords(features: List<FoldFeature>) {
    private val vocabulary = features.flatMap { raw(it.id.substringAfter(':')) }.toSet()
    private fun raw(text: String) = text.lowercase(Locale.ROOT).split('_', '/', '-').filter { it.isNotEmpty() }
    fun of(text: String): List<String> = raw(text).map { word ->
        val candidates = buildList {
            if (word.endsWith("ies")) add(word.dropLast(3) + "y")
            if (word.endsWith("ves")) { add(word.dropLast(3) + "f"); add(word.dropLast(3) + "fe") }
            if (word.endsWith('s') && listOf("ss", "us", "is").none(word::endsWith)) add(word.dropLast(1))
        }
        candidates.firstOrNull { it in vocabulary } ?: word
    }
}

internal data class FoldRow(val feature: FoldFeature, val words: List<String>) {
    val identity get() = feature.domain to feature.id
    val scope get() = feature.domain to feature.guard
}

internal data class FoldDecision(
    val family: List<String> = emptyList(),
    val facets: List<String> = emptyList(),
    val tagScope: String? = null,
    val reason: String = "identity",
)

internal fun List<String>.containsPhrase(phrase: List<String>): Boolean =
    phrase.isNotEmpty() && size >= phrase.size && (0..size - phrase.size).any { subList(it, it + phrase.size) == phrase }

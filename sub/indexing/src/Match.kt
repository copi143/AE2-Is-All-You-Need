package allyouneed.indexing

/** 一次匹配：第 [document] 个文本，位于该文本内 `[start, end)`（以 codec 单元计）。 */
@JvmRecord
@ConsistentCopyVisibility
data class Match internal constructor(
    val fmIndex: FMIndex,
    val document: Int,
    val start: Int,
    val end: Int,
) {
    fun toBaseMatch(): BaseMatch = BaseMatch(document, start, end)
}

@JvmRecord
data class BaseMatch(
    val document: Int,
    val start: Int,
    val end: Int,
)

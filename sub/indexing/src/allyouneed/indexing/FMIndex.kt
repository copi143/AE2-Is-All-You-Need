package allyouneed.indexing

/** 一次匹配：第 [document] 个文本，位于该文本内 `[start, end)`（以 codec 单元计）。 */
data class Match(val document: Int, val start: Int, val end: Int)

/**
 * FM-index 全文搜索库。
 *
 * 将大量小文本一次性拼接索引；每个文本前后各加一个分隔符，因此除了普通子串搜索，
 * 还能以分隔符为锚点做前缀/后缀匹配（[searchPrefix] / [searchSuffix]）。
 *
 * 内部符号重映射：真实符号 `s -> s + 2`，文档分隔符 `-> 1`，唯一终结符 `-> 0`。
 * 查询模式只含真实符号（`>= 2`），绝不会命中分隔符/终结符，故不会跨文档匹配。
 *
 * 匹配结果中的偏移量以所选 [TextCodec] 的单元计（utf8 为字节、utf16 为 char、utf32 为码点）。
 */
class FMIndex private constructor(
    private val core: FmCore,
    private val codec: TextCodec,
    private val docContentStart: IntArray,
    private val docLengths: IntArray,
) {
    val codecName: String get() = codec.name

    val documentCount: Int get() = docLengths.size

    /** 普通子串搜索，返回所有出现位置。 */
    fun search(pattern: String): List<Match> {
        val raw = codec.encode(pattern)
        if (raw.isEmpty()) return emptyList()
        val pat = IntArray(raw.size) { raw[it] + 2 }
        return core.locate(pat).map { mapContent(it, raw.size) }
    }

    /** 前缀匹配：返回所有以 [prefix] 开头的文本（offset 恒为 0）。 */
    fun searchPrefix(prefix: String): List<Match> {
        val raw = codec.encode(prefix)
        if (raw.isEmpty()) return emptyList()
        val pat = IntArray(raw.size + 1) { i -> if (i == 0) 1 else raw[i - 1] + 2 }
        return core.locate(pat).map { p ->
            val doc = docContentStart.binarySearch(p + 1)
            check(doc >= 0) { "前缀定位失败" }
            Match(doc, 0, raw.size)
        }
    }

    /** 后缀匹配：返回所有以 [suffix] 结尾的文本。 */
    fun searchSuffix(suffix: String): List<Match> {
        val raw = codec.encode(suffix)
        if (raw.isEmpty()) return emptyList()
        val pat = IntArray(raw.size + 1) { i -> if (i == raw.size) 1 else raw[i] + 2 }
        return core.locate(pat).map { mapContent(it, raw.size) }
    }

    /** 出现次数。 */
    fun count(pattern: String): Int {
        val raw = codec.encode(pattern)
        if (raw.isEmpty()) return 0
        val pat = IntArray(raw.size) { raw[it] + 2 }
        return core.count(pat)
    }

    fun contains(pattern: String): Boolean = count(pattern) > 0

    private fun mapContent(p: Int, len: Int): Match {
        var lo = 0
        var hi = docContentStart.size
        while (lo + 1 < hi) {
            val mid = (lo + hi) ushr 1
            if (docContentStart[mid] <= p) lo = mid else hi = mid
        }
        val doc = lo
        val start = p - docContentStart[doc]
        return Match(doc, start, start + len)
    }

    companion object {
        fun build(texts: List<String>, codec: TextCodec = Utf8Codec): FMIndex {
            val alphabetSize = codec.separator + 2
            val text = ArrayList<Int>()
            val docContentStart = IntArray(texts.size)
            val docLengths = IntArray(texts.size)
            for (i in texts.indices) {
                val raw = codec.encode(texts[i])
                text.add(1)
                docContentStart[i] = text.size
                for (s in raw) text.add(s + 2)
                docLengths[i] = raw.size
                text.add(1)
            }
            text.add(0)
            val core = FmCore.build(text.toIntArray(), alphabetSize)
            return FMIndex(core, codec, docContentStart, docLengths)
        }
    }
}

package allyouneed.indexing

/**
 * FM-index 全文搜索库。
 *
 * 将大量小文本一次性拼接索引；每个文本前后各加一个分隔符，因此除了普通子串搜索，
 * 还能以分隔符为锚点做前缀/后缀匹配（[searchPrefix] / [searchSuffix]）。
 *
 * 符号约定（所有路径统一，载荷均为原始值存储）：
 * - `0` 为唯一最小终结符（最末位），编码器保证输出不含 `0`；
 * - 分隔符为二进制全 `1`（utf8 `0xFF` / utf16 `0xFFFF` / utf32 `-1`）；
 * - 查询模式只含载荷原始值，绝不会命中分隔符/终结符，故不会跨文档匹配。
 *
 * 实现细节：utf32 的外部份隔符 `-1` 为负数，无法直接作为 `SuffixArray`/`WaveletMatrix`
 * 的非负下标，内部将其映射为 `0x110000`（最大码点 `+1`，仍为最大符号），载荷与终结符
 * 保持原始值不变；查询载荷无需任何映射，前缀/后缀模式中的分隔符使用该内部值。
 *
 * 匹配结果中的偏移量以所选 codec 的单元计（utf8 为字节、utf16 为 char、utf32 为码点）。
 */
class FMIndex private constructor(
    private val core: FmCore,
    private val codec: Codec<*, *>,
    private val docContentStart: IntArray,
    private val docLengths: IntArray,
) {
    val codecName: String get() = codec.name

    val documentCount: Int get() = docLengths.size

    /** 普通子串搜索，返回所有出现位置。 */
    fun search(pattern: String): List<Match> {
        return when (codec) {
            is Codec.Byte -> {
                val pat = codec.encode(pattern)
                if (pat.isEmpty()) return emptyList()
                core.locate(pat).map { mapContent(it, pat.size) }
            }

            is Codec.Short -> {
                val pat = codec.encode(pattern)
                if (pat.isEmpty()) return emptyList()
                core.locate(pat).map { mapContent(it, pat.size) }
            }

            is Codec.Int -> {
                val pat = codec.encode(pattern)
                if (pat.isEmpty()) return emptyList()
                core.locate(pat).map { mapContent(it, pat.size) }
            }
        }
    }

    /** 前缀匹配：返回所有以 [prefix] 开头的文本（offset 恒为 0）。 */
    fun searchPrefix(prefix: String): List<Match> {
        return when (codec) {
            is Codec.Byte -> {
                val raw = codec.encode(prefix)
                if (raw.isEmpty()) return emptyList()
                val pat = ByteArray(raw.size + 1) { i ->
                    if (i == 0) codec.separator else raw[i - 1]
                }
                core.locate(pat).map { p ->
                    val doc = docContentStart.binarySearch(p + 1)
                    check(doc >= 0) { "前缀定位失败" }
                    Match(this, doc, 0, raw.size)
                }
            }

            is Codec.Short -> {
                val raw = codec.encode(prefix)
                if (raw.isEmpty()) return emptyList()
                val pat = ShortArray(raw.size + 1) { i ->
                    if (i == 0) codec.separator else raw[i - 1]
                }
                core.locate(pat).map { p ->
                    val doc = docContentStart.binarySearch(p + 1)
                    check(doc >= 0) { "前缀定位失败" }
                    Match(this, doc, 0, raw.size)
                }
            }

            is Codec.Int -> {
                val raw = codec.encode(prefix)
                if (raw.isEmpty()) return emptyList()
                val pat = IntArray(raw.size + 1) { i -> if (i == 0) codec.separator else raw[i - 1] }
                core.locate(pat).map { p ->
                    val doc = docContentStart.binarySearch(p + 1)
                    check(doc >= 0) { "前缀定位失败" }
                    Match(this, doc, 0, raw.size)
                }
            }
        }
    }

    /** 后缀匹配：返回所有以 [suffix] 结尾的文本。 */
    fun searchSuffix(suffix: String): List<Match> {
        return when (codec) {
            is Codec.Byte -> {
                val raw = codec.encode(suffix)
                if (raw.isEmpty()) return emptyList()
                val pat = ByteArray(raw.size + 1) { i ->
                    if (i == raw.size) codec.separator else raw[i]
                }
                core.locate(pat).map { mapContent(it, raw.size) }
            }

            is Codec.Short -> {
                val raw = codec.encode(suffix)
                if (raw.isEmpty()) return emptyList()
                val pat = ShortArray(raw.size + 1) { i ->
                    if (i == raw.size) codec.separator else raw[i]
                }
                core.locate(pat).map { mapContent(it, raw.size) }
            }

            is Codec.Int -> {
                val raw = codec.encode(suffix)
                if (raw.isEmpty()) return emptyList()
                val pat = IntArray(raw.size + 1) { i -> if (i == raw.size) codec.separator else raw[i] }
                core.locate(pat).map { mapContent(it, raw.size) }
            }
        }
    }

    /** 出现次数。 */
    fun count(pattern: String): Int {
        return when (codec) {
            is Codec.Byte -> {
                val pat = codec.encode(pattern)
                if (pat.isEmpty()) return 0
                core.count(pat)
            }

            is Codec.Short -> {
                val pat = codec.encode(pattern)
                if (pat.isEmpty()) return 0
                core.count(pat)
            }

            is Codec.Int -> {
                val pat = codec.encode(pattern)
                if (pat.isEmpty()) return 0
                core.count(pat)
            }
        }
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
        return Match(this, doc, start, start + len)
    }

    @Suppress("ArrayInDataClass", "DuplicatedCode")
    companion object {
        fun build(texts: List<String>, codec: Codec<*, *> = Codec.UTF8): FMIndex {
            return when (codec) {
                is Codec.Byte -> buildBytes(texts, codec)
                is Codec.Short -> buildShorts(texts, codec)
                is Codec.Int -> buildInts(texts, codec)
            }
        }

        @JvmRecord
        private data class Builder<T, U>(
            val texts: List<String>,
            val codec: Codec<T, U>,
            val sizeof: (data: U) -> Int,
            val constructor: (size: Int) -> U,
            val encoded: List<U> = texts.map { codec.encode(it) },
            val fullText: U = constructor(encoded.fold(2) { acc, data -> acc + sizeof(data) + 1 }),
            val docContentStart: IntArray = IntArray(encoded.size),
            val docLengths: IntArray = IntArray(encoded.size),
        ) {
            inline operator fun invoke(crossinline builder: Builder<T, U>.() -> FmCore): FMIndex {
                return FMIndex(builder(), codec, docContentStart, docLengths)
            }
        }

        private fun buildBytes(texts: List<String>, codec: Codec.Byte): FMIndex {
            return (Builder(texts, codec, { it.size }, ::ByteArray)) {
                fullText[0] = codec.separator
                encoded.foldIndexed(1) { index, acc, data ->
                    docContentStart[index] = acc
                    docLengths[index] = data.size
                    data.copyInto(fullText, acc)
                    fullText[acc + data.size] = codec.separator
                    acc + data.size + 1
                }.let { require(it == fullText.size - 1) }
                fullText[fullText.size - 1] = 0
                FmCore.build(fullText, codec.alphabetSize)
            }
        }

        private fun buildShorts(texts: List<String>, codec: Codec.Short): FMIndex {
            return (Builder(texts, codec, { it.size }, ::ShortArray)) {
                fullText[0] = codec.separator
                encoded.foldIndexed(1) { index, acc, data ->
                    docContentStart[index] = acc
                    docLengths[index] = data.size
                    data.copyInto(fullText, acc)
                    fullText[acc + data.size] = codec.separator
                    acc + data.size + 1
                }.let { require(it == fullText.size - 1) }
                fullText[fullText.size - 1] = 0
                FmCore.build(fullText, codec.alphabetSize)
            }
        }

        private fun buildInts(texts: List<String>, codec: Codec.Int): FMIndex {
            return (Builder(texts, codec, { it.size }, ::IntArray)) {
                fullText[0] = codec.separator
                encoded.foldIndexed(1) { index, acc, data ->
                    docContentStart[index] = acc
                    docLengths[index] = data.size
                    data.copyInto(fullText, acc)
                    fullText[acc + data.size] = codec.separator
                    acc + data.size + 1
                }.let { require(it == fullText.size - 1) }
                fullText[fullText.size - 1] = 0
                FmCore.build(fullText, codec.alphabetSize)
            }
        }
    }
}

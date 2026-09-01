package allyouneed.indexing

/**
 * 位向量，提供常数时间的 `rank1` / `rank0` 查询。
 *
 * `rank1(i)` 返回 `[0, i)` 区间内 1 的个数；`rank0(i) = i - rank1(i)`。
 */
class BitVector internal constructor(
    private val words: LongArray,
    val length: Int,
    private val prefix: IntArray,
) {
    fun get(i: Int): Boolean {
        require(i in 0 until length)
        return (words[i ushr 6] and (1L shl (i and 63))) != 0L
    }

    fun rank1(i: Int): Int {
        if (i <= 0) return 0
        if (i >= length) return prefix[prefix.size - 1]
        val w = i ushr 6
        val off = i and 63
        return prefix[w] + java.lang.Long.bitCount(words[w] and ((1L shl off) - 1L))
    }

    fun rank0(i: Int): Int = i - rank1(i)

    class Builder(private val length: Int) {
        private val words = LongArray((length + 63) ushr 6)

        fun set(i: Int) {
            words[i ushr 6] = words[i ushr 6] or (1L shl (i and 63))
        }

        fun build(): BitVector {
            val prefix = IntArray(words.size + 1)
            for (k in words.indices) prefix[k + 1] = prefix[k] + java.lang.Long.bitCount(words[k])
            return BitVector(words, length, prefix)
        }
    }
}

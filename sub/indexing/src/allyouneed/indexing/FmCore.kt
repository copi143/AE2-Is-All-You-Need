package allyouneed.indexing

/**
 * FM-index 通用核心：在已拼接、已重映射的整数文本上完成 BWT、`C[]`、rank 结构与定位。
 *
 * 不保存显式的 BWT 数组——WaveletMatrix 已完整编码 BWT，定位时用 [WaveletMatrix.access]
 * 按需取符号，省掉每符号 4 字节的内存。
 *
 * 符号约定：文本中 `0` 为唯一最小终结符（最末位），正数为普通符号。
 * 模式必须使用与构建时一致的（重映射后）符号。
 */
class FmCore private constructor(
    private val c: IntArray,
    private val wm: WaveletMatrix,
    private val sampledSa: IntArray,
    private val sampled: BitVector,
    private val n: Int,
) {
    fun count(pattern: IntArray): Int {
        if (pattern.isEmpty()) return 0
        var l = 0
        var r = n
        for (i in pattern.size - 1 downTo 0) {
            val sym = pattern[i]
            l = c[sym] + wm.rank(sym, l)
            r = c[sym] + wm.rank(sym, r)
            if (l >= r) return 0
        }
        return r - l
    }

    fun locate(pattern: IntArray): IntArray {
        if (pattern.isEmpty()) return IntArray(0)
        var l = 0
        var r = n
        for (i in pattern.size - 1 downTo 0) {
            val sym = pattern[i]
            l = c[sym] + wm.rank(sym, l)
            r = c[sym] + wm.rank(sym, r)
            if (l >= r) return IntArray(0)
        }
        val result = IntArray(r - l)
        for (row in l until r) result[row - l] = locateRow(row)
        return result
    }

    private fun locateRow(start: Int): Int {
        var row = start
        var steps = 0
        while (!sampled.get(row)) {
            val sym = wm.access(row)
            row = c[sym] + wm.rank(sym, row)
            steps++
        }
        return sampledSa[sampled.rank1(row)] + steps
    }

    companion object {
        fun build(text: IntArray, alphabetSize: Int, sampleInterval: Int = 64): FmCore {
            require(text.isNotEmpty())
            val n = text.size
            val sa = SuffixArray.build(text)

            // BWT 是 text 的排列，符号计数可直接由 text 得到，无需先构造 BWT
            val cnt = IntArray(alphabetSize)
            for (v in text) cnt[v]++
            val c = IntArray(alphabetSize)
            var sum = 0
            for (i in 0 until alphabetSize) {
                c[i] = sum
                sum += cnt[i]
            }

            val bits = 32 - Integer.numberOfLeadingZeros(maxOf(alphabetSize - 1, 1))
            val bwt = IntArray(n)
            for (i in 0 until n) {
                val p = sa[i]
                bwt[i] = if (p == 0) text[n - 1] else text[p - 1]
            }
            val wm = WaveletMatrix.build(bwt, bits)

            val builder = BitVector.Builder(n)
            var sampledCount = 0
            for (i in 0 until n) if (sa[i] % sampleInterval == 0) sampledCount++
            val sampledSa = IntArray(sampledCount)
            var k = 0
            for (i in 0 until n) {
                if (sa[i] % sampleInterval == 0) {
                    builder.set(i)
                    sampledSa[k++] = sa[i]
                }
            }

            return FmCore(c, wm, sampledSa, builder.build(), n)
        }
    }
}

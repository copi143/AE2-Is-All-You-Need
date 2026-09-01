package allyouneed.indexing

/**
 * FM-index 通用核心：在已拼接、已重映射的整数文本上完成 BWT、`C[]`、rank 结构与定位。
 *
 * 符号约定：文本中 `0` 为唯一最小终结符（最末位），正数为普通符号。
 * 模式必须使用与构建时一致的（重映射后）符号。
 */
class FmCore private constructor(
    private val bwt: IntArray,
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
            val sym = bwt[row]
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

            val bwt = IntArray(n)
            for (i in 0 until n) {
                val p = sa[i]
                bwt[i] = if (p == 0) text[n - 1] else text[p - 1]
            }

            val cnt = IntArray(alphabetSize)
            for (v in bwt) cnt[v]++
            val c = IntArray(alphabetSize)
            var sum = 0
            for (i in 0 until alphabetSize) {
                c[i] = sum
                sum += cnt[i]
            }

            val bits = 32 - Integer.numberOfLeadingZeros(maxOf(alphabetSize - 1, 1))
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

            return FmCore(bwt, c, wm, sampledSa, builder.build(), n)
        }
    }
}

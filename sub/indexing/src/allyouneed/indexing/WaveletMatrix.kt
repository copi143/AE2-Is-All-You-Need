package allyouneed.indexing

/**
 * Wavelet Matrix：在整数序列上支持 `rank(value, end)`（`[0, end)` 区间内 `value` 的出现次数）。
 *
 * 以每一 bit 为一层做稳定划分，零分支在前、一分支在后；每层用 [BitVector] 记录 bit 与 rank。
 */
class WaveletMatrix internal constructor(
    private val levels: Array<BitVector>,
    private val zeros: IntArray,
) {
    fun rank(value: Int, end: Int): Int {
        var l = 0
        var r = end
        for (level in levels.indices.reversed()) {
            val bv = levels[level]
            if (((value ushr level) and 1) == 0) {
                l = bv.rank0(l)
                r = bv.rank0(r)
            } else {
                l = zeros[level] + bv.rank1(l)
                r = zeros[level] + bv.rank1(r)
            }
        }
        return r - l
    }

    /** 返回序列第 [i] 个元素的值。 */
    fun access(i: Int): Int {
        var pos = i
        var value = 0
        for (level in levels.indices.reversed()) {
            val bv = levels[level]
            val bit = if (bv.get(pos)) 1 else 0
            value = (value shl 1) or bit
            pos = if (bit == 0) bv.rank0(pos) else zeros[level] + bv.rank1(pos)
        }
        return value
    }

    companion object {
        fun build(values: IntArray, bits: Int): WaveletMatrix {
            require(bits >= 1) { "bits 必须 >= 1" }
            val n = values.size
            val levels = arrayOfNulls<BitVector>(bits)
            val zeros = IntArray(bits)
            var cur = values
            for (level in bits - 1 downTo 0) {
                val builder = BitVector.Builder(n)
                var zc = 0
                for (idx in 0 until n) {
                    if (((cur[idx] ushr level) and 1) != 0) builder.set(idx) else zc++
                }
                zeros[level] = zc
                val next = IntArray(n)
                var zi = 0
                var oi = zc
                for (idx in 0 until n) {
                    val v = cur[idx]
                    if (((v ushr level) and 1) == 0) next[zi++] = v else next[oi++] = v
                }
                levels[level] = builder.build()
                cur = next
            }
            @Suppress("UNCHECKED_CAST")
            return WaveletMatrix(levels as Array<BitVector>, zeros)
        }
    }
}

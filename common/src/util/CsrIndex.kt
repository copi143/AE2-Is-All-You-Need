package allyouneed.util

/**
 * CSR (compressed sparse row) adjacency: per-key contiguous id ranges in a flat [ids] array,
 * delimited by [offsets] (size keyCount + 1). Replaces per-key List wrappers with two int arrays.
 */
class CsrIndex private constructor(
    private val offsets: IntArray,
    private val ids: IntArray,
) {
    val keyCount: Int
        get() = offsets.size - 1

    fun size(key: Int): Int {
        require(key in 0 until keyCount) { "key out of range: $key" }
        return offsets[key + 1] - offsets[key]
    }

    fun idAt(key: Int, index: Int): Int {
        val size = size(key)
        if (index !in 0 until size) {
            throw IndexOutOfBoundsException("index $index, size $size")
        }
        return ids[offsets[key] + index]
    }

    /** Immutable random-access view of one key's values; [resolve] maps stored ids back to values. */
    fun <T> slice(key: Int, resolve: (Int) -> T): List<T> {
        val from = offsets[key]
        val size = offsets[key + 1] - from
        return object : AbstractList<T>(), RandomAccess {
            override val size: Int = size

            override fun get(index: Int): T {
                if (index !in 0 until size) {
                    throw IndexOutOfBoundsException("index $index, size $size")
                }
                return resolve(ids[from + index])
            }
        }
    }

    companion object {
        /** Builds CSR from per-key id lists; value order within each key is preserved. */
        fun build(perKeyIds: List<IntArray>): CsrIndex {
            val offsets = IntArray(perKeyIds.size + 1)
            var total = 0
            perKeyIds.forEachIndexed { i, arr ->
                total += arr.size
                offsets[i + 1] = total
            }
            val ids = IntArray(total)
            var pos = 0
            for (arr in perKeyIds) {
                arr.copyInto(ids, pos)
                pos += arr.size
            }
            return CsrIndex(offsets, ids)
        }
    }
}

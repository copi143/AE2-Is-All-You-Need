package allyouneed.logic.tick

/**
 * Binary min-heap whose elements know their own slot (via [indexGetter]/[indexSetter]), making
 * [remove] O(log n) instead of PriorityQueue's O(n) scan. An element belongs to at most one heap
 * at a time; its index is -1 while outside any heap.
 */
class IndexedHeap<T>(
    private val comparator: Comparator<in T>,
    private val indexGetter: (T) -> Int,
    private val indexSetter: (T, Int) -> Unit,
) {

    fun interface IndexSetter<T> {
        fun set(t: T, index: Int)
    }

    constructor(
        comparator: Comparator<in T>,
        indexGetter: java.util.function.ToIntFunction<T>,
        indexSetter: IndexSetter<T>,
    ) : this(comparator, { indexGetter.applyAsInt(it) }, { t, i -> indexSetter.set(t, i) })

    val size: Int
        get() = count

    private var count = 0
    private var heap: Array<Any?> = arrayOfNulls(16)

    fun isEmpty(): Boolean = count == 0

    @Suppress("UNCHECKED_CAST")
    fun peek(): T? = if (count == 0) null else heap[0] as T

    fun contains(t: T): Boolean {
        val i = indexGetter(t)
        return i in 0 until count && heap[i] === t
    }

    fun add(t: T) {
        require(indexGetter(t) == -1) { "element is already in a heap" }
        if (count == heap.size) {
            heap = heap.copyOf(heap.size * 2)
        }
        heap[count] = t
        indexSetter(t, count)
        count++
        siftUp(count - 1)
    }

    fun poll(): T? = if (count == 0) null else removeAt(0)

    fun remove(t: T): Boolean {
        if (!contains(t)) {
            return false
        }
        removeAt(indexGetter(t))
        return true
    }

    @Suppress("UNCHECKED_CAST")
    private fun removeAt(index: Int): T {
        val removed = heap[index] as T
        count--
        val moved = heap[count]
        heap[count] = null
        indexSetter(removed, -1)
        if (index < count) {
            moved as T
            heap[index] = moved
            indexSetter(moved, index)
            val parent = (index - 1) ushr 1
            if (index > 0 && comparator.compare(heap[parent] as T, moved) > 0) {
                siftUp(index)
            } else {
                siftDown(index)
            }
        }
        return removed
    }

    @Suppress("UNCHECKED_CAST")
    private fun siftUp(i0: Int) {
        var i = i0
        val x = heap[i] as T
        while (i > 0) {
            val parent = (i - 1) ushr 1
            val p = heap[parent] as T
            if (comparator.compare(x, p) >= 0) {
                break
            }
            heap[i] = p
            indexSetter(p, i)
            i = parent
        }
        heap[i] = x
        indexSetter(x, i)
    }

    @Suppress("UNCHECKED_CAST")
    private fun siftDown(i0: Int) {
        var i = i0
        val x = heap[i] as T
        val half = count ushr 1
        while (i < half) {
            var child = (i shl 1) + 1
            var c = heap[child] as T
            val right = child + 1
            if (right < count && comparator.compare(c, heap[right] as T) > 0) {
                child = right
                c = heap[child] as T
            }
            if (comparator.compare(x, c) <= 0) {
                break
            }
            heap[i] = c
            indexSetter(c, i)
            i = child
        }
        heap[i] = x
        indexSetter(x, i)
    }
}

package allyouneed.logic.tick

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.test.fail

class IndexedHeapTest {

    private class Elem(val key: Int) {
        var idx = -1
    }

    private fun newHeap() = IndexedHeap(
        comparator = compareBy<Elem> { it.key },
        indexGetter = { it.idx },
        indexSetter = { e, i -> e.idx = i },
    )

    @Test
    fun `poll returns elements in order`() {
        val heap = newHeap()
        val elems = (0 until 100).map { Elem(it) }.shuffled(Random(42))
        elems.forEach(heap::add)
        var prev = -1
        while (!heap.isEmpty()) {
            val e = heap.poll()!!
            assertEquals(-1, e.idx)
            assertTrue(e.key >= prev)
            prev = e.key
        }
        assertNull(heap.poll())
    }

    @Test
    fun `remove arbitrary element keeps heap property`() {
        val heap = newHeap()
        val elems = (0 until 50).map { Elem(it * 2) }
        elems.shuffled(Random(1)).forEach(heap::add)
        for (e in elems.filterIndexed { i, _ -> i % 3 == 0 }) {
            assertTrue(heap.remove(e))
            assertEquals(-1, e.idx)
            assertFalse(heap.contains(e))
        }
        val rest = elems.filterIndexed { i, _ -> i % 3 != 0 }
        assertEquals(rest.size, heap.size)
        rest.sortedBy { it.key }.forEach { assertSame(it, heap.poll()) }
        assertTrue(heap.isEmpty())
    }

    @Test
    fun `remove non-member is a no-op`() {
        val heap = newHeap()
        heap.add(Elem(1))
        assertFalse(heap.remove(Elem(1)))
        assertEquals(1, heap.size)
    }

    @Test
    fun `double add is rejected`() {
        val heap = newHeap()
        val e = Elem(1)
        heap.add(e)
        try {
            heap.add(e)
            fail("expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
        }
    }

    @Test
    fun `randomized against PriorityQueue oracle`() {
        val heap = newHeap()
        val oracle = java.util.PriorityQueue<Elem>(compareBy { it.key })
        val rng = Random(7)
        val live = ArrayDeque<Elem>()
        repeat(5000) {
            when (rng.nextInt(4)) {
                0, 1 -> {
                    val e = Elem(rng.nextInt(100))
                    heap.add(e)
                    oracle.add(e)
                    live.addLast(e)
                }

                2 -> {
                    val expected = oracle.peek()?.key
                    assertEquals(expected, heap.peek()?.key)
                    val o = oracle.poll()
                    assertEquals(o?.key, heap.poll()?.key)
                    if (o != null) live.remove(o)
                }

                else -> {
                    if (live.isNotEmpty()) {
                        val e = live.random(rng)
                        assertEquals(oracle.remove(e), heap.remove(e))
                        live.remove(e)
                    }
                }
            }
            assertEquals(oracle.size, heap.size)
        }
    }

    @Test
    fun `element index distinguishes two heaps`() {
        val a = newHeap()
        val b = newHeap()
        val e1 = Elem(1)
        val e2 = Elem(2)
        a.add(e1)
        b.add(e2)
        assertTrue(a.contains(e1))
        assertFalse(a.contains(e2))
        assertFalse(b.remove(e1))
        assertEquals(1, a.size)
    }
}

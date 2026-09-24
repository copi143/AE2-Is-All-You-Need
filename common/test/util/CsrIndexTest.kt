package allyouneed.util

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CsrIndexTest {

    private val names = arrayOf("a", "b", "c", "d", "e")

    @Test
    fun `slice returns values in insertion order`() {
        val csr = CsrIndex.build(listOf(intArrayOf(0, 3, 1), intArrayOf(), intArrayOf(4, 2)))
        assertEquals(listOf("a", "d", "b"), csr.slice(0) { names[it] })
        assertEquals(emptyList(), csr.slice(1) { names[it] })
        assertEquals(listOf("e", "c"), csr.slice(2) { names[it] })
    }

    @Test
    fun `size and idAt bounds`() {
        val csr = CsrIndex.build(listOf(intArrayOf(1, 2), intArrayOf(3)))
        assertEquals(2, csr.keyCount)
        assertEquals(2, csr.size(0))
        assertEquals(1, csr.size(1))
        assertEquals(2, csr.idAt(0, 1))
        assertEquals(3, csr.idAt(1, 0))
        assertFailsWith<IndexOutOfBoundsException> { csr.idAt(0, 2) }
        assertFailsWith<IllegalArgumentException> { csr.size(2) }
    }

    @Test
    fun `slice is immutable and bounds-checked`() {
        val csr = CsrIndex.build(listOf(intArrayOf(0)))
        val slice = csr.slice(0) { names[it] }
        assertFailsWith<IndexOutOfBoundsException> { slice[1] }
        assertFailsWith<ClassCastException> { slice as MutableList<String> }
    }

    @Test
    fun `randomized against list-of-lists oracle`() {
        val rng = Random(3)
        val oracle = (0 until 200).map { (0 until rng.nextInt(8)).map { rng.nextInt(1000) } }
        val csr = CsrIndex.build(oracle.map { it.toIntArray() })
        assertEquals(oracle.size, csr.keyCount)
        for (key in oracle.indices) {
            assertEquals(oracle[key].size, csr.size(key))
            assertEquals(oracle[key], csr.slice(key) { it })
        }
    }

    @Test
    fun `empty index`() {
        val csr = CsrIndex.build(emptyList())
        assertEquals(0, csr.keyCount)
        assertTrue(CsrIndex.build(listOf(intArrayOf(), intArrayOf())).slice(1) { it }.isEmpty())
    }
}

package allyouneed.util.bigint

import org.junit.jupiter.api.Test
import java.math.BigInteger
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IncrementalCellIndexTest {

    @Test
    fun `simulate and zero change do not recount`() {
        val index = IncrementalCellIndex<String>()
        index.markValid()
        var queried = 0
        assertTrue(index.onChange(false, 8L, "iron") { queried++; BigInteger.TEN })
        assertTrue(index.onChange(true, 0L, "iron") { queried++; BigInteger.TEN })
        assertEquals(0, queried)
        assertEquals(0, index.cells.size)
        assertTrue(index.valid)
    }

    @Test
    fun `invalid index skips query`() {
        val index = IncrementalCellIndex<String>()
        var queried = 0
        assertFalse(index.onChange(true, 4L, "iron") { queried++; BigInteger.TEN })
        assertEquals(0, queried)
        assertFalse(index.valid)
    }

    @Test
    fun `null query invalidates without writing`() {
        val index = IncrementalCellIndex<String>()
        index.markValid()
        index.cells.set("iron", 3L)
        assertFalse(index.onChange(true, 1L, "iron") { null })
        assertFalse(index.valid)
        assertEquals(3L, index.cells.getSaturatedLong("iron"))
    }

    @Test
    fun `zero amount removes the key`() {
        val index = IncrementalCellIndex<String>()
        index.markValid()
        index.cells.set("iron", 8L)
        index.last.set("iron", 8L)
        assertTrue(index.onChange(true, 8L, "iron") { BigInteger.ZERO })
        assertTrue(index.valid)
        assertFalse(index.cells.containsKey("iron"))
        assertEquals(8L, index.last.getSaturatedLong("iron"))
    }

    @Test
    fun `modulate recounts cells without rewriting last`() {
        val index = IncrementalCellIndex<String>()
        index.markValid()
        index.cells.set("iron", 1L)
        index.cells.set("gold", 4L)
        index.last.set("iron", 6L)
        index.last.set("gold", 4L)
        assertTrue(index.onChange(true, 2L, "iron") { BigInteger.valueOf(7L) })
        assertEquals(7L, index.cells.getSaturatedLong("iron"))
        assertEquals(4L, index.cells.getSaturatedLong("gold"))
        assertEquals(6L, index.last.getSaturatedLong("iron"))
        assertEquals(4L, index.last.getSaturatedLong("gold"))
        assertTrue(index.valid)
    }

    @Test
    fun `copyLast is independent of later last mutation`() {
        val index = IncrementalCellIndex<String>()
        index.last.set("iron", 5L)
        val snap = index.copyLast()
        index.last.clear()
        index.last.set("gold", 9L)
        assertEquals(5L, snap.getSaturatedLong("iron"))
        assertFalse(snap.containsKey("gold"))
        assertEquals(9L, index.last.getSaturatedLong("gold"))
    }

    @Test
    fun `invalidate then markValid restores incremental path`() {
        val index = IncrementalCellIndex<String>()
        index.markValid()
        index.invalidate()
        assertFalse(index.valid)
        index.markValid()
        assertTrue(index.onChange(true, 1L, "iron") { BigInteger.ONE })
        assertEquals(1L, index.cells.getSaturatedLong("iron"))
    }
}

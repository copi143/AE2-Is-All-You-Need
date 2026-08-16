package allyouneed.util.id

import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.lang.ref.WeakReference
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class WeakInternedIdsTest {
    private data class Probe(val primary: String, val extra: String = "")

    private fun contentTable() = WeakInternedIds(Probe::hashCode) { a, b -> a == b }

    private fun primaryTable() = WeakInternedIds<Probe>(
        hashOf = { it.primary.hashCode() },
        eq = { a, b -> a.primary == b.primary },
    )

    @Test
    fun `equal content shares secondary id`() {
        val table = contentTable()
        val a = Probe("diamond", "a")
        val b = Probe("diamond", "a")
        assertEquals(table.assign(a), table.assign(b))
        assertEquals(1, table.size())
    }

    @Test
    fun `different content gets different secondary id`() {
        val table = contentTable()
        val a = Probe("diamond", "a")
        val b = Probe("diamond", "b")
        assertNotEquals(table.assign(a), table.assign(b))
        assertEquals(2, table.size())
    }

    @Test
    fun `same primary shares id ignoring extra`() {
        val table = primaryTable()
        val a = Probe("diamond", "nbt1")
        val b = Probe("diamond", "nbt2")
        assertEquals(table.assign(a), table.assign(b))
        assertEquals(1, table.size())
    }

    @Test
    fun `same instance assign is stable`() {
        val table = contentTable()
        val a = Probe("iron")
        assertEquals(table.assign(a), table.assign(a))
        assertEquals(1, table.size())
    }

    @Test
    fun `peek does not allocate`() {
        val table = contentTable()
        val a = Probe("gold")
        assertEquals(-1, table.peek(a))
        val id = table.assign(a)
        assertEquals(id, table.peek(a))
        assertEquals(id, table.peek(Probe("gold")))
    }

    @Test
    fun `find returns a live witness`() {
        val table = contentTable()
        val a = Probe("copper")
        val id = table.assign(a)
        assertSame(a, table.find(id))
    }

    @Test
    fun `clear resets ids`() {
        val table = contentTable()
        val a = Probe("coal")
        val invalidated = mutableListOf<Probe>()
        table.assign(a)
        table.clear { invalidated.add(it) }
        assertEquals(listOf(a), invalidated)
        assertEquals(0, table.size())
        assertEquals(-1, table.peek(a))
        assertEquals(0, table.assign(Probe("stone")))
    }

    @Test
    fun `grows past initial capacity`() {
        val table = contentTable()
        val keys = (0 until 80).map { Probe("k$it") }
        val ids = keys.map { table.assign(it) }
        assertEquals(80, ids.toSet().size)
        assertEquals(80, table.size())
        keys.forEachIndexed { i, key -> assertEquals(ids[i], table.peek(key)) }
    }

    @Test
    fun `recycles id after referent is collected`() {
        val table = WeakInternedIds<Any>(
            hashOf = { System.identityHashCode(it) },
            eq = { a, b -> a === b },
        )
        var key: Any? = Any()
        val id = table.assign(key!!)
        val wr = WeakReference(key)
        key = null
        repeat(20) {
            System.gc()
            Thread.sleep(10)
            if (wr.get() == null) return@repeat
        }
        assumeTrue(wr.get() == null, "GC did not collect referent")
        assertEquals(0, table.size())
        assertNull(table.find(id))
        assertEquals(id, table.assign(Any()))
    }
}

package allyouneed.util.inventory

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InventoryWatchersTest {

    @Test
    fun `handler mutation wakes the watcher`() {
        var hits = 0
        val handler = Any()
        val handle = InventoryWatchers.watchAt(1, 10L, listOf(handler)) { hits++ }
        InventoryWatchers.notifyHandler(handler)
        assertEquals(1, hits)
        InventoryWatchers.unwatch(handle)
    }

    @Test
    fun `unknown handler is ignored`() {
        var hits = 0
        val handle = InventoryWatchers.watchAt(1, 11L, listOf(Any())) { hits++ }
        InventoryWatchers.notifyHandler(Any())
        assertEquals(0, hits)
        InventoryWatchers.unwatch(handle)
    }

    @Test
    fun `unwatch stops notifications`() {
        var hits = 0
        val handler = Any()
        val handle = InventoryWatchers.watchAt(2, 12L, listOf(handler)) { hits++ }
        InventoryWatchers.unwatch(handle)
        InventoryWatchers.notifyHandler(handler)
        assertEquals(0, hits)
        assertTrue(handle.closed)
    }

    @Test
    fun `reentrant notify does not recurse`() {
        var hits = 0
        val handler = Any()
        val handle = InventoryWatchers.watchAt(3, 13L, listOf(handler)) {
            hits++
            InventoryWatchers.notifyHandler(handler)
        }
        InventoryWatchers.notifyHandler(handler)
        assertEquals(1, hits)
        InventoryWatchers.unwatch(handle)
    }

    @Test
    fun `linked child handler wakes the parent watch`() {
        var hits = 0
        val facade = Any()
        val inner = Any()
        InventoryWatchers.linkChild(facade, inner)
        val handle = InventoryWatchers.watchAt(6, 16L, listOf(facade)) { hits++ }
        InventoryWatchers.notifyHandler(inner)
        assertEquals(1, hits)
        InventoryWatchers.unwatch(handle)
    }

    @Test
    fun `pos key isolates different inventories`() {
        var a = 0
        var b = 0
        val ha = InventoryWatchers.watchAt(1, 1L, emptyList()) { a++ }
        val hb = InventoryWatchers.watchAt(1, 2L, emptyList()) { b++ }
        InventoryWatchers.notifyAt(1, 1L)
        InventoryWatchers.notifyAt(1, 2L)
        assertEquals(1, a)
        assertEquals(1, b)
        InventoryWatchers.unwatch(ha)
        InventoryWatchers.unwatch(hb)
    }

    @Test
    fun `unwatch last listener drops handler mapping`() {
        var hits = 0
        val handler = Any()
        val first = InventoryWatchers.watchAt(4, 14L, listOf(handler)) { hits++ }
        InventoryWatchers.unwatch(first)
        val second = InventoryWatchers.watchAt(4, 15L, emptyList()) { hits++ }
        InventoryWatchers.notifyHandler(handler)
        assertEquals(0, hits)
        InventoryWatchers.notifyAt(4, 15L)
        assertEquals(1, hits)
        InventoryWatchers.unwatch(second)
    }
}

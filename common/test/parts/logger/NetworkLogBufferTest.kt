package allyouneed.parts.logger

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NetworkLogBufferTest {

    private fun entry(seq: Int, kind: NetworkLogKind = NetworkLogKind.NodeAdded): NetworkLogEntry =
        NetworkLogEntry(seq * 1000L, kind, listOf("arg$seq"))

    private fun bufferOf(count: Int, start: Int = 0): NetworkLogBuffer {
        val b = NetworkLogBuffer(1)
        for (i in start until start + count) b.append(entry(i))
        return b
    }

    @Test
    fun `append keeps insertion order`() {
        val b = bufferOf(3)
        assertEquals(3, b.size)
        assertEquals(entry(0), b.entryAt(0))
        assertEquals(entry(2), b.entryAt(2))
    }

    @Test
    fun `ring overwrites oldest when full`() {
        val b = bufferOf(LogStore.MAX_ENTRIES + 10)
        assertEquals(LogStore.MAX_ENTRIES, b.size)
        assertEquals(entry(10), b.entryAt(0))
        assertEquals(entry(LogStore.MAX_ENTRIES + 9), b.entryAt(LogStore.MAX_ENTRIES - 1))
    }

    @Test
    fun `ring stays correct across repeated overwrite cycles`() {
        val b = bufferOf(LogStore.MAX_ENTRIES * 2 + 5)
        assertEquals(entry(LogStore.MAX_ENTRIES + 5), b.entryAt(0))
        b.append(entry(-1))
        assertEquals(entry(LogStore.MAX_ENTRIES + 6), b.entryAt(0))
        assertEquals(entry(-1), b.entryAt(LogStore.MAX_ENTRIES - 1))
    }

    @Test
    fun `count with all filter is size`() {
        val b = bufferOf(100)
        assertEquals(100, b.count(NetworkLogCategory.All))
    }

    @Test
    fun `count filters by category mask`() {
        val b = NetworkLogBuffer(1)
        repeat(30) { b.append(entry(it, NetworkLogKind.NodeAdded)) } // Device
        repeat(20) { b.append(entry(100 + it, NetworkLogKind.CraftStart)) } // Crafting
        assertEquals(30, b.count(NetworkLogCategory.Device.mask))
        assertEquals(20, b.count(NetworkLogCategory.Crafting.mask))
        assertEquals(50, b.count(NetworkLogCategory.Device.mask or NetworkLogCategory.Crafting.mask))
        assertEquals(0, b.count(NetworkLogCategory.Energy.mask))
    }

    @Test
    fun `query pages within filtered stream`() {
        val b = NetworkLogBuffer(1)
        for (i in 0 until 200) {
            val kind = if (i % 2 == 0) NetworkLogKind.NodeAdded else NetworkLogKind.CraftStart
            b.append(entry(i, kind))
        }
        val page = b.query(10, NetworkLogCategory.Device.mask, 5)
        assertEquals(100, page.total)
        assertEquals(10, page.offset)
        assertEquals(5, page.entries.size)
        assertEquals(entry(20), page.entries.first())
    }

    @Test
    fun `query clamps offset beyond total`() {
        val b = bufferOf(10)
        val page = b.query(999, NetworkLogCategory.All, 5)
        assertEquals(10, page.total)
        assertEquals(10, page.offset)
        assertTrue(page.entries.isEmpty())
    }

    @Test
    fun `query negative offset starts at zero`() {
        val b = bufferOf(10)
        val page = b.query(-5, NetworkLogCategory.All, 3)
        assertEquals(0, page.offset)
        assertEquals(listOf(entry(0), entry(1), entry(2)), page.entries)
    }

    @Test
    fun `clear resets ring`() {
        val b = bufferOf(50)
        b.clear()
        assertEquals(0, b.size)
        assertTrue(b.dirty)
        b.append(entry(0))
        assertEquals(entry(0), b.entryAt(0))
    }

    @Test
    fun `nbt round trip preserves order and content`() {
        val b = NetworkLogBuffer(7)
        b.append(entry(0, NetworkLogKind.GridBootStart))
        b.append(entry(1, NetworkLogKind.PowerOff))
        val restored = NetworkLogBuffer.fromNbt(7, b.toNbt())
        assertEquals(2, restored.size)
        assertEquals(entry(0, NetworkLogKind.GridBootStart), restored.entryAt(0))
        assertEquals(entry(1, NetworkLogKind.PowerOff), restored.entryAt(1))
        assertFalse(restored.dirty)
    }

    @Test
    fun `nbt load trims to capacity keeping newest`() {
        val b = NetworkLogBuffer(1)
        for (i in 0 until LogStore.MAX_ENTRIES + 3) b.append(entry(i))
        val restored = NetworkLogBuffer.fromNbt(1, b.toNbt())
        assertEquals(LogStore.MAX_ENTRIES, restored.size)
        assertEquals(entry(3), restored.entryAt(0))
    }
}

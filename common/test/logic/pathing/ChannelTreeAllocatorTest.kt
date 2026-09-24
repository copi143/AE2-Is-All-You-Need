package allyouneed.logic.pathing

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChannelTreeAllocatorTest {
    @Test
    fun `tree assigns all and skips phase 2`() {
        val n = 10
        val maxCh = IntArray(n) { 8 }
        maxCh[0] = 0
        maxCh[1] = 32
        val flags = IntArray(n)
        flags[1] = ChannelGraph.DENSE
        flags[2] = ChannelGraph.PREFERRED
        val demand = BooleanArray(n)
        for (i in 3 until n) {
            demand[i] = true
            flags[i] = ChannelGraph.REQUIRE
        }
        val g = graph(
            n,
            controllers = booleanArrayOf(true, false, false, false, false, false, false, false, false, false),
            maxCh, flags, demand,
            edges = listOf(0 to 1, 1 to 2, 2 to 3, 2 to 4, 2 to 5, 2 to 6, 2 to 7, 2 to 8, 2 to 9),
        )
        val r = ChannelTreeAllocator.allocate(g)
        assertEquals(7, r.channelsInUse)
        assertFalse(r.usedPhase2)
        for (i in 3 until n) assertTrue(r.assigned[i])
    }

    @Test
    fun `parallel smart cables augment to 10`() {
        val n = 14
        val maxCh = IntArray(n) { 8 }
        maxCh[0] = 0
        maxCh[1] = 32
        val flags = IntArray(n)
        flags[1] = ChannelGraph.DENSE
        flags[2] = ChannelGraph.PREFERRED
        flags[3] = ChannelGraph.PREFERRED
        val demand = BooleanArray(n)
        for (i in 4 until n) {
            demand[i] = true
            flags[i] = ChannelGraph.REQUIRE
        }
        val edges = ArrayList<Pair<Int, Int>>()
        edges += 0 to 1
        edges += 1 to 2
        edges += 1 to 3
        for (i in 4 until n) {
            edges += 2 to i
            edges += 3 to i
        }
        val g = graph(
            n,
            controllers = BooleanArray(n) { it == 0 },
            maxCh, flags, demand, edges,
        )
        val r = ChannelTreeAllocator.allocate(g)
        assertEquals(10, r.channelsInUse)
        assertTrue(r.usedPhase2)
        assertEquals(10, r.assigned.count { it })
    }

    @Test
    fun `forest shortage skips phase 2`() {
        val n = 13
        val maxCh = IntArray(n) { 8 }
        maxCh[0] = 0
        maxCh[1] = 32
        val flags = IntArray(n)
        flags[1] = ChannelGraph.DENSE
        flags[2] = ChannelGraph.PREFERRED
        val demand = BooleanArray(n)
        for (i in 3 until n) {
            demand[i] = true
            flags[i] = ChannelGraph.REQUIRE
        }
        val edges = ArrayList<Pair<Int, Int>>()
        edges += 0 to 1
        edges += 1 to 2
        for (i in 3 until n) edges += 2 to i
        val g = graph(
            n,
            controllers = BooleanArray(n) { it == 0 },
            maxCh, flags, demand, edges,
        )
        val r = ChannelTreeAllocator.allocate(g)
        assertEquals(8, r.channelsInUse)
        assertFalse(r.usedPhase2)
    }

    @Test
    fun `multiblock costs one channel`() {
        val n = 5
        val maxCh = intArrayOf(0, 32, 8, 8, 8)
        val flags = intArrayOf(
            0,
            ChannelGraph.DENSE,
            ChannelGraph.REQUIRE,
            ChannelGraph.REQUIRE,
            ChannelGraph.REQUIRE,
        )
        val demand = booleanArrayOf(false, false, true, true, true)
        val group = intArrayOf(-1, -1, 0, 0, 0)
        val g = graph(
            n,
            controllers = booleanArrayOf(true, false, false, false, false),
            maxCh, flags, demand,
            edges = listOf(0 to 1, 1 to 2, 1 to 3, 1 to 4),
            group = group,
        )
        val r = ChannelTreeAllocator.allocate(g)
        assertEquals(1, r.channelsInUse)
        assertEquals(1, r.assigned.count { it })
        assertEquals(2, r.ride.count { it })
        assertFalse(r.usedPhase2)
    }

    @Test
    fun `compressed blocked by cannot-carry-compressed`() {
        val n = 4
        val maxCh = intArrayOf(0, 32, 32, 8)
        val flags = intArrayOf(
            0,
            ChannelGraph.DENSE,
            ChannelGraph.DENSE or ChannelGraph.NO_COMPRESSED,
            ChannelGraph.REQUIRE or ChannelGraph.COMPRESSED,
        )
        val demand = booleanArrayOf(false, false, false, true)
        val g = graph(
            n,
            controllers = booleanArrayOf(true, false, false, false),
            maxCh, flags, demand,
            edges = listOf(0 to 1, 1 to 2, 2 to 3),
        )
        val r = ChannelTreeAllocator.allocate(g)
        assertEquals(0, r.channelsInUse)
        assertFalse(r.assigned[3])
        assertFalse(r.usedPhase2)
    }

    @Test
    fun `swallow saturates vertex so nothing routes through`() {
        val n = 4
        val maxCh = intArrayOf(0, 32, 8, 8)
        val flags = intArrayOf(
            0,
            ChannelGraph.DENSE or ChannelGraph.REQUIRE,
            ChannelGraph.REQUIRE,
            ChannelGraph.REQUIRE,
        )
        val demand = booleanArrayOf(false, true, true, true)
        val swallow = booleanArrayOf(false, true, false, false)
        val g = graph(
            n,
            controllers = booleanArrayOf(true, false, false, false),
            maxCh, flags, demand,
            edges = listOf(0 to 1, 1 to 2, 1 to 3),
            swallow = swallow,
        )
        val r = ChannelTreeAllocator.allocate(g)
        assertTrue(r.assigned[1])
        assertFalse(r.assigned[2])
        assertFalse(r.assigned[3])
        assertEquals(1, r.channelsInUse)
    }

    @Test
    fun `dense-to-dense swallow blocks downstream`() {
        val n = 5
        val maxCh = intArrayOf(0, 32, 32, 8, 8)
        val flags = intArrayOf(
            0,
            ChannelGraph.DENSE,
            ChannelGraph.DENSE or ChannelGraph.REQUIRE,
            ChannelGraph.REQUIRE,
            ChannelGraph.REQUIRE,
        )
        val demand = booleanArrayOf(false, false, true, true, true)
        val swallow = booleanArrayOf(false, false, true, false, false)
        val g = graph(
            n,
            controllers = booleanArrayOf(true, false, false, false, false),
            maxCh, flags, demand,
            edges = listOf(0 to 1, 1 to 2, 2 to 3, 2 to 4),
            swallow = swallow,
        )
        val r = ChannelTreeAllocator.allocate(g)
        assertTrue(r.assigned[2])
        assertFalse(r.assigned[3])
        assertFalse(r.assigned[4])
        assertEquals(1, r.channelsInUse)
        assertFalse(r.usedPhase2)
    }

    @Test
    fun `phase 2 keeps tree winners when augmenting`() {
        val n = 14
        val maxCh = IntArray(n) { 8 }
        maxCh[0] = 0
        maxCh[1] = 32
        val flags = IntArray(n)
        flags[1] = ChannelGraph.DENSE
        flags[2] = ChannelGraph.PREFERRED
        flags[3] = ChannelGraph.PREFERRED
        val demand = BooleanArray(n)
        for (i in 4 until n) {
            demand[i] = true
            flags[i] = ChannelGraph.REQUIRE
        }
        val edges = ArrayList<Pair<Int, Int>>()
        edges += 0 to 1
        edges += 1 to 2
        edges += 1 to 3
        for (i in 4 until n) {
            edges += 2 to i
            edges += 3 to i
        }
        val g = graph(
            n,
            controllers = BooleanArray(n) { it == 0 },
            maxCh, flags, demand, edges,
        )
        val r = ChannelTreeAllocator.allocate(g)
        assertEquals(10, r.channelsInUse)
        assertTrue(r.usedPhase2)
        for (i in 4 until 12) assertTrue(r.assigned[i])
        assertEquals(10, r.assigned.count { it })
        assertTrue(r.nodeUsed[2] >= 8)
        assertTrue(r.nodeUsed[3] >= 2)
        for (i in 4 until n) {
            if (r.assigned[i]) assertTrue(r.nodeUsed[i] > 0)
        }
    }

    @Test
    fun `two controllers on same dense do not invent capacity`() {
        val n = 13
        val maxCh = IntArray(n) { 8 }
        maxCh[0] = 0
        maxCh[1] = 0
        maxCh[2] = 32
        val flags = IntArray(n)
        flags[2] = ChannelGraph.DENSE
        flags[3] = ChannelGraph.PREFERRED
        val demand = BooleanArray(n)
        for (i in 4 until n) {
            demand[i] = true
            flags[i] = ChannelGraph.REQUIRE
        }
        val edges = ArrayList<Pair<Int, Int>>()
        edges += 0 to 2
        edges += 1 to 2
        edges += 2 to 3
        for (i in 4 until n) edges += 3 to i
        val g = graph(
            n,
            controllers = BooleanArray(n) { it == 0 || it == 1 },
            maxCh, flags, demand, edges,
        )
        val r = ChannelTreeAllocator.allocate(g)
        assertEquals(8, r.channelsInUse)
        assertFalse(r.usedPhase2)
        assertEquals(8, r.assigned.count { it })
    }

    @Test
    fun `phase 2 does not leak through swallow`() {
        val n = 5
        val maxCh = intArrayOf(0, 32, 8, 8, 8)
        val flags = intArrayOf(
            0,
            ChannelGraph.DENSE,
            ChannelGraph.PREFERRED or ChannelGraph.REQUIRE,
            ChannelGraph.REQUIRE,
            ChannelGraph.REQUIRE,
        )
        val demand = booleanArrayOf(false, false, true, true, true)
        val swallow = booleanArrayOf(false, false, true, false, false)
        val g = graph(
            n,
            controllers = booleanArrayOf(true, false, false, false, false),
            maxCh, flags, demand,
            edges = listOf(0 to 1, 1 to 2, 2 to 3, 2 to 4, 3 to 4),
            swallow = swallow,
        )
        val r = ChannelTreeAllocator.allocate(g)
        assertTrue(r.assigned[2])
        assertFalse(r.assigned[3])
        assertFalse(r.assigned[4])
        assertEquals(1, r.channelsInUse)
    }

    @Test
    fun `compressed uses detour while normal winner stays`() {
        val n = 5
        val maxCh = intArrayOf(0, 32, 32, 8, 8)
        val flags = intArrayOf(
            0,
            ChannelGraph.DENSE,
            ChannelGraph.DENSE or ChannelGraph.NO_COMPRESSED,
            ChannelGraph.REQUIRE or ChannelGraph.COMPRESSED,
            ChannelGraph.REQUIRE,
        )
        val demand = booleanArrayOf(false, false, false, true, true)
        val g = graph(
            n,
            controllers = booleanArrayOf(true, false, false, false, false),
            maxCh, flags, demand,
            edges = listOf(0 to 1, 1 to 2, 2 to 3, 3 to 4, 1 to 4),
        )
        val r = ChannelTreeAllocator.allocate(g)
        assertTrue(r.assigned[4])
        assertTrue(r.assigned[3])
        assertEquals(2, r.channelsInUse)
        assertTrue(r.nodeUsed[4] > 0)
        assertTrue(r.nodeUsed[3] > 0)
    }

    @Test
    fun `channelsByBlocks counts nodes and tree edges`() {
        val n = 4
        val maxCh = intArrayOf(0, 32, 8, 8)
        val flags = intArrayOf(0, ChannelGraph.DENSE, ChannelGraph.PREFERRED, ChannelGraph.REQUIRE)
        val demand = booleanArrayOf(false, false, false, true)
        val g = graph(
            n,
            controllers = booleanArrayOf(true, false, false, false),
            maxCh, flags, demand,
            edges = listOf(0 to 1, 1 to 2, 2 to 3),
        )
        val r = ChannelTreeAllocator.allocate(g)
        assertEquals(1, r.channelsInUse)
        assertEquals(6, r.channelsByBlocks)
        assertEquals(1, r.nodeUsed[1])
        assertEquals(1, r.nodeUsed[2])
        assertEquals(1, r.nodeUsed[3])
        assertEquals(0, r.nodeUsed[0])
    }

    private fun graph(
        n: Int,
        controllers: BooleanArray,
        maxCh: IntArray,
        flags: IntArray,
        demand: BooleanArray,
        edges: List<Pair<Int, Int>>,
        swallow: BooleanArray = BooleanArray(n),
        group: IntArray = IntArray(n) { -1 },
    ): ChannelGraph {
        val nodeCols = ChannelNodeColumns(n)
        for (i in 0 until n) {
            nodeCols.add(
                maxChannel = maxCh[i],
                flag = flags[i],
                controller = controllers[i],
                demand = demand[i],
                swallow = swallow[i],
                group = group[i],
            )
        }
        val edgeCols = ChannelEdgeColumns(edges.size)
        for ((a, b) in edges) {
            edgeCols.add(endA = a, endB = b)
        }
        return ChannelGraph(nodeCols, edgeCols)
    }
}

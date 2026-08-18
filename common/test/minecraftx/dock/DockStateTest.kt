package minecraftx.dock

import minecraftx.compose.dock.DockAxis
import minecraftx.compose.dock.DockDrop
import minecraftx.compose.dock.DockNode
import minecraftx.compose.dock.DockSide
import minecraftx.compose.dock.DockState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DockStateTest {

    private fun sample(): DockState = DockState(
        root = DockNode.Split(
            id = "s0",
            axis = DockAxis.Horizontal,
            ratio = 0.5f,
            first = DockNode.Leaf("l0", listOf("alpha", "beta"), "alpha"),
            second = DockNode.Leaf("l1", listOf("gamma"), "gamma"),
        ),
        nextId = 2,
    )

    @Test
    fun `moves a tab into another leaf`() {
        val next = sample().moveTab("alpha", DockDrop.Center("l1"))
        assertEquals(listOf("beta"), next.findLeaf("l0")!!.tabs)
        assertEquals(listOf("gamma", "alpha"), next.findLeaf("l1")!!.tabs)
        assertEquals("alpha", next.findLeaf("l1")!!.active)
    }

    @Test
    fun `splits a leaf when dropped on an edge`() {
        val next = sample().moveTab("alpha", DockDrop.Edge("l1", DockSide.Right))
        val split = next.root as DockNode.Split
        val right = split.second as DockNode.Split
        assertEquals(DockAxis.Horizontal, right.axis)
        assertEquals(listOf("alpha"), (right.second as DockNode.Leaf).tabs)
        assertEquals(listOf("gamma"), (right.first as DockNode.Leaf).tabs)
        assertEquals(listOf("beta"), next.findLeaf("l0")!!.tabs)
    }

    @Test
    fun `collapses a leaf that lost its last tab`() {
        val next = sample().moveTab("gamma", DockDrop.Center("l0"))
        val leaf = next.root as DockNode.Leaf
        assertEquals("l0", leaf.id)
        assertEquals(listOf("alpha", "beta", "gamma"), leaf.tabs)
    }

    @Test
    fun `does not split a leaf with a single tab onto itself`() {
        val start = sample()
        assertEquals(start, start.moveTab("gamma", DockDrop.Edge("l1", DockSide.Left)))
    }

    @Test
    fun `reorders tabs in the same leaf`() {
        val next = sample().moveTab("beta", DockDrop.TabBar("l0", 0))
        assertEquals(listOf("beta", "alpha"), next.findLeaf("l0")!!.tabs)
    }

    @Test
    fun `close and open round trip`() {
        val closed = sample().closeTab("beta")
        assertTrue("beta" in closed.closed)
        assertEquals(listOf("alpha"), closed.findLeaf("l0")!!.tabs)
        val opened = closed.openTab("beta")
        assertTrue(opened.closed.isEmpty())
        assertEquals(listOf("alpha", "beta"), opened.findLeaf("l0")!!.tabs)
    }

    @Test
    fun `clamps split ratio`() {
        val next = sample().setRatio("s0", 2f)
        assertEquals(DockState.MAX_RATIO, (next.root as DockNode.Split).ratio)
    }

    @Test
    fun `selecting an unknown tab is a no-op`() {
        val start = sample()
        assertEquals(start, start.selectTab("l0", "missing"))
        assertNull(start.findLeafOf("missing"))
    }
}

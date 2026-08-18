package minecraftx.dock

import minecraftx.compose.dock.DockDrop
import minecraftx.compose.dock.DockLeafLayout
import minecraftx.compose.dock.DockSide
import minecraftx.compose.dock.hitDockDrop
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class DockDropTest {

    private val leaf = DockLeafLayout(
        id = "l0",
        x = 10f,
        y = 20f,
        width = 100f,
        height = 80f,
        tabCount = 2,
        tabBarHeight = 16f,
    )

    @Test
    fun `hits tab bar insertion index`() {
        assertEquals(DockDrop.TabBar("l0", 0), hitDockDrop(listOf(leaf), 20f, 25f))
        assertEquals(DockDrop.TabBar("l0", 2), hitDockDrop(listOf(leaf), 100f, 25f))
    }

    @Test
    fun `hits center and edges of the content area`() {
        assertEquals(DockDrop.Center("l0"), hitDockDrop(listOf(leaf), 60f, 60f))
        assertEquals(DockDrop.Edge("l0", DockSide.Left), hitDockDrop(listOf(leaf), 15f, 60f))
        assertEquals(DockDrop.Edge("l0", DockSide.Right), hitDockDrop(listOf(leaf), 105f, 60f))
        assertEquals(DockDrop.Edge("l0", DockSide.Bottom), hitDockDrop(listOf(leaf), 60f, 95f))
    }

    @Test
    fun `misses outside all leaves`() {
        assertNull(hitDockDrop(listOf(leaf), 0f, 0f))
    }

    @Test
    fun `drop hints describe the action`() {
        assertEquals("并入此组", minecraftx.compose.dock.dropHint(DockDrop.Center("l0")))
        assertEquals("拆到左侧", minecraftx.compose.dock.dropHint(DockDrop.Edge("l0", DockSide.Left)))
        assertEquals("插入标签", minecraftx.compose.dock.dropHint(DockDrop.TabBar("l0", 1)))
    }
}

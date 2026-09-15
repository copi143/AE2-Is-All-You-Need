package allyouneed.me.pathing

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChannelMaxFlowTest {
    @Test
    fun `unit path`() {
        val g = ChannelMaxFlow(3)
        g.addEdge(0, 1, 1)
        g.addEdge(1, 2, 1)
        assertEquals(1, g.maxFlow(0, 2))
        assertFalse(g.reachable(0, 2))
    }

    @Test
    fun `parallel edges stack`() {
        val g = ChannelMaxFlow(2)
        g.addEdge(0, 1, 8)
        g.addEdge(0, 1, 8)
        assertEquals(16, g.maxFlow(0, 1))
    }

    @Test
    fun `vertex capacity via split`() {
        val vin = 0
        val vout = 1
        val s = 2
        val t = 3
        val g = ChannelMaxFlow(4)
        g.addEdge(s, vin, ChannelMaxFlow.INF)
        g.addEdge(vin, vout, 8)
        g.addEdge(vout, t, ChannelMaxFlow.INF)
        g.addEdge(vout, t, ChannelMaxFlow.INF)
        assertEquals(8, g.maxFlow(s, t))
    }

    @Test
    fun `no path`() {
        val g = ChannelMaxFlow(2)
        g.addEdge(0, 1, 0)
        assertEquals(0, g.maxFlow(0, 1))
        assertFalse(g.reachable(0, 1))
        assertTrue(g.reachable(0, 0))
    }
}

package allyouneed.client.integration.emi.fold

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [EmiFoldEdges] 合并外轮廓的回归测试（纯逻辑）。
 */
class EmiFoldEdgesTest {
    private typealias E = EmiFoldEdges.Edge

    @Test
    fun `孤立格四边全画`() {
        val out = EmiFoldEdges.edgesFor(mapOf((0 to 0) to "a"))
        assertEquals(setOf(E.TOP, E.BOTTOM, E.LEFT, E.RIGHT), out[0 to 0])
    }

    @Test
    fun `横向相邻共享边省去`() {
        val out = EmiFoldEdges.edgesFor(
            mapOf((0 to 0) to "a", (1 to 0) to "a"),
        )
        assertEquals(setOf(E.TOP, E.BOTTOM, E.LEFT), out[0 to 0])
        assertEquals(setOf(E.TOP, E.BOTTOM, E.RIGHT), out[1 to 0])
    }

    @Test
    fun `纵向相邻共享边省去`() {
        val out = EmiFoldEdges.edgesFor(
            mapOf((0 to 0) to "a", (0 to 1) to "a"),
        )
        assertEquals(setOf(E.TOP, E.LEFT, E.RIGHT), out[0 to 0])
        assertEquals(setOf(E.BOTTOM, E.LEFT, E.RIGHT), out[0 to 1])
    }

    @Test
    fun `2x2同组只剩外圈8条边`() {
        val cells = mapOf(
            (0 to 0) to "a", (1 to 0) to "a",
            (0 to 1) to "a", (1 to 1) to "a",
        )
        val out = EmiFoldEdges.edgesFor(cells)
        assertEquals(8, out.values.sumOf { it.size })
        assertEquals(setOf(E.TOP, E.LEFT), out[0 to 0])
        assertEquals(setOf(E.TOP, E.RIGHT), out[1 to 0])
        assertEquals(setOf(E.BOTTOM, E.LEFT), out[0 to 1])
        assertEquals(setOf(E.BOTTOM, E.RIGHT), out[1 to 1])
    }

    @Test
    fun `不同组相邻各自画边`() {
        val out = EmiFoldEdges.edgesFor(
            mapOf((0 to 0) to "a", (1 to 0) to "b"),
        )
        // 共享边两侧都画（同像素后画者胜，保证两组都有轮廓）
        assertTrue(E.RIGHT in out.getValue(0 to 0))
        assertTrue(E.LEFT in out.getValue(1 to 0))
    }

    @Test
    fun `L形缺口内角正确`() {
        val out = EmiFoldEdges.edgesFor(
            mapOf((0 to 0) to "a", (1 to 0) to "a", (0 to 1) to "a"),
        )
        // (0,0) 右下都是同组，只剩上左
        assertEquals(setOf(E.TOP, E.LEFT), out[0 to 0])
        // (1,0) 左是同组，下方缺口要画底边
        assertEquals(setOf(E.TOP, E.RIGHT, E.BOTTOM), out[1 to 0])
        // (0,1) 上是同组，右方缺口要画右边
        assertEquals(setOf(E.BOTTOM, E.LEFT, E.RIGHT), out[0 to 1])
    }

    @Test
    fun `空输入返回空`() {
        assertTrue(EmiFoldEdges.edgesFor(emptyMap()).isEmpty())
    }
}

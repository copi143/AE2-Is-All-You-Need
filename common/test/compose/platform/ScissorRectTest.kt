package allyouneed.compose.platform

import allyouneed.client.compose.platform.ScissorRect
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ScissorRectTest {

    @Test
    fun `intersect keeps the overlapping region`() {
        val a = ScissorRect(0, 0, 100, 80)
        val b = ScissorRect(20, 10, 60, 90)
        assertEquals(ScissorRect(20, 10, 60, 80), a.intersect(b))
    }

    @Test
    fun `disjoint rects become empty`() {
        val a = ScissorRect(0, 0, 10, 10)
        val b = ScissorRect(20, 20, 30, 30)
        assertTrue(a.intersect(b).isEmpty)
    }

    @Test
    fun `zero-area rect is empty`() {
        assertTrue(ScissorRect(4, 4, 4, 10).isEmpty)
        assertTrue(ScissorRect(4, 4, 10, 4).isEmpty)
        assertFalse(ScissorRect(4, 4, 5, 5).isEmpty)
    }
}

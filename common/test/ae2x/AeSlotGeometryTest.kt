package allyouneed.ae2x

import ae2x.compose.AeSlotGeometry
import ae2x.compose.ExclusionAccumulator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AeSlotGeometryTest {

    @Test
    fun `maps compose window position to AE2 slot coordinates with item inset`() {
        val mapped = AeSlotGeometry.toSlotPos(
            windowX = 40f,
            windowY = 20f,
            uiScale = 1f,
            guiLeft = 10,
            guiTop = 5,
        )
        assertEquals(31, mapped.x)
        assertEquals(16, mapped.y)
        assertEquals(41, AeSlotGeometry.ghostX(10, mapped.x))
        assertEquals(21, AeSlotGeometry.ghostY(5, mapped.y))
    }

    @Test
    fun `scales window coordinates before subtracting gui origin`() {
        val mapped = AeSlotGeometry.toSlotPos(20f, 10f, uiScale = 2f, guiLeft = 0, guiTop = 0)
        assertEquals(41, mapped.x)
        assertEquals(21, mapped.y)
    }
}

class ExclusionAccumulatorTest {

    @Test
    fun `accumulates zones and clears at the start of a frame`() {
        val acc = ExclusionAccumulator()
        acc.add(0, 0, 10, 10)
        acc.add(20, 0, 8, 8)
        assertEquals(2, acc.snapshot().size)
        acc.beginFrame()
        assertTrue(acc.snapshot().isEmpty())
        acc.add(1, 2, 3, 4)
        acc.add(0, 0, 0, 10)
        val zones = acc.snapshot()
        assertEquals(1, zones.size)
        assertEquals(1, zones[0].x)
        assertEquals(4, zones[0].height)
    }
}

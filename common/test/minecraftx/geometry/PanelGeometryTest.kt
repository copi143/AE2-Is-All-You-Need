package minecraftx.geometry

import minecraftx.compose.geometry.PanelGeometry
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PanelGeometryTest {

    @Test
    fun `half-open box excludes the far edge`() {
        assertTrue(PanelGeometry.containsHalfOpen(0, 0, 0f, 0f, 18))
        assertTrue(PanelGeometry.containsHalfOpen(17, 17, 0f, 0f, 18))
        assertFalse(PanelGeometry.containsHalfOpen(18, 0, 0f, 0f, 18))
        assertFalse(PanelGeometry.containsHalfOpen(0, 18, 0f, 0f, 18))
    }
}

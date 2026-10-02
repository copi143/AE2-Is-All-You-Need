package allyouneed.compose.platform

import minecraftx.compose.material.scrollbarThumbHeight
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ScrollbarTest {
    @Test
    fun `thumb fits short and empty tracks`() {
        assertEquals(0, scrollbarThumbHeight(0, 100f))
        assertEquals(8, scrollbarThumbHeight(8, 100f))
        assertEquals(16, scrollbarThumbHeight(16, 100f))
    }

    @Test
    fun `thumb preserves proportions and handles large tracks without overflow`() {
        assertEquals(50, scrollbarThumbHeight(100, 100f))
        assertEquals(16, scrollbarThumbHeight(100, 10000f))
        assertEquals(50000, scrollbarThumbHeight(100000, 100000f))
    }
}

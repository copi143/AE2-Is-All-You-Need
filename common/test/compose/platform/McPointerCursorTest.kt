package allyouneed.compose.platform

import allyouneed.client.compose.platform.McPointerCursor
import androidx.compose.ui.input.pointer.PointerIcon
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.awt.Cursor

class McPointerCursorTest {

    @Test
    fun `official icons map to glfw shapes`() {
        assertNull(McPointerCursor.glfwShapeOf(null))
        assertNull(McPointerCursor.glfwShapeOf(PointerIcon.Default))
        assertEquals(0x36004, McPointerCursor.glfwShapeOf(PointerIcon.Hand))
        assertEquals(0x36002, McPointerCursor.glfwShapeOf(PointerIcon.Text))
        assertEquals(0x36003, McPointerCursor.glfwShapeOf(PointerIcon.Crosshair))
    }

    @Test
    fun `awt resize cursors map to glfw 34 shapes`() {
        assertEquals(0x36005, McPointerCursor.glfwShapeOf(PointerIcon(Cursor(Cursor.E_RESIZE_CURSOR))))
        assertEquals(0x36005, McPointerCursor.glfwShapeOf(PointerIcon(Cursor(Cursor.W_RESIZE_CURSOR))))
        assertEquals(0x36006, McPointerCursor.glfwShapeOf(PointerIcon(Cursor(Cursor.N_RESIZE_CURSOR))))
        assertEquals(0x36006, McPointerCursor.glfwShapeOf(PointerIcon(Cursor(Cursor.S_RESIZE_CURSOR))))
        assertEquals(0x36007, McPointerCursor.glfwShapeOf(PointerIcon(Cursor(Cursor.NW_RESIZE_CURSOR))))
        assertEquals(0x36008, McPointerCursor.glfwShapeOf(PointerIcon(Cursor(Cursor.NE_RESIZE_CURSOR))))
        assertEquals(0x36009, McPointerCursor.glfwShapeOf(PointerIcon(Cursor(Cursor.MOVE_CURSOR))))
    }
}

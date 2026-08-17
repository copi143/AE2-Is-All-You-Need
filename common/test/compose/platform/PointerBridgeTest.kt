package allyouneed.compose.platform

import allyouneed.client.compose.platform.keyboardModifiersOf
import allyouneed.client.compose.platform.pointerButtonOf
import allyouneed.client.compose.platform.pointerButtonsOf
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.isTertiaryPressed
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PointerBridgeTest {

    @Test
    fun `maps left right and middle mouse buttons`() {
        assertEquals(PointerButton.Primary, pointerButtonOf(0))
        assertEquals(PointerButton.Secondary, pointerButtonOf(1))
        assertEquals(PointerButton.Tertiary, pointerButtonOf(2))
        assertNull(pointerButtonOf(3))
    }

    @Test
    fun `packs simultaneous mouse buttons`() {
        val buttons = pointerButtonsOf(primary = true, tertiary = true)
        assertTrue(buttons.isPrimaryPressed)
        assertFalse(buttons.isSecondaryPressed)
        assertTrue(buttons.isTertiaryPressed)
    }

    @Test
    fun `packs keyboard modifiers`() {
        val mods = keyboardModifiersOf(shift = true, ctrl = true)
        assertTrue(mods.isShiftPressed)
        assertTrue(mods.isCtrlPressed)
        assertFalse(keyboardModifiersOf().isShiftPressed)
    }
}

@file:Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")

package allyouneed.client.compose.platform

import androidx.compose.ui.input.pointer.AwtCursor
import androidx.compose.ui.input.pointer.PointerIcon
import net.minecraft.client.Minecraft
import org.lwjgl.glfw.GLFW
import java.awt.Cursor

object McPointerCursor {
    private val handles = HashMap<Int, Long>()
    private var applied = Long.MIN_VALUE

    fun glfwShapeOf(icon: PointerIcon?): Int? {
        if (icon == null || icon == PointerIcon.Default) return null
        if (icon == PointerIcon.Hand) return GLFW.GLFW_POINTING_HAND_CURSOR
        if (icon == PointerIcon.Text) return GLFW.GLFW_IBEAM_CURSOR
        if (icon == PointerIcon.Crosshair) return GLFW.GLFW_CROSSHAIR_CURSOR
        val type = (icon as? AwtCursor)?.cursor?.type ?: return null
        return when (type) {
            Cursor.DEFAULT_CURSOR -> null
            Cursor.CROSSHAIR_CURSOR -> GLFW.GLFW_CROSSHAIR_CURSOR
            Cursor.TEXT_CURSOR -> GLFW.GLFW_IBEAM_CURSOR
            Cursor.HAND_CURSOR -> GLFW.GLFW_POINTING_HAND_CURSOR
            Cursor.E_RESIZE_CURSOR, Cursor.W_RESIZE_CURSOR -> GLFW.GLFW_RESIZE_EW_CURSOR
            Cursor.N_RESIZE_CURSOR, Cursor.S_RESIZE_CURSOR -> GLFW.GLFW_RESIZE_NS_CURSOR
            Cursor.NW_RESIZE_CURSOR, Cursor.SE_RESIZE_CURSOR -> GLFW.GLFW_RESIZE_NWSE_CURSOR
            Cursor.NE_RESIZE_CURSOR, Cursor.SW_RESIZE_CURSOR -> GLFW.GLFW_RESIZE_NESW_CURSOR
            Cursor.MOVE_CURSOR -> GLFW.GLFW_RESIZE_ALL_CURSOR
            Cursor.WAIT_CURSOR -> GLFW.GLFW_NOT_ALLOWED_CURSOR
            else -> null
        }
    }

    fun apply(icon: PointerIcon?) {
        val window = runCatching { Minecraft.getInstance()?.window?.window }.getOrNull() ?: return
        val handle = handleFor(glfwShapeOf(icon))
        if (handle == applied) return
        GLFW.glfwSetCursor(window, handle)
        applied = handle
    }

    private fun handleFor(shape: Int?): Long {
        if (shape == null) return 0L
        handles[shape]?.let { return it }
        val created = runCatching { GLFW.glfwCreateStandardCursor(shape) }.getOrNull() ?: 0L
        val handle = if (created != 0L) created else handleFor(fallbackShape(shape))
        handles[shape] = handle
        return handle
    }

    private fun fallbackShape(shape: Int): Int? = when (shape) {
        GLFW.GLFW_RESIZE_NWSE_CURSOR, GLFW.GLFW_RESIZE_NESW_CURSOR -> GLFW.GLFW_RESIZE_EW_CURSOR
        GLFW.GLFW_RESIZE_ALL_CURSOR, GLFW.GLFW_NOT_ALLOWED_CURSOR -> null
        GLFW.GLFW_RESIZE_EW_CURSOR, GLFW.GLFW_RESIZE_NS_CURSOR,
        GLFW.GLFW_POINTING_HAND_CURSOR, GLFW.GLFW_IBEAM_CURSOR,
        GLFW.GLFW_CROSSHAIR_CURSOR -> GLFW.GLFW_ARROW_CURSOR
        else -> null
    }
}

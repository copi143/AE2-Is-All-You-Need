package allyouneed.client.compose.platform

import net.minecraft.client.gui.GuiGraphics
import kotlin.math.max
import kotlin.math.min

data class ScissorRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val isEmpty: Boolean get() = right <= left || bottom <= top

    fun intersect(other: ScissorRect): ScissorRect = ScissorRect(
        left = max(left, other.left),
        top = max(top, other.top),
        right = min(right, other.right),
        bottom = min(bottom, other.bottom),
    )
}

/**
 * 1:1 wrapper around [GuiGraphics] scissor. Vanilla already keeps a nested stack and intersects
 * on push; each [push] must be paired with exactly one [pop]. Calling [GuiGraphics.disableScissor]
 * when the vanilla stack is empty throws `Scissor stack underflow`.
 */
object McScissor {
    private var enabled = 0

    val depth: Int get() = enabled

    fun push(graphics: GuiGraphics, left: Int, top: Int, right: Int, bottom: Int) {
        push(graphics, ScissorRect(left, top, right, bottom))
    }

    fun push(graphics: GuiGraphics, rect: ScissorRect) {
        graphics.enableScissor(rect.left, rect.top, rect.right, rect.bottom)
        enabled++
    }

    fun pop(graphics: GuiGraphics) {
        if (enabled <= 0) return
        graphics.disableScissor()
        enabled--
    }

    fun reset(graphics: GuiGraphics? = null) {
        if (graphics == null) {
            enabled = 0
            return
        }
        while (enabled > 0) pop(graphics)
    }
}

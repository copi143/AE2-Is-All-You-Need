package allyouneed.client.compose.platform

import net.minecraft.client.gui.GuiGraphics

/**
 * 1:1 wrapper around [GuiGraphics] scissor. Vanilla already keeps a nested stack and intersects
 * on push; each [push] must be paired with exactly one [pop]. Calling [GuiGraphics.disableScissor]
 * when the vanilla stack is empty throws `Scissor stack underflow`.
 */
object McScissor {
    private var enabled = 0

    val depth: Int get() = enabled

    fun push(graphics: GuiGraphics, left: Int, top: Int, right: Int, bottom: Int) {
        graphics.enableScissor(left, top, right, bottom)
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

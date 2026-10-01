package allyouneed.client.compose.platform

import net.minecraft.client.gui.GuiGraphics

/**
 * 1:1 wrapper around [GuiGraphics] scissor. Vanilla already keeps a nested stack and intersects
 * on push; each [push] must be paired with exactly one [pop]. Calling [GuiGraphics.disableScissor]
 * when the vanilla stack is empty throws `Scissor stack underflow`.
 *
 * Record-aware: while [McGraphics.activeRecorder] is set (record phase), [push]/[pop] append the
 * equivalent scissor command to the recording instead of touching GL, and [graphics] may be null.
 * The [depth] counter tracks both modes so save/restore marks stay consistent.
 */
object McScissor {
    private var enabled = 0

    val depth: Int get() = enabled

    fun push(graphics: GuiGraphics?, left: Int, top: Int, right: Int, bottom: Int) {
        val recorder = McGraphics.activeRecorder
        if (recorder != null) {
            recorder.ops += { g -> g.enableScissor(left, top, right, bottom) }
        } else {
            (graphics ?: McGraphics.current)!!.enableScissor(left, top, right, bottom)
        }
        enabled++
    }

    fun pop(graphics: GuiGraphics?) {
        if (enabled <= 0) return
        val recorder = McGraphics.activeRecorder
        if (recorder != null) {
            recorder.ops += { g -> g.disableScissor() }
        } else {
            (graphics ?: McGraphics.current)!!.disableScissor()
        }
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

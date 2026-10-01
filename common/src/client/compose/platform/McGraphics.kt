package allyouneed.client.compose.platform

import net.minecraft.client.gui.GuiGraphics
import org.joml.Matrix4f

/**
 * Hands the active [GuiGraphics] to composable draw modifiers that paint text with the Minecraft
 * font, plus the recording bridge used by the two-phase render pipeline (see [ComposeOwner]):
 *
 *  - **Record phase** (world-render stage): [activeRecorder] is set, [current] is null. Canvas
 *    geometry is recorded by [McCanvas]; raw-GuiGraphics work (text, item icons, custom GL) must be
 *    wrapped in [defer], which stores the block instead of running it. [currentPose] still returns
 *    the would-be pose matrix so scissor rects can be computed at record time.
 *  - **Replay phase** (GUI stage): [activeRecorder] is null, [current] is the live graphics and
 *    every deferred block runs inline with the correct pose/scissor state.
 */
object McGraphics {
    var current: GuiGraphics? = null

    internal var activeRecorder: McDrawRecorder? = null

    /** True while a draw pass (record or live) is in progress. */
    val active: Boolean get() = current != null || activeRecorder != null

    /**
     * Runs [block] with the live [GuiGraphics] — immediately during the replay phase, or recorded
     * for replay during the record phase. The block must not call `drawContent()` or use the
     * enclosing DrawScope; capture record-time values in locals instead.
     */
    fun defer(block: (GuiGraphics) -> Unit) {
        val recorder = activeRecorder
        if (recorder != null) {
            recorder.ops += block
        } else {
            current?.let(block)
        }
    }

    /**
     * The pose matrix that a drawing at this point of the pass would use, in both record and
     * replay/live mode. Do not cache the returned instance; read it immediately.
     */
    fun currentPose(): Matrix4f? {
        val recorder = activeRecorder
        return recorder?.poseStack?.last()?.pose() ?: current?.pose()?.last()?.pose()
    }
}

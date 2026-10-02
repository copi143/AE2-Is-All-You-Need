package allyouneed.client.compose.platform

import net.minecraft.client.gui.GuiGraphics
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.flattenContours
import androidx.compose.ui.graphics.singleRectOrNull
import com.mojang.blaze3d.systems.RenderSystem
import org.joml.Matrix4f

/**
 * Unified clip stack. Axis-aligned rectangles use [GuiGraphics] scissor; other paths use independent
 * offscreen masks. Every push has one pop, including empty regions and difference clips.
 *
 * Record-aware: while [McGraphics.activeRecorder] is set (record phase), [push]/[pop] append the
 * equivalent scissor command to the recording instead of touching GL, and [graphics] may be null.
 * Recording depth and the live cleanup stack are separate so interrupted replay can unwind safely.
 */
object McScissor {
    private var recordedDepth = 0
    // Live/replay entries are tracked separately: a replay may abort halfway through a recording.
    private val live = ArrayDeque<(() -> Unit)>()

    val depth: Int get() = if (McGraphics.activeRecorder != null) recordedDepth else live.size

    fun pushRect(graphics: GuiGraphics?, rect: Rect, matrix: Matrix4f, clipOp: ClipOp = ClipOp.Intersect) {
        if (rect.isEmpty && clipOp == ClipOp.Intersect) {
            push(graphics, 0, 0, 0, 0)
        } else if (ClipGeometry.axisAligned(matrix) && clipOp == ClipOp.Intersect) {
            val b = ClipGeometry.bounds(ClipGeometry.rectangle(rect, matrix))
            push(graphics, b[0], b[1], b[2], b[3])
        } else {
            pushPath(graphics, Path().apply { if (!rect.isEmpty) addRect(rect) }, matrix, clipOp)
        }
    }

    fun pushPath(graphics: GuiGraphics?, path: Path, matrix: Matrix4f, clipOp: ClipOp = ClipOp.Intersect) {
        val rect = path.singleRectOrNull()
        if (rect != null && ClipGeometry.axisAligned(matrix) && clipOp == ClipOp.Intersect) {
            pushRect(graphics, rect, matrix)
            return
        }
        val contours = path.flattenContours(0.25f).map { ClipGeometry.transform(it, matrix) }
        val mask = fillContours(contours, path.fillType, -1)
        enqueue(graphics) { g ->
            g.flush()
            val projection = Matrix4f(RenderSystem.getProjectionMatrix()).mul(RenderSystem.getModelViewMatrix())
            val layer = McClipTarget.begin(mask, projection, clipOp == ClipOp.Difference)
            live.addLast { try { g.flush() } finally { layer.finish() } }
        }
    }

    private fun enqueue(graphics: GuiGraphics?, op: (GuiGraphics) -> Unit) {
        val recorder = McGraphics.activeRecorder
        if (recorder != null) {
            recorder.ops += op
            recordedDepth++
        } else {
            op((graphics ?: McGraphics.current)!!)
        }
    }

    fun push(graphics: GuiGraphics?, left: Int, top: Int, right: Int, bottom: Int) {
        enqueue(graphics) { g ->
            g.enableScissor(left, top, right, bottom)
            live.addLast { g.disableScissor() }
        }
    }

    fun pop(graphics: GuiGraphics?) {
        val recorder = McGraphics.activeRecorder
        if (recorder != null) {
            if (recordedDepth <= 0) return
            recorder.ops += { popLive() }
            recordedDepth--
        } else {
            popLive()
        }
    }

    private fun popLive() { if (live.isNotEmpty()) live.removeLast().invoke() }

    fun reset(graphics: GuiGraphics? = null) {
        if (graphics == null) {
            recordedDepth = 0
            return
        }
        while (live.isNotEmpty()) popLive()
    }
}

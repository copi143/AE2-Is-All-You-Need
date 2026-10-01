package allyouneed.client.compose.platform

import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.gui.GuiGraphics

/**
 * Record-phase output of a Compose draw pass: an ordered list of draw commands to execute against
 * a live [GuiGraphics] during the GUI stage, plus a virtual [PoseStack] that mirrors the pose
 * transforms so scissor rects can be computed while recording (no real graphics exists yet).
 *
 * The base transform (layer origin + UI scale) is applied to [poseStack] before recording and
 * re-applied live at replay; the recorded ops only cover the tree's own transforms.
 */
internal class McDrawRecorder {
    val ops = ArrayList<(GuiGraphics) -> Unit>()
    val poseStack = PoseStack()
}

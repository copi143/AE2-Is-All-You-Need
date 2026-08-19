package allyouneed.client.compose.platform

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent
import net.minecraft.client.gui.screens.inventory.tooltip.TooltipRenderUtil

/**
 * Replicates [GuiGraphics.renderTooltipInternal] (private in vanilla) for a pre-built list of
 * [ClientTooltipComponent]s, which is what EMI exposes via `EmiStack.getTooltip()`. Background,
 * text lines and image lines follow the vanilla layout: content at (x, y) with the border drawn by
 * [TooltipRenderUtil.renderTooltipBackground] around it.
 *
 * The pose juggling is important and mirrors vanilla exactly: the background fill runs inside
 * [GuiGraphics.drawManaged] (disables depth test so the fill lands), then the pose is translated
 * +400 in z so the text (drawn via the buffer with that matrix) sits in front of the background.
 * Skipping the push/translate/pop makes the text render behind the tooltip background.
 */
fun GuiGraphics.renderMcTooltip(font: Font, tooltip: List<ClientTooltipComponent>, x: Int, y: Int) {
    if (tooltip.isEmpty()) return
    val width = tooltip.maxOf { it.getWidth(font) }
    val height = tooltip.sumOf { it.height }
    pose().pushPose()
    drawManaged { TooltipRenderUtil.renderTooltipBackground(this, x, y, width, height, 400) }
    pose().translate(0f, 0f, 400f)
    val buffer = Minecraft.getInstance().renderBuffers().bufferSource()
    val matrix = pose().last().pose()
    var lineY = y
    for ((index, component) in tooltip.withIndex()) {
        component.renderText(font, x, lineY, matrix, buffer)
        lineY += component.height + if (index == 0) 2 else 0
    }
    lineY = y
    for ((index, component) in tooltip.withIndex()) {
        component.renderImage(font, x, lineY, this)
        lineY += component.height + if (index == 0) 2 else 0
    }
    buffer.endBatch()
    pose().popPose()
}

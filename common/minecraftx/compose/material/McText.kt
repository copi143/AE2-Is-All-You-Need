package minecraftx.compose.material

import allyouneed.client.compose.platform.McGraphics
import allyouneed.client.compose.platform.McScissor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import minecraftx.compose.theme.McTheme
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component
import kotlin.math.max
import kotlin.math.min

/**
 * Renders [text] with the Minecraft font, truncated to [maxWidth] logical pixels. When [clipFrame]
 * (a rect in node-local coordinates describing a clipping window) is provided and the glyph box only
 * partially overlaps it, a hardware scissor clips the text to the frame; fully-inside / fully-outside
 * rows skip the scissor entirely.
 *
 * Layout uses official Compose constraints measured against the MC font metrics; painting bypasses
 * the official text pipeline (which would pull in the skiko font stack) and draws straight onto
 * [McGraphics]'s GuiGraphics inside the node's translated frame. [color] is a fallback for
 * components that carry their own style (e.g. [Component.withStyle]).
 */
@Composable
fun McText(
    text: Component,
    modifier: Modifier = Modifier,
    color: Int = McTheme.colors.textPrimary.value.toInt(),
    maxWidth: Int = Int.MAX_VALUE,
    clipFrame: Rect? = null,
) {
    val font = Minecraft.getInstance().font
    Layout(
        content = {},
        modifier = modifier.drawBehind {
            val g = McGraphics.current ?: return@drawBehind
            drawClipped(g, font, text, min(font.width(text), maxWidth), color, clipFrame)
        },
    ) { _, constraints: androidx.compose.ui.unit.Constraints ->
        val w = constraints.constrainWidth(min(font.width(text), maxWidth))
        val h = constraints.constrainHeight(font.lineHeight)
        layout(w, h) {}
    }
}

@Composable
fun McText(
    text: String,
    modifier: Modifier = Modifier,
    color: Int = McTheme.colors.textPrimary.value.toInt(),
    maxWidth: Int = Int.MAX_VALUE,
    clipFrame: Rect? = null,
) {
    McText(Component.literal(text), modifier, color, maxWidth, clipFrame)
}

/** String convenience overload of [McText]. */
@Composable
fun Text(text: String, modifier: Modifier = Modifier, color: Int = McTheme.colors.textPrimary.value.toInt()) {
    McText(Component.literal(text), modifier, color)
}

/**
 * Draws [text] at the node's local origin, truncated to [widthPx]. When [clipFrame] is provided and
 * the glyph box only partially overlaps it, a hardware scissor clips the text to the frame; rows
 * fully inside or fully outside the frame skip the scissor entirely.
 *
 * The clip rectangle is derived from the live modelview pose instead of manually re-deriving the
 * panel geometry: [GuiGraphics.drawString] transforms glyphs with that same `pose().last().pose()`
 * matrix, so the scissor region is always pixel-aligned with the drawn text, regardless of zoom.
 */
private fun drawClipped(
    g: GuiGraphics,
    font: Font,
    text: Component,
    widthPx: Int,
    color: Int,
    clipFrame: Rect?,
) {
    if (clipFrame == null) {
        drawText(g, font, text, widthPx, color)
        return
    }
    if (clipFrame.left <= 0f && clipFrame.top <= 0f &&
        clipFrame.right >= widthPx.toFloat() && clipFrame.bottom >= font.lineHeight.toFloat()
    ) {
        drawText(g, font, text, widthPx, color)
        return
    }
    if (clipFrame.right <= 0f || clipFrame.bottom <= 0f ||
        clipFrame.left >= widthPx.toFloat() || clipFrame.top >= font.lineHeight.toFloat()
    ) {
        return
    }
    val matrix = g.pose().last().pose()
    val nodeX = matrix.m30()
    val nodeY = matrix.m31()
    val scaleX = matrix.m00()
    val scaleY = matrix.m11()
    val clipLeft = max(nodeX, nodeX + clipFrame.left * scaleX)
    val clipTop = max(nodeY, nodeY + clipFrame.top * scaleY)
    val clipRight = min(nodeX + widthPx * scaleX, nodeX + clipFrame.right * scaleX)
    val clipBottom = min(nodeY + font.lineHeight * scaleY, nodeY + clipFrame.bottom * scaleY)
    if (clipRight <= clipLeft || clipBottom <= clipTop) return
    McScissor.push(g, clipLeft.toInt(), clipTop.toInt(), clipRight.toInt(), clipBottom.toInt())
    try {
        drawText(g, font, text, widthPx, color)
    } finally {
        McScissor.pop(g)
    }
}

private fun drawText(g: GuiGraphics, font: Font, text: Component, widthPx: Int, color: Int) {
    if (widthPx < font.width(text)) {
        g.drawString(font, font.plainSubstrByWidth(text.getString(), widthPx), 0, 0, color, false)
    } else {
        g.drawString(font, text, 0, 0, color, false)
    }
}

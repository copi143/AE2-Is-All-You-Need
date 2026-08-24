package minecraftx.compose.material

import allyouneed.client.compose.platform.McGraphics
import allyouneed.client.compose.platform.McScissor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import minecraftx.compose.text.LocalMcTextEngine
import minecraftx.compose.text.McStyledString
import minecraftx.compose.text.McTextEngine
import minecraftx.compose.text.McTextLayout
import minecraftx.compose.text.rememberTextLayout
import minecraftx.compose.text.toStyledString
import minecraftx.compose.theme.McTheme
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component
import kotlin.math.max
import kotlin.math.min

/**
 * Renders [text] with the active [minecraftx.compose.text.McTextEngine], truncated to [maxWidth]
 * logical pixels on a single line. When [clipFrame] (a rect in node-local coordinates describing a
 * clipping window) is provided and the glyph box only partially overlaps it, a hardware scissor
 * clips the text to the frame; fully-inside / fully-outside rows skip the scissor entirely.
 *
 * Layout and painting are delegated to the engine (see [LocalMcTextEngine]); this component keeps
 * its historical API and single-line truncation behavior.
 */
@Composable
fun McText(
    text: Component,
    modifier: Modifier = Modifier,
    color: Int = McTheme.colors.textPrimary.toArgb(),
    maxWidth: Int = Int.MAX_VALUE,
    clipFrame: Rect? = null,
) {
    McTextInternal(text.toStyledString(), modifier, color, maxWidth, clipFrame)
}

@Composable
fun McText(
    text: String,
    modifier: Modifier = Modifier,
    color: Int = McTheme.colors.textPrimary.toArgb(),
    maxWidth: Int = Int.MAX_VALUE,
    clipFrame: Rect? = null,
) {
    McTextInternal(McStyledString(text), modifier, color, maxWidth, clipFrame)
}

/** String convenience overload of [McText]. */
@Composable
fun Text(text: String, modifier: Modifier = Modifier, color: Int = McTheme.colors.textPrimary.toArgb()) {
    McText(text, modifier, color)
}

@Composable
private fun McTextInternal(
    styled: McStyledString,
    modifier: Modifier,
    color: Int,
    maxWidth: Int,
    clipFrame: Rect?,
) {
    val engine = LocalMcTextEngine.current
    val layout = rememberTextLayout(styled, maxWidth.coerceAtLeast(0), singleLine = true)
    val drawnWidth = min(layout.width, maxWidth)
    Layout(
        content = {},
        modifier = modifier.drawBehind {
            val g = McGraphics.current ?: return@drawBehind
            drawClipped(g, engine, layout, drawnWidth, color, clipFrame)
        },
    ) { _, constraints: Constraints ->
        val w = constraints.constrainWidth(min(drawnWidth, maxWidth))
        val h = constraints.constrainHeight(engine.lineHeight)
        layout(w, h) {}
    }
}

/**
 * Multi-line wrapping text. The string re-wraps to the incoming max width constraint; [maxLines]
 * limits how many lines are measured and drawn (overflowing content is simply clipped, no ellipsis).
 */
@Composable
fun McWrappedText(
    text: McStyledString,
    modifier: Modifier = Modifier,
    color: Int = McTheme.colors.textPrimary.toArgb(),
    maxLines: Int = Int.MAX_VALUE,
) {
    val engine = LocalMcTextEngine.current
    var laidOut: McTextLayout = McTextLayout.EMPTY
    Layout(
        content = {},
        modifier = modifier.drawBehind {
            val g = McGraphics.current ?: return@drawBehind
            drawClipped(g, engine, laidOut, laidOut.width, color, clipFrame = null)
        },
    ) { _, constraints ->
        val maxW = if (constraints.hasBoundedWidth) constraints.maxWidth else Int.MAX_VALUE
        val full = engine.layout(text, maxW)
        val lines = if (full.lines.size > maxLines) full.lines.take(maxLines) else full.lines
        laidOut = McTextLayout(lines, full.lineHeight)
        val w = constraints.constrainWidth(laidOut.width)
        val h = constraints.constrainHeight(laidOut.height)
        layout(w, h) {}
    }
}

@Composable
fun McWrappedText(
    text: String,
    modifier: Modifier = Modifier,
    color: Int = McTheme.colors.textPrimary.toArgb(),
    maxLines: Int = Int.MAX_VALUE,
) {
    McWrappedText(McStyledString(text), modifier, color, maxLines)
}

/**
 * Draws [layout] at the node's local origin, truncated horizontally to [widthPx]. When [clipFrame]
 * is provided and the glyph box only partially overlaps it, a hardware scissor clips the text to the
 * frame; rows fully inside or fully outside the frame skip the scissor entirely.
 *
 * The clip rectangle is derived from the live modelview pose instead of manually re-deriving the
 * panel geometry: glyph drawing transforms vertices with that same `pose().last().pose()` matrix,
 * so the scissor region stays pixel-aligned with the drawn text regardless of zoom.
 */
private fun DrawScope.drawClipped(
    g: GuiGraphics,
    engine: McTextEngine,
    layout: McTextLayout,
    widthPx: Int,
    color: Int,
    clipFrame: Rect?,
) {
    val lineHeightPx = engine.lineHeight
    if (clipFrame == null) {
        with(engine) { paint(layout, Color(color)) }
        return
    }
    if (clipFrame.left <= 0f && clipFrame.top <= 0f &&
        clipFrame.right >= widthPx.toFloat() && clipFrame.bottom >= lineHeightPx.toFloat()
    ) {
        with(engine) { paint(layout, Color(color)) }
        return
    }
    if (clipFrame.right <= 0f || clipFrame.bottom <= 0f ||
        clipFrame.left >= widthPx.toFloat() || clipFrame.top >= lineHeightPx.toFloat()
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
    val clipBottom = min(nodeY + lineHeightPx * scaleY, nodeY + clipFrame.bottom * scaleY)
    if (clipRight <= clipLeft || clipBottom <= clipTop) return
    McScissor.push(g, clipLeft.toInt(), clipTop.toInt(), clipRight.toInt(), clipBottom.toInt())
    try {
        with(engine) { paint(layout, Color(color)) }
    } finally {
        McScissor.pop(g)
    }
}

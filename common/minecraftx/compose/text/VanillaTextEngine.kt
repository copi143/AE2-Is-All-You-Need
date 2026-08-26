package minecraftx.compose.text

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toArgb
import allyouneed.client.compose.platform.McGraphics
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Style
import net.minecraft.util.FormattedCharSequence

/**
 * Reference [McTextEngine] drawing through the vanilla bitmap font ([Minecraft.font] +
 * [McGraphics]). Layout wraps text with [TextWrap]; painting goes through
 * [net.minecraft.client.gui.GuiGraphics.drawString].
 *
 * [letterSpacing] inserts extra px between characters — used to instantiate demo variants without
 * needing a second rendering stack.
 */
class VanillaTextEngine(
    override val id: String = "vanilla",
    private val letterSpacing: Int = 0,
) : McTextEngine {

    override val lineHeight: Int
        get() = Minecraft.getInstance().font.lineHeight

    override fun layout(text: McStyledString, maxWidth: Int, singleLine: Boolean): McTextLayout {
        val font = Minecraft.getInstance().font
        return TextWrap.layout(text, maxWidth, singleLine, lineHeight) { cp, style ->
            val piece = String(Character.toChars(cp))
            font.width(FormattedCharSequence.forward(piece, style?.toMcStyle() ?: Style.EMPTY)) + letterSpacing
        }
    }

    override fun widthOf(text: String, style: McSpanStyle?): Int {
        if (text.isEmpty()) return 0
        val font = Minecraft.getInstance().font
        return font.width(FormattedCharSequence.forward(text, style?.toMcStyle() ?: Style.EMPTY)) +
            letterSpacing * text.codePointCount(0, text.length).coerceAtLeast(0)
    }

    override fun indexAtWidth(text: String, width: Int, style: McSpanStyle?): Int {
        if (text.isEmpty() || width <= 0) return 0
        val font = Minecraft.getInstance().font
        val kept = font.plainSubstrByWidth(text, width)
        return kept.length
    }

    override fun DrawScope.paint(layout: McTextLayout, fallbackColor: Color) {
        val g = McGraphics.current ?: return
        val font = Minecraft.getInstance().font
        val fbArgb = fallbackColor.toArgb()
        for ((li, line) in layout.lines.withIndex()) {
            val y = li * layout.lineHeight
            for (run in line.runs) {
                val mcStyle = run.style?.toMcStyle() ?: Style.EMPTY
                val argb = run.style?.color?.let { it.toArgb() } ?: fbArgb
                g.drawString(font, FormattedCharSequence.forward(run.text, mcStyle), run.x, y, argb, false)
            }
        }
    }
}

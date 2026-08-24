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
 * [McGraphics]). Layout wraps text with its own greedy line breaker (space breaks for latin
 * scripts, per-character breaks for CJK ranges, hard breaks for oversized words); painting goes
 * through [GuiGraphics.drawString] exactly like the legacy components did.
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
        val emptyLine = McTextLayout(listOf(TextLine(emptyList(), 0)), lineHeight)
        if (text.text.isEmpty()) return emptyLine
        if (maxWidth <= 0) return McTextLayout.EMPTY.takeIf { !singleLine } ?: emptyLine

        val font = Minecraft.getInstance().font
        val runs = text.toMcRuns()

        // Flatten runs into per-codepoint parallel arrays for the wrapper.
        val sb = StringBuilder()
        val cps = ArrayList<Int>(text.text.length)
        val starts = ArrayList<Int>(text.text.length + 1) // UTF-16 offset of each codepoint (+ sentinel)
        val styleIdx = ArrayList<Int>(text.text.length)
        val widths = ArrayList<Int>(text.text.length)

        for ((runIdx, run) in runs.withIndex()) {
            val s = run.text
            var i = 0
            while (i < s.length) {
                val cp = s.codePointAt(i)
                val piece = String(Character.toChars(cp))
                cps += cp
                starts += sb.length
                styleIdx += runIdx
                widths += font.width(FormattedCharSequence.forward(piece, run.style)) + letterSpacing
                sb.append(piece)
                i += Character.charCount(cp)
            }
        }
        starts += sb.length
        val spanStyles = runs.map { it.visual }
        val n = cps.size

        val lines = mutableListOf<TextLine>()
        var lineStart = 0
        var x = 0
        var lastBreak = -1 // cp index right after a legal break point
        var breakX = 0     // pen x at lastBreak

        fun widthBetween(from: Int, to: Int): Int {
            var w = 0
            for (i in from until to) w += widths[i]
            return w
        }

        fun emitLine(from: Int, toRaw: Int, rawWidth: Int) {
            var to = toRaw
            var w = rawWidth
            while (to > from && cps[to - 1] == ' '.code) { // trim trailing spaces off drawn lines
                w -= widths[to - 1]
                to--
            }
            val outRuns = mutableListOf<StyledRun>()
            var g = from
            var gx = 0
            while (g < to) {
                val st = styleIdx[g]
                var ge = g + 1
                while (ge < to && styleIdx[ge] == st) ge++
                val txt = sb.substring(starts[g], starts[ge])
                outRuns += StyledRun(gx, txt, spanStyles[st])
                for (i in g until ge) gx += widths[i]
                g = ge
            }
            lines += TextLine(outRuns, w.coerceAtLeast(gx))
        }

        var i = 0
        while (i < n) {
            val advance = widths[i]
            if (x + advance > maxWidth && i > lineStart) {
                if (singleLine) { // truncate: keep only what fit before this char
                    emitLine(lineStart, i, x)
                    return McTextLayout(lines, lineHeight)
                }
                if (lastBreak > lineStart) {
                    emitLine(lineStart, lastBreak, breakX)
                    var next = lastBreak
                    while (next < n && cps[next] == ' '.code) next++ // drop leading spaces
                    lineStart = next
                    x = widthBetween(lineStart, i)
                } else { // hard break inside an unbreakable word
                    emitLine(lineStart, i, x)
                    lineStart = i
                    x = 0
                }
                lastBreak = -1
                breakX = 0
            }
            x += advance
            if (isBreakableAfter(cps[i])) {
                lastBreak = i + 1
                breakX = x
            }
            i++
        }
        emitLine(lineStart, n, x)
        return McTextLayout(lines, lineHeight)
    }

    override fun DrawScope.paint(layout: McTextLayout, fallbackColor: Color) {
        val g = McGraphics.current ?: return
        val font = Minecraft.getInstance().font
        val fbArgb = fallbackColor.toArgb()
        for ((li, line) in layout.lines.withIndex()) {
            val y = li * layout.lineHeight
            for (run in line.runs) {
                val style = run.style?.toMcStyle() ?: Style.EMPTY
                val argb = run.style?.color?.let { it.toArgb() } ?: fbArgb
                g.drawString(font, FormattedCharSequence.forward(run.text, style), run.x, y, argb, false)
            }
        }
    }

    private companion object {
        /** Space always breaks; CJK-family ranges break after every character. */
        fun isBreakableAfter(cp: Int): Boolean = when {
            cp == ' '.code -> true
            cp in 0x2E80..0x9FFF -> true // CJK radicals, kana, unified ideographs
            cp in 0xAC00..0xD7AF -> true // hangul syllables
            cp in 0xF900..0xFAFF -> true // compatibility ideographs
            cp in 0xFF00..0xFFEF -> true // fullwidth forms
            cp >= 0x20000 -> true        // supplementary plane ideographs
            else -> false
        }
    }
}

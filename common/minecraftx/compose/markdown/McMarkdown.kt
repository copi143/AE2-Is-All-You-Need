package minecraftx.compose.markdown

import allyouneed.client.compose.platform.McGraphics
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import minecraftx.compose.text.LocalMcTextEngine
import minecraftx.compose.text.McSpanStyle
import minecraftx.compose.text.McStyledString
import minecraftx.compose.text.McTextEngine
import minecraftx.compose.text.McTextLayout
import minecraftx.compose.theme.McTheme
import kotlin.math.floor
import kotlin.math.max

/**
 * Renders GFM markdown (headings, paragraphs, emphasis/strikethrough/code spans, links, fenced and
 * indented code blocks, nested ordered/unordered/task lists, tables, quotes, horizontal rules)
 * through the active [minecraftx.compose.text.McTextEngine].
 *
 * Parsing happens once per input string; semantic roles are resolved to theme colors at layout
 * time. Notable v1 simplifications: table alignment markers are ignored, images render as their
 * alt text, and links are styled but not clickable.
 */
@Composable
fun McMarkdown(
    markdown: String,
    modifier: Modifier = Modifier,
    color: Int = McTheme.colors.textPrimary.toArgb(),
) {
    val engine = LocalMcTextEngine.current
    val colors = McTheme.colors
    val blocks = remember(markdown) { MdParser.parse(markdown) }
    var cmds: List<Cmd> = emptyList()

    Layout(
        content = {},
        modifier = modifier.drawBehind {
            if (McGraphics.current == null) return@drawBehind
            for (cmd in cmds) drawCmd(cmd, engine)
        },
    ) { _, constraints ->
        val maxW = if (constraints.hasBoundedWidth) constraints.maxWidth else Int.MAX_VALUE
        val palette = Palette(
            codeBackground = colors.mdCodeBackground,
            codeForeground = colors.mdCodeText,
            quoteBar = colors.mdQuoteBar,
            ruleLine = colors.mdRuleLine,
            headingAccent = colors.mdHeadingAccent,
            link = colors.mdLink,
            fallback = Color(color),
            border = colors.panelBorder,
        )
        val builder = LayoutBuilder(engine, max(maxW, 0), palette)
        cmds = builder.build(blocks)
        val width = constraints.constrainWidth(maxW.coerceAtLeast(0))
        val height = constraints.constrainHeight(builder.height)
        layout(width, height) {}
    }
}

/** Theme-resolved colors used while building the draw command list. */
private data class Palette(
    val codeBackground: Color,
    val codeForeground: Color,
    val quoteBar: Color,
    val ruleLine: Color,
    val headingAccent: Color,
    val link: Color,
    val fallback: Color,
    val border: Color,
)

private sealed interface Cmd {
    data class Text(val layout: McTextLayout, val x: Int, val y: Int, val fallback: Color) : Cmd
    data class Box(val x: Int, val y: Int, val w: Int, val h: Int, val color: Color) : Cmd
    data class HLine(val x: Int, val y: Int, val w: Int, val thickness: Int, val color: Color) : Cmd
    data class VLine(val x: Int, val y: Int, val h: Int, val thickness: Int, val color: Color) : Cmd
    data class TaskMark(val checked: Boolean, val x: Int, val y: Int, val size: Int, val color: Color, val boxColor: Color) : Cmd
}

/** Walks parsed blocks top-down, producing absolutely positioned draw commands. */
private class LayoutBuilder(private val engine: McTextEngine, private val maxW: Int, private val p: Palette) {

    private val cmds = mutableListOf<Cmd>()
    private var y = 0
    var height = 0
        private set

    private val lh: Int get() = engine.lineHeight
    private val gap: Int get() = (lh * 2) / 3
    private val halfGap: Int get() = gap / 2

    fun build(blocks: List<MdBlock>): List<Cmd> {
        emitAll(blocks, indent = 0)
        height = max(y, lh)
        return cmds
    }

    private fun emitAll(blocks: List<MdBlock>, indent: Int) {
        for ((index, b) in blocks.withIndex()) {
            if (index > 0) y += halfGap
            when (b) {
                is MdBlock.Paragraph -> paragraph(b.styled, indent)
                is MdBlock.Heading -> heading(b.level, b.styled)
                is MdBlock.CodeBlock -> codeBlock(b.lines, indent)
                is MdBlock.Quote -> quote(b.inner, indent)
                is MdBlock.MdList -> list(b, indent)
                is MdBlock.Table -> table(b)
                MdBlock.Rule -> rule()
            }
        }
    }

    /** Lays out [styled] at the current y, advancing it past the drawn lines. */
    private fun text(styled: McStyledString, indent: Int, fallback: Color = p.fallback, maxWidth: Int = maxW - indent): McTextLayout {
        val resolved = styled.resolveThemeColors(p.codeForeground, p.link)
        val layout = engine.layout(resolved, maxWidth.coerceAtLeast(1))
        cmds += Cmd.Text(layout, indent, y, fallback)
        y += layout.height
        return layout
    }

    private fun paragraph(styled: McStyledString, indent: Int) {
        text(styled, indent)
        y += gap
    }

    private fun heading(level: Int, styled: McStyledString) {
        y += halfGap
        val base = when (level) {
            1, 2 -> McSpanStyle(bold = true, color = p.headingAccent)
            else -> McSpanStyle(bold = true)
        }
        val emphasized = McStyledString(styled.text, styled.spans.map { it.copy(style = base.merge(it.style)) })
        val laidOut = text(emphasized, indent = 0)
        if (level <= 2) { // underline accent across the content width
            cmds += Cmd.HLine(0, y + 2, laidOut.width.coerceAtMost(maxW), if (level == 1) 2 else 1, p.headingAccent)
            y += 4
        }
        y += gap
    }

    private fun codeBlock(lines: List<String>, indent: Int) {
        if (lines.isEmpty()) return
        val innerW = maxW - indent
        val padX = 4
        val padY = 3
        val boxH = lines.size * lh + padY * 2
        cmds += Cmd.Box(indent, y, innerW, boxH, p.codeBackground)
        val startY = y
        y += padY
        for (line in lines) {
            cmds += Cmd.Text(engine.layout(McStyledString(line), innerW - padX * 2, singleLine = true), indent + padX, y, p.codeForeground)
            y += lh
        }
        y = startY + boxH + gap
    }

    private fun quote(inner: List<MdBlock>, indent: Int) {
        val startY = y
        emitAll(inner, indent + QUOTE_INDENT)
        val contentH = y - startY
        if (contentH > 0) cmds += Cmd.VLine(indent + 2, startY + 1, contentH - 2, 2, p.quoteBar)
        y += halfGap
    }

    private fun list(block: MdBlock.MdList, indent: Int) {
        val contentIndent = indent + LIST_INDENT
        for ((i, item) in block.items.withIndex()) {
            val markerY = y
            when {
                block.checkboxes.getOrNull(i) != null -> cmds += Cmd.TaskMark(
                    checked = block.checkboxes[i] == true,
                    x = indent + 1,
                    y = markerY + (lh - TASK_BOX) / 2,
                    size = TASK_BOX,
                    color = p.fallback,
                    boxColor = p.border,
                )
                block.ordered -> {
                    val marker = engine.layout(McStyledString("${i + 1}."), LIST_INDENT - 4, singleLine = true)
                    cmds += Cmd.Text(marker, indent, markerY, p.fallback)
                }
                else -> cmds += Cmd.Box(indent + 2, markerY + (lh - BULLET) / 2, BULLET, BULLET, p.fallback)
            }
            emitAll(item, contentIndent)
        }
        y += halfGap
    }

    private fun table(block: MdBlock.Table) {
        val cols = block.header.size
        if (cols == 0) return
        val padX = 4
        val resolve = { s: McStyledString -> s.resolveThemeColors(p.codeForeground, p.link) }
        val headerCells: List<McStyledString> = block.header.map(resolve)
        val rowCells: List<List<McStyledString>> = block.rows.map { row ->
            (0 until cols).map { c -> resolve(row.getOrNull(c) ?: McStyledString.EMPTY) }
        }

        // Natural widths from unbounded single-line layouts; columns scale down proportionally on overflow.
        fun naturalOf(s: McStyledString): Int = engine.layout(s, Int.MAX_VALUE, singleLine = true).width
        val natural = IntArray(cols) { c ->
            max(naturalOf(headerCells[c]), rowCells.maxOfOrNull { naturalOf(it[c]) } ?: 0)
        }
        val naturalSum = natural.sum().coerceAtLeast(1)
        val scale = if (naturalSum + cols * padX * 2 > maxW) {
            floor(((maxW - cols * padX * 2).coerceAtLeast(cols * 8).toDouble()) / naturalSum)
        } else 1.0
        val colW = IntArray(cols) { c -> max((natural[c] * scale).toInt(), 8) + padX * 2 }
        val tableW = colW.sum().coerceAtMost(maxW)

        val startY = y
        var x = 0
        for ((c, cell) in headerCells.withIndex()) { // header row
            cmds += Cmd.Text(engine.layout(cell, colW[c] - padX * 2, singleLine = true), x + padX, y, p.fallback)
            x += colW[c]
        }
        y += lh
        cmds += Cmd.HLine(0, y, tableW, 1, p.headingAccent)
        for (row in rowCells) {
            x = 0
            for ((c, cell) in row.withIndex()) {
                cmds += Cmd.Text(engine.layout(cell, colW[c] - padX * 2, singleLine = true), x + padX, y + 1, p.fallback)
                x += colW[c]
            }
            y += lh + 1
        }
        cmds += Cmd.HLine(0, y, tableW, 1, p.ruleLine)
        cmds += Cmd.VLine(0, startY, y - startY, 1, p.ruleLine)
        var vx = 0
        for (c in 0 until cols) {
            vx += colW[c]
            cmds += Cmd.VLine(vx.coerceAtMost(tableW - 1), startY, y - startY, 1, p.ruleLine)
        }
        y += gap
    }

    private fun rule() {
        cmds += Cmd.HLine(0, y + lh / 2, maxW, 1, p.ruleLine)
        y += lh + gap
    }

    companion object {
        private const val QUOTE_INDENT = 12
        private const val LIST_INDENT = 16
        private const val TASK_BOX = 8
        private const val BULLET = 3

        /** Resolves semantic roles (code/link) into concrete theme colors before layout. */
        fun McStyledString.resolveThemeColors(codeColor: Color, linkColor: Color): McStyledString =
            if (spans.none { it.style.semantic != null }) this
            else McStyledString(text, spans.map { it.copy(style = it.style.withResolvedSemantic(codeColor, linkColor)) })
    }
}

private fun DrawScope.drawCmd(cmd: Cmd, engine: McTextEngine) {
    when (cmd) {
        is Cmd.Text -> translate(cmd.x.toFloat(), cmd.y.toFloat()) {
            with(engine) { paint(cmd.layout, cmd.fallback) }
        }
        is Cmd.Box -> drawRect(cmd.color, Offset(cmd.x.toFloat(), cmd.y.toFloat()), Size(cmd.w.toFloat(), cmd.h.toFloat()))
        is Cmd.HLine -> drawRect(cmd.color, Offset(cmd.x.toFloat(), cmd.y.toFloat()), Size(cmd.w.toFloat(), cmd.thickness.toFloat()))
        is Cmd.VLine -> drawRect(cmd.color, Offset(cmd.x.toFloat(), cmd.y.toFloat()), Size(cmd.thickness.toFloat(), cmd.h.toFloat()))
        is Cmd.TaskMark -> {
            val s = cmd.size.toFloat()
            drawRectOutline(cmd.x, cmd.y, cmd.size, cmd.boxColor)
            if (cmd.checked) { // check mark: two short strokes inside the box
                drawLine(cmd.color, Offset(cmd.x + s * 0.15f, cmd.y + s * 0.55f), Offset(cmd.x + s * 0.4f, cmd.y + s * 0.85f), 1f)
                drawLine(cmd.color, Offset(cmd.x + s * 0.4f, cmd.y + s * 0.85f), Offset(cmd.x + s * 0.9f, cmd.y + s * 0.15f), 1f)
            }
        }
    }
}

private fun DrawScope.drawRectOutline(x: Int, y: Int, size: Int, color: Color) {
    val s = size.toFloat()
    val t = 1f
    drawRect(color, Offset(x.toFloat(), y.toFloat()), Size(s, t))                  // top
    drawRect(color, Offset(x.toFloat(), y + size - 1f), Size(s, t))                 // bottom
    drawRect(color, Offset(x.toFloat(), y.toFloat()), Size(t, s))                   // left
    drawRect(color, Offset(x + size - 1f, y.toFloat()), Size(t, s))                 // right
}

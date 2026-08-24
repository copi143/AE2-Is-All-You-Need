package minecraftx.compose.markdown

import minecraftx.compose.text.McSemantic
import minecraftx.compose.text.McSpanStyle
import minecraftx.compose.text.McStyledString
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.getTextInNode
import org.intellij.markdown.flavours.gfm.GFMElementTypes
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.flavours.gfm.GFMTokenTypes
import org.intellij.markdown.parser.MarkdownParser

/**
 * Block-level IR produced by parsing GFM source with the JetBrains markdown parser. Inline content
 * is already flattened into [McStyledString] spans (bold/italic/strike/code/link styling applied).
 */
sealed interface MdBlock {
    data class Paragraph(val styled: McStyledString) : MdBlock

    /** ATX/SETEXT heading; [level] 1..6 (visual weight applied by the renderer). */
    data class Heading(val level: Int, val styled: McStyledString) : MdBlock

    data class CodeBlock(val lines: List<String>) : MdBlock

    /** Blockquote containing nested blocks (may nest arbitrarily). */
    data class Quote(val inner: List<MdBlock>) : MdBlock

    /**
     * Ordered/unordered list. [checkboxes] parallels [items]: null = plain item, true/false =
     * GFM task item state.
     */
    data class MdList(val ordered: Boolean, val items: List<List<MdBlock>>, val checkboxes: List<Boolean?>) : MdBlock

    /** GFM table; all cells are single-line inline strings (alignment markers ignored for now). */
    data class Table(val header: List<McStyledString>, val rows: List<List<McStyledString>>) : MdBlock

    /** Horizontal rule (`---`). */
    data object Rule : MdBlock
}

/** Markdown source -> List<[MdBlock]> using the GFM flavour (tables, strikethrough, task lists). */
object MdParser {
    private val parser = MarkdownParser(GFMFlavourDescriptor())

    fun parse(src: String): List<MdBlock> = try {
        parser.parse(MarkdownElementTypes.MARKDOWN_FILE, src).children.mapNotNull { block(it, src) }
    } catch (_: org.intellij.markdown.MarkdownParsingException) {
        listOf(MdBlock.Paragraph(McStyledString(src)))
    }

    private fun block(node: ASTNode, src: String): MdBlock? = when (node.type) {
        MarkdownElementTypes.PARAGRAPH -> MdBlock.Paragraph(collectInline(node, src))

        MarkdownElementTypes.ATX_1 -> heading(1, node, src)
        MarkdownElementTypes.ATX_2 -> heading(2, node, src)
        MarkdownElementTypes.ATX_3 -> heading(3, node, src)
        MarkdownElementTypes.ATX_4 -> heading(4, node, src)
        MarkdownElementTypes.ATX_5 -> heading(5, node, src)
        MarkdownElementTypes.ATX_6 -> heading(6, node, src)
        MarkdownElementTypes.SETEXT_1 -> heading(1, node, src)
        MarkdownElementTypes.SETEXT_2 -> heading(2, node, src)

        MarkdownElementTypes.CODE_FENCE -> MdBlock.CodeBlock(fenceLines(node, src))
        MarkdownElementTypes.CODE_BLOCK -> MdBlock.CodeBlock(rawLines(node, src))

        MarkdownElementTypes.BLOCK_QUOTE -> MdBlock.Quote(node.children.mapNotNull { block(it, src) })

        MarkdownElementTypes.UNORDERED_LIST -> list(node, src, ordered = false)
        MarkdownElementTypes.ORDERED_LIST -> list(node, src, ordered = true)

        GFMElementTypes.TABLE -> table(node, src)

        MarkdownTokenTypes.HORIZONTAL_RULE -> MdBlock.Rule

        else -> null // HTML blocks, link definitions, stray tokens: skipped for now
    }

    private fun heading(level: Int, node: ASTNode, src: String): MdBlock =
        MdBlock.Heading(level, collectInline(node, src))

    private fun fenceLines(node: ASTNode, src: String): List<String> {
        val contents = node.children.filter { it.type == MarkdownTokenTypes.CODE_FENCE_CONTENT }
        if (contents.isEmpty()) return emptyList()
        val text = src.substring(contents.first().startOffset, contents.last().endOffset)
        return text.split('\n')
    }

    private fun rawLines(node: ASTNode, src: String): List<String> =
        node.getTextInNode(src).toString().split('\n').map { it.trimEnd() }

    private fun list(node: ASTNode, src: String, ordered: Boolean): MdBlock {
        val items = mutableListOf<List<MdBlock>>()
        val boxes = mutableListOf<Boolean?>()
        for (li in node.children) {
            if (li.type != MarkdownElementTypes.LIST_ITEM) continue
            boxes += li.children.firstOrNull { it.type == GFMTokenTypes.CHECK_BOX }
                ?.let { !it.getTextInNode(src).contains(' ') } // "[x]" checked vs "[ ]"
            val contentNodes = li.children.filter {
                it.type != GFMTokenTypes.CHECK_BOX &&
                    it.type != MarkdownTokenTypes.LIST_BULLET &&
                    it.type != MarkdownTokenTypes.LIST_NUMBER
            }
            items += contentNodes.mapNotNull { block(it, src) }
        }
        return MdBlock.MdList(ordered, items, boxes)
    }

    private fun table(node: ASTNode, src: String): MdBlock? {
        val headerNode = node.children.firstOrNull { it.type == GFMElementTypes.HEADER } ?: return null
        val header = headerNode.children.filter { it.type == GFMTokenTypes.CELL }.map { collectInline(it, src) }
        val rows = node.children.filter { it.type == GFMElementTypes.ROW }.map { row ->
            row.children.filter { it.type == GFMTokenTypes.CELL }.map { collectInline(it, src) }
        }
        if (header.isEmpty()) return null
        return MdBlock.Table(header, rows)
    }

    // ---- inline collection ---------------------------------------------------------------

    private fun collectInline(node: ASTNode, src: String, style: McSpanStyle = McSpanStyle.DEFAULT): McStyledString {
        val sb = StringBuilder()
        val spans = mutableListOf<McStyledString.Span>()

        fun walkInnerLink(n: ASTNode, source: String, target: StringBuilder, s: McSpanStyle) {
            if (n.children.isEmpty() && n.type != MarkdownElementTypes.LINK_TEXT) {
                target.append(n.getTextInNode(source))
            } else {
                n.children.forEach { walkInnerLink(it, source, target, s) }
            }
        }

        fun walk(n: ASTNode, s: McSpanStyle) {
            when (n.type) {
                MarkdownElementTypes.EMPH -> n.children.forEach { walk(it, s.merge(EMPH_STYLE)) }
                MarkdownElementTypes.STRONG -> n.children.forEach { walk(it, s.merge(STRONG_STYLE)) }
                GFMElementTypes.STRIKETHROUGH -> n.children.forEach { walk(it, s.merge(STRIKE_STYLE)) }

                MarkdownElementTypes.CODE_SPAN -> {
                    val start = sb.length
                    n.children.filter { it.type == MarkdownTokenTypes.CODE_LINE }.forEach {
                        sb.append(it.getTextInNode(src))
                    }
                    if (sb.length > start) spans += McStyledString.Span(start, sb.length, s.merge(CODE_STYLE))
                }

                MarkdownElementTypes.INLINE_LINK,
                MarkdownElementTypes.FULL_REFERENCE_LINK,
                MarkdownElementTypes.SHORT_REFERENCE_LINK,
                -> {
                    val textNode = n.children.firstOrNull { it.type == MarkdownElementTypes.LINK_TEXT }
                    val start = sb.length
                    if (textNode != null) {
                        textNode.children.forEach { walkInnerLink(it, src, sb, s) }
                    } else {
                        sb.append(n.getTextInNode(src))
                    }
                    if (sb.length > start) spans += McStyledString.Span(start, sb.length, s.merge(LINK_STYLE))
                }

                MarkdownElementTypes.AUTOLINK,
                GFMTokenTypes.GFM_AUTOLINK,
                MarkdownTokenTypes.EMAIL_AUTOLINK,
                -> {
                    val start = sb.length
                    sb.append(n.getTextInNode(src))
                    if (sb.length > start) spans += McStyledString.Span(start, sb.length, s.merge(LINK_STYLE))
                }

                MarkdownElementTypes.IMAGE -> {
                    // v1: render the alt text in the code style; no image pipeline yet.
                    val alt = n.children.firstOrNull { it.type == MarkdownElementTypes.LINK_TEXT }
                    val start = sb.length
                    sb.append(alt?.getTextInNode(src)?.toString()?.takeIf { it.isNotBlank() } ?: "image")
                    if (sb.length > start) spans += McStyledString.Span(start, sb.length, s.merge(CODE_STYLE))
                }

                MarkdownTokenTypes.TEXT,
                MarkdownTokenTypes.WHITE_SPACE,
                MarkdownTokenTypes.SINGLE_QUOTE,
                MarkdownTokenTypes.DOUBLE_QUOTE,
                MarkdownTokenTypes.LPAREN,
                MarkdownTokenTypes.RPAREN,
                MarkdownTokenTypes.LBRACKET,
                MarkdownTokenTypes.RBRACKET,
                MarkdownTokenTypes.LT,
                MarkdownTokenTypes.GT,
                MarkdownTokenTypes.COLON,
                MarkdownTokenTypes.EXCLAMATION_MARK,
                MarkdownTokenTypes.URL,
                MarkdownTokenTypes.HARD_LINE_BREAK,
                MarkdownTokenTypes.EOL,
                -> {
                    var piece = n.getTextInNode(src).toString()
                    if (n.type == MarkdownTokenTypes.EOL || n.type == MarkdownTokenTypes.HARD_LINE_BREAK) {
                        piece = " " // soft breaks collapse to a space; wrapping is the engine's job
                    }
                    if (piece.isNotEmpty()) {
                        val start = sb.length
                        sb.append(piece)
                        if (!s.isDefault) spans += McStyledString.Span(start, sb.length, s)
                    }
                }

                // Delimiter tokens (EMPH `*`/`_`, BACKTICK, TILDE, ...) must NOT render as text:
                // they fall through to the descend branch and vanish (no children).
                else -> n.children.forEach { walk(it, s) }
            }
        }

        node.children.forEach { walk(it, style) }
        return McStyledString(sb.toString(), spans)
    }

    private val EMPH_STYLE = McSpanStyle(italic = true)
    private val STRONG_STYLE = McSpanStyle(bold = true)
    private val STRIKE_STYLE = McSpanStyle(strikethrough = true)
    private val LINK_STYLE = McSpanStyle(underline = true, semantic = McSemantic.LINK)
    private val CODE_STYLE = McSpanStyle(semantic = McSemantic.CODE)
}

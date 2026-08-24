package minecraftx.compose.text

import net.minecraft.network.chat.Component
import net.minecraft.network.chat.FormattedText
import net.minecraft.network.chat.Style
import java.util.Optional

/**
 * Converters between MC's [Component] tree and the engine-agnostic [McStyledString] IR, plus
 * visual-field mappings between [Style] and [McSpanStyle].
 */

/** Flattens a Component tree into a McStyledString, resolving MC's style inheritance. */
fun Component.toStyledString(): McStyledString {
    val sb = StringBuilder()
    val spans = mutableListOf<McStyledString.Span>()

    fun walk(node: Component, inherited: Style) {
        val effective = inheritVisual(inherited, node.style)
        val start = sb.length
        // Only THIS node's contents: getString() would re-append the whole subtree. visit() also
        // expands translatable/keybind contents the way the vanilla font renderer would.
        node.contents.visit(
            { _, text ->
                sb.append(text)
                Optional.empty<Unit>()
            },
            Style.EMPTY,
        )
        if (sb.length > start) {
            effective.toMcSpanStyleOrNull()?.let { spans += McStyledString.Span(start, sb.length, it) }
        }
        for (sibling in node.siblings) walk(sibling, effective)
    }

    walk(this, Style.EMPTY)
    return McStyledString(sb.toString(), spans)
}

/** A flat (text, MC style) segment — the engine-internal layout input. */
internal class McRun(val text: String, val style: Style, val visual: McSpanStyle?)

/**
 * Splits a McStyledString into non-overlapping runs of uniform MC style. Ranges outside any span
 * keep the default (empty) style so the paint phase can apply its fallback color.
 */
internal fun McStyledString.toMcRuns(base: Style = Style.EMPTY): List<McRun> {
    if (text.isEmpty()) return emptyList()
    if (spans.isEmpty()) return listOf(McRun(text, base, null))

    val out = mutableListOf<McRun>()
    var cursor = 0
    for (range in spans.sortedBy { it.start }) {
        val start = maxOf(range.start, cursor)
        val end = minOf(range.end, text.length)
        if (start > cursor) out += McRun(text.substring(cursor, start), base, null)
        if (end > start) out += McRun(text.substring(start, end), range.style.toMcStyle(base), range.style)
        cursor = maxOf(cursor, end)
    }
    if (cursor < text.length) out += McRun(text.substring(cursor), base, null)
    return out.filter { it.text.isNotEmpty() }
}

private fun Style.toMcSpanStyleOrNull(): McSpanStyle? {
    val bold = isBold
    val italic = isItalic
    val underlined = isUnderlined
    val strikethrough = isStrikethrough
    if (color == null && bold == null && italic == null &&
        underlined == null && strikethrough == null
    ) {
        return null
    }
    return McSpanStyle(
        color = color?.let { androidx.compose.ui.graphics.Color(it.value) },
        bold = bold,
        italic = italic,
        underline = underlined,
        strikethrough = strikethrough,
    )
}

/** Resolves child-vs-parent inheritance for the visual fields (child wins when set). */
private fun inheritVisual(parent: Style, child: Style): Style {
    var s = child
    if (child.color == null) s = s.withColor(parent.color)
    if (child.isBold == null) s = s.withBold(parent.isBold)
    if (child.isItalic == null) s = s.withItalic(parent.isItalic)
    if (child.isUnderlined == null) s = s.withUnderlined(parent.isUnderlined)
    if (child.isStrikethrough == null) s = s.withStrikethrough(parent.isStrikethrough)
    return s
}

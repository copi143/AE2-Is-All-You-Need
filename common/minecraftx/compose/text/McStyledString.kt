package minecraftx.compose.text

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import net.minecraft.network.chat.Style

/** Semantic style roles produced by markdown parsing; the renderer resolves them via the theme. */
enum class McSemantic { CODE, LINK }

/**
 * Engine-agnostic visual subset of character styling (the fields every [McTextEngine] must honor).
 * Null fields mean "unspecified": they inherit from the surrounding context or the paint-time
 * fallback color.
 */
data class McSpanStyle(
    val color: Color? = null,
    val bold: Boolean? = null,
    val italic: Boolean? = null,
    val underline: Boolean? = null,
    val strikethrough: Boolean? = null,
    val semantic: McSemantic? = null,
) {
    val isDefault: Boolean
        get() = color == null && bold == null && italic == null &&
            underline == null && strikethrough == null && semantic == null

    /** [other]'s specified fields win over ours (used to nest markdown emphasis etc.). */
    fun merge(other: McSpanStyle): McSpanStyle = McSpanStyle(
        color = other.color ?: color,
        bold = other.bold ?: bold,
        italic = other.italic ?: italic,
        underline = other.underline ?: underline,
        strikethrough = other.strikethrough ?: strikethrough,
        semantic = other.semantic ?: semantic,
    )

    /** Replaces an unresolved [semantic] role with a concrete color (theme-time resolution). */
    fun withResolvedSemantic(codeColor: Color, linkColor: Color): McSpanStyle = when (semantic) {
        McSemantic.CODE -> merge(McSpanStyle(color = codeColor))
        McSemantic.LINK -> merge(McSpanStyle(color = linkColor))
        null -> this
    }

    fun toMcStyle(base: Style = Style.EMPTY): Style {
        var s = base
        color?.let { s = s.withColor(it.toArgb()) }
        bold?.let { s = s.withBold(it) }
        italic?.let { s = s.withItalic(it) }
        underline?.let { s = s.withUnderlined(it) }
        strikethrough?.let { s = s.withStrikethrough(it) }
        return s
    }

    companion object {
        val DEFAULT = McSpanStyle()
    }
}

/**
 * Minimal styled-string IR shared by all engines and producers ([net.minecraft.network.chat.Component]
 * conversion, markdown rendering). Spans are expected to be non-overlapping and sorted by start;
 * overlapping input spans are resolved left-to-right (a later span only covers unconsumed text).
 */
class McStyledString(val text: String, val spans: List<Span> = emptyList()) {

    data class Span(val start: Int, val end: Int, val style: McSpanStyle)

    operator fun plus(other: McStyledString): McStyledString {
        if (text.isEmpty()) return other
        if (other.text.isEmpty()) return this
        val offset = text.length
        return McStyledString(
            text + other.text,
            spans + other.spans.map { it.copy(start = it.start + offset, end = it.end + offset) },
        )
    }

    companion object {
        val EMPTY = McStyledString("")
    }
}

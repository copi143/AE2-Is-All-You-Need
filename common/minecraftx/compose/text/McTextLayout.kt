package minecraftx.compose.text

/**
 * One same-styled stretch of text inside a laid-out line. [style] may be null (= inherit the paint
 * fallback color); [x] is the run's offset from the left edge of its line in px.
 */
class StyledRun(val x: Int, val text: String, val style: McSpanStyle?)

/** A single wrapped line of a [McTextLayout]. */
class TextLine(val runs: List<StyledRun>, val width: Int)

/**
 * Engine-agnostic result of laying out a [McStyledString]. Immutable and cacheable: engines
 * produce it in [McTextEngine.layout] and consume it again in [McTextEngine.paint], so tables can
 * measure twice and big documents can reflow without re-parsing.
 */
class McTextLayout(val lines: List<TextLine>, val lineHeight: Int) {

    val width: Int = lines.maxOfOrNull { it.width } ?: 0
    val height: Int = lines.size * lineHeight

    companion object {
        val EMPTY = McTextLayout(emptyList(), 0)
    }
}

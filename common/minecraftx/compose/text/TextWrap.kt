package minecraftx.compose.text

object TextWrap {

    fun isBreakableAfter(cp: Int): Boolean = when {
        cp == ' '.code -> true
        cp in 0x2E80..0x9FFF -> true
        cp in 0xAC00..0xD7AF -> true
        cp in 0xF900..0xFAFF -> true
        cp in 0xFF00..0xFFEF -> true
        cp >= 0x20000 -> true
        else -> false
    }

    fun layout(
        text: McStyledString,
        maxWidth: Int,
        singleLine: Boolean,
        lineHeight: Int,
        measureCp: (codePoint: Int, style: McSpanStyle?) -> Int,
    ): McTextLayout {
        val emptyLine = McTextLayout(listOf(TextLine(emptyList(), 0)), lineHeight)
        if (text.text.isEmpty()) return emptyLine
        if (maxWidth <= 0) return McTextLayout.EMPTY.takeIf { !singleLine } ?: emptyLine

        val sb = StringBuilder()
        val cps = ArrayList<Int>(text.text.length)
        val starts = ArrayList<Int>(text.text.length + 1)
        val styles = ArrayList<McSpanStyle?>(text.text.length)
        val widths = ArrayList<Int>(text.text.length)

        for (run in text.toVisualRuns()) {
            val s = run.text
            var i = 0
            while (i < s.length) {
                val cp = s.codePointAt(i)
                val piece = String(Character.toChars(cp))
                cps += cp
                starts += sb.length
                styles += run.style
                widths += measureCp(cp, run.style)
                sb.append(piece)
                i += Character.charCount(cp)
            }
        }
        starts += sb.length
        val n = cps.size

        val lines = mutableListOf<TextLine>()
        var lineStart = 0
        var x = 0
        var lastBreak = -1
        var breakX = 0

        fun widthBetween(from: Int, to: Int): Int {
            var w = 0
            for (i in from until to) w += widths[i]
            return w
        }

        fun emitLine(from: Int, toRaw: Int, rawWidth: Int) {
            var to = toRaw
            var w = rawWidth
            while (to > from && cps[to - 1] == ' '.code) {
                w -= widths[to - 1]
                to--
            }
            val outRuns = mutableListOf<StyledRun>()
            var g = from
            var gx = 0
            while (g < to) {
                val st = styles[g]
                var ge = g + 1
                while (ge < to && styles[ge] == st) ge++
                val txt = sb.substring(starts[g], starts[ge])
                outRuns += StyledRun(gx, txt, st)
                for (i in g until ge) gx += widths[i]
                g = ge
            }
            lines += TextLine(outRuns, w.coerceAtLeast(gx))
        }

        var i = 0
        while (i < n) {
            val cp = cps[i]
            if (cp == '\n'.code) {
                if (!singleLine) {
                    emitLine(lineStart, i, x)
                    lineStart = i + 1
                    x = 0
                    lastBreak = -1
                    breakX = 0
                }
                i++
                continue
            }
            val advance = widths[i]
            if (x + advance > maxWidth && i > lineStart) {
                if (singleLine) {
                    emitLine(lineStart, i, x)
                    return McTextLayout(lines, lineHeight)
                }
                if (cp == ' '.code) {
                    emitLine(lineStart, i, x)
                    var next = i + 1
                    while (next < n && cps[next] == ' '.code) next++
                    lineStart = next
                    x = 0
                    lastBreak = -1
                    breakX = 0
                    i = next
                    continue
                }
                if (lastBreak > lineStart) {
                    emitLine(lineStart, lastBreak, breakX)
                    var next = lastBreak
                    while (next < n && cps[next] == ' '.code) next++
                    lineStart = next
                    x = widthBetween(lineStart, i)
                } else {
                    emitLine(lineStart, i, x)
                    lineStart = i
                    x = 0
                }
                lastBreak = -1
                breakX = 0
            }
            x += advance
            if (isBreakableAfter(cp)) {
                lastBreak = i + 1
                breakX = x
            }
            i++
        }
        emitLine(lineStart, n, x)
        return McTextLayout(lines, lineHeight)
    }
}

internal class VisualRun(val text: String, val style: McSpanStyle?)

internal fun McStyledString.toVisualRuns(): List<VisualRun> {
    if (text.isEmpty()) return emptyList()
    if (spans.isEmpty()) return listOf(VisualRun(text, null))
    val out = mutableListOf<VisualRun>()
    var cursor = 0
    for (range in spans.sortedBy { it.start }) {
        val start = maxOf(range.start, cursor)
        val end = minOf(range.end, text.length)
        if (start > cursor) out += VisualRun(text.substring(cursor, start), null)
        if (end > start) out += VisualRun(text.substring(start, end), range.style)
        cursor = maxOf(cursor, end)
    }
    if (cursor < text.length) out += VisualRun(text.substring(cursor), null)
    return out.filter { it.text.isNotEmpty() }
}

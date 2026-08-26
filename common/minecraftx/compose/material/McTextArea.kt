package minecraftx.compose.material

import androidx.compose.ui.graphics.toArgb
import allyouneed.client.compose.platform.LocalMcTextInputService
import allyouneed.client.compose.platform.McGraphics
import allyouneed.client.compose.platform.McScissor
import allyouneed.client.compose.platform.ScrollState
import allyouneed.client.compose.platform.TextNavigation
import allyouneed.client.compose.platform.rememberFrameCallback
import allyouneed.client.compose.platform.rememberScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.input.EditCommand
import androidx.compose.ui.text.input.EditProcessor
import androidx.compose.ui.text.input.SetSelectionCommand
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import minecraftx.compose.foundation.mcScroll
import minecraftx.compose.text.LocalMcTextEngine
import minecraftx.compose.text.McStyledString
import minecraftx.compose.text.McTextEngine
import minecraftx.compose.theme.McColorScheme
import minecraftx.compose.theme.McTheme
import net.minecraft.client.gui.GuiGraphics
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Multi-line counterpart to [McTextField]: a bordered text area with soft wrapping, a blinking
 * caret, a selection highlight spanning rows, an IME composition underline and vertical scrolling
 * that keeps the caret visible (smooth wheel via [ScrollState], plus a slim scrollbar).
 *
 * Input flows through [LocalMcTextInputService] exactly like [McTextField] (same EditCommand
 * pipeline). Because soft wrapping makes "row" a visual concept the service cannot know about,
 * Up/Down/Home/End are routed back into this field through the service's [TextNavigation] hook.
 *
 * @param value the controlled editing state (text + selection + composition).
 * @param onValueChange called with every edit; update [value] back from here.
 * @param imeEnabled whether this field accepts IME text; false switches to pure ASCII input.
 * @param placeholder muted hint drawn on the first row while the field is empty.
 */
@Composable
fun McTextArea(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    imeEnabled: Boolean = true,
    width: Int = 200,
    height: Int = 80,
    placeholder: String? = null,
    colors: McColorScheme = McTheme.colors,
) {
    val engine = LocalMcTextEngine.current
    val service = LocalMcTextInputService.current
    val id = remember { nextFieldId() }
    val processor = remember { EditProcessor().apply { reset(value, null) } }

    var internal by remember { mutableStateOf(value) }
    var blinkTick by remember { mutableIntStateOf(0) }
    rememberFrameCallback { blinkTick++ }
    // Selection anchor for shift-extensions of the navigation hook (Up/Down/Home/End with shift).
    var anchor by remember { mutableIntStateOf(0) }
    val scrollState = rememberScrollState()

    val isActive = service.activeSession == id
    // Per-field indirection so closures that outlive one recomposition (the session's navigation
    // hook and the pointer-input handler) always read the latest layout.
    val layoutHolder = remember { LayoutHolder() }

    // Re-sync the internal buffer when the value was changed from outside the field.
    LaunchedEffect(value) {
        if (value != internal) {
            processor.reset(value, null)
            internal = value
        }
    }

    val contentWidth = width - TEXT_PAD_LEFT - TEXT_AREA_PAD_RIGHT
    val rowHeightPx = engine.lineHeight
    val viewHeight = (height - 2 * TEXT_AREA_PAD_V).coerceAtLeast(rowHeightPx)
    // Rebuilt unconditionally so the navigation hook and the draw pass always see current text.
    val layout = buildTextAreaLayout(engine, internal.text, contentWidth)
    scrollState.maxScroll = max(0, layout.rows.size * rowHeightPx - viewHeight).toFloat()
    if (scrollState.display > scrollState.maxScroll) scrollState.seek(scrollState.maxScroll)
    layoutHolder.layout = layout

    // Keep the caret visible while typing / moving.
    LaunchedEffect(internal) {
        anchor = internal.selection.max
        val caretTop = layout.rowForOffset(internal.selection.max) * rowHeightPx.toFloat()
        if (caretTop < scrollState.display) {
            scrollState.seek(caretTop)
        } else if (caretTop + rowHeightPx > scrollState.display + viewHeight) {
            scrollState.seek(caretTop + rowHeightPx - viewHeight)
        }
    }

    fun applyEdit(commands: List<EditCommand>) {
        val newValue = processor.apply(commands)
        internal = newValue
        onValueChange(newValue)
    }

    // (Re-)register as the active input session when focus arrives; release it on blur.
    LaunchedEffect(isActive, imeEnabled) {
        if (isActive) {
            service.registerSession(
                id = id,
                imeEnabled = imeEnabled,
                singleLine = false,
                valueProvider = { internal },
                onEditCommand = ::applyEdit,
                onImeActionPerformed = {},
                navigation = object : TextNavigation {
                    override fun moveVertically(deltaRows: Int, select: Boolean) {
                        val rows = layoutHolder.layout.rows
                        val from = layoutHolder.layout.rowForOffset(internal.selection.max)
                        val fromRow = rows[from]
                        val desiredX =
                            engine.widthOf(fromRow.text.substring(0, (internal.selection.max - fromRow.start).coerceIn(0, fromRow.text.length)))
                                .toFloat()
                        val targetRow = rows[(from + deltaRows).coerceIn(0, rows.lastIndex)]
                        applySelection(select, targetRow.start + engine.indexAtWidth(targetRow.text, max(0, desiredX.roundToInt())))
                    }

                    override fun toLineBoundary(toStart: Boolean, select: Boolean) {
                        val layout = layoutHolder.layout
                        val row = layout.rows[layout.rowForOffset(internal.selection.max)]
                        applySelection(select, if (toStart) row.start else row.end)
                    }

                    private fun applySelection(select: Boolean, target: Int) {
                        if (!select) anchor = target
                        applyEdit(listOf(SetSelectionCommand(min(anchor, target), max(anchor, target))))
                    }
                },
            )
        } else {
            service.unregisterSession(id)
        }
    }

    Box(
        modifier
            .size(width.dp, height.dp)
            .pointerHoverIcon(PointerIcon.Text)
            .mcScroll(scrollState)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type != PointerEventType.Press) continue
                        val change = event.changes.firstOrNull() ?: continue
                        if (change.isConsumed) continue
                        change.consume()
                        service.activate(id)
                        val press = change.position
                        val rows = layoutHolder.layout.rows
                        val rowIdx = ((press.y - TEXT_AREA_PAD_V + scrollState.display) / rowHeightPx).toInt()
                            .coerceIn(0, rows.lastIndex)
                        val row = rows[rowIdx]
                        val clickX = (press.x - TEXT_PAD_LEFT).roundToInt().coerceAtLeast(0)
                        val offset = row.start + engine.indexAtWidth(row.text, clickX)
                        applyEdit(listOf(SetSelectionCommand(offset, offset)))
                    }
                }
            }
            .drawBehind {
                val g = McGraphics.current ?: return@drawBehind
                drawArea(
                    g = g,
                    engine = engine,
                    value = internal,
                    layout = layout,
                    scrollY = scrollState.display,
                    viewHeight = viewHeight,
                    blinkTick = blinkTick,
                    focused = isActive,
                    rowHeightPx = rowHeightPx,
                    width = width,
                    height = height,
                    placeholder = placeholder,
                    colors = colors,
                )
            },
    ) {
        if (scrollState.maxScroll > 0f) {
            McScrollbar(
                state = scrollState,
                modifier = Modifier.align(Alignment.CenterEnd).size(width = 4.dp, height = (height - 2).dp),
                trackWidth = 4.dp,
                barWidth = 2.dp,
            )
        }
    }
}

/**
 * Stable indirection so closures that outlive one recomposition (the session's navigation hook and
 * the pointer-input handler) always read the latest layout.
 */
private class LayoutHolder {
    lateinit var layout: TextAreaLayout
}

private fun DrawScope.drawArea(
    g: GuiGraphics,
    engine: McTextEngine,
    value: TextFieldValue,
    layout: TextAreaLayout,
    scrollY: Float,
    viewHeight: Int,
    blinkTick: Int,
    focused: Boolean,
    rowHeightPx: Int,
    width: Int,
    height: Int,
    placeholder: String?,
    colors: McColorScheme,
) {
    val border = if (focused) colors.inputBorderFocused else colors.inputBorder
    g.fill(0, 0, width, height, border.toArgb())
    g.fill(1, 1, width - 1, height - 1, colors.inputBackground.toArgb())

    val matrix = g.pose().last().pose()
    val nodeX = matrix.m30()
    val nodeY = matrix.m31()
    val scaleX = matrix.m00()
    val scaleY = matrix.m11()
    // Mirror the safe min/max clip construction used by McText (handles flipped / zoomed matrices).
    val clipLeft = max(nodeX, nodeX + 1f * scaleX)
    val clipTop = max(nodeY, nodeY + 1f * scaleY)
    val clipRight = minOf(nodeX + width * scaleX, nodeX + (width - 1) * scaleX)
    val clipBottom = minOf(nodeY + height * scaleY, nodeY + (height - 1) * scaleY)
    if (clipRight > clipLeft && clipBottom > clipTop) {
        McScissor.push(g, clipLeft.toInt(), clipTop.toInt(), clipRight.toInt(), clipBottom.toInt())
        try {
            drawRows(g, engine, value, layout, scrollY, viewHeight, blinkTick, focused, rowHeightPx, placeholder, colors)
        } finally {
            McScissor.pop(g)
        }
    }
}

private fun DrawScope.drawRows(
    g: GuiGraphics,
    engine: McTextEngine,
    value: TextFieldValue,
    layout: TextAreaLayout,
    scrollY: Float,
    viewHeight: Int,
    blinkTick: Int,
    focused: Boolean,
    rowHeightPx: Int,
    placeholder: String?,
    colors: McColorScheme,
) {
    val firstRow = max(0, ((scrollY - TEXT_AREA_PAD_V) / rowHeightPx).toInt())
    val lastRow = min(layout.rows.lastIndex, ((scrollY + viewHeight) / rowHeightPx).toInt() + 1)

    for (i in firstRow..lastRow) {
        val row = layout.rows[i]
        val y = rowY(i, scrollY, rowHeightPx)
        val rowLayout = engine.layout(McStyledString(row.text), Int.MAX_VALUE, true)
        translate(TEXT_PAD_LEFT.toFloat(), y.toFloat()) {
            with(engine) { paint(rowLayout, colors.textPrimary) }
        }
    }
    if (value.text.isEmpty() && !placeholder.isNullOrEmpty()) {
        val ph = engine.layout(McStyledString(placeholder), Int.MAX_VALUE, true)
        translate(TEXT_PAD_LEFT.toFloat(), rowY(0, scrollY, rowHeightPx).toFloat()) {
            with(engine) { paint(ph, colors.textSecondary) }
        }
    }

    // Selection highlight, intersected with every visible row.
    val selStart = value.selection.min
    val selEnd = value.selection.max
    if (!value.selection.collapsed) {
        for (i in firstRow..lastRow) {
            val row = layout.rows[i]
            val y = rowY(i, scrollY, rowHeightPx)
            val a = max(selStart, row.start)
            val b = min(selEnd, row.end)
            when {
                a < b -> g.fill(
                    xInRow(engine, row, a - row.start),
                    y,
                    xInRow(engine, row, b - row.start),
                    y + rowHeightPx,
                    colors.textSelection.toArgb(),
                )
                // A fully-covered empty row still gets a small highlight block.
                selStart <= row.start && selEnd >= row.end && row.text.isEmpty() ->
                    g.fill(TEXT_PAD_LEFT, y, TEXT_PAD_LEFT + 2, y + rowHeightPx, colors.textSelection.toArgb())
            }
        }
    }

    // Blinking caret at the collapsed cursor position.
    if (focused && value.selection.collapsed && (blinkTick / CARET_BLINK_FRAMES) % 2 == 0) {
        val caretRow = layout.rowForOffset(value.selection.min)
        if (caretRow in firstRow..lastRow) {
            val row = layout.rows[caretRow]
            val x = xInRow(engine, row, value.selection.min - row.start)
            g.fill(x, rowY(caretRow, scrollY, rowHeightPx), x + 2, rowY(caretRow, scrollY, rowHeightPx) + rowHeightPx, colors.textCaret.toArgb())
        }
    }

    // Composing-text underline (IME preedit region), intersected with each visible row.
    val composition = value.composition
    if (composition != null && !composition.collapsed) {
        for (i in firstRow..lastRow) {
            val row = layout.rows[i]
            val y = rowY(i, scrollY, rowHeightPx)
            val a = max(composition.min, row.start)
            val b = min(composition.max, row.end)
            if (a < b) {
                g.fill(
                    xInRow(engine, row, a - row.start),
                    y + rowHeightPx - 2,
                    xInRow(engine, row, b - row.start),
                    y + rowHeightPx,
                    colors.textCaret.toArgb(),
                )
            }
        }
    }
}

private fun rowY(rowIndex: Int, scrollY: Float, rowHeightPx: Int): Int =
    (TEXT_AREA_PAD_V + rowIndex * rowHeightPx - scrollY).roundToInt()

/** Screen-space x of a local character offset within its row. */
private fun xInRow(engine: McTextEngine, row: TextAreaLayout.Row, localOffset: Int): Int =
    TEXT_PAD_LEFT + engine.widthOf(row.text.substring(0, localOffset.coerceIn(0, row.text.length)))

/**
 * Soft-wrapped visual rows of the textarea buffer. A row is the half-open character range
 * `[start, end)` within the full text; hard newlines split rows and never belong to any row.
 */
internal class TextAreaLayout(val rows: List<Row>) {
    class Row(val start: Int, val end: Int, val text: String)

    /** Index of the visual row owning [offset]; an offset at a row end belongs to the next row. */
    fun rowForOffset(offset: Int): Int {
        for (i in rows.indices) {
            if (offset < rows[i].end || i == rows.lastIndex) return i
        }
        return 0
    }
}

/**
 * Wraps [text] into visual rows of at most [maxWidth] pixels ([McTextEngine.widthOf] semantics).
 * Prefers breaking after a space; long words break hard.
 */
internal fun buildTextAreaLayout(engine: McTextEngine, text: String, maxWidth: Int): TextAreaLayout {
    val rows = ArrayList<TextAreaLayout.Row>()
    var lineStart = 0
    for (i in 0..text.length) {
        if (i == text.length || text[i] == '\n') {
            appendWrappedRows(engine, text, lineStart, i, maxWidth, rows)
            lineStart = i + 1
        }
    }
    return TextAreaLayout(rows)
}

private fun appendWrappedRows(
    engine: McTextEngine,
    text: String,
    start: Int,
    endExcl: Int,
    maxWidth: Int,
    out: MutableList<TextAreaLayout.Row>,
) {
    var segStart = start
    do {
        val seg = text.substring(segStart, endExcl)
        if (seg.isEmpty() || engine.widthOf(seg) <= maxWidth) {
            out += TextAreaLayout.Row(segStart, endExcl, seg)
            return
        }
        var lo = 1
        var hi = seg.length - 1
        var fit = 1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (engine.widthOf(seg.substring(0, mid)) <= maxWidth) {
                fit = mid
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        val space = seg.lastIndexOf(' ', fit)
        val cut = if (space > 0) space + 1 else fit
        out += TextAreaLayout.Row(segStart, segStart + cut, seg.substring(0, cut))
        segStart += cut
    } while (segStart < endExcl)
}

private const val TEXT_PAD_LEFT = 3
private const val TEXT_AREA_PAD_V = 3
private const val TEXT_AREA_PAD_RIGHT = 9
private const val CARET_BLINK_FRAMES = 20

private val fieldIdCounter = java.util.concurrent.atomic.AtomicInteger()

private fun nextFieldId(): Int = fieldIdCounter.incrementAndGet()

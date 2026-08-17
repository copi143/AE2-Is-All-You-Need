package minecraftx.compose.material

import allyouneed.client.compose.platform.LocalMcTextInputService
import allyouneed.client.compose.platform.McGraphics
import allyouneed.client.compose.platform.McScissor
import allyouneed.client.compose.platform.rememberFrameCallback
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.input.EditProcessor
import androidx.compose.ui.text.input.SetSelectionCommand
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import minecraftx.compose.theme.McColorScheme
import minecraftx.compose.theme.McTheme
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * A browser-style single-line text field for the framework.
 *
 * Input goes through [LocalMcTextInputService], which translates Minecraft's raw key events into
 * compose [EditCommand]s — the same commands the OS IME would emit. Two input modes are supported,
 * exactly like toggling the OS input method in a browser:
 *
 *  - **`imeEnabled = true`** (default): committed text — direct key presses and IME commits alike —
 *    arrives via the screen's `charTyped` and is inserted through [androidx.compose.ui.text.input.CommitTextCommand].
 *  - **`imeEnabled = false`**: `charTyped` is ignored and printable keys are mapped from GLFW key
 *    codes with a US-layout shift table (see [McTextInputService]).
 *
 * Editing keys (backspace, delete, arrows with shift-selection, home/end, Enter, Ctrl+A) work in
 * both modes. The field draws with the Minecraft font, shows a blinking caret, a selection
 * highlight and a composing-text underline, and scrolls horizontally to keep the caret visible.
 *
 * Clicking the field moves the caret to the click position and takes input focus (only one field is
 * active at a time, tracked by the service's [McTextInputService.activeSession]).
 *
 * @param value the controlled editing state (text + selection + composition).
 * @param onValueChange called with every edit; update [value] back from here.
 * @param imeEnabled whether this field accepts IME text; false switches to pure ASCII input.
 * @param singleLine when true Enter fires [onImeActionPerformed], otherwise it inserts a newline.
 * @param placeholder muted hint drawn while the field is empty.
 */
@Composable
fun McTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    imeEnabled: Boolean = true,
    singleLine: Boolean = true,
    width: Int = 200,
    height: Int = 20,
    placeholder: String? = null,
    colors: McColorScheme = McTheme.colors,
    onImeActionPerformed: (ImeAction) -> Unit = {},
) {
    val font = Minecraft.getInstance().font
    val service = LocalMcTextInputService.current
    val id = remember { nextFieldId() }
    val processor = remember { EditProcessor().apply { reset(value, null) } }

    var internal by remember { mutableStateOf(value) }
    var scrollX by remember { mutableFloatStateOf(0f) }
    var blinkTick by remember { mutableIntStateOf(0) }
    rememberFrameCallback { blinkTick++ }

    val isActive = service.activeSession == id

    // Re-sync the internal buffer when the value was changed from outside the field.
    LaunchedEffect(value) {
        if (value != internal) {
            processor.reset(value, null)
            internal = value
        }
    }

    // (Re-)register as the active input session when focus arrives; release it on blur.
    LaunchedEffect(isActive, imeEnabled, singleLine) {
        if (isActive) {
            service.registerSession(
                id = id,
                imeEnabled = imeEnabled,
                singleLine = singleLine,
                valueProvider = { internal },
                onEditCommand = { commands ->
                    val newValue = processor.apply(commands)
                    internal = newValue
                    onValueChange(newValue)
                },
                onImeActionPerformed = onImeActionPerformed,
            )
        } else {
            service.unregisterSession(id)
        }
    }

    // Keep the caret visible while typing / moving.
    LaunchedEffect(internal) {
        scrollX = computeTargetScroll(font, internal, scrollX, width)
    }

    Box(
        modifier
            .size(width.dp, height.dp)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type != PointerEventType.Press) continue
                        val change = event.changes.firstOrNull() ?: continue
                        if (change.isConsumed) continue
                        val press = change.position
                        change.consume()
                        service.activate(id)
                        val clickPx = press.x - TEXT_PAD_LEFT + scrollX
                        val offset = if (internal.text.isEmpty()) {
                            0
                        } else {
                            font.plainSubstrByWidth(internal.text, max(0, clickPx.roundToInt())).length
                        }
                        val newValue = processor.apply(listOf(SetSelectionCommand(offset, offset)))
                        internal = newValue
                        onValueChange(newValue)
                    }
                }
            }
            .drawBehind {
                val g = McGraphics.current ?: return@drawBehind
                drawField(
                    g = g,
                    font = font,
                    value = internal,
                    scrollX = scrollX,
                    blinkTick = blinkTick,
                    focused = isActive,
                    width = width,
                    height = height,
                    placeholder = placeholder,
                    colors = colors,
                )
            },
    )
}

private fun drawField(
    g: GuiGraphics,
    font: Font,
    value: TextFieldValue,
    scrollX: Float,
    blinkTick: Int,
    focused: Boolean,
    width: Int,
    height: Int,
    placeholder: String?,
    colors: McColorScheme,
) {
    val border = if (focused) colors.inputBorderFocused else colors.inputBorder
    g.fill(0, 0, width, height, border.value.toInt())
    g.fill(1, 1, width - 1, height - 1, colors.inputBackground.value.toInt())

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
            drawContent(g, font, value, scrollX, blinkTick, focused, width, height, placeholder, colors)
        } finally {
            McScissor.pop(g)
        }
    }
}

private fun drawContent(
    g: GuiGraphics,
    font: Font,
    value: TextFieldValue,
    scrollX: Float,
    blinkTick: Int,
    focused: Boolean,
    width: Int,
    height: Int,
    placeholder: String?,
    colors: McColorScheme,
) {
    val text = value.text
    val scrollOffset = if (text.isEmpty()) 0 else font.plainSubstrByWidth(text, scrollX.roundToInt()).length
    val prefixWidth = font.width(text.substring(0, scrollOffset))
    val textY = (height - font.lineHeight) / 2

    // Visible text slice, offset so glyph `scrollOffset` sits at drawX.
    val visibleWidth = width - TEXT_PAD_LEFT - TEXT_PAD_RIGHT
    val drawX = TEXT_PAD_LEFT + prefixWidth - scrollX
    if (text.isEmpty()) {
        if (!placeholder.isNullOrEmpty()) {
            g.drawString(font, placeholder, drawX.roundToInt(), textY, colors.textSecondary.value.toInt(), false)
        }
    } else {
        val drawSub = text.substring(scrollOffset)
        val availPx = (visibleWidth + scrollX - prefixWidth).roundToInt().coerceAtLeast(0)
        val slice = if (font.width(drawSub) > availPx) font.plainSubstrByWidth(drawSub, availPx) else drawSub
        g.drawString(font, slice, drawX.roundToInt(), textY, colors.textPrimary.value.toInt(), false)
    }

    // Selection highlight or blinking caret.
    if (value.selection.collapsed) {
        if (focused && (blinkTick / CARET_BLINK_FRAMES) % 2 == 0) {
            val caretX = xForOffset(font, text, value.selection.min, scrollX)
            g.fill(caretX, 2, caretX + 2, height - 2, colors.textCaret.value.toInt())
        }
    } else {
        val selStart = xForOffset(font, text, value.selection.min, scrollX)
        val selEnd = xForOffset(font, text, value.selection.max, scrollX)
        g.fill(selStart, 2, selEnd, height - 2, colors.textSelection.value.toInt())
    }

    // Composing-text underline (IME preedit region, when the buffer reports one).
    val composition = value.composition
    if (composition != null && !composition.collapsed) {
        val compStart = xForOffset(font, text, composition.min, scrollX)
        val compEnd = xForOffset(font, text, composition.max, scrollX)
        g.fill(compStart, height - 3, compEnd, height - 1, colors.textCaret.value.toInt())
    }
}

/** Screen-space x of the text offset [offset] given the current scroll. */
private fun xForOffset(font: Font, text: String, offset: Int, scrollX: Float): Int =
    (TEXT_PAD_LEFT + font.width(text.substring(0, offset)) - scrollX).roundToInt()

private fun computeTargetScroll(font: Font, value: TextFieldValue, current: Float, width: Int): Float {
    val visibleWidth = width - TEXT_PAD_LEFT - TEXT_PAD_RIGHT
    val maxScroll = max(0, font.width(value.text) - visibleWidth).toFloat()
    val caretX = font.width(value.text.substring(0, value.selection.max)).toFloat()
    var target = current
    if (caretX - target < 0f) {
        target = caretX
    } else if (caretX - target > visibleWidth) {
        target = caretX - visibleWidth
    }
    return target.coerceIn(0f, maxScroll)
}

private val fieldIdCounter = java.util.concurrent.atomic.AtomicInteger()

private fun nextFieldId(): Int = fieldIdCounter.incrementAndGet()

private const val TEXT_PAD_LEFT = 3
private const val TEXT_PAD_RIGHT = 3
private const val CARET_BLINK_FRAMES = 20

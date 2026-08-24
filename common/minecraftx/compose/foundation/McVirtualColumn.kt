package minecraftx.compose.foundation

import androidx.compose.ui.graphics.toArgb
import allyouneed.client.compose.platform.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import minecraftx.compose.material.McText
import minecraftx.compose.theme.McTheme
import net.minecraft.network.chat.Component

/**
 * A single pre-positioned text line for [McVirtualColumn]: [y] is the absolute logical Y (before
 * applying the scroll offset), [x] the left indent and [color] the paint fallback. When [color] is
 * null the active theme's [McTheme.colors] primary text color is used.
 */
data class McLine(
    val text: Component,
    val x: Int,
    val y: Int,
    val color: Int? = null,
)

/**
 * A virtualized, smooth-scrollable column of [McLine]s. Rows outside the viewport (minus one line
 * of headroom) are not composed at all, and rows that only partially overlap the viewport are
 * clipped pixel-perfectly by [McText]'s hardware scissor — the same strategy used by the item
 * details content area. The wheel scrolls via the [Modifier.mcScroll] handler applied to the
 * container, so a host screen needs no `mouseScrolled` logic at all.
 *
 * Size the modifier to the viewport (e.g. `Modifier.size(viewportW.dp, viewportH.dp)`).
 */
@Composable
fun McVirtualColumn(
    lines: List<McLine>,
    state: ScrollState,
    modifier: Modifier = Modifier,
    viewportWidth: Int,
    viewportHeight: Int,
    lineHeight: Int,
) {
    Box(modifier.mcScroll(state)) {
        val offset = state.display
        val defaultColor = McTheme.colors.textPrimary.toArgb()
        for (line in lines) {
            val y = line.y - offset
            if (y >= -lineHeight.toFloat() && y < viewportHeight.toFloat()) {
                McText(
                    text = line.text,
                    modifier = Modifier.offset(line.x.dp, y.dp),
                    color = line.color ?: defaultColor,
                    maxWidth = viewportWidth - line.x,
                    clipFrame = Rect(
                        -line.x.toFloat(),
                        -y,
                        (viewportWidth - line.x).toFloat(),
                        (viewportHeight - y).toFloat(),
                    ),
                )
            }
        }
    }
}

/**
 * Routes pointer [PointerEventType.Scroll] events into [state] ([ScrollState.scrollBy]), so wheel
 * input over the modified node scrolls the region with the framework's smoothing. Only nodes the
 * cursor is actually over receive scroll events (official hit testing), so a host screen does not
 * need to re-implement the "am I inside the panel?" geometry check.
 *
 * The event is **consumed** so that nested scroll containers (e.g. an [McScrollBox] inside another
 * [McScrollBox]) do not both scroll on one wheel notch: the inner container handles the notch first
 * (leaf-first hit path), and outer containers skip already-consumed deltas.
 */
fun Modifier.mcScroll(state: ScrollState): Modifier = pointerInput(state) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent()
            if (event.type == PointerEventType.Scroll) {
                val change = event.changes.firstOrNull() ?: continue
                if (change.isConsumed) continue
                val dy = change.scrollDelta.y
                if (dy == 0f) continue
                state.scrollBy(-dy * ScrollState.WHEEL_STEP)
                change.consume()
            }
        }
    }
}

package minecraftx.compose.foundation

import allyouneed.client.compose.platform.McGraphics
import allyouneed.client.compose.platform.ScrollState
import allyouneed.client.compose.platform.rememberScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import minecraftx.compose.material.McScrollbar
import minecraftx.compose.theme.McTheme
import kotlin.math.min

/**
 * A generic overflow container hosting virtual content of [contentWidth] x [contentHeight] logical
 * pixels; the viewport size comes from the incoming [modifier] (e.g. `Modifier.fillMaxSize()` inside
 * a sized parent, or `Modifier.size(w, h)` directly).
 *
 * Two content models are supported:
 *
 *  - **fixed** (default): pass [contentHeight] and children are positioned with absolute logical
 *    offsets relative to the content origin, like a virtual canvas.
 *  - **flow** (pass `contentHeight = null`): the content is measured with unbounded height and the
 *    viewport is clamped to the screen, so a plain `Column` (or any flow layout) can be scrolled as
 *    a whole — the measured height is fed back into the scroll range automatically.
 *
 * Overflow policies (constructor flags):
 *
 *  - **clamp** (default): the content origin is pinned to the viewport top and the content is
 *    clipped to the viewport bounds, so nothing ever spills outside — rows below the fold are
 *    simply invisible, exactly like a `Box` + `graphicsLayer(clip = true)`.
 *  - **scroll** (default): with [scrollable] enabled the content is additionally scrolled vertically
 *    by the shared [state] (wheel via the viewport's `mcScroll` handler, plus an optional
 *    [McScrollbar] drawn on top). [autoScroll] re-clamps the offset when the viewport shrinks
 *    (window resize) and pins the content top once the whole area fits.
 *  - **ignore**: with [clip] disabled the content overflows unconditionally — use sparingly.
 *
 * Content children are clipped pixel-perfectly by the same hardware-scissor technique as [McText]'s
 * `clipFrame`; content children should therefore not need their own [McText] `clipFrame` (nested
 * scissor regions are not supported).
 */
@Composable
fun McScrollBox(
    contentWidth: Int = Int.MAX_VALUE,
    contentHeight: Int? = null,
    modifier: Modifier = Modifier,
    scrollable: Boolean = true,
    clip: Boolean = true,
    autoScroll: Boolean = true,
    state: ScrollState = rememberScrollState(),
    backgroundColor: Color? = null,
    scrollbarWidth: Int = 4,
    scrollbarColor: Color = McTheme.colors.scrollbarBar,
    scrollbarTrackColor: Color = McTheme.colors.scrollbarTrack,
    content: @Composable BoxScope.() -> Unit,
) {
    BoxWithConstraints(modifier.then(if (scrollable) Modifier.mcScroll(state) else Modifier)) {
        val viewportW = min(constraints.maxWidth, contentWidth)
        // flow 模式:首次测量前高度未知(取 0),测量完成后写回真实内容高度再重排。
        var measuredContentH by remember { mutableStateOf(contentHeight ?: 0) }
        val contentH = contentHeight ?: measuredContentH
        val viewportH = min(constraints.maxHeight, contentH)
        state.maxScroll = if (scrollable) (contentH - viewportH).coerceAtLeast(0).toFloat() else 0f
        if (autoScroll && state.display > state.maxScroll) state.seek(state.maxScroll)
        val offset = state.display

        Box(
            Modifier
                .size(viewportW.dp, viewportH.dp)
                .then(if (backgroundColor != null) Modifier.background(backgroundColor) else Modifier)
                .then(if (clip) Modifier.scissorClip() else Modifier),
        ) {
            Box(
                Modifier
                    .offset(0.dp, (-offset).dp)
                    .then(
                        if (contentHeight != null) {
                            Modifier.size(contentWidth.dp, contentHeight.dp)
                        } else {
                            Modifier.layoutUnboundedHeight { measuredContentH = it }
                        },
                    ),
            ) { content() }
        }

        if (scrollable && state.maxScroll > 0f && scrollbarWidth > 0) {
            McScrollbar(
                state = state,
                modifier = Modifier
                    .offset((viewportW - scrollbarWidth).dp, 0.dp)
                    .size(scrollbarWidth.dp, viewportH.dp),
                trackWidth = scrollbarWidth.dp,
                barWidth = scrollbarWidth.dp,
                trackColor = scrollbarTrackColor,
                barColor = scrollbarColor,
            )
        }
    }
}

/**
 * Measures the node's content with unbounded height so flow layouts can size themselves naturally,
 * reports the node size as the incoming viewport constraints, and hands the measured content height
 * to [onHeight] (which feeds it back into the scroll range). Content taller than the viewport still
 * draws (parents never clip) — the enclosing viewport's scissor is what crops it.
 */
private fun Modifier.layoutUnboundedHeight(onHeight: (Int) -> Unit): Modifier =
    layout { measurable, constraints ->
        val placeable = measurable.measure(constraints.copy(maxHeight = Constraints.Infinity))
        onHeight(placeable.height)
        layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(0, 0) }
    }

/**
 * Clips the node's content to the node's own bounds using the same hardware-scissor technique as
 * [McText]'s `clipFrame`: the rectangle is derived from the live modelview pose so it stays
 * pixel-aligned with the content regardless of zoom, and the scissor is torn down as soon as the
 * content pass finishes.
 */
private fun Modifier.scissorClip(): Modifier = drawWithContent {
    val g = McGraphics.current ?: return@drawWithContent
    val matrix = g.pose().last().pose()
    val nodeX = matrix.m30()
    val nodeY = matrix.m31()
    val scaleX = matrix.m00()
    val scaleY = matrix.m11()
    val clipLeft = nodeX.toInt()
    val clipTop = nodeY.toInt()
    val clipRight = (nodeX + size.width * scaleX).toInt()
    val clipBottom = (nodeY + size.height * scaleY).toInt()
    if (clipRight <= clipLeft || clipBottom <= clipTop) return@drawWithContent
    g.enableScissor(clipLeft, clipTop, clipRight, clipBottom)
    try {
        drawContent()
        g.flush()
    } finally {
        g.disableScissor()
    }
}

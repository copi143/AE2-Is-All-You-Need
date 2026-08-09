package allyouneed.client.compose.material

import allyouneed.client.compose.platform.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.max

/**
 * Slim vertical scrollbar bound to a [ScrollState]. Clicking the track jumps directly; dragging
 * scrubs 1:1 with the cursor ([ScrollState.seek] writes the display value immediately, skipping the
 * smooth animation). Positions are logical (root constraints are /scale), so the mapping to the
 * scroll value stays consistent with the wheel.
 *
 * The composable measures itself from the incoming modifier — size it with `Modifier.size(width,
 * trackHeight)` (or `fillMaxHeight()` inside a sized parent). It draws nothing when
 * [ScrollState.maxScroll] is zero.
 */
@Composable
fun McScrollbar(
    state: ScrollState,
    modifier: Modifier = Modifier,
    trackWidth: androidx.compose.ui.unit.Dp = 4.dp,
    barWidth: androidx.compose.ui.unit.Dp = 2.dp,
    trackColor: Color = Color(0xAA444444),
    barColor: Color = Color(0xFFAAAAAA),
) {
    BoxWithConstraints(modifier) {
        if (state.maxScroll <= 0f) return@BoxWithConstraints
        val trackHeight = constraints.maxHeight
        val barHeight = max(16, trackHeight * trackHeight / (trackHeight + state.maxScroll.toInt()))
        val travel = trackHeight - barHeight
        val barY = if (state.maxScroll == 0f) 0 else (travel * state.display / state.maxScroll).toInt()
        Box(
            Modifier
                .fillMaxSize()
                .background(trackColor)
                .pointerInput(state.maxScroll, travel) {
                    var dragging = false
                    var grabOffset = 0f
                    fun seek(y: Float) {
                        val frac = ((y - grabOffset) / travel).coerceIn(0f, 1f)
                        state.seek(frac * state.maxScroll)
                    }
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            val p = event.changes.firstOrNull()?.position ?: continue
                            when (event.type) {
                                PointerEventType.Press -> {
                                    dragging = true
                                    val barCenter = travel * state.display / state.maxScroll
                                    grabOffset = p.y - barCenter
                                    seek(p.y)
                                }
                                PointerEventType.Move -> if (dragging) seek(p.y)
                                PointerEventType.Release -> dragging = false
                                else -> Unit
                            }
                        }
                    }
                },
        )
        Box(
            Modifier
                .fillMaxWidth()
                .offset(x = ((trackWidth - barWidth) / 2), y = barY.dp)
                .size(barWidth, barHeight.dp)
                .background(barColor),
        )
    }
}

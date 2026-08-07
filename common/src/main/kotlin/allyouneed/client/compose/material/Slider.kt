package allyouneed.client.compose.material

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp

@Composable
fun Slider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    range: ClosedFloatingPointRange<Float> = 0f..1f,
) {
    Layout(
        content = {},
        modifier = modifier
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    change.consume()
                    val fraction =
                        (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                    onValueChange(range.start + fraction * (range.endInclusive - range.start))
                }
            }
            .drawBehind {
                drawSlider(value, range)
            },
    ) { _, constraints: androidx.compose.ui.unit.Constraints ->
        val w = constraints.constrainWidth(200.dp.roundToPx())
        val h = constraints.constrainHeight(20.dp.roundToPx())
        layout(w, h) {}
    }
}

private fun DrawScope.drawSlider(value: Float, range: ClosedFloatingPointRange<Float>) {
    val trackY = size.height / 2f
    val fraction = ((value - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)
    val thumbX = fraction * size.width

    drawRect(
        color = Color(0xFF444444),
        topLeft = Offset(0f, trackY - 2f),
        size = Size(size.width, 4f),
    )
    drawRect(
        color = Color(0xFFAAAAAA),
        topLeft = Offset(0f, trackY - 2f),
        size = Size(thumbX, 4f),
    )
    drawRect(
        color = Color.White,
        topLeft = Offset(thumbX - 4f, trackY - 4f),
        size = Size(8f, 8f),
    )
}

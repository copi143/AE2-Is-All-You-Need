package allyouneed.client.compose.material

import allyouneed.client.compose.ui.draw.McDrawScope
import allyouneed.client.compose.ui.layout.*
import allyouneed.client.compose.ui.modifier.DrawModifier
import allyouneed.client.compose.ui.modifier.Modifier
import androidx.compose.runtime.Composable

@Composable
fun Slider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    range: ClosedFloatingPointRange<Float> = 0f..1f,
) {
    Layout(
        modifier = modifier then SliderDrawModifier(value, range),
        measurePolicy = SliderMeasurePolicy,
        content = {},
    )
}

private object SliderMeasurePolicy : MeasurePolicy {
    override fun measure(
        scope: MeasureScope,
        measurables: List<Measurable>,
        constraints: Constraints,
    ): MeasureResult {
        val w = constraints.constrainWidth(200)
        val h = constraints.constrainHeight(20)
        return MeasureResult(w, h, emptyList()) {}
    }
}

private class SliderDrawModifier(
    private val value: Float,
    private val range: ClosedFloatingPointRange<Float>,
) : DrawModifier {
    override fun draw(scope: McDrawScope, drawContent: () -> Unit) {
        val w = scope.currentWidth
        val h = scope.currentHeight
        val trackY = h / 2 - 1
        val thumbX = ((value - range.start) / (range.endInclusive - range.start) * (w - 8)).toInt()

        scope.fillRect(0, trackY, w, 4, 0xFF444444.toInt())
        scope.fillRect(0, trackY, thumbX + 4, 4, 0xFFAAAAAA.toInt())
        scope.fillRect(thumbX, trackY - 2, 8, 8, 0xFFFFFFFF.toInt())
    }
}

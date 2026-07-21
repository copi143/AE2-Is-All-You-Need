package allyouneed.compose.ui.layout

import allyouneed.compose.ui.modifier.Modifier
import androidx.compose.runtime.Composable

@Composable
fun Box(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Layout(modifier = modifier, measurePolicy = BoxMeasurePolicy, content = content)
}

private object BoxMeasurePolicy : MeasurePolicy {
    override fun measure(
        scope: MeasureScope,
        measurables: List<Measurable>,
        constraints: Constraints,
    ): MeasureResult {
        var maxW = 0
        var maxH = 0
        val placeables = measurables.map { m ->
            val p = m.measure(constraints)
            maxW = maxOf(maxW, p.width)
            maxH = maxOf(maxH, p.height)
            p
        }
        maxW = constraints.constrainWidth(maxW)
        maxH = constraints.constrainHeight(maxH)
        return MeasureResult(maxW, maxH, placeables) {
            placeables.forEach { it.place(0, 0) }
        }
    }
}

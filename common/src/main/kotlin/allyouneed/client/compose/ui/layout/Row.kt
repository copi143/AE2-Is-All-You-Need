package allyouneed.client.compose.ui.layout

import allyouneed.client.compose.ui.modifier.Modifier
import androidx.compose.runtime.Composable

@Composable
fun Row(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Layout(modifier = modifier, measurePolicy = RowMeasurePolicy, content = content)
}

private object RowMeasurePolicy : MeasurePolicy {
    override fun measure(
        scope: MeasureScope,
        measurables: List<Measurable>,
        constraints: Constraints,
    ): MeasureResult {
        var totalW = 0
        var maxH = 0
        val placeables = measurables.map { m ->
            val p = m.measure(Constraints(maxHeight = constraints.maxHeight))
            totalW += p.width
            maxH = maxOf(maxH, p.height)
            p
        }
        totalW = constraints.constrainWidth(totalW)
        maxH = constraints.constrainHeight(maxH)
        var x = 0
        return MeasureResult(totalW, maxH, placeables) {
            placeables.forEach { p ->
                p.place(x, 0)
                x += p.width
            }
        }
    }
}

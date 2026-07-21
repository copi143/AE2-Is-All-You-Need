package allyouneed.compose.ui.layout

import allyouneed.compose.ui.modifier.Modifier
import androidx.compose.runtime.Composable

@Composable
fun Column(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Layout(modifier = modifier, measurePolicy = ColumnMeasurePolicy, content = content)
}

private object ColumnMeasurePolicy : MeasurePolicy {
    override fun measure(
        scope: MeasureScope,
        measurables: List<Measurable>,
        constraints: Constraints,
    ): MeasureResult {
        var totalH = 0
        var maxW = 0
        val placeables = measurables.map { m ->
            val p = m.measure(Constraints(maxWidth = constraints.maxWidth))
            totalH += p.height
            maxW = maxOf(maxW, p.width)
            p
        }
        maxW = constraints.constrainWidth(maxW)
        totalH = constraints.constrainHeight(totalH)
        var y = 0
        return MeasureResult(maxW, totalH, placeables) {
            placeables.forEach { p ->
                p.place(0, y)
                y += p.height
            }
        }
    }
}

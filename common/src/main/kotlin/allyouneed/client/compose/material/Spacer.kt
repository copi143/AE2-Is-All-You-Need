package allyouneed.client.compose.material

import allyouneed.client.compose.ui.layout.*
import allyouneed.client.compose.ui.modifier.Modifier
import androidx.compose.runtime.Composable

@Composable
fun Spacer(modifier: Modifier = Modifier) {
    Layout(
        modifier = modifier,
        measurePolicy = SpacerMeasurePolicy,
        content = {},
    )
}

private object SpacerMeasurePolicy : MeasurePolicy {
    override fun measure(
        scope: MeasureScope,
        measurables: List<Measurable>,
        constraints: Constraints,
    ): MeasureResult {
        return MeasureResult(
            constraints.constrainWidth(0),
            constraints.constrainHeight(0),
            emptyList(),
        ) {}
    }
}

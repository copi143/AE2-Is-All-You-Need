package allyouneed.compose.material

import allyouneed.compose.ui.layout.Constraints
import allyouneed.compose.ui.layout.Measurable
import allyouneed.compose.ui.layout.MeasurePolicy
import allyouneed.compose.ui.layout.MeasureResult
import allyouneed.compose.ui.layout.MeasureScope
import allyouneed.compose.ui.modifier.Modifier
import androidx.compose.runtime.Composable

@Composable
fun Spacer(modifier: Modifier = Modifier) {
    allyouneed.compose.ui.layout.Layout(
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

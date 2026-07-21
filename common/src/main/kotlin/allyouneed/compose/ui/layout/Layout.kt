package allyouneed.compose.ui.layout

import allyouneed.compose.ui.modifier.Modifier
import allyouneed.compose.ui.node.LayoutNode
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ComposeNode

@Composable
fun Layout(
    modifier: Modifier = Modifier,
    measurePolicy: MeasurePolicy,
    content: @Composable () -> Unit,
) {
    ComposeNode<LayoutNode, allyouneed.compose.ui.node.UiApplier>(
        factory = { LayoutNode() },
        update = {
            set(measurePolicy) { this.measurePolicy = it }
            set(modifier) { this.modifier = it }
        },
        content = content,
    )
}
